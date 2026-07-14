package com.activetour.wbbotcontroller.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.activetour.wbbotcontroller.MainActivity
import com.activetour.wbbotcontroller.model.StickerForOrder
import com.activetour.wbbotcontroller.model.WBOrder
import com.activetour.wbbotcontroller.utils.PreferencesManager
import com.activetour.wbbotcontroller.worker.CheckOrdersWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

import org.telegram.telegrambots.meta.api.objects.InputFile
//import org.telegram.telegrambots.meta.api.objects.ChatId
import java.io.File

class TelegramBotService : Service(), LongPollingSingleThreadUpdateConsumer {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var telegramClient: OkHttpTelegramClient
    private lateinit var botApplication: TelegramBotsLongPollingApplication
    private lateinit var preferencesManager: PreferencesManager

    // ID последнего обработанного заказа (чтобы не дублировать)
    private var lastCheckedOrderId = 0L

    // ID текущей активной поставки
    private var currentSupplyId: String? = null

    companion object {
        private const val TAG = "TelegramBotService"
        var isRunning = false
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "TelegramBotChannel"
        private const val PERIODIC_WORK_NAME = "telegram_bot_periodic_check"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "========== onCreate: НАЧАЛО ==========")

        try {
            preferencesManager = PreferencesManager(this)
            Log.d(TAG, "✅ onCreate: PreferencesManager инициализирован")

            val isBotEnabled = preferencesManager.isBotEnabled()
            Log.d(TAG, "onCreate: isBotEnabled = $isBotEnabled")

            if (!isBotEnabled) {
                Log.w(TAG, "⚠️ onCreate: botEnabled = false, останавливаем сервис")
                stopSelf()
                return
            }

            createNotificationChannel()
            Log.d(TAG, "✅ onCreate: NotificationChannel создан")

            startForeground(NOTIFICATION_ID, createNotification())
            Log.d(TAG, "✅ onCreate: startForeground выполнен")

            isRunning = true
            Log.d(TAG, "✅ onCreate: isRunning = true")

            startBot()
            Log.d(TAG, "✅ onCreate: startBot вызван")

        } catch (e: Exception) {
            Log.e(TAG, "❌ onCreate: КРИТИЧЕСКАЯ ОШИБКА!", e)
            stopSelf()
        }
        Log.d(TAG, "========== onCreate: КОНЕЦ ==========")
    }

    private fun startBot() {
        Log.d(TAG, "========== startBot: НАЧАЛО ==========")

        try {
            val token = preferencesManager.getBotToken()
            if (token.isEmpty()) {
                Log.e(TAG, "❌ startBot: токен ПУСТ!")
                showAndroidNotification("Ошибка", "Токен бота не настроен!")
                stopSelf()
                return
            }

            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                try {
                    Log.d(TAG, "startBot: создание OkHttpTelegramClient...")
                    telegramClient = OkHttpTelegramClient(token)
                    Log.d(TAG, "✅ startBot: OkHttpTelegramClient создан")

                    Log.d(TAG, "startBot: создание TelegramBotsLongPollingApplication...")
                    botApplication = TelegramBotsLongPollingApplication()
                    Log.d(TAG, "✅ startBot: TelegramBotsLongPollingApplication создан")

                    Log.d(TAG, "startBot: регистрация бота...")
                    botApplication.registerBot(token, this@TelegramBotService)
                    Log.d(TAG, "✅ startBot: бот успешно зарегистрирован")

                    val botId = token.split(":").firstOrNull() ?: ""
                    if (botId.isNotEmpty()) {
                        preferencesManager.addChatId(botId)
                        Log.d(TAG, "✅ ID бота сохранён: $botId")
                    }

                    Log.d(TAG, "startBot: запуск периодической проверки...")
                    startPeriodicCheck()
                    Log.d(TAG, "✅ startBot: периодическая проверка запущена")

                    // Проверяем заказы только если уже есть активный чат
                    if (preferencesManager.getAllChatIds().isNotEmpty()) {
                        Log.d(TAG, "startBot: первая проверка заказов...")
                        withContext(Dispatchers.IO) {
                            checkAndNotifyAboutOrders(true)
                        }
                        Log.d(TAG, "✅ startBot: первая проверка выполнена")
                    } else {
                        Log.d(
                            TAG,
                            "startBot: чат ещё не активирован, первая проверка отложена до получения сообщения"
                        )
                    }

                    showAndroidNotification("✅ Бот запущен", "Бот успешно запущен")

                    Log.d(TAG, "✅✅✅ startBot: БОТ УСПЕШНО ЗАПУЩЕН! ✅✅✅")

                } catch (e: Exception) {
                    Log.e(TAG, "❌ startBot: ОШИБКА в корутине!", e)
                    showAndroidNotification("❌ Ошибка запуска бота", "Проверьте токен и интернет")
                    stopSelf()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ startBot: ОШИБКА вне корутины!", e)
            showAndroidNotification("❌ Критическая ошибка", "Ошибка при запуске бота")
            stopSelf()
        }

        Log.d(TAG, "========== startBot: КОНЕЦ ==========")
    }

    /**
     * Запуск периодической проверки через WorkManager
     */
    private fun startPeriodicCheck() {
        Log.d(TAG, "---------- startPeriodicCheck: НАЧАЛО ----------")

        try {
            val minutes = preferencesManager.getCheckIntervalMinutes().toLong()
            val actualInterval = if (minutes < 15) 15 else minutes
            Log.d(TAG, "startPeriodicCheck: интервал = $actualInterval минут")

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicWorkRequest = PeriodicWorkRequestBuilder<CheckOrdersWorker>(
                actualInterval, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest
            )
            Log.d(TAG, "✅ startPeriodicCheck: WorkManager выполнен")

        } catch (e: Exception) {
            Log.e(TAG, "❌ startPeriodicCheck: ОШИБКА!", e)
        }

        Log.d(TAG, "---------- startPeriodicCheck: КОНЕЦ ----------")
    }

    /**
     * Автоматическая проверка заказов (отправка во все чаты)
     */
    private suspend fun checkAndNotifyAboutOrders(autoCheck: Boolean) {
        Log.d(TAG, "========== checkAndNotifyAboutOrders: НАЧАЛО ==========")

        try {
            // ✅ ЖДЁМ 1 СЕКУНДУ ПЕРЕД КАЖДЫМ ЗАПРОСОМ (для соблюдения лимита)
            delay(1000)

            val orderChecker = WbOrderChecker(applicationContext)
            val newOrders = orderChecker.getNewOrders()

            if (newOrders.isEmpty()) {
                if (!autoCheck) sendMessageToAllChats(" Новых заказов нет.")
                Log.d(TAG, "checkAndNotifyAboutOrders: новых заказов нет")
                return
            }

            val actualNewOrders = newOrders.filter { it.id!! > lastCheckedOrderId }
            if (actualNewOrders.isEmpty()) {
                Log.d(TAG, "checkAndNotifyAboutOrders: новых заказов после фильтрации нет")
                return
            }

            // Формируем сообщение о новых заказах
            val ordersMessage = buildString {
//                appendLine("📦 *НОВЫЕ ЗАКАЗЫ!*")
//                appendLine()
                actualNewOrders.forEach { order ->
                    appendLine("• *${order.article}*")
                    appendLine("  Номер: `${order.id}`")
                    appendLine("  Дата: ${order.createdAt!!.replace("T", " ").replace("Z", "")}")
                    appendLine()
                }
            }

            sendMessageToAllChats(ordersMessage)

            actualNewOrders.forEach { order ->
                if (order.id!! > lastCheckedOrderId) {
                    lastCheckedOrderId = order.id
                }
            }

            // Создание поставок или давление в существующую
            processSupplyAndAddOrders(actualNewOrders)

            Log.d(TAG, "✅ checkAndNotifyAboutOrders: УСПЕШНО ЗАВЕРШЕНА")

        } catch (e: Exception) {
            Log.e(TAG, "❌ checkAndNotifyAboutOrders: ОШИБКА!", e)
            sendMessageToAllChats("⚠️ *Ошибка при проверке заказов*\n${e.message}")
        }

        Log.d(TAG, "========== checkAndNotifyAboutOrders: КОНЕЦ ==========")
    }

    /**
     * Автоматическое создание поставки и добавление заказов
     */
    private suspend fun processSupplyAndAddOrders(orders: List<WBOrder>) {
        if (orders.isEmpty()) return

        val orderIds = orders.map { it.id }
        Log.d(TAG, "processSupplyAndAddOrders: orderIds = $orderIds")

        try {
            val supplyChecker = WbSupplyChecker(applicationContext)
            currentSupplyId = supplyChecker.getLastActiveSupply()

            val supplyType = if (currentSupplyId != null) " " else "НОВУЮ (созданную мной)"

            if (currentSupplyId == null) {
                val supplyName = "Поставка от ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                }"
                currentSupplyId = supplyChecker.createSupply(supplyName)

                if (currentSupplyId == null) {
                    sendMessageToAllChats("❌ *КРИТИЧЕСКАЯ ОШИБКА*\nНе удалось создать поставку!")
                    return
                }
            }

            val success = supplyChecker.addOrdersToSupply(currentSupplyId!!, orderIds)

            if (success) {
                val message = buildString {
                    appendLine("Заказы добавлены в $supplyType ")
                    appendLine("поставку: `${currentSupplyId}`")
                    appendLine("Добавлено заказов: ${orderIds.size}")
                }
                // ✅ Отправляем ВО ВСЕ чаты
                sendMessageToAllChats(message)
            } else {
                sendMessageToAllChats("❌ *Ошибка при добавлении заказов в поставку*")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ processSupplyAndAddOrders: ОШИБКА!", e)
            sendMessageToAllChats("❌ *Ошибка при обработке поставки*\n${e.message}")
        }
    }

    /**
     * Добавление поставки в доставку и получение QR кодов
     */
    private suspend fun sendToDeliveryAndGetQRCodes() {
        Log.d(TAG, "========== sendToDeliveryAndGetQRCodes: НАЧАЛО ==========")
        try {
            val supplyChecker = WbSupplyChecker(applicationContext)
            currentSupplyId = supplyChecker.getLastActiveSupply()

            if (currentSupplyId == null) {
                sendMessageToAllChats("Поставки для добавления в доставку нет!")
                return
            }

            val delivery = WBDeliveryAndQRCodes(applicationContext)
            Log.d(
                TAG,
                "= sendToDeliveryAndGetQRCodes: поставку - $currentSupplyId добавляем в доставку и получаем QR коды ="
            )




            // 4. Добавляем грузоместа
            if (!delivery.addCargoLocations(currentSupplyId!!)) sendMessageToAllChats("❌ Ошибка добавления грузомест в поставку $currentSupplyId.")

            // 2. Получаем стикеры (QR-коды) заказов в поставке
            val stickersForOrders: List<StickerForOrder> = delivery.getStickersForOrders(currentSupplyId)
            if (stickersForOrders == null) {
                sendMessageToAllChats("❌ Ошибка при получении стикеров для заказов в поставке $currentSupplyId.")
            } else {
                sendStickersToAllChats(stickersForOrders, "стикеры для заказов в поставке $currentSupplyId")
            }

            // 1. Получаем QR-код поставки
            val suppliesQRCodeFile = delivery.getQRCodesSupplies(currentSupplyId!!)
            if (suppliesQRCodeFile == null) {
                sendMessageToAllChats("❌ Ошибка при получении QR кода для поставки $currentSupplyId.")
            } else {
                sendDocumentToAllChats(suppliesQRCodeFile, "QR-код поставки $currentSupplyId")
            }

            // 3. Генерируем файл листа подбора
            val excelFile = delivery.generateSelectionSheet(currentSupplyId!!)
            if (excelFile != null) {
                sendDocumentToAllChats(excelFile, "Лист подбора для поставки $currentSupplyId")
                // После отправки удалить временный файл (опционально)
                excelFile.delete()
            } else {
                sendMessageToAllChats( "❌ Ошибка при формировании Листа подбора для поставки $currentSupplyId.")
            }





//            val answer = delivery.sendToDeliveryAndGetQRCodes(currentSupplyId)
//            Log.d(
//                TAG,
//                "= sendToDeliveryAndGetQRCodes: поставка - $answer добавлена в доставку ="
//            )


//            val success = supplyChecker.addOrdersToSupply(currentSupplyId!!, orderIds)

            if (answer.isBlank()) {
                val message = buildString {
                    appendLine("Заказы добавлены в $supplyType ")
                    appendLine("поставку: `${currentSupplyId}`")
                    appendLine("Добавлено заказов: ${orderIds.size}")
                }
                // ✅ Отправляем ВО ВСЕ чаты
                sendMessageToAllChats(message)
            } else {
                sendMessageToAllChats(answer)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ processSupplyAndAddOrders: ОШИБКА!", e)
            sendMessageToAllChats("❌ *Ошибка при обработке поставки*\n${e.message}")
        }
    }

    /**
     * Отправление листа подбора в Telegram
     */
    private fun sendDocumentToAllChats(document: File, caption: String = "") {
        val chatIds = preferencesManager.getAllChatIds()
        if (chatIds.isEmpty()) {
            Log.d(TAG, "Нет активных чатов для отправки документа")
            return
        }

        val messageThreadId = preferencesManager.getMessageThreadId() // <-- Получаем ID подгруппы из настроек
        for (chatId in chatIds) {
            try {
                val sendDocument = SendDocument.builder()
                    .chatId(chatId)
                    .document(InputFile(document))
                    .apply {
                        if (messageThreadId > 0) {
                            this.messageThreadId(messageThreadId) // <-- Устанавливаем ID подгруппы
                        }
                    }
                    .caption(caption)
                    .build()

                telegramClient.execute(sendDocument)
                Log.d(TAG, "✅ Документ отправлен в чат $chatId")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Ошибка отправки документа в чат $chatId: ${e.message}", e)
            }
        }
    }

    /**
     * Обработка входящих сообщений от Telegram
     */
    override fun consume(update: Update) {
        Log.d(TAG, "🔔 consume: получено обновление от Telegram")

        try {
            if (update.hasMessage() && update.message.hasText()) {

                val chatId = update.message.chatId.toString()
                val text = update.message.text
                val threadId = update.message.messageThreadId // <-- Получаем ID подгруппы
                Log.d(TAG, "consume: сообщение из чата $chatId, $threadId: '$text'")

                // Проверяем, активирован ли уже этот чат
                if (!preferencesManager.getAllChatIds().contains(chatId)) {
                    // Чат ещё не активирован – ждём только команду СТАРТ
                    if (text.equals("СТАРТ", ignoreCase = true)) {
                        // Активируем чат
                        addChat(chatId)
                        if (threadId != null && threadId != 0) {
                            preferencesManager.setMessageThreadId(threadId)
                            Log.d(TAG, "✅ ID подгруппы сохранён: $threadId")
                        }
                        preferencesManager.setWelcomeSent(true) // отметим, что приветствие уже отправим
                        // Отправляем приветственное сообщение
                        sendMessage(chatId, buildString {
                            appendLine("✅ *Бот активирован!*")
//                            appendLine("🤖 Теперь я буду отслеживать заказы Wildberries")
//                            appendLine("и отправлять уведомления в этот чат.")
//                            appendLine()
//                            appendLine("📌 *Команды:*")
                            appendLine("• `Жиган проверь` - проверить заказы сейчас")
                            appendLine("• `QR коды 1` - Добавить поставку в доставку и полючить QR коды, 1 можно заменить на другое число")
//                            appendLine("• `/status` - статус бота")
//                            appendLine("• `/help` - справка")
                        })
                        // После активации можно сразу проверить заказы (опционально)
                        serviceScope.launch {
                            checkAndNotifyAboutOrders(false)
                        }
                    }

                    return // Дальше не обрабатываем другие команды
                }

                // --- Чат уже активирован – обрабатываем команды обычным образом ---
                // Обработка команд
                when {
                    text.contains("Жиган проверь", ignoreCase = true) -> {
                        sendMessageToAllChats("🔍 Выполняю проверку...")
                        serviceScope.launch {
//                            checkAndNotifyAboutOrdersForChat(chatId)
                            checkAndNotifyAboutOrders(false)   // ← рассылает во все чаты
                        }
                    }

                    text.contains("QR коды", ignoreCase = true) -> {
                        // Извлекаем число из текста
                        val numberCargoSpaces = text.replace(Regex("[^0-9]"), "").toIntOrNull()

                        if (numberCargoSpaces != null && numberCargoSpaces > 0) {
                            preferencesManager.setNumberCargoSpaces(numberCargoSpaces)
                            sendMessageToAllChats("Добовляю в доставку...")
                            serviceScope.launch {
                                sendToDeliveryAndGetQRCodes()
                            }
                        } else {
                            sendMessageToAllChats("❌ После команды 'QR коды' укажите количество коробок.\nПример: `QR коды 1`")
                        }
                    }


                    text.equals("/status", ignoreCase = true) -> {
                        sendMessage(chatId, buildString {
                            appendLine("✅ *Статус бота:*")
                            appendLine("• Состояние: Активен")
                            appendLine(
                                "• 🕐 ${
                                    LocalDateTime.now()
                                        .format(DateTimeFormatter.ofPattern("HH:mm:ss dd.MM.yyyy"))
                                }"
                            )
                            appendLine("• 📊 Интервал проверки: ${preferencesManager.getCheckIntervalMinutes()} минут")
                        })
                    }

                    text.equals("/help", ignoreCase = true) -> {
                        sendMessage(chatId, buildString {
                            appendLine("🤖 *Доступные команды:*")
                            appendLine("• `Жиган проверь` - проверить заказы")
                            appendLine("• `QR коды` - Добавить поставку в доставку и полючить QR коды")
                            appendLine("• `/status` - статус бота")
                            appendLine("• `/help` - эта справка")
                        })
                    }

                    else -> {
                        // Не отвечаем на обычные сообщения
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ consume: ОШИБКА!", e)
        }
    }

    /**
     * Добавляет чат в список автоматически
     */
    private fun addChat(chatId: String) {
        if (chatId.isEmpty()) return

        if (preferencesManager.getAllChatIds().contains(chatId)) {
            Log.d(TAG, "addChat: пропускаем ID бота")
            return
        } else {
            preferencesManager.addChatId(chatId)
            Log.d(TAG, "✅ Чат автоматически добавлен: $chatId")
        }
    }

    /**
     * Отправляет сообщение во все чаты
     */
    private fun sendMessageToAllChats(text: String) {
        if (text.isBlank()) return

        val chatIds = preferencesManager.getAllChatIds()
        if (chatIds.isEmpty()) {
            Log.d(TAG, "sendMessageToAllChats: нет активных чатов")
            return
        }

        for (chatId in chatIds) {
            sendMessage(chatId, text)
        }
    }

    /**
     * Отправляет сообщение в конкретный чат
     */
    private fun sendMessage(chatId: String, text: String) {
        if (text.isBlank() || chatId.isBlank()) return

        try {
            val messageThreadId = preferencesManager.getMessageThreadId() // <-- Получаем ID подгруппы из настроек
            val message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("Markdown")
                .apply {
                    if (messageThreadId > 0) {
                        this.messageThreadId(messageThreadId) // <-- Устанавливаем ID подгруппы
                    }
                }
                .build()

            runBlocking {
                try {
                    telegramClient.execute(message)
                    Log.d(TAG, "✅ сообщение отправлено в $chatId (threadId: $messageThreadId)")
                } catch (e: TelegramApiException) {
                    if (e.message?.contains("bot can't send messages") == true ||
                        e.message?.contains("Forbidden") == true
                    ) {
                        Log.w(TAG, "⚠️ Бот не может отправлять в $chatId, удаляем")
                        preferencesManager.removeChatId(chatId)
                    } else {
                        Log.e(TAG, "❌ ошибка в $chatId", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ ошибка при создании сообщения", e)
        }
    }

    private fun showAndroidNotification(title: String, content: String) {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "bot_notifications",
                "Уведомления бота",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о статусе бота"
            }
            notificationManager.createNotificationChannel(channel)
//            }

            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, "bot_notifications")
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка при показе уведомления", e)
        }
    }

    private fun createNotificationChannel() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Telegram Bot Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Сервис отслеживания заказов Wildberries"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
//        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WB Telegram Bot")
            .setContentText("Бот активен и отслеживает заказы")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        WorkManager.getInstance(this).cancelUniqueWork(PERIODIC_WORK_NAME)
        if (::botApplication.isInitialized) {
            botApplication.close()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Обработка ручной проверки
        when (intent?.getStringExtra("command")) {
            "checkNow" -> {
                serviceScope.launch {
                    checkAndNotifyAboutOrders(false)
                }
            }

            "autoCheck" -> {
                serviceScope.launch {
                    checkAndNotifyAboutOrders(true)
                }
            }

            "sendToDeliveryAndGetQRCodes" -> {
                serviceScope.launch {
                    sendToDeliveryAndGetQRCodes()
                }
            }

            "notifyBotStopped" -> {
                serviceScope.launch {
                    sendMessageToAllChats("⚠️ Бот остановлен")
                }
            }

            "errorPutNumberCargoSpaces" -> {
                serviceScope.launch {
                    sendMessageToAllChats("⚠️ ❌ Ошибка добавления грузомест в поставку $currentSupplyId.")
                }
            }
        }
        // Остальной код (startForeground и т.д.) – уже есть
        return START_STICKY
    }
}