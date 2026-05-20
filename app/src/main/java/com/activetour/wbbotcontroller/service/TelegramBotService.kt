package com.activetour.wbbotcontroller.service

import com.activetour.wbbotcontroller.model.WBOrder
import com.activetour.wbbotcontroller.worker.CheckOrdersWorker

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.activetour.wbbotcontroller.MainActivity
import com.activetour.wbbotcontroller.R
import com.activetour.wbbotcontroller.utils.PreferencesManager
import kotlinx.coroutines.*
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

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
                        preferencesManager.setBotId(botId)
                        Log.d(TAG, "✅ ID бота сохранён: $botId")
                    }

                    Log.d(TAG, "startBot: запуск периодической проверки...")
                    startPeriodicCheck()
                    Log.d(TAG, "✅ startBot: периодическая проверка запущена")

                    // ✅ ЖДЁМ 5 СЕКУНД ПЕРЕД ПЕРВОЙ ПРОВЕРКОЙ
                    Log.d(TAG, "startBot: ожидание 5 секунд перед первой проверкой...")
                    delay(5000)

                    Log.d(TAG, "startBot: первая проверка заказов...")
                    withContext(Dispatchers.IO) {
                        checkAndNotifyAboutOrders()
                    }
                    Log.d(TAG, "✅ startBot: первая проверка выполнена")

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
    private suspend fun checkAndNotifyAboutOrders() {
        Log.d(TAG, "========== checkAndNotifyAboutOrders: НАЧАЛО ==========")

        try {
            // ✅ ЖДЁМ 1 СЕКУНДУ ПЕРЕД КАЖДЫМ ЗАПРОСОМ (для соблюдения лимита)
            delay(1000)

            val orderChecker = WbOrderChecker(applicationContext)
            val newOrders = orderChecker.getNewOrders()

            if (newOrders.isEmpty()) {
                Log.d(TAG, "checkAndNotifyAboutOrders: новых заказов нет")
                return
            }

            val actualNewOrders = newOrders.filter { it.id > lastCheckedOrderId }
            if (actualNewOrders.isEmpty()) {
                Log.d(TAG, "checkAndNotifyAboutOrders: новых заказов после фильтрации нет")
                return
            }

            // Формируем сообщение о новых заказах
            val ordersMessage = buildString {
                appendLine("📦 *НОВЫЕ ЗАКАЗЫ!*")
                appendLine()
                actualNewOrders.forEach { order ->
                    appendLine("• *${order.article}*")
                    appendLine("  🆔 Номер: `${order.id}`")
                    appendLine("  📅 Дата: ${order.createdAt?.replace("T", " ")?.replace("Z", "")}")
                    appendLine()
                }
            }

            sendMessageToAllChats(ordersMessage)

            actualNewOrders.forEach { order ->
                if (order.id > lastCheckedOrderId) {
                    lastCheckedOrderId = order.id
                }
            }

            if (preferencesManager.isAutoCreateSupply()) {
                processSupplyAndAddOrders(actualNewOrders)
            }

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
        delay(2000)
        if (orders.isEmpty()) return

        val orderIds = orders.map { it.id }
        Log.d(TAG, "processSupplyAndAddOrders: orderIds = $orderIds")

        try {
            val supplyChecker = WbSupplyChecker(applicationContext)
            currentSupplyId = supplyChecker.getLastActiveSupply()

            val supplyType = if (currentSupplyId != null) "СУЩЕСТВУЮЩУЮ" else "НОВУЮ"

            if (currentSupplyId == null) {
                val supplyName = "Поставка от ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}"
                currentSupplyId = supplyChecker.createSupply(supplyName)

                if (currentSupplyId == null) {
                    sendMessageToAllChats("❌ *КРИТИЧЕСКАЯ ОШИБКА*\nНе удалось создать поставку!")
                    return
                }
            }

            val success = supplyChecker.addOrdersToSupply(currentSupplyId!!, orderIds)

            if (success) {
                val message = buildString {
                    appendLine("✅ Заказы успешно добавлены в $supplyType поставку!")
                    appendLine("📦 Поставка: `${currentSupplyId}`")
                    appendLine("📊 Добавлено заказов: ${orderIds.size}")
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
     * Обработка входящих сообщений от Telegram
     */
    override fun consume(update: Update) {
        Log.d(TAG, "🔔 consume: получено обновление от Telegram")

        try {
            if (update.hasMessage() && update.message.hasText()) {
                val chatId = update.message.chatId.toString()
                val text = update.message.text

                Log.d(TAG, "consume: сообщение из чата $chatId: '$text'")

                // ✅ АВТОМАТИЧЕСКИ добавляем чат (пользователь не знает про ID)
                addChat(chatId)

                // Приветствие при первом сообщении
                if (preferencesManager.getAllChatIds().size == 1 &&
                    !preferencesManager.isWelcomeSent()) {
                    preferencesManager.setWelcomeSent(true)
                    sendMessage(chatId, buildString {
                        appendLine("✅ *Бот активирован!*")
                        appendLine()
                        appendLine("🤖 Я буду отслеживать заказы Wildberries")
                        appendLine("и отправлять уведомления в этот чат.")
                        appendLine()
                        appendLine("📌 *Команды:*")
                        appendLine("• `Жиган проверь` - проверить заказы сейчас")
                        appendLine("• `/status` - статус бота")
                        appendLine("• `/help` - справка")
                    })
                }

                // Обработка команд
                when {
                    text.contains("Жиган проверь", ignoreCase = true) || text.equals("/check", ignoreCase = true) -> {
                        sendMessage(chatId, "🔍 Выполняю проверку...")
                        serviceScope.launch {
                            checkAndNotifyAboutOrdersForChat(chatId)
                        }
                    }
                    text.equals("/status", ignoreCase = true) -> {
                        sendMessage(chatId, buildString {
                            appendLine("✅ *Статус бота:*")
                            appendLine("• Состояние: Активен")
                            appendLine("• 🕐 ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss dd.MM.yyyy"))}")
                            appendLine("• 📊 Интервал проверки: ${preferencesManager.getCheckIntervalMinutes()} минут")
                        })
                    }
                    text.equals("/help", ignoreCase = true) -> {
                        sendMessage(chatId, buildString {
                            appendLine("🤖 *Доступные команды:*")
                            appendLine("• `Жиган проверь` - проверить заказы")
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
     * Проверка заказов и отправка результата в конкретный чат
     */
    private suspend fun checkAndNotifyAboutOrdersForChat(chatId: String) {
        try {
            val orderChecker = WbOrderChecker(applicationContext)
            val newOrders = orderChecker.getNewOrders()

            if (newOrders.isEmpty()) {
                sendMessage(chatId, "📭 Новых заказов нет.")
                return
            }

            val actualNewOrders = newOrders.filter { it.id > lastCheckedOrderId }

            if (actualNewOrders.isEmpty()) {
                sendMessage(chatId, "📭 Новых заказов после фильтрации нет.")
                return
            }

            val message = buildString {
                appendLine("📦 *Найдено ${actualNewOrders.size} новых заказов:*")
                appendLine()
                actualNewOrders.forEach { order ->
                    appendLine("• ${order.article} (№`${order.id}`)")
                }
            }
            sendMessage(chatId, message)

        } catch (e: Exception) {
            sendMessage(chatId, "⚠️ *Ошибка при проверке заказов*\n${e.message}")
        }
    }

    /**
     * Добавляет чат в список автоматически
     */
    private fun addChat(chatId: String) {
        if (chatId.isEmpty()) return

        val botId = preferencesManager.getBotId()
        if (chatId == botId) {
            Log.d(TAG, "addChat: пропускаем ID бота")
            return
        }

        val currentChats = preferencesManager.getAllChatIds()
        if (!currentChats.contains(chatId)) {
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
            val message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("Markdown")
                .build()

            runBlocking {
                try {
                    telegramClient.execute(message)
                    Log.d(TAG, "✅ сообщение отправлено в $chatId")
                } catch (e: TelegramApiException) {
                    if (e.message?.contains("bot can't send messages") == true ||
                        e.message?.contains("Forbidden") == true) {
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
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "bot_notifications",
                    "Уведомления бота",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Уведомления о статусе бота"
                }
                notificationManager.createNotificationChannel(channel)
            }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Telegram Bot Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Сервис отслеживания заказов Wildberries"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
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
}