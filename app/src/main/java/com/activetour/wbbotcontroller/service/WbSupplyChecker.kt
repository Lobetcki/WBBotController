package com.activetour.wbbotcontroller.service

import android.content.Context
import android.util.Log
import com.activetour.wbbotcontroller.utils.PreferencesManager
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlinx.coroutines.delay

class WbSupplyChecker(private val context: Context) {

    companion object {
        private const val TAG = "WbSupplyChecker"
    }

    private val client = OkHttpClient()
    private val gson = Gson()
    private val prefs = PreferencesManager(context)

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun getLastActiveSupply(): String? = withContext(Dispatchers.IO) {
        delay(2000) // задержка 2 секунды для соблюдения лимита
        val token = prefs.getWbApiToken()
        val url = prefs.getWbSuppliesUrl()

        if (token.isBlank()) {
            Log.e(TAG, "❌ WB API токен не настроен!")
            return@withContext null
        }

        val request = Request.Builder()
            .url("$url?limit=100&next=0")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext null

            if (!response.isSuccessful) {
                Log.e(TAG, "❌ Ошибка получения поставок. Код: ${response.code}")
                return@withContext null
            }

            val jsonResponse = JsonParser.parseString(responseBody).asJsonObject

            if (!jsonResponse.has("supplies")) {
                Log.d(TAG, "В ответе нет поля 'supplies'")
                return@withContext null
            }

            val supplies = jsonResponse.getAsJsonArray("supplies")

            // Ищем активную поставку (с конца массива)
            for (i in supplies.size() - 1 downTo 0) {
                val supply = supplies[i].asJsonObject

                if (supply.has("done") && !supply.get("done").asBoolean) {
                    val supplyId = supply.get("id").asString
                    Log.i(TAG, "📦 Найдена активная поставка: $supplyId")
                    return@withContext supplyId
                }
            }

            Log.i(TAG, "Активных поставок не найдено")
            null
        } catch (e: IOException) {
            Log.e(TAG, "Ошибка при получении поставок: ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка парсинга ответа: ${e.message}", e)
            null
        }
    }

    suspend fun createSupply(supplyName: String): String? = withContext(Dispatchers.IO) {
        delay(2000)
        val token = prefs.getWbApiToken()
        val url = prefs.getWbSuppliesUrl()

        if (token.isBlank()) {
            Log.e(TAG, "❌ WB API токен не настроен!")
            return@withContext null
        }

        val jsonBody = gson.toJson(mapOf("name" to supplyName))
        val body = jsonBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext null

            if (response.isSuccessful && response.code == 201) {
                val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
                val supplyId = jsonResponse.get("id").asString
                Log.i(TAG, "✅ Создана поставка: $supplyId")
                return@withContext supplyId
            } else {
                Log.e(TAG, "❌ Ошибка создания поставки. Код: ${response.code}")
                return@withContext null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Ошибка при создании поставки: ${e.message}", e)
            null
        }
    }

    suspend fun addOrdersToSupply(supplyId: String, orderIds: List<Long>): Boolean = withContext(Dispatchers.IO) {
        delay(2000)
        if (orderIds.isEmpty()) {
            Log.w(TAG, "Нет заказов для добавления")
            return@withContext false
        }

        val token = prefs.getWbApiToken()
//        val url = prefs.getWbSuppliesUrl()
        val url = prefs.getWbAddOrdersUrl()

        if (token.isBlank()) {
            Log.e(TAG, "❌ WB API токен не настроен!")
            return@withContext false
        }

        val jsonBody = gson.toJson(mapOf("orders" to orderIds))
        val body = jsonBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("$url/$supplyId/orders")
            .addHeader("Authorization", "Bearer $token")
            .patch(body)
            .build()

        try {
            val response = client.newCall(request).execute()

            if (response.isSuccessful && response.code == 204) {
                Log.i(TAG, "✅ Заказы $orderIds добавлены в поставку $supplyId")
                return@withContext true
            } else {
                Log.e(TAG, "❌ Ошибка добавления заказов. Код: ${response.code}")
                return@withContext false
            }
        } catch (e: IOException) {
            Log.e(TAG, "Ошибка при добавлении заказов: ${e.message}", e)
            false
        }
    }
}