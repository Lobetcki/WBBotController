package com.activetour.wbbotcontroller.service

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
import com.activetour.wbbotcontroller.MainActivity
import com.activetour.wbbotcontroller.model.StickerForOrder
import com.activetour.wbbotcontroller.model.WBOrder
import com.activetour.wbbotcontroller.utils.PreferencesManager
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MaxBotService : Service() {

    companion object {
        private const val TAG = "MaxBotService"
        private const val NOTIFICATION_ID = 2002
        private const val CHANNEL_ID = "MaxBotChannel"

        var isRunning = false
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var preferencesManager: PreferencesManager
    private var maxClient: MaxBotClient? = null
    private var lastUpdateOffset: Long = 0

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: запуск MAX Bot Service")
        preferencesManager = PreferencesManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = intent?.getStringExtra("command")
        Log.d(TAG, "onStartCommand: command = $command")

        if (!isRunning) {
            startForegroundServiceNotification()
            isRunning = true
            startMaxBotWorker()
        }

        if (command == "checkNow") {
            serviceScope.launch {
                checkAndNotifyAboutOrders(false)
            }
        }

        return START_STICKY
    }

    private fun startMaxBotWorker() {
        serviceScope.launch {
            val token = preferencesManager.getMaxBotToken()
            if (token.isBlank()) {
                Log.w(TAG, "Токен MAX Мессенджера пуст")
                return@launch
            }

            maxClient = MaxBotClient(token)
            Log.d(TAG, "✅ MAX Bot Client инициализирован")

            // Фоновый опрос Long Polling для MAX
            while (isRunning) {
                try {
                    val updatesJson = maxClient?.getUpdates(lastUpdateOffset)
                    if (!updatesJson.isNullOrBlank()) {
                        processMaxUpdates(updatesJson)
                    }
                    delay(3000)
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка опроса MAX API: ${e.message}")
                    delay(10000)
                }
            }
        }
    }

    private suspend fun processMaxUpdates(json: String) {
        try {
            val root = JsonParser.parseString(json).asJsonObject
            if (!root.has("result")) return

            val array = root.getAsJsonArray("result")
            for (elem in array) {
                val updateObj = elem.asJsonObject
                val updateId = updateObj.get("update_id").asLong
                if (updateId >= lastUpdateOffset) {
                    lastUpdateOffset = updateId + 1
                }

                if (updateObj.has("message")) {
                    val msg = updateObj.getAsJsonObject("message")
                    val chatId = msg.get("chat_id").asString
                    val text = msg.get("text")?.asString.orEmpty().trim()
                    val threadId = msg.get("thread_id")?.asInt ?: 0

                    handleMaxMessage(chatId, text, threadId)
                } else if (updateObj.has("callback")) {
                    val cb = updateObj.getAsJsonObject("callback")
                    val chatId = cb.get("chat_id").asString
                    val payload = cb.get("payload")?.asString.orEmpty()
                    val threadId = cb.get("thread_id")?.asInt ?: 0

                    handleMaxCallback(chatId, payload, threadId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обработки входящих сообщений MAX: ${e.message}")
        }
    }

    private suspend fun handleMaxMessage(chatId: String, text: String, threadId: Int) {
        val cleanText = text.substringBefore("@").trim()

        if (cleanText.equals("СТАРТ", ignoreCase = true) || cleanText.equals("/start", ignoreCase = true)) {
            preferencesManager.setMaxOrdersChatId(chatId)
            preferencesManager.setMaxOrdersThreadId(threadId)
            preferencesManager.setMaxBotEnabled(true)

            sendMaxMessage(
                chatId,
                "✅ *MAX Бот активирован в этом чате!*\nВсе заказы и поставки Wildberries будут дублироваться сюда.",
                threadId,
                createMaxBotMenu(chatId, threadId)
            )
            return
        }

        if (cleanText.equals("ПОСТАВКА", ignoreCase = true) || cleanText.equals("/start_supply", ignoreCase = true)) {
            preferencesManager.setMaxSupplyChatId(chatId)
            preferencesManager.setMaxSupplyThreadId(threadId)

            sendMaxMessage(
                chatId,
                "✅ *Подчат для ПОСТАВОК в MAX привязан!*\nСтикеры, QR-коды и листы подбора будут выводиться сюда.",
                threadId,
                createMaxBotMenu(chatId, threadId)
            )
            return
        }

        when {
            cleanText.contains("проверить заказы", ignoreCase = true) || cleanText.equals("/check", ignoreCase = true) -> {
                checkAndNotifyAboutOrders(false)
            }

            cleanText.contains("сформировать поставку", ignoreCase = true) || cleanText.equals("/supply", ignoreCase = true) -> {
                sendToDeliveryAndGetQRCodes()
            }

            cleanText.contains("передать в доставку", ignoreCase = true) || cleanText.equals("/deliver", ignoreCase = true) -> {
                deliverActiveSupply(chatId, threadId)
            }

            cleanText.equals("Меню", ignoreCase = true) || cleanText.equals("/menu", ignoreCase = true) -> {
                sendMaxMessage(chatId, "Меню:", threadId, createMaxBotMenu(chatId, threadId))
            }

            cleanText.equals("/status", ignoreCase = true) -> {
                sendMaxMessage(
                    chatId,
                    "✅ *Статус MAX Бота: Работает 🟢*\nВремя: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss dd.MM.yyyy"))}",
                    threadId
                )
            }
        }
    }

    private suspend fun handleMaxCallback(chatId: String, payload: String, threadId: Int) {
        when (payload) {
            "cmd_check" -> checkAndNotifyAboutOrders(false)
            "cmd_supply" -> sendToDeliveryAndGetQRCodes()
            "cmd_deliver" -> deliverActiveSupply(chatId, threadId)
            "cmd_status" -> {
                sendMaxMessage(
                    chatId,
                    "✅ *Статус MAX Бота: Работает 🟢*\nВремя: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss dd.MM.yyyy"))}",
                    threadId
                )
            }
            "cmd_cancel" -> {
                sendMaxMessage(chatId, "❌ Действие отменено", threadId)
            }
        }
    }

    private fun createMaxBotMenu(chatId: String, threadId: Int): List<List<MaxButton>> {
        val ordersThread = preferencesManager.getMaxOrdersThreadId()
        val supplyThread = preferencesManager.getMaxSupplyThreadId()

        val isSupplySubchat = (threadId == supplyThread && supplyThread > 0)

        return if (isSupplySubchat) {
            listOf(
                listOf(MaxButton("📦 Сформировать поставку", "cmd_supply"), MaxButton("🚚 Передать в доставку", "cmd_deliver")),
                listOf(MaxButton("📊 Статус", "cmd_status"), MaxButton("❌ Отмена", "cmd_cancel"))
            )
        } else {
            listOf(
                listOf(MaxButton("🔍 Проверить заказы", "cmd_check")),
                listOf(MaxButton("📊 Статус", "cmd_status"), MaxButton("❌ Отмена", "cmd_cancel"))
            )
        }
    }

    private suspend fun sendMaxMessage(chatId: String, text: String, threadId: Int = 0, buttons: List<List<MaxButton>>? = null) {
        maxClient?.sendMessage(chatId, text, threadId, buttons)
    }

    private suspend fun sendMaxDocumentToAllChats(file: File, caption: String) {
        val oChat = preferencesManager.getMaxOrdersChatId()
        val sChat = preferencesManager.getMaxSupplyChatId()

        if (sChat.isNotBlank()) {
            val sThread = preferencesManager.getMaxSupplyThreadId()
            maxClient?.sendDocument(sChat, file, caption, sThread)
        } else if (oChat.isNotBlank()) {
            val oThread = preferencesManager.getMaxOrdersThreadId()
            maxClient?.sendDocument(oChat, file, caption, oThread)
        }
    }

    private suspend fun checkAndNotifyAboutOrders(autoCheck: Boolean) {
        try {
            val orderChecker = WbOrderChecker(applicationContext)
            val newOrders = orderChecker.getNewOrders()

            if (newOrders.isEmpty()) {
                if (!autoCheck) {
                    val oChat = preferencesManager.getMaxOrdersChatId()
                    if (oChat.isNotBlank()) {
                        sendMaxMessage(oChat, "Новых заказов нет.", preferencesManager.getMaxOrdersThreadId())
                    }
                }
                return
            }

            val ordersMessage = buildString {
                newOrders.forEach { order ->
                    appendLine()
                    appendLine("• *${order.article}*")
                    appendLine("  Номер: `${order.id}`")
                    appendLine("  Дата: ${order.createdAt}")
                }
            }

            val oChat = preferencesManager.getMaxOrdersChatId()
            if (oChat.isNotBlank()) {
                sendMaxMessage(oChat, "📦 *Поступили новые заказы WB:*$ordersMessage", preferencesManager.getMaxOrdersThreadId())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка проверки заказов в MAX: ${e.message}")
        }
    }

    private suspend fun sendToDeliveryAndGetQRCodes() {
        try {
            val supplyChecker = WbSupplyChecker(applicationContext)
            val activeSupply = supplyChecker.getLastActiveSupply() ?: return

            val delivery = WBDeliveryAndQRCodes(applicationContext)
            delivery.addCargoLocations(activeSupply)

            val qrResult = delivery.getQRCodesSuppliesWithDetails(activeSupply)
            if (qrResult.files.isNotEmpty()) {
                qrResult.files.forEachIndexed { idx, file ->
                    sendMaxDocumentToAllChats(file, "📷 QR-стикер коробки ${idx + 1} поставки `$activeSupply`")
                }
            }

            val stickers = delivery.getStickersForOrders(activeSupply)
            if (stickers.isNotEmpty()) {
                val mergedPdf = delivery.createMergedStickersPdf(stickers, activeSupply)
                if (mergedPdf != null && mergedPdf.exists()) {
                    sendMaxDocumentToAllChats(mergedPdf, "🏷 Стикеры заказов - ${stickers.size} шт.")
                }
            }

            val excelFile = delivery.generateSelectionSheet(activeSupply, stickers)
            if (excelFile != null && excelFile.exists()) {
                sendMaxDocumentToAllChats(excelFile, "📋 Лист подбора")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обработки поставки MAX: ${e.message}")
        }
    }

    private suspend fun deliverActiveSupply(chatId: String, threadId: Int) {
        val supplyChecker = WbSupplyChecker(applicationContext)
        val activeSupply = supplyChecker.getLastActiveSupply() ?: return

        val cargoSpaces = preferencesManager.getNumberCargoSpaces()
        val delivery = WBDeliveryAndQRCodes(applicationContext)
        val result = delivery.sendToDeliveryWithDetails(activeSupply, cargoSpaces)

        val text = if (result.success) {
            "✅ Поставка `$activeSupply` переведена в статус 'В доставке' в MAX!"
        } else {
            "❌ Ошибка перевода в доставку: ${result.errorMessage}"
        }
        sendMaxMessage(chatId, text, threadId)
    }

    private fun startForegroundServiceNotification() {
        val channelId = CHANNEL_ID
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "MAX Bot Service",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("WB MAX Bot Controller")
            .setContentText("MAX Мессенджер бот запущен")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
    }
}