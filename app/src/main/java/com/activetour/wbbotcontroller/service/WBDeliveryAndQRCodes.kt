package com.activetour.wbbotcontroller.service

import android.content.Context
import android.util.Base64
import android.util.Log
import com.activetour.wbbotcontroller.model.StickerForOrder
import com.activetour.wbbotcontroller.model.WBOrder
import com.activetour.wbbotcontroller.utils.PreferencesManager
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class WBDeliveryAndQRCodes(private val context: Context) {

    companion object {
        private const val TAG = "WBDeliveryAndQRCodes"
    }

    private val client = OkHttpClient()
    private val gson = Gson()
    private val prefs = PreferencesManager(context)
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Получить список ID всех заказов в поставке
     */
    suspend fun getOrderIdsList(supplyId: String): List<Long>? = withContext(Dispatchers.IO) {
        val token = prefs.getWbApiToken()
        if (token.isBlank()) {
            Log.e(TAG, "❌ WB API токен не настроен!")
            return@withContext null
        }

        val urlStr = String.format(prefs.getWbIdOrdersInSupplyUrl(), supplyId)
        val request = Request.Builder()
            .url(urlStr)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        return@withContext try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                val jsonObject = JsonParser.parseString(body).asJsonObject
                val orderIdsArray = jsonObject.getAsJsonArray("orderIds")
                val orderIds = orderIdsArray?.map { it.asLong } ?: emptyList()
                Log.i(TAG, "✅ Получены ID заказов поставки $supplyId: $orderIds")
                orderIds
            } else {
                Log.e(TAG, "❌ Ошибка получения ID заказов поставки $supplyId. Код: ${response.code}")
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "❌ Ошибка получения ID заказов: ${e.message}", e)
            null
        }
    }

    /**
     * Получение информации о деталях заказов в поставке
     */
    suspend fun getOrdersDetailsBySupply(
        orderIds: List<Long>,
        supplyId: String
    ): List<WBOrder>? = withContext(Dispatchers.IO) {
        if (orderIds.isEmpty()) return@withContext emptyList()

        val token = prefs.getWbApiToken()
        if (token.isBlank()) {
            Log.e(TAG, "❌ WB API токен не настроен!")
            return@withContext null
        }

        val orderIdSet = orderIds.toSet()
        val matchedOrders = mutableListOf<WBOrder>()
        var nextCursor: String? = "0"

        do {
            val url = prefs.getWBOrdersDetailsUrl().toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("limit", prefs.getLimitForSelectionList().toString())
                ?.addQueryParameter("next", nextCursor)
                ?.build() ?: return@withContext null

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e(TAG, "❌ Ошибка API при получении деталей заказов. Код: ${response.code}")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                val jsonObject = JsonParser.parseString(body).asJsonObject

                nextCursor = if (jsonObject.has("next") && !jsonObject.get("next").isJsonNull) {
                    jsonObject.get("next").asString
                } else {
                    null
                }

                if (jsonObject.has("orders")) {
                    val ordersArray = jsonObject.getAsJsonArray("orders")
                    val type = object : TypeToken<List<WBOrder>>() {}.type
                    val orders: List<WBOrder> = gson.fromJson(ordersArray, type)

                    val filtered = orders.filter { it.id in orderIdSet }
                    matchedOrders.addAll(filtered)
                }

            } catch (e: IOException) {
                Log.e(TAG, "❌ Ошибка: ${e.message}", e)
                return@withContext null
            }

        } while (!nextCursor.isNullOrEmpty() && nextCursor != "0")

        return@withContext matchedOrders
    }

    /**
     * Запрос стикеров (QR-кодов) для списка заказов в поставке
     */
    suspend fun getStickersForOrders(supplyId: String): List<StickerForOrder> = withContext(Dispatchers.IO) {
        val orderIds = getOrderIdsList(supplyId) ?: return@withContext emptyList()
        if (orderIds.isEmpty()) return@withContext emptyList()

        val token = prefs.getWbApiToken()
        if (token.isBlank()) {
            Log.e(TAG, "❌ WB API токен не настроен!")
            return@withContext emptyList()
        }

        val type = prefs.getTypeFileQRCodes()
        val url = prefs.getWbStickersUrl().toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("type", type)
            ?.addQueryParameter("width", "40")
            ?.addQueryParameter("height", "30")
            ?.build() ?: return@withContext emptyList()

        val jsonBody = gson.toJson(mapOf("orders" to orderIds))
        val body = jsonBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "❌ Ошибка получения стикеров от WB. Код: ${response.code}")
                return@withContext emptyList()
            }

            val bodyString = response.body?.string() ?: return@withContext emptyList()
            val jsonObject = JsonParser.parseString(bodyString).asJsonObject
            if (!jsonObject.has("stickers")) return@withContext emptyList()

            val stickersArray = jsonObject.getAsJsonArray("stickers")
            val result = mutableListOf<StickerForOrder>()

            for (element in stickersArray) {
                val stickerObj = element.asJsonObject
                val orderId = stickerObj.get("orderId").asLong
                val partA = if (stickerObj.has("partA")) stickerObj.get("partA").asString else ""
                val partB = if (stickerObj.has("partB")) stickerObj.get("partB").asString else ""
                val barcode = if (stickerObj.has("barcode")) stickerObj.get("barcode").asString else ""
                val fileBase64 = if (stickerObj.has("file")) stickerObj.get("file").asString else barcode

                if (fileBase64.isNotEmpty()) {
                    val fileBytes = try {
                        Base64.decode(fileBase64, Base64.DEFAULT)
                    } catch (e: Exception) {
                        null
                    }

                    if (fileBytes != null) {
                        val file = File(context.cacheDir, "sticker_${orderId}.$type")
                        FileOutputStream(file).use { fos -> fos.write(fileBytes) }

                        result.add(
                            StickerForOrder(
                                orderId = orderId,
                                partA = partA,
                                partB = partB,
                                barcode = barcode,
                                file = file
                            )
                        )
                    }
                }
            }

            Log.i(TAG, "✅ Успешно получено ${result.size} стикеров для заказов поставки $supplyId")
            return@withContext result

        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка при получении стикеров: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    /**
     * Запрос QR-кода самой поставки от WB API
     */
    suspend fun getQRCodesSupplies(supplyId: String): File? = withContext(Dispatchers.IO) {
        val token = prefs.getWbApiToken()
        if (token.isBlank()) {
            Log.e(TAG, "❌ WB API токен не настроен!")
            return@withContext null
        }

        val type = prefs.getTypeFileQRCodes()
        val rawUrl = String.format(prefs.getWBQRCodeSuppliesUrl(), supplyId)
        val url = rawUrl.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("type", type)
            ?.build() ?: return@withContext null

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        return@withContext try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyString = response.body?.string() ?: return@withContext null
                val jsonObject = JsonParser.parseString(bodyString).asJsonObject
                val base64Data = if (jsonObject.has("file")) {
                    jsonObject.get("file").asString
                } else if (jsonObject.has("barcode")) {
                    jsonObject.get("barcode").asString
                } else {
                    null
                }

                if (base64Data.isNullOrEmpty()) {
                    Log.e(TAG, "❌ Поле 'file'/'barcode' отсутствует или пусто")
                    return@withContext null
                }

                val fileBytes = Base64.decode(base64Data, Base64.DEFAULT)
                val tempFile = File(context.cacheDir, "qrcode_supply_${supplyId}.$type")
                FileOutputStream(tempFile).use { fos -> fos.write(fileBytes) }
                Log.i(TAG, "✅ QR-код поставки сохранён: ${tempFile.absolutePath}")
                tempFile
            } else {
                Log.e(TAG, "❌ Ошибка получения QR-кода поставки. Код: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка получения QR-кода: ${e.message}", e)
            null
        }
    }

    /**
     * Добавить количество грузомест в поставку
     */
    suspend fun addCargoLocations(supplyId: String): Boolean = withContext(Dispatchers.IO) {
        val numberCargoSpaces = prefs.getNumberCargoSpaces()
        val token = prefs.getWbApiToken()
        if (token.isBlank()) return@withContext false

        val jsonBody = gson.toJson(mapOf("amount" to numberCargoSpaces))
        val body = jsonBody.toRequestBody(mediaType)
        val url = String.format(prefs.getWbAddNumberCargoSpacesUrl(), supplyId)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        return@withContext try {
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка добавления грузомест: ${e.message}", e)
            false
        }
    }

    /**
     * Генерация CSV-файла (Лист подбора) для печати/склада
     */
    suspend fun generateSelectionSheet(supplyId: String): File? = withContext(Dispatchers.IO) {
        val orderIds = getOrderIdsList(supplyId) ?: return@withContext null
        if (orderIds.isEmpty()) return@withContext null

        val orders = getOrdersDetailsBySupply(orderIds, supplyId) ?: return@withContext null
        val tempFile = File(context.cacheDir, "selection_sheet_${supplyId}.csv")

        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        FileOutputStream(tempFile).use { fos ->
            fos.write(bom)
            val writer = fos.bufferedWriter(Charsets.UTF_8)
            writer.write("№ задания;Артикул продавца;Дата создания;Цвет")
            writer.newLine()

            orders.forEach { order ->
                val row = listOf(
                    order.id.toString(),
                    order.article,
                    order.createdAt,
                    order.colorCode ?: ""
                )
                writer.write(row.joinToString(";") { escapeCsv(it) })
                writer.newLine()
            }
            writer.flush()
        }
        return@withContext tempFile
    }

    private fun escapeCsv(value: String): String {
        val needQuotes = value.contains(';') || value.contains('"') || value.contains('\n') || value.contains('\r')
        return if (needQuotes) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    /**
     * Передать поставку в доставку
     */
    suspend fun sendToDelivery(supplyId: String, cargoSpacesCount: Int = prefs.getNumberCargoSpaces()): Boolean = withContext(Dispatchers.IO) {
        val token = prefs.getWbApiToken()
        if (token.isBlank()) return@withContext false

        val url = "${prefs.getWbSuppliesUrl()}/$supplyId/deliver"
        val jsonBody = gson.toJson(mapOf("cargoSpacesCount" to cargoSpacesCount))
        val body = jsonBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .patch(body)
            .build()

        return@withContext try {
            val response = client.newCall(request).execute()
            response.isSuccessful && (response.code == 200 || response.code == 204)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка передачи поставки в доставку: ${e.message}", e)
            false
        }
    }
}