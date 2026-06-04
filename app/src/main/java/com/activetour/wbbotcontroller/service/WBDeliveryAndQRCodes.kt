package com.activetour.wbbotcontroller.service

import android.content.Context
import android.util.Log
import com.activetour.wbbotcontroller.model.WBOrder
import com.activetour.wbbotcontroller.utils.PreferencesManager
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.io.FileOutputStream

class WBDeliveryAndQRCodes(private val context: Context) {

    companion object {
        private const val TAG = "WBDeliveryAndQRCodes"
    }

    private val client = OkHttpClient()
    private val gson = Gson()
    private val prefs = PreferencesManager(context)

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private val processedOrderIds = mutableSetOf<Long>()

    suspend fun sendToDeliveryAndGetQRCodes(currentSupplyId: String?): String {
        if (currentSupplyId.isNullOrEmpty()) return "Нет поставки для добавления в доставку"
        Log.d(TAG, "🚚 Обработка поставки $currentSupplyId")

        // 1. Генерируем файл листа подбора
//        val excelFile = generateSelectionSheet(currentSupplyId)
//        if (excelFile == null) {
//            return "❌ Ошибка при формировании Листа подбора для поставки $currentSupplyId."
//        }
        // Здесь нужно отправить файл в чат (вызывающий код должен это сделать)
        // Например, вернуть путь к файлу или сам файл, чтобы TelegramBotService отправил его.
        // Пока просто возвращаем успех, но фактически файл не отправлен.

        // 2. Добавляем грузоместа
//        if (!addCargoLocations(currentSupplyId)) return "❌ Ошибка добавления грузомест в поставку $currentSupplyId."
        // 3. Получаем QR-коды
//        if (!getQRCodes(currentSupplyId)) return "❌ Ошибка при получении QR кодов для поставки $currentSupplyId."
        // 4. Передаём в доставку
        if (!sendToDelivery(currentSupplyId)) return "❌ Ошибка добавления поставки $currentSupplyId в доставку."

        return ""
    }

    // ========== 1. Генерация файла листа подбора ==========
    suspend fun generateSelectionSheet(currentSupplyId: String): File? {
        val orderIds = getOrderIdsList(currentSupplyId) ?: return null
        if (orderIds.isEmpty()) {
            Log.e(TAG, "Нет заказов в поставке $currentSupplyId")
            return null
        }

        val orders: List<WBOrder>? = getOrdersDetailsBySupply(orderIds, currentSupplyId) ?: return null
        if (orders!!.isEmpty()) return null

        val tempFile = File.createTempFile(
            "selection_sheet_$currentSupplyId",
            ".csv",
            context.cacheDir
        )

        // UTF-8 с BOM для русских букв
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        FileOutputStream(tempFile).use { fos ->
            fos.write(bom)
            val writer = fos.bufferedWriter(Charsets.UTF_8)
            // Заголовки
            val headers = listOf(
                "№ задания",
                "Цвет",
                "Артикул продавца",
                "Стикер",
                "Дата создания",
//                "НМ ID",
//                "Цена",
//                "Количество",
//                "Склад"
            )
            writer.write(headers.joinToString(";") { escapeCsv(it) })
            writer.newLine()


            // Данные
            orders.forEach { order ->
                val row = listOf(
                    order.id?.toString() ?: "",
                    order.article ?: "",
                    order.createdAt ?: "",
                    order.colorCode ?: "",
//                    "1",  // количество (можно вычислить из skus)
//                    order.nmId?.toString() ?: "",
//                    order.price?.toString() ?: "",
//                    order.warehouseId?.toString() ?: ""
                ) ////////////////////////////////////////////////
                writer.write(row.joinToString(";") { escapeCsv(it) })
                writer.newLine()
            }
            writer.flush()
        }
        Log.i(TAG, "✅ CSV-файл создан: ${tempFile.absolutePath}")
        return tempFile
    }

    private fun escapeCsv(value: String): String {
        // Если значение содержит точку с запятой, кавычку или перевод строки – оборачиваем в кавычки и дублируем кавычки
        val needQuotes =
            value.contains(';') || value.contains('"') || value.contains('\n') || value.contains(
                '\r'
            )
        return if (needQuotes) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    // ========== 2. Получение списка ID заказов в поставке ==========
    suspend fun getOrderIdsList(currentSupplyId: String): List<Long>? {
        delay(1000)                         //////////////////////
        val token = prefs.getWbApiToken()
        if (token.isBlank()) {
            Log.e(TAG, "❌ WB API токен не настроен!")
            return null
        }

        val url = prefs.getWBOrderIdsListUrl().replace("{supplyId}", currentSupplyId)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                val jsonObject = JsonParser.parseString(body).asJsonObject
                val orderIdsArray = jsonObject.getAsJsonArray("orderIds")
                val orderIds = orderIdsArray?.map { it.asLong } ?: emptyList()
                Log.i(TAG, "✅ Получены ID заказов поставки $currentSupplyId: $orderIds")
                orderIds
            } else {
                Log.e(
                    TAG,
                    "❌ Ошибка получения ID заказов поставки $currentSupplyId. Код: ${response.code}"
                )
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "❌ Ошибка олучены ID заказов поставки $currentSupplyId: ${e.message}", e)
            null
        }
    }

    // ========== 3. Получение деталей заказов по их ID ==========
    suspend fun getOrdersDetailsBySupply(
        orderIds: List<Long>,
        currentSupplyId: String
    ): List<WBOrder>? {

        if (orderIds.isEmpty()) return emptyList()
        delay(1000)

        val token = prefs.getWbApiToken()
        if (token.isBlank()) {
            Log.e(TAG, "❌ WB API токен не настроен!")
            return null
        }

        val orderIdSet = orderIds.toSet()
        val matchedOrders = mutableListOf<WBOrder>()
        // Начинаем с минимального ID в виде строки
        var nextCursor: String? = orderIds.minOrNull()?.toString() ?: "0"

        do {
            val url = prefs.getWBOrdersDetailsUrl().toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("limit", prefs.getLimitForSelectionList().toString())
                ?.addQueryParameter("next", nextCursor)
                ?.build() ?: run {
                Log.e(TAG, "❌ Не удалось построить URL")
                return null
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e(TAG, "❌ Ошибка API. Код: ${response.code}")
                    return null
                }
                val body = response.body?.string() ?: return null
                val jsonObject = JsonParser.parseString(body).asJsonObject

                // Обновляем курсор для следующей итерации (строкой)
                nextCursor = if (jsonObject.has("next") && !jsonObject.get("next").isJsonNull) {
                    jsonObject.get("next").asString
                } else {
                    null
                }

                val ordersArray = jsonObject.getAsJsonArray("orders")
                val type = object : TypeToken<List<WBOrder>>() {}.type
                val orders: List<WBOrder> = gson.fromJson(ordersArray, type)

                // Фильтруем по ID и supplyId
                val filtered = orders.filter { it.id in orderIdSet && it.supplyId == currentSupplyId }
                matchedOrders.addAll(filtered)

                Log.i(TAG, "Получено ${orders.size} заказов, отфильтровано ${filtered.size}. Следующий курсор: $nextCursor")

            } catch (e: IOException) {
                Log.e(TAG, "❌ Ошибка: ${e.message}", e)
                return null
            }

        } while (nextCursor != null && nextCursor != "0")

        return matchedOrders
    }

    suspend fun addCargoLocations(currentSupplyId: String): Boolean {
        delay(1000)                               /////////////////////////////
        val numberCargoSpaces = prefs.getNumberCargoSpaces()
        Log.d(
            TAG,
            "= sendToDeliveryAndGetQRCodes: поставка - $currentSupplyId, добавляем количество грузомест - $numberCargoSpaces ="
        )

        val token = prefs.getWbApiToken()
        if (token.isBlank()) {
            Log.e(TAG, "❌ WB API токен не настроен!")
            return false
        }

        val jsonBody = gson.toJson(mapOf("amount" to numberCargoSpaces))
        val body = jsonBody.toRequestBody(mediaType)

        val url = prefs.getWbAddNumberCargoSpacesUrl().replace("{supplyId}", currentSupplyId)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        return try {
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                Log.i(
                    TAG,
                    "✅ количество грузомест $numberCargoSpaces добавлены в поставку ${response.body} $currentSupplyId"
                )
                true
            } else {
                Log.e(
                    TAG,
                    "❌ Ошибка добавления грузомест в поставку $currentSupplyId. Код: ${response.code}"
                )
                false
            }
        } catch (e: IOException) {
            Log.e(TAG, "❌ Ошибка добавления грузомест в поставку $currentSupplyId: ${e.message}", e)
            false
        }
    }

    suspend fun getQRCodesSupplies(currentSupplyId: String): File? {
        delay(1000)

        val token = prefs.getWbApiToken()
        if (token.isBlank()) {
            Log.e(TAG, "❌ WB API токен не настроен!")
            return null
        }

//        val url = String.format(prefs.getQRCodesUrl(), supplyId)
//        val urlQRCodeSupplies = prefs.getWBQRCodeSuppliesUrl().replace("{supplyId}", currentSupplyId)

        val type = prefs.getTypeFileQRCodes()

        val url = prefs.getWBQRCodeSuppliesUrl().replace("{supplyId}", currentSupplyId)
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("type", type)
            ?.build() ?: run {
            Log.e(TAG, "❌ Не удалось построить URL для получения QR-кода для поставки")
            return null
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyString = response.body?.string() ?: return null
                val jsonObject = JsonParser.parseString(bodyString).asJsonObject
                val base64Data = jsonObject.get("file")?.asString
                if (base64Data.isNullOrEmpty()) {
                    Log.e(TAG, "❌ Поле 'file' отсутствует или пусто")
                    return null
                }
                val fileBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)

                // Определяем расширение файла по типу
//                val extension = when (type) {
//                    "png" -> "png"
//                    "svg" -> "svg"
//                    "zplv", "zplh" -> "zpl"
//                    else -> "bin"
//                }
                val tempFile = File.createTempFile("qrcode_supply_$currentSupplyId", ".$type", context.cacheDir)
                tempFile.writeBytes(fileBytes)
                Log.i(TAG, "✅ QR-код сохранён: ${tempFile.absolutePath}")
                tempFile
            } else {
                Log.e(TAG, "❌ Ошибка получения QR-кода. Код: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка: ${e.message}", e)
            null
        }
    }

    suspend fun sendToDelivery(supplyId: String, cargoSpacesCount: Int): Boolean {
        delay(1000)

        val token = prefs.getWbApiToken()
        if (token.isBlank()) {
            Log.e(TAG, "❌ WB API токен не настроен!")
            return false
        }

        // PATCH /api/marketplace/v3/supplies/{supplyId}/deliver
//        val url = "${prefs.getWbSuppliesUrl()}/$supplyId/deliver"
        val jsonBody = gson.toJson(mapOf("cargoSpacesCount" to cargoSpacesCount))
        val body = jsonBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .patch(body)
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful && response.code == 204) {
                Log.i(TAG, "✅ Поставка $supplyId передана в доставку")
                true
            } else {
                Log.e(TAG, "❌ Ошибка передачи в доставку. Код: ${response.code}")
                false
            }
        } catch (e: IOException) {
            Log.e(TAG, "❌ Ошибка передачи в доставку: ${e.message}", e)
            false
        }
    }
}