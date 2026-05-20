package com.activetour.wbbotcontroller.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.activetour.wbbotcontroller.model.WBOrder
import com.activetour.wbbotcontroller.service.WbOrderChecker
import com.activetour.wbbotcontroller.service.WbSupplyChecker
import com.activetour.wbbotcontroller.utils.PreferencesManager
import kotlinx.coroutines.runBlocking

class CheckOrdersWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val TAG = "CheckOrdersWorker"
    }

    private var lastCheckedOrderId = 0L

    override fun doWork(): Result {
        return try {
            val prefs = PreferencesManager(applicationContext)

            // Проверяем, включён ли бот
            if (!prefs.isBotEnabled()) {
                Log.d(TAG, "Бот выключен, проверка отменена")
                return Result.success()
            }

            // ✅ ЗАДЕРЖКА ДЛЯ СОБЛЮДЕНИЯ ЛИМИТА API (1 запрос в секунду)
            Log.d(TAG, "Ожидание 2 секунды перед запросом к API...")
            Thread.sleep(5000)  //  секунды задержки

            Log.d(TAG, "Начинаем проверку заказов...")
            val orderChecker = WbOrderChecker(applicationContext)
            val newOrders = runBlocking { orderChecker.getNewOrders() }

            if (newOrders.isNotEmpty()) {
                val actualNewOrders = newOrders.filter { it.id > lastCheckedOrderId }

                if (actualNewOrders.isNotEmpty()) {
                    Log.i(TAG, "Найдено ${actualNewOrders.size} новых заказов")

                    actualNewOrders.forEach { order ->
                        if (order.id > lastCheckedOrderId) {
                            lastCheckedOrderId = order.id
                        }
                    }

                    // Обработка поставок (обёрнуто в runBlocking)
                    runBlocking {
                        processSupplies(actualNewOrders, prefs)
                    }
                } else {
                    Log.d(TAG, "Новых заказов (по ID) не найдено")
                }
            } else {
                Log.d(TAG, "Нет новых заказов")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при проверке заказов: ${e.message}", e)
            Result.retry()
        }
    }

    private suspend fun processSupplies(orders: List<WBOrder>, prefs: PreferencesManager) {
        val supplyChecker = WbSupplyChecker(applicationContext)
        val orderIds = orders.map { it.id }

        try {
            var supplyId = supplyChecker.getLastActiveSupply()

            if (supplyId == null) {
                val supplyName = "Поставка от ${System.currentTimeMillis()}"
                supplyId = supplyChecker.createSupply(supplyName)
                Log.i(TAG, "Создана новая поставка: $supplyId")
            } else {
                Log.i(TAG, "Используем существующую поставку: $supplyId")
            }

            if (supplyId != null) {
                val success = supplyChecker.addOrdersToSupply(supplyId, orderIds)
                if (success) {
                    Log.i(TAG, "Заказы $orderIds добавлены в поставку $supplyId")
                } else {
                    Log.e(TAG, "Ошибка при добавлении заказов в поставку")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при обработке поставок: ${e.message}", e)
        }
    }
}