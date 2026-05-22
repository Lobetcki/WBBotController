package com.activetour.wbbotcontroller.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.activetour.wbbotcontroller.service.TelegramBotService
import com.activetour.wbbotcontroller.utils.PreferencesManager

class CheckOrdersWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val TAG = "CheckOrdersWorker"
    }

    override fun doWork(): Result {
        return try {
            val prefs = PreferencesManager(applicationContext)

            // Проверяем, включён ли бот
            if (!prefs.isBotEnabled()) {
                Log.d(TAG, "Бот выключен, проверка отменена")
                return Result.success()
            }

            Log.d(TAG, "Начинаем проверку заказов...")
            // Проверяем заказы только если уже есть активный чат
            if (prefs.getAllChatIds().isNotEmpty()) {
                Log.d(TAG, "doWork: проверка заказов...")

                val intent = Intent(applicationContext, TelegramBotService::class.java)
                intent.putExtra("command", "autoCheck")
                applicationContext.startService(intent)

                Log.d(TAG, "✅ doWork: проверка выполнена")
            } else {
                Log.d(TAG,"doWork: чат ещё не активирован, первая проверка отложена до получения сообщения"
                )
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при проверке заказов: ${e.message}", e)
            Result.retry()
        }
    }
}