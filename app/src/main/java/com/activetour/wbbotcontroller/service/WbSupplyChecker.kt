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

class WbSupplyChecker(private val context: Context) {

    companion object {
        private const val TAG = "WbSupplyChecker"
    }

    private val client = OkHttpClient()
    private val gson = Gson()
    private val prefs = PreferencesManager(context)

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun getLastActiveSupply(): String? = withContext(Dispatchers.IO) {
        val token = prefs.getWbApiToken()
        val url = prefs.getWbSuppliesUrl()
        if (token.isBlank()) {
            Log.e(TAG, "❌ WB API токен не настроен!")
            return@withContext null
        }

        val limit = 100 // максимально возможный, чтобы уменьшить число запросов
        var next = prefs.getNextForListSupplies()
        var lastActiveId: String? = null

        do {
            val request = Request.Builder()
                .url("$url?limit=$limit&next=$next")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: break
                if (!response.isSuccessful) {
                    Log.e(TAG, "❌ Ошибка API - получения поставок. Код: ${response.code}")
                    break
                }

                val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
                val supplies = jsonResponse.getAsJsonArray("supplies") ?: break

                // Если пришёл пустой массив – новых поставок нет
                if (supplies.size() == 0) {
                    Log.d(TAG, "Новых поставок нет")
                    if (next > 10) prefs.setNextForListSupplies(next - 10)
                    break
                }

                // Ищем активную поставку (с конца массива)
                for (i in supplies.size() - 1 downTo 0) {
                    val supply = supplies[i].asJsonObject

                    if (supply.has("done") && !supply.get("done").asBoolean) {
                        val supplyId = supply.get("id").asString
//                        Log.i(TAG, "📦 Найдена активная поставка: $supplyId")
                        lastActiveId = supplyId
                        break
                    }
                }

                // Получаем next для следующего запроса
                val nextValue = jsonResponse.get("next").asLong
                // Сохраняем next для следующего вызова (это будет курсор на следующую страницу)
                if (nextValue > 10) {
                    prefs.setNextForListSupplies(nextValue - 10)
                }
                next = nextValue

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка: ${e.message}", e)
                break
            }
        } while (true)

        if (lastActiveId != null) {
            Log.i(TAG, "📦 Найдена последняя активная поставка: $lastActiveId")
        } else {
            Log.i(TAG, "Активных поставок не найдено")
        }
        return@withContext lastActiveId
    }

    suspend fun createSupply(supplyName: String): String? = withContext(Dispatchers.IO) {
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
        if (orderIds.isEmpty()) {
            Log.w(TAG, "Нет заказов для добавления")
            return@withContext false
        }

        val token = prefs.getWbApiToken()
        val url = String.format(prefs.getWbAddOrdersUrl(), supplyId)

        if (token.isBlank()) {
            Log.e(TAG, "❌ WB API токен не настроен!")
            return@withContext false
        }

        val jsonBody = gson.toJson(mapOf("orders" to orderIds))
        val body = jsonBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
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

    suspend fun getCountOrdersInSupply(supplyId: String): Int = withContext(Dispatchers.IO) {

        val url = String.format(prefs.getWbIdOrdersInSupplyUrl(), supplyId)

        val token = prefs.getWbApiToken()
        if (token.isBlank()) {
            Log.e(TAG, "❌ WB API токен не настроен!")
            return@withContext 0
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext 0

            Log.d(TAG, "HTTP Status: ${response.code}")

            if (!response.isSuccessful) {
                Log.e(TAG, "Ошибка WB API: ${response.code} - $responseBody")
                return@withContext 0
            }

            // Парсим JSON-ответ
            val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
            val orderIdsArray = jsonResponse.getAsJsonArray("orderIds")
            // Если поле отсутствует или null, возвращаем 0
            if (orderIdsArray == null) {
                return@withContext 0
            }
            // Возвращаем количество ID в массиве
            orderIdsArray.size()
        } catch (e: IOException) {
            Log.e(TAG, "Ошибка при получении кол-ва заказов в поставке: ${e.message}", e)
            0
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка парсинга ответа: ${e.message}", e)
            0
        }
    }

}