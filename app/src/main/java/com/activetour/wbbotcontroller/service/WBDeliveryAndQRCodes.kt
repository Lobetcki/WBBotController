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

        val urlStr = prefs.getWbIdOrdersInSupplyUrl().replace("{supplyId}", supplyId).replace("%s", supplyId)
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
    suspend fun getOrdersDetailsBySupply(orderIds: List<Long>, supplyId: String
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
     * Запрос QR-кодов грузомест (коробок) поставки от WB API
     */
    data class QrResult(val files: List<File>, val errorMessage: String?)
    data class DeliveryResult(val success: Boolean, val errorMessage: String?)

    private fun parseWbError(responseBody: String, responseCode: Int): String {
        if (responseBody.isBlank()) return "HTTP ошибка ${responseCode}"
        return try {
            val json = JsonParser.parseString(responseBody).asJsonObject
            when {
                json.has("message") && !json.get("message").isJsonNull -> json.get("message").asString
                json.has("errorText") && !json.get("errorText").isJsonNull -> json.get("errorText").asString
                json.has("error") && !json.get("error").isJsonNull -> json.get("error").asString
                else -> responseBody.take(200)
            }
        } catch (_: Exception) {
            responseBody.take(200).ifBlank { "HTTP ошибка ${responseCode}" }
        }
    }

    /**
     * Запрос QR-кодов грузомест (коробок) поставки от WB API с детализацией ошибки
     */
    suspend fun getQRCodesSuppliesWithDetails(supplyId: String): QrResult = withContext(Dispatchers.IO) {
        val token = prefs.getWbApiToken()
        if (token.isBlank()) {
            return@withContext QrResult(emptyList(), "WB API токен не настроен")
        }

        val trbxIds = getTrbxIdsForSupply(supplyId)
        if (trbxIds == null) {
            return@withContext QrResult(emptyList(), "Не удалось получить список грузомест (ошибка запроса GET /trbx)")
        }
        if (trbxIds.isEmpty()) {
            return@withContext QrResult(emptyList(), "У поставки $supplyId отсутствуют зарегистрированные грузоместа (trbx). Сначала добавьте грузоместа.")
        }

        val type = prefs.getTypeFileQRCodes().lowercase()
        if (type !in setOf("svg", "zplv", "zplh", "png")) {
            return@withContext QrResult(emptyList(), "Неподдерживаемый формат QR-стикера: $type")
        }
        val rawUrl = prefs.getWBQRCodeSuppliesUrl().replace("{supplyId}", supplyId).replace("%s", supplyId)
        val url = rawUrl.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("type", type)
            ?.build() ?: return@withContext QrResult(emptyList(), "Неверный URL генерации QR-кодов")

        val jsonBody = gson.toJson(mapOf("trbxIds" to trbxIds))
        val body = jsonBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        return@withContext try {
            val response = client.newCall(request).execute()
            val bodyString = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                val jsonObject = JsonParser.parseString(bodyString).asJsonObject
                if (!jsonObject.has("stickers")) {
                    return@withContext QrResult(emptyList(), "Ответ WB API не содержит поле 'stickers': $bodyString")
                }
                val stickersArray = jsonObject.getAsJsonArray("stickers")
                val resultFiles = mutableListOf<File>()

                for ((index, element) in stickersArray.withIndex()) {
                    val stickerObj = element.asJsonObject
                    val fileContent = stickerObj.get("file")
                        ?.takeUnless { it.isJsonNull }
                        ?.asString
                        .orEmpty()
                    if (fileContent.isNotEmpty()) {
                        try {
                            val fileBytes = if (type == "png") {
                                Base64.decode(fileContent, Base64.DEFAULT)
                            } else {
                                fileContent.toByteArray(Charsets.UTF_8)
                            }
                            val barcode = stickerObj.get("barcode")
                                ?.takeUnless { it.isJsonNull }
                                ?.asString
                                .orEmpty()
                            val safeId = barcode.replace(Regex("[^A-Za-z0-9_-]"), "_")
                                .take(80).ifBlank { "${supplyId}_$index" }
                            val tempFile = File(context.cacheDir, "qrcode_trbx_${safeId}.$type")
                            FileOutputStream(tempFile).use { fos -> fos.write(fileBytes) }
                            resultFiles.add(tempFile)
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Ошибка сохранения QR-стикера №${index + 1}", e)
                        }
                    }
                }
                if (resultFiles.isEmpty()) {
                    QrResult(emptyList(), "WB API вернул пустой список файлов стикеров для грузомест")
                } else {
                    QrResult(resultFiles, null)
                }
            } else {
                val err = parseWbError(bodyString, response.code)
                QrResult(emptyList(), err)
            }
        } catch (e: Exception) {
            QrResult(emptyList(), e.message ?: "Сетевая ошибка при запросе QR-кодов")
        }
    }

    /**
     * Вспомогательный метод для получения ID грузомест поставки
     */
    private suspend fun getTrbxIdsForSupply(supplyId: String): List<String>? = withContext(Dispatchers.IO) {
        val token = prefs.getWbApiToken()
        val url = prefs.getWBListBoxIDsUrl().replace("{supplyId}", supplyId).replace("%s", supplyId)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyString = response.body?.string() ?: return@withContext null
                val jsonObject = JsonParser.parseString(bodyString).asJsonObject
                // GET /trbx возвращает {"trbxes":[{"id":"WB-MP-..."}]} или {"trbxs":[{"id":"WB-TRBX-..."}]}.
                val trbxArray = jsonObject.getAsJsonArray("trbxes")
                    ?: jsonObject.getAsJsonArray("trbxs")
                    ?: run {
                        Log.e(TAG, "❌ В ответе GET /trbx отсутствует поле trbxes/trbxs: $bodyString")
                        return@withContext null
                    }
                trbxArray.mapNotNull { element ->
                    element.takeIf { it.isJsonObject }
                        ?.asJsonObject
                        ?.get("id")
                        ?.takeUnless { it.isJsonNull }
                        ?.asString
                }
            } else {
                val errorBody = response.body?.string().orEmpty()
                Log.e(TAG, "❌ Ошибка получения списка грузомест. Код: ${response.code}, ответ: $errorBody")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка: ${e.message}", e)
            null
        }
    }

    /**
     * Добавить количество грузомест в поставку с детализацией ошибки
     */
    suspend fun addCargoLocations(supplyId: String): DeliveryResult = withContext(Dispatchers.IO) {
        val numberCargoSpaces = prefs.getNumberCargoSpaces()
        if (numberCargoSpaces < 1) return@withContext DeliveryResult(false, "Количество грузомест меньше 1")
        val token = prefs.getWbApiToken()
        if (token.isBlank()) return@withContext DeliveryResult(false, "WB API токен не настроен")

        val jsonBody = gson.toJson(mapOf("amount" to numberCargoSpaces))
        val body = jsonBody.toRequestBody(mediaType)
        val url = prefs.getWbAddNumberCargoSpacesUrl().replace("{supplyId}", supplyId).replace("%s", supplyId)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        return@withContext try {
            val response = client.newCall(request).execute()
            val bodyString = response.body?.string().orEmpty()
            if (response.isSuccessful || response.code == 200 || response.code == 204 || response.code == 409) {
                DeliveryResult(true, null)
            } else {
                val err = parseWbError(bodyString, response.code)
                DeliveryResult(false, err)
            }
        } catch (e: Exception) {
            DeliveryResult(false, e.message ?: "Сетевая ошибка при добавлении грузомест")
        }
    }

    /**
     * Генерация CSV-файла (Лист подбора) для печати/склада
     */
    suspend fun generateSelectionSheet(supplyId: String, stickers: List<StickerForOrder>): File? = withContext(Dispatchers.IO) {
        val orderIds = getOrderIdsList(supplyId) ?: return@withContext null
        if (orderIds.isEmpty()) return@withContext null

        val orders = getOrdersDetailsBySupply(orderIds, supplyId) ?: return@withContext null
        val tempFile = File(context.cacheDir, "selection_sheet_${supplyId}.csv")

        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        FileOutputStream(tempFile).use { fos ->
            fos.write(bom)
            val writer = fos.bufferedWriter(Charsets.UTF_8)
            writer.write("№ задания;Артикул продавца;Дата создания;Стикер")
            writer.newLine()

            orders.forEach { order ->
                val row = listOf(
                    order.id.toString(),
                    order.article,
                    order.createdAt,
                    stickers.find { it.orderId == order.id }
                        ?.let { "${it.partA} ${it.partB}" }
                        ?: ""
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
     * Передать поставку в доставку с детализацией ошибки от WB API
     */
    suspend fun sendToDeliveryWithDetails(supplyId: String, cargoSpacesCount: Int = prefs.getNumberCargoSpaces()): DeliveryResult = withContext(Dispatchers.IO) {
        if (cargoSpacesCount <= 0) {
            return@withContext DeliveryResult(false, "Количество грузомест ($cargoSpacesCount) должно быть больше 0")
        }
        val token = prefs.getWbApiToken()
        if (token.isBlank()) return@withContext DeliveryResult(false, "WB API токен не настроен")

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
            val bodyString = response.body?.string().orEmpty()
            Log.i(TAG, "📦 Ответ WB на deliver [supplyId=$supplyId]: код=${response.code}, тело=$bodyString")
            if (response.isSuccessful && (response.code == 200 || response.code == 204)) {
                DeliveryResult(true, null)
            } else {
                val err = parseWbError(bodyString, response.code)
                DeliveryResult(false, err)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка передачи в доставку: ${e.message}", e)
            DeliveryResult(false, e.message ?: "Сетевая ошибка при передаче в доставку")
        }
    }

    suspend fun sendToDelivery(supplyId: String, cargoSpacesCount: Int = prefs.getNumberCargoSpaces()): Boolean {
        return sendToDeliveryWithDetails(supplyId, cargoSpacesCount).success
    }

    /**
     * Объединяет все отдельные файлы стикеров в один единый многостраничный PDF-файл
     */
    fun createMergedStickersPdf(stickers: List<StickerForOrder>, supplyId: String): File? {
        val validStickers = stickers.filter { it.file.exists() }
        if (validStickers.isEmpty()) return null

        val outputFile = File(context.cacheDir, "all_stickers_${supplyId}.pdf")
        val pdfDocument = android.graphics.pdf.PdfDocument()

        return try {
            var pageIndex = 1
            for (sticker in validStickers) {
                val bitmap = android.graphics.BitmapFactory.decodeFile(sticker.file.absolutePath) ?: continue

                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, pageIndex).create()
                val page = pdfDocument.startPage(pageInfo)

                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdfDocument.finishPage(page)

                bitmap.recycle()
                pageIndex++
            }

            if (pageIndex == 1) {
                pdfDocument.close()
                return null
            }

            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            Log.i(TAG, "✅ Создан объединённый PDF-файл со стикерами: ${outputFile.absolutePath}")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка объединения стикеров в PDF: ${e.message}", e)
            null
        } finally {
            try {
                pdfDocument.close()
            } catch (_: Exception) {}
        }
    }
}