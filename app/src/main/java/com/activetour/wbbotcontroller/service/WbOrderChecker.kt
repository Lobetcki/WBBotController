package com.activetour.wbbotcontroller.service

import android.content.Context
import android.util.Log
import com.activetour.wbbotcontroller.model.WBOrder
import com.activetour.wbbotcontroller.utils.PreferencesManager
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class WbOrderChecker(private val context: Context) {

    companion object {
        private const val TAG = "WbOrderChecker"
    }

    private val client = OkHttpClient()
    private val gson = Gson()
    private val prefs = PreferencesManager(context)

    suspend fun getNewOrders(): List<WBOrder> = withContext(Dispatchers.IO) {

        // 🎭 Если включён мок‑режим – возвращаем тестовые данные
        if (prefs.isMockMode()) {
            Log.d(TAG, "🎭 МОК-РЕЖИМ: возвращаем тестовые заказы")
            return@withContext getMockOrders()
        }

        val token = prefs.getWbApiToken()
        val url = prefs.getWbOrdersUrl()

        if (token.isBlank()) {
            Log.e(TAG, "❌ WB API токен не настроен!")
            return@withContext emptyList()
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext emptyList()

            Log.d(TAG, "HTTP Status: ${response.code}")

            if (!response.isSuccessful) {
                Log.e(TAG, "Ошибка WB API: ${response.code} - $responseBody")
                return@withContext emptyList()
            }

            parseOrdersResponse(responseBody)
        } catch (e: IOException) {
            Log.e(TAG, "Ошибка при получении заказов: ${e.message}", e)
            emptyList()
        }
    }

    private fun parseOrdersResponse(responseBody: String): List<WBOrder> {
        try {
            val jsonObject = JsonParser.parseString(responseBody).asJsonObject

            if (!jsonObject.has("orders")) {
                Log.w(TAG, "Ответ не содержит поля 'orders'")
                return emptyList()
            }

            val ordersArray = jsonObject.getAsJsonArray("orders")
            if (ordersArray.isEmpty()) {
                Log.i(TAG, "Нет новых заказов")
                return emptyList()
            }

            val orderListType = object : TypeToken<ArrayList<WBOrder>>() {}.type
            val orders: List<WBOrder> = gson.fromJson(ordersArray, orderListType)

            Log.i(TAG, "✅ Получено ${orders.size} новых сборочных заданий")

            orders.forEach { order ->
                Log.d(TAG, "Заказ ID: ${order.id}, Артикул: ${order.article}, Дата: ${order.createdAt}")
            }

            return orders
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка парсинга JSON: ${e.message}")
            Log.d(TAG, "Ответ сервера: $responseBody")
            return emptyList()
        }
    }

    /**
     * Генерирует список тестовых заказов для отладки.
     */
    private fun getMockOrders(): List<WBOrder> {
        return listOf(
            WBOrder(
                id = 100528,
                article = "Мок-товар 1",
                createdAt = "2026-05-20T10:00:00Z"
                // остальные поля будут заполнены значениями по умолчанию (null, 0, false)
            ),
            WBOrder(
                id = 100529,
                article = "Мок-товар 2",
                createdAt = "2026-05-20T10:05:00Z"
            ),
            WBOrder(
                id = 100530,
                article = "Мок-товар 3",
                createdAt = "2026-05-20T10:10:00Z"
            )
        )
    }
}