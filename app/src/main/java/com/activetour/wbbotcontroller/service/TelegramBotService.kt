package com.activetour.wbbotcontroller.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
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
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.io.File
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

    // Чаты, ожидающие от пользователя ввода количества коробок (грузомест)
    private val awaitingCargoSpacesChats = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

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

            Log.d(TAG, "startBot: создание OkHttpTelegramClient...")
            telegramClient = OkHttpTelegramClient(token)
            Log.d(TAG, "✅ startBot: OkHttpTelegramClient создан")

            serviceScope.launch {
                try {
                    Log.d(TAG, "startBot: создание TelegramBotsLongPollingApplication...")
                    botApplication = TelegramBotsLongPollingApplication()
                    Log.d(TAG, "✅ startBot: TelegramBotsLongPollingApplication создан")

                    Log.d(TAG, "startBot: регистрация бота...")
                    botApplication.registerBot(token, this@TelegramBotService)
                    Log.d(TAG, "✅ startBot: бот успешно зарегистрирован")

                    registerBotCommands()

                    val botId = token.split(":").firstOrNull() ?: ""
                    if (botId.isNotEmpty()) {
                        Log.d(TAG, "✅ ID бота: $botId")
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
                        Log.d(TAG, "startBot: чат ещё не активирован, первая проверка отложена до получения сообщения")
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

            val orderChecker = WbOrderChecker(applicationContext)
            val newOrders = orderChecker.getNewOrders()

            if (newOrders.isEmpty()) {
                if (!autoCheck) sendOrdersMessageToAllChats(" Новых заказов нет.")
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
                actualNewOrders.forEach { order ->
                    appendLine()
                    appendLine("• *${order.article}*")
                    appendLine("  Номер: `${order.id}`")
                    appendLine("  Дата: ${order.createdAt?.replace("T", " ")?.replace("Z", "")}")
                }
            }

            actualNewOrders.forEach { order ->
                if (order.id > lastCheckedOrderId) {
                    lastCheckedOrderId = order.id
                }
            }

            // Создание поставок или добавление в существующую
            processSupplyAndAddOrders(actualNewOrders, ordersMessage)

            Log.d(TAG, "✅ checkAndNotifyAboutOrders: УСПЕШНО ЗАВЕРШЕНА")

        } catch (e: Exception) {
            Log.e(TAG, "❌ checkAndNotifyAboutOrders: ОШИБКА!", e)
            sendOrdersMessageToAllChats("⚠️ *Ошибка при проверке заказов*\n${e.message}")
        }

        Log.d(TAG, "========== checkAndNotifyAboutOrders: КОНЕЦ ==========")
    }

    /**
     * Автоматическое создание поставки и добавление заказов
     */
    private suspend fun processSupplyAndAddOrders(orders: List<WBOrder>, ordersMessage: String) {
        if (orders.isEmpty()) return

        val orderIds = orders.map { it.id }
        Log.d(TAG, "processSupplyAndAddOrders: orderIds = $orderIds")

        try {
            val supplyChecker = WbSupplyChecker(applicationContext)
            currentSupplyId = supplyChecker.getLastActiveSupply()

            var countOrders = orderIds.size
            var supplyType = ""
            var success = false
            var supplyNotFound = currentSupplyId == null

            if (supplyNotFound) {
                supplyType = "НОВУЮ (созданную мной)"
                val supplyName = "Поставка от ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))}"
                currentSupplyId = supplyChecker.createSupply(supplyName)

                if (currentSupplyId == null) {
                    sendOrdersMessageToAllChats("❌ *КРИТИЧЕСКАЯ ОШИБКА*\nНе удалось создать поставку!")
                    return
                }
            }

            if (!supplyNotFound) {
                countOrders = countOrders + supplyChecker.getCountOrdersInSupply(currentSupplyId!!)
            }
            success = supplyChecker.addOrdersToSupply(currentSupplyId!!, orderIds)

            if (success) {
                val message = buildString {
                    appendLine("В $supplyType поставку: $currentSupplyId добавлены заказы:")
                    appendLine(ordersMessage)
                    appendLine("В поставке заказов: $countOrders")
                }
                // ✅ Отправляем в тему Заказов
                sendOrdersMessageToAllChats(message)
            } else {
                sendOrdersMessageToAllChats("❌ *Ошибка при добавлении заказов в поставку*")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ processSupplyAndAddOrders: ОШИБКА!", e)
            sendOrdersMessageToAllChats("❌ *Ошибка при обработке поставки*\n${e.message}")
        }
    }

    /**
     * Обработка входящих сообщений и кнопок от Telegram
     */
    override fun consume(update: Update) {
        Log.d(TAG, "🔔 consume: получено обновление от Telegram")

        try {
            // 1. Обработка нажатий на инлайн-кнопки (CallbackQuery)
            if (update.hasCallbackQuery()) {
                val callbackQuery = update.callbackQuery
                val callbackData = callbackQuery.data
                val chatId = callbackQuery.message.chatId.toString()
                val threadId = (callbackQuery.message as? org.telegram.telegrambots.meta.api.objects.message.Message)?.messageThreadId ?: 0

                Log.d(TAG, "🔔 callbackQuery: chatId=$chatId, threadId=$threadId, data=$callbackData")

                try {
                    val answer = AnswerCallbackQuery.builder()
                        .callbackQueryId(callbackQuery.id)
                        .build()
                    telegramClient.execute(answer)
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка ответа на callbackQuery: ${e.message}")
                }

                // Удаляем предыдущее сообщение с кнопками меню после нажатия
                deleteMessage(chatId, callbackQuery.message.messageId)

                when (callbackData) {
                    "cmd_check" -> {
                        sendOrdersMessageToAllChats("🔍 Выполняю проверку заказов...")
                        serviceScope.launch {
                            checkAndNotifyAboutOrders(false)
                        }
                    }

                    "cmd_supply" -> {
                        val targetThread = preferencesManager.getSupplyThreadId(chatId)
                        serviceScope.launch {
                            val supplyChecker = WbSupplyChecker(applicationContext)
                            val activeSupply = supplyChecker.getLastActiveSupply()

                            if (activeSupply == null) {
                                sendMessage(chatId, "❌ *Активной поставки для обработки не найдено!*", targetThread)
                            } else {
                                currentSupplyId = activeSupply
                                awaitingCargoSpacesChats.add(chatId)
                                sendMessage(chatId, buildString {
                                    appendLine("📦 *Формирование поставки:* `$activeSupply`")
                                    appendLine("Укажите количество коробок (грузомест) числом (например: 1, 2, 3...)")
                                    appendLine("Или отправьте *Отмена* для отмены действия.")
                                }, targetThread)
                            }
                        }
                    }

                    "cmd_deliver" -> {
                        val targetThread = preferencesManager.getSupplyThreadId(chatId)
                        serviceScope.launch {
                            deliverActiveSupply(chatId, targetThread)
                        }
                    }

                    "cmd_status" -> {
                        val targetThread = threadId ?: 0
                        val oTopic = preferencesManager.getOrdersThreadId(chatId)
                        val sTopic = preferencesManager.getSupplyThreadId(chatId)
                        sendMessage(chatId, buildString {
                            appendLine("✅ *Статус работы бота:*")
                            appendLine("• Состояние: Работает 🟢")
                            appendLine("• Время: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss dd.MM.yyyy"))}")
                            appendLine("• Интервал проверки: ${preferencesManager.getCheckIntervalMinutes()} минут")
                            if (oTopic > 0) appendLine("• ID темы для Заказов: `$oTopic`")
                            if (sTopic > 0) appendLine("• ID темы для Поставок: `$sTopic`")
                        }, targetThread)
                    }

                    "cmd_help" -> {
                        val targetThread = threadId ?: 0
                        sendMessage(chatId, buildString {
                            appendLine("🤖 *Справка и меню управления:*")
                            appendLine("• `СТАРТ` — активировать бота (вся информация по умолчанию выводится сюда)")
                            appendLine("• `ПОСТАВКА` — перенаправить вывод листов подбора, стикеров и QR-кодов в этот подчат")
                            appendLine("• `Жиган проверь` — запустить мгновенную проверку заказов")
                            appendLine("• `Жиган поставка` — сформировать поставку, получить лист подбора, стикеры в PDF и QR-коды")
                            appendLine("• `Жиган в доставку` — передать активную поставку в статус 'В доставке'")
                            appendLine("• `/status` — проверить статус работы бота")
                            appendLine("• `/help` — показать эту справку и меню")
                        }, targetThread, createBotMenuKeyboard(chatId, targetThread))
                    }
                }
                return
            }

            // 2. Обработка текстовых сообщений
            if (update.hasMessage() && update.message.hasText()) {

                val chatId = update.message.chatId.toString()
                val text = update.message.text
                val threadId = update.message.messageThreadId // <-- Получаем ID подгруппы
                Log.d(TAG, "consume: сообщение из чата $chatId, threadId=$threadId: '$text'")

                // Обработка стартовых команд
                if (text.equals("ПОСТАВКА", ignoreCase = true)
                    || text.equals("/start_supply", ignoreCase = true)
                ) {

                    val currentThread = threadId ?: 0
                    preferencesManager.setSupplyThreadId(chatId, currentThread)
                    addChat(chatId)

                    sendMessage(chatId, buildString {
                        appendLine("✅ *Подчат для ПОСТАВОК перепривязан!*")
                        appendLine("📌 Теперь листы подбора, стикеры в PDF и QR-коды по команде `Жиган поставка` будут выводиться в этот подчат.")
                        appendLine("📌 Сообщения о заказах продолжат приходить в основной подчат/чат.")
                    }, currentThread, createMenuLauncherKeyboard())

                    sendMessage(chatId, "Меню", currentThread, createBotMenuKeyboard(chatId, currentThread))
                    return
                }

                if (text.equals("СТАРТ", ignoreCase = true)
                    || text.equals("СТАРТ ЗАКАЗЫ", ignoreCase = true)
                    || text.equals("/start", ignoreCase = true)
                ) {

                    val currentThread = threadId ?: 0
                    preferencesManager.setOrdersThreadId(chatId, currentThread)
                    preferencesManager.setSupplyThreadId(chatId, currentThread)
                    addChat(chatId)
                    preferencesManager.setWelcomeSent(true)

                    sendMessage(chatId, buildString {
                        appendLine("✅ *Бот активирован!*")
                        appendLine("📌 В этот подчат/чат будет приходить вся информация приложения (заказы, стикеры, QR-коды и листы подбора).")
                        appendLine()
                        appendLine("💡 *Для разделения:* если вы хотите получать стикеры и QR-коды в ДРУГОМ подчате, просто напишите команду `ПОСТАВКА` в том подчате.")
                    }, currentThread, createMenuLauncherKeyboard())

                    sendMessage(chatId, "Меню", currentThread, createBotMenuKeyboard(chatId, currentThread))

                    serviceScope.launch {
                        checkAndNotifyAboutOrders(false)
                    }
                    return
                }

                // Проверяем, активирован ли уже этот чат
                if (!preferencesManager.getAllChatIds().contains(chatId)) {
                    // Чат ещё не активирован – предлагаем запуск
                    sendMessage(
                        chatId,
                        "⚠️ Чат ещё не активирован. Напишите `СТАРТ` для запуска бота.",
                        threadId ?: 0
                    )
                    return
                }

                // --- Чат уже активирован – обрабатываем команды ---
                if (awaitingCargoSpacesChats.contains(chatId)) {
                    val trimmed = text.trim()
                    val targetThread = threadId ?: preferencesManager.getSupplyThreadId(chatId)
                    if (trimmed.equals("Отмена", ignoreCase = true) || trimmed.equals(
                            "/cancel",
                            ignoreCase = true
                        )
                    ) {
                        awaitingCargoSpacesChats.remove(chatId)
                        sendMessage(chatId, "❌ *Действие отменено.*", targetThread)
                        return
                    }

                    val count = trimmed.toIntOrNull()
                    if (count != null && count > 0) {
                        awaitingCargoSpacesChats.remove(chatId)
                        preferencesManager.setNumberCargoSpaces(count)
                        sendMessage(
                            chatId,
                            "✅ Установлено коробок: *$count*. Выполняю запрос поставки...",
                            targetThread
                        )
                        serviceScope.launch {
                            sendToDeliveryAndGetQRCodes()
                        }
                        return
                    } else {
                        sendMessage(
                            chatId,
                            "⚠️ *Пожалуйста, укажите количество коробок числом (например: 1, 2, 3)* или отправьте *Отмена*.",
                            targetThread
                        )
                        return
                    }
                }

                // Обработка команд
                when {
                    text.contains("проверить заказы", ignoreCase = true)
                            || text.contains("Жиган проверь", ignoreCase = true)
                            || text.equals("/check", ignoreCase = true) -> {
                        sendOrdersMessageToAllChats("🔍 Выполняю проверку...")
                        serviceScope.launch {
                            checkAndNotifyAboutOrders(false)
                        }
                    }

                    text.contains("Сформировать поставку", ignoreCase = true)
                            || text.contains("Жиган поставка", ignoreCase = true)
                            || text.contains("Жиган qr", ignoreCase = true)
                            || text.contains("Жиган стикеры", ignoreCase = true)
                            || text.equals("/qr", ignoreCase = true)
                            || text.equals("/supply", ignoreCase = true) -> {

                        val targetThread = preferencesManager.getSupplyThreadId(chatId)
                        serviceScope.launch {
                            val supplyChecker = WbSupplyChecker(applicationContext)
                            val activeSupply = supplyChecker.getLastActiveSupply()

                            if (activeSupply == null) {
                                sendMessage(chatId, "❌ *Активной поставки для обработки не найдено!*", targetThread)
                            } else {
                                currentSupplyId = activeSupply
                                awaitingCargoSpacesChats.add(chatId)
                                sendMessage(chatId, buildString {
                                    appendLine("📦 *Формирование поставки:* `$activeSupply`")
                                    appendLine("Укажите количество коробок (грузомест) числом (например: 1, 2, 3...)")
                                    appendLine("Или отправьте *Отмена* для отмены действия.")
                                }, targetThread)
                            }
                        }
                    }

                    text.contains("Передать в доставку", ignoreCase = true)
                            || text.contains("Жиган в доставку", ignoreCase = true)
                            || text.contains("Жиган закрыть поставку", ignoreCase = true)
                            || text.contains("Жиган доставку", ignoreCase = true)
                            || text.equals("/deliver", ignoreCase = true) -> {

                        val targetThread = preferencesManager.getSupplyThreadId(chatId)
                        serviceScope.launch {
                            deliverActiveSupply(chatId, targetThread)
                        }
                    }

                    text.contains("Статус", ignoreCase = true)
                            || text.equals("/status", ignoreCase = true) -> {
                        val targetThread = threadId ?: 0
                        val oTopic = preferencesManager.getOrdersThreadId(chatId)
                        val sTopic = preferencesManager.getSupplyThreadId(chatId)
                        sendMessage(chatId, buildString {
                            appendLine("✅ *Статус работы бота:*")
                            appendLine("• Состояние: Работает 🟢")
                            appendLine(
                                "• Время: ${
                                    LocalDateTime.now()
                                        .format(DateTimeFormatter.ofPattern("HH:mm:ss dd.MM.yyyy"))
                                }"
                            )
                            appendLine("• Интервал проверки: ${preferencesManager.getCheckIntervalMinutes()} минут")
                            if (oTopic > 0) appendLine("• ID темы для Заказов: `$oTopic`")
                            if (sTopic > 0) appendLine("• ID темы для Поставок: `$sTopic`")
                        }, targetThread)
                    }

                    text.contains("Меню", ignoreCase = true)
                            || text.contains("Справка", ignoreCase = true)
                            || text.equals("Меню", ignoreCase = true)
                            || text.equals("/help", ignoreCase = true)
                            || text.equals("/menu", ignoreCase = true) -> {
                        val targetThread = threadId ?: 0
                        // Удаляем исходящее текстовое сообщение пользователя, вызвавшее меню
                        deleteMessage(chatId, update.message.messageId)
                        sendMessage(chatId, "Меню", targetThread, createBotMenuKeyboard(chatId, targetThread))
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
     * Вспомогательный метод для удаления сообщения в Telegram
     */
    private fun deleteMessage(chatId: String, messageId: Int) {
        if (chatId.isBlank() || messageId <= 0) return
        if (!::telegramClient.isInitialized) return
        try {
            val deleteMsg = DeleteMessage.builder()
                .chatId(chatId)
                .messageId(messageId)
                .build()
            telegramClient.execute(deleteMsg)
            Log.d(TAG, "🗑️ Сообщение $messageId в чате $chatId удалено")
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось удалить сообщение $messageId в $chatId: ${e.message}")
        }
    }

    /**
     * Добавляет чат в список автоматически
     */
    private fun addChat(chatId: String) {
        if (chatId.isEmpty()) return
        val botId = preferencesManager.getBotId()
        if (botId.isNotEmpty() && chatId == botId) {
            Log.d(TAG, "addChat: пропускаем ID бота ($chatId)")
            return
        }

        if (!preferencesManager.getAllChatIds().contains(chatId)) {
            preferencesManager.addChatId(chatId)
            Log.d(TAG, "✅ Чат автоматически добавлен: $chatId")
        }
    }

    /**
     * Регистрирует стандартные команды бота в Меню Telegram (кнопка [/])
     */
    private fun registerBotCommands() {
        try {
            val commands = listOf(
                BotCommand("/check", "Проверить новые заказы"),
                BotCommand("/supply", "Сформировать поставку (стикеры, QR-коды)"),
                BotCommand("/deliver", "Передать поставку в доставку"),
                BotCommand("/status", "Статус работы бота"),
                BotCommand("/help", "Справка и меню")
            )
            val setMyCommands = SetMyCommands.builder()
                .commands(commands)
                .build()
            telegramClient.execute(setMyCommands)
            Log.d(TAG, "✅ Команды бота зарегистрированы в меню Telegram")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка регистрации команд бота: ${e.message}")
        }
    }

    /**
     * Создаёт одну компактную кнопку [📱 Меню] рядом с полем ввода текста в Telegram
     */
    private fun createMenuLauncherKeyboard(): ReplyKeyboardMarkup {
        val row = KeyboardRow("📱 Меню")
        return ReplyKeyboardMarkup.builder()
            .keyboard(listOf(row))
            .resizeKeyboard(true)
            .isPersistent(true)
            .build()
    }

    /**
     * Создаёт адаптивное меню с инлайн-кнопками в Telegram в зависимости от подчата/чата
     */
    private fun createBotMenuKeyboard(chatId: String, threadId: Int): InlineKeyboardMarkup {
        val ordersChat = preferencesManager.getOrdersChatId()
        val supplyChat = preferencesManager.getSupplyChatId()
        val ordersThread = preferencesManager.getOrdersThreadId(chatId)
        val supplyThread = preferencesManager.getSupplyThreadId(chatId)

        val isSingleTarget = (ordersChat == supplyChat || supplyChat.isEmpty()) && (ordersThread == supplyThread || supplyThread == 0)
        val keyboardRows = mutableListOf<InlineKeyboardRow>()

        if (isSingleTarget) {
            keyboardRows.add(
                InlineKeyboardRow(
                    InlineKeyboardButton.builder().text("🔍 Проверить заказы").callbackData("cmd_check").build(),
                    InlineKeyboardButton.builder().text("📦 Сформировать поставку").callbackData("cmd_supply").build()
                )
            )
            keyboardRows.add(
                InlineKeyboardRow(
                    InlineKeyboardButton.builder().text("🚚 Передать в доставку").callbackData("cmd_deliver").build(),
                    InlineKeyboardButton.builder().text("📊 Статус").callbackData("cmd_status").build()
                )
            )
            keyboardRows.add(
                InlineKeyboardRow(
                    InlineKeyboardButton.builder().text("❓ Справка").callbackData("cmd_help").build()
                )
            )
        } else {
            val isSupplySubchat = (threadId == supplyThread || (threadId == 0 && chatId == supplyChat))

            if (isSupplySubchat) {
                keyboardRows.add(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("📦 Сформировать поставку").callbackData("cmd_supply").build(),
                        InlineKeyboardButton.builder().text("🚚 Передать в доставку").callbackData("cmd_deliver").build()
                    )
                )
                keyboardRows.add(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("📊 Статус").callbackData("cmd_status").build(),
                        InlineKeyboardButton.builder().text("❓ Справка").callbackData("cmd_help").build()
                    )
                )
            } else {
                keyboardRows.add(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("🔍 Проверить заказы").callbackData("cmd_check").build()
                    )
                )
                keyboardRows.add(
                    InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("📊 Статус").callbackData("cmd_status").build(),
                        InlineKeyboardButton.builder().text("❓ Справка").callbackData("cmd_help").build()
                    )
                )
            }
        }

        return InlineKeyboardMarkup.builder()
            .keyboard(keyboardRows)
            .build()
    }

    /**
     * Переводит активную поставку в статус 'В доставке' в WB API
     */
    private suspend fun deliverActiveSupply(chatId: String, threadId: Int) {
        val supplyChecker = WbSupplyChecker(applicationContext)
        val activeSupply = supplyChecker.getLastActiveSupply()

        if (activeSupply == null) {
            sendMessage(chatId, "❌ *Активной поставки для перевода в доставку не найдено!*", threadId, createBotMenuKeyboard(chatId, threadId))
            return
        }

        sendMessage(chatId, "🚚 Передаю поставку `$activeSupply` в статус 'В доставке'...", threadId)
        val delivery = WBDeliveryAndQRCodes(applicationContext)
        val success = delivery.sendToDelivery(activeSupply)

        if (success) {
            sendSupplyMessageToAllChats("✅ Поставка `$activeSupply` успешно переведена в статус 'В доставке'!")
        } else {
            sendSupplyMessageToAllChats("❌ Не удалось перевести поставку `$activeSupply` в статус 'В доставке'. Проверьте API токен и состояние поставки.")
        }
    }

    /**
     * Отправляет сообщение о заказах во все привязанные чаты и темы Заказов
     */
    private fun sendOrdersMessageToAllChats(text: String) {
        if (text.isBlank()) return
        val chatIds = preferencesManager.getOrdersChatIds()
        if (chatIds.isEmpty()) return

        for (chatId in chatIds) {
            val threadId = preferencesManager.getOrdersThreadId(chatId)
            sendMessage(chatId, text, threadId)
        }
    }

    /**
     * Отправляет сообщение о поставке во все привязанные чаты и темы Поставок
     */
    private fun sendSupplyMessageToAllChats(text: String) {
        if (text.isBlank()) return
        val chatIds = preferencesManager.getSupplyChatIds()
        if (chatIds.isEmpty()) return

        for (chatId in chatIds) {
            val threadId = preferencesManager.getSupplyThreadId(chatId)
            sendMessage(chatId, text, threadId)
        }
    }

    /**
     * Отправляет сообщение в конкретный чат и подчат (threadId)
     */
    private fun sendMessage(
        chatId: String,
        text: String,
        overrideThreadId: Int? = null,
        replyMarkup: ReplyKeyboard? = null
    ) {
        if (chatId.isBlank()) return
        val botId = preferencesManager.getBotId()
        if (botId.isNotEmpty() && chatId == botId) return
        if (!::telegramClient.isInitialized) {
            Log.w(TAG, "⚠️ sendMessage: telegramClient ещё не инициализирован")
            return
        }

        try {
            val messageThreadId = overrideThreadId ?: preferencesManager.getMessageThreadId(chatId)
            val message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("Markdown")
                .apply {
                    if (messageThreadId > 0) {
                        this.messageThreadId(messageThreadId) // <-- Устанавливаем ID подгруппы
                    }
                    if (replyMarkup != null) {
                        this.replyMarkup(replyMarkup)
                    }
                }
                .build()

            runBlocking {
                try {
                    telegramClient.execute(message)
                    Log.d(TAG, "✅ сообщение отправлено в $chatId (threadId: $messageThreadId)")
                } catch (e: TelegramApiException) {
                    if (e.message?.contains(
                            "message thread not found",
                            ignoreCase = true
                        ) == true
                    ) {
                        Log.w(
                            TAG,
                            "⚠️ Тема $messageThreadId не найдена в чате $chatId; повторяю отправку без темы"
                        )
                        preferencesManager.clearMessageThreadId(chatId, messageThreadId)
                        try {
                            telegramClient.execute(
                                SendMessage.builder()
                                    .chatId(chatId)
                                    .text(text)
                                    .parseMode("Markdown")
                                    .build()
                            )
                            Log.d(TAG, "✅ сообщение отправлено в $chatId без темы")
                        } catch (retryException: TelegramApiException) {
                            Log.e(
                                TAG,
                                "❌ повторная отправка сообщения без темы в $chatId не удалась",
                                retryException
                            )
                        }
                    } else if (e.message?.contains("bot can't send messages") == true ||
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
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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

    /**
     * Обработка запроса на получение стикеров и QR-кодов поставки и передача в доставку
     */
    private suspend fun sendToDeliveryAndGetQRCodes() {
        Log.d(TAG, "========== sendToDeliveryAndGetQRCodes: НАЧАЛО ==========")
        try {
            sendSupplyMessageToAllChats("🔄 Запрос стикеров и QR-кодов поставки от Wildberries...")

            val supplyChecker = WbSupplyChecker(applicationContext)
            currentSupplyId = supplyChecker.getLastActiveSupply()

            if (currentSupplyId == null) {
                sendSupplyMessageToAllChats("❌ Активной поставки для обработки не найдено!")
                return
            }

            val delivery = WBDeliveryAndQRCodes(applicationContext)
            sendSupplyMessageToAllChats("📦 Найдена активная поставка: `$currentSupplyId`")

            // 1. Добавляем грузоместа
            delivery.addCargoLocations(currentSupplyId!!)

            // 4. Получаем и отправляем QR-код самой поставки (на коробку/паллету)
            val qrCodeFiles = delivery.getQRCodesSupplies(currentSupplyId!!)
            if (qrCodeFiles.isNotEmpty()) {
                qrCodeFiles.forEachIndexed { index, qrCodeFile ->
                    if (qrCodeFile.exists()) {
                        sendSupplyDocumentToAllChats(
                            qrCodeFile,
                            "📷 QR-стикер коробки ${index + 1} поставки `$currentSupplyId`"
                        )
                    }
                    try {
                        qrCodeFile.delete()
                    } catch (_: Exception) {
                    }
                }
            } else {
                sendSupplyMessageToAllChats("⚠️ Не удалось получить QR-код поставки `$currentSupplyId`")
            }

            // 2. Получаем и отправляем стикеры для заказов в поставке (на товары)
            val stickers = delivery.getStickersForOrders(currentSupplyId!!)
            if (stickers.isNotEmpty()) {
                sendSupplyMessageToAllChats("🏷 Получено стикеров для заказов: ${stickers.size}. Объединяю в PDF...")

                val mergedPdf = delivery.createMergedStickersPdf(stickers, currentSupplyId!!)
                if (mergedPdf != null && mergedPdf.exists()) {
                    sendSupplyDocumentToAllChats(
                        mergedPdf,
                        "🏷 Все стикеры заказов поставки `$currentSupplyId` (${stickers.size} шт.)"
                    )
                    try { mergedPdf.delete() } catch (_: Exception) {}
                } else {
                    sendSupplyStickersToAllChats(stickers, "Стикер поставки `$currentSupplyId`")
                }
                stickers.forEach {
                    try {
                        it.file.delete()
                    } catch (_: Exception) {
                    }
                }
            } else {
                sendSupplyMessageToAllChats("⚠️ Не найдено стикеров для заказов в поставке `$currentSupplyId`")
            }

            // 3. Генерируем и отправляем Лист подбора (CSV)
            val excelFile = delivery.generateSelectionSheet(currentSupplyId!!, stickers)
            if (excelFile != null && excelFile.exists()) {
                sendSupplyDocumentToAllChats(
                    excelFile,
                    "📋 Лист подбора для поставки `$currentSupplyId`"
                )
                try {
                    excelFile.delete()
                } catch (_: Exception) {
                }
            }

            // 5. Передаем поставку в доставку в WB
            val deliverySuccess = delivery.sendToDelivery(currentSupplyId!!)
            if (deliverySuccess) {
                sendSupplyMessageToAllChats("✅ Поставка `$currentSupplyId` переведена в статус 'В доставке'")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ sendToDeliveryAndGetQRCodes: ОШИБКА!", e)
            sendSupplyMessageToAllChats("❌ *Ошибка при обработке поставки*\n${e.message}")
        }
    }

    /**
     * Отправление документа/файла во все привязанные чаты и темы Поставки
     */
    private fun sendSupplyDocumentToAllChats(document: File, caption: String = "") {
        if (!document.exists()) return
        if (!::telegramClient.isInitialized) {
            Log.w(TAG, "⚠️ sendSupplyDocumentToAllChats: telegramClient ещё не инициализирован")
            return
        }
        val chatIds = preferencesManager.getSupplyChatIds()
        if (chatIds.isEmpty()) return

        for (chatId in chatIds) {
            val threadId = preferencesManager.getSupplyThreadId(chatId)
            sendDocument(chatId, document, caption, threadId)
        }
    }

    private fun sendDocument(
        chatId: String,
        document: File,
        caption: String = "",
        overrideThreadId: Int? = null
    ) {
        if (!document.exists()) return
        if (!::telegramClient.isInitialized) return
        val botId = preferencesManager.getBotId()
        if (botId.isNotEmpty() && chatId == botId) return

        val messageThreadId = overrideThreadId ?: preferencesManager.getSupplyThreadId(chatId)
        try {
            val sendDocument = SendDocument.builder()
                .chatId(chatId)
                .document(InputFile(document))
                .caption(caption)
                .apply {
                    if (messageThreadId > 0) {
                        this.messageThreadId(messageThreadId)
                    }
                }
                .build()

            telegramClient.execute(sendDocument)
            Log.d(
                TAG,
                "✅ Документ отправлен в чат $chatId (threadId: $messageThreadId): ${document.name}"
            )
        } catch (e: Exception) {
            if (e.message?.contains("message thread not found", ignoreCase = true) == true) {
                Log.w(
                    TAG,
                    "⚠️ Тема $messageThreadId не найдена в чате $chatId; повторяю отправку документа без темы"
                )
                preferencesManager.clearMessageThreadId(chatId, messageThreadId)
                try {
                    val retryDocument = SendDocument.builder()
                        .chatId(chatId)
                        .document(InputFile(document))
                        .caption(caption)
                        .build()
                    telegramClient.execute(retryDocument)
                    Log.d(TAG, "✅ документ отправлен в $chatId без темы: ${document.name}")
                } catch (retryException: Exception) {
                    Log.e(
                        TAG,
                        "❌ повторная отправка документа без темы в $chatId не удалась",
                        retryException
                    )
                }
            } else {
                Log.e(TAG, "❌ Ошибка отправки документа в чат $chatId: ${e.message}", e)
            }
        }
    }

    /**
     * Отправление списка стикеров заказов во все активные чаты в тему Поставки
     */
    private fun sendSupplyStickersToAllChats(
        stickers: List<StickerForOrder>,
        caption: String = ""
    ) {
        if (stickers.isEmpty()) return
        for (sticker in stickers) {
            if (sticker.file.exists()) {
                val label = "Стикер заказа №${sticker.orderId} (${sticker.partA} ${sticker.partB})"
                sendSupplyDocumentToAllChats(sticker.file, "$caption\n$label")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Обработка команд
        when (intent?.getStringExtra("command")) {
            "checkNow" -> {
                serviceScope.launch {
                    checkAndNotifyAboutOrders(false)
                }
            }

            "sendToDeliveryAndGetQRCodes" -> {
                serviceScope.launch {
                    sendToDeliveryAndGetQRCodes()
                }
            }

            "autoCheck" -> {
                serviceScope.launch {
                    checkAndNotifyAboutOrders(true)
                }
            }

            "notifyBotStopped" -> {
                serviceScope.launch {
                    sendOrdersMessageToAllChats("⚠️ Бот остановлен")
                    sendSupplyMessageToAllChats("⚠️ Бот остановлен")
                }
            }
        }
        return START_STICKY
    }
}
