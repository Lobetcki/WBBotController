package com.activetour.wbbotcontroller.service

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

data class MaxButton(val text: String, val payload: String)

class MaxBotClient(private val botToken: String, private val baseUrl: String = "https://api.max-messenger.ru/v1") {

    companion object {
        private const val TAG = "MaxBotClient"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Отправляет текстовое сообщение (с опциональными инлайн-кнопками и подчатом) в MAX Мессенджер
     */
    suspend fun sendMessage(
        chatId: String,
        text: String,
        threadId: Int = 0,
        buttons: List<List<MaxButton>>? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (botToken.isBlank() || chatId.isBlank() || text.isBlank()) return@withContext false

        val payloadMap = mutableMapOf<String, Any>(
            "chat_id" to chatId,
            "text" to text,
            "parse_mode" to "markdown"
        )

        if (threadId > 0) {
            payloadMap["thread_id"] = threadId
        }

        if (!buttons.isNullOrEmpty()) {
            val keyboardList = buttons.map { row ->
                row.map { btn ->
                    mapOf(
                        "text" to btn.text,
                        "payload" to btn.payload
                    )
                }
            }
            payloadMap["keyboard"] = mapOf("buttons" to keyboardList)
        }

        val jsonBody = gson.toJson(payloadMap)
        val body = jsonBody.toRequestBody(jsonMediaType)

        val url = "$baseUrl/messages/send"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $botToken")
            .post(body)
            .build()

        return@withContext try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                Log.d(TAG, "✅ Сообщение успешно отправлено в MAX: chatId=$chatId, threadId=$threadId")
                true
            } else {
                Log.e(TAG, "❌ Ошибка отправки в MAX API. Код: ${response.code}, Ответ: $responseBody")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка сети при отправке в MAX Мессенджер: ${e.message}", e)
            false
        }
    }

    /**
     * Отправляет файл/документ (PDF стикеры, CSV лист подбора, QR-коды) в MAX Мессенджер
     */
    suspend fun sendDocument(
        chatId: String,
        file: File,
        caption: String = "",
        threadId: Int = 0
    ): Boolean = withContext(Dispatchers.IO) {
        if (botToken.isBlank() || chatId.isBlank() || !file.exists()) return@withContext false

        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("file", file.name, file.asRequestBody("application/octet-stream".toMediaType()))

        if (caption.isNotBlank()) {
            builder.addFormDataPart("caption", caption)
        }
        if (threadId > 0) {
            builder.addFormDataPart("thread_id", threadId.toString())
        }

        val requestBody = builder.build()
        val url = "$baseUrl/messages/send_file"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $botToken")
            .post(requestBody)
            .build()

        return@withContext try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                Log.d(TAG, "✅ Файл ${file.name} успешно отправлен в MAX Мессенджер")
                true
            } else {
                Log.e(TAG, "❌ Ошибка отправки файла в MAX. Код: ${response.code}, Ответ: $responseBody")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ошибка сети при отправке файла в MAX: ${e.message}", e)
            false
        }
    }

    /**
     * Запрос длинных опросов (Long Polling) обновлений от MAX Мессенджера
     */
    suspend fun getUpdates(offset: Long = 0): String? = withContext(Dispatchers.IO) {
        if (botToken.isBlank()) return@withContext null

        val url = "$baseUrl/updates?offset=$offset&limit=50&timeout=25"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $botToken")
            .get()
            .build()

        return@withContext try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}