package com.activetour.wbbotcontroller.utils

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("bot_prefs", Context.MODE_PRIVATE)

    // ================= Тестировочный режим (мок-режим) =====================
    fun isMockMode(): Boolean = prefs.getBoolean("mock_mode", true)
    fun setMockMode(enabled: Boolean) = prefs.edit().putBoolean("mock_mode", enabled).apply()

    // ==================== Telegram Settings ====================

    fun getBotToken(): String = prefs.getString("bot_token", "") ?: ""
    fun setBotToken(token: String) = prefs.edit().putString("bot_token", token).apply()

    // ==================== Bot ID ====================
//    fun getChatId(): String = prefs.getString("bot_id", "") ?: ""
//    fun setChatId(botId: String) = prefs.edit().putString("bot_id", botId).apply()

    fun getMessageThreadId(): Int = prefs.getInt("message_thread_id", 0)
    fun setMessageThreadId(threadId: Int) = prefs.edit().putInt("message_thread_id", threadId).apply()


    // ==================== Multiple Chats ====================
    fun getAllChatIds(): Set<String> = prefs.getStringSet("all_chat_ids", emptySet()) ?: emptySet()

    fun addChatId(chatId: String) {
        val current = getAllChatIds().toMutableSet()
        if (current.add(chatId)) {
            prefs.edit().putStringSet("all_chat_ids", current).apply()
        }
    }

    fun removeChatId(chatId: String) {
        val current = getAllChatIds().toMutableSet()
        if (current.remove(chatId)) {
            prefs.edit().putStringSet("all_chat_ids", current).apply()
        }
    }

    // ==================== Wildberries API Settings ====================

    fun getWbApiToken(): String = prefs.getString("wb_api_token", "") ?: ""
    fun setWbApiToken(token: String) = prefs.edit().putString("wb_api_token", token).apply()

    // ==================== URLs ====================

    fun getWbOrdersUrl(): String = prefs.getString(     // URL заказов
        "wb_orders_url",
        "https://marketplace-api-sandbox.wildberries.ru/api/v3/orders/new"
    ) ?: "https://marketplace-api-sandbox.wildberries.ru/api/v3/orders/new"

    fun setWbOrdersUrl(url: String) = prefs.edit().putString("wb_orders_url", url).apply()

    fun getWbSuppliesUrl(): String = prefs.getString(       // URL  список поставок
        "wb_supplies_url",
        "https://marketplace-api-sandbox.wildberries.ru/api/v3/supplies"
    ) ?: "https://marketplace-api-sandbox.wildberries.ru/api/v3/supplies"

    fun setWbSuppliesUrl(url: String) = prefs.edit().putString("wb_supplies_url", url).apply()

    fun getWbAddOrdersUrl(): String = prefs.getString(   // URL добавления заказов Добавить сборочные задания к поставке
        "wb_add_orders_url",
        "https://marketplace-api-sandbox.wildberries.ru/api/marketplace/v3/supplies/{supplyId}/orders"
    ) ?: "https://marketplace-api-sandbox.wildberries.ru/api/marketplace/v3/supplies/{supplyId}/orders"

    fun setWbAddOrdersUrl(url: String) = prefs.edit().putString("wb_add_orders_url", url).apply()

    // Добавить поставку к доставке
    fun getWbAddNumberCargoSpacesUrl(): String = prefs.getString(   // URL Добавить грузоместа к поставке
        "wb_add_number_cargo_spaces_url",
        "https://marketplace-api-sandbox.wildberries.ru/api/v3/supplies/{supplyId}/trbx"
    ) ?: "https://marketplace-api-sandbox.wildberries.ru/api/v3/supplies/{supplyId}/trbx"

    fun setWbAddNumberCargoSpacesUrl(url: String) = prefs.edit().putString("wb_add_number_cargo_spaces_url", url).apply()

                                    // - Лист подбора
    fun getWBOrderIdsListUrl(): String = prefs.getString(   // URL Получение списка ID заказов в поставке - лист подбора
        "wb_order_ids_list_url",
        "https://marketplace-api.wildberries.ru/api/marketplace/v3/supplies/{supplyId}/order-ids"
    ) ?: "https://marketplace-api.wildberries.ru/api/marketplace/v3/supplies/{supplyId}/order-ids"

    fun setOrderIdsListUrl(url: String) = prefs.edit().putString("wb_order_ids_list_url", url).apply()

    fun getWBOrdersDetailsUrl(): String = prefs.getString(   // URL Получить информацию о сборочных заданиях - лист подбора
        "wb_orders_details_url",
        "https://marketplace-api.wildberries.ru/api/v3/orders"
    ) ?: "https://marketplace-api.wildberries.ru/api/v3/orders"

    fun setOrdersDetailsUrl(url: String) = prefs.edit().putString("wb_orders_details_url", url).apply()

                    // URL Получить QR-коды
    fun getWBQRCodeSuppliesUrl(): String = prefs.getString(   // URL Получить QR-код поставки
        "wb_qr_codes_url",
        "https://marketplace-api.wildberries.ru/api/v3/supplies/{supplyId}/barcode"
    ) ?: "https://marketplace-api.wildberries.ru/api/v3/supplies/{supplyId}/barcode"



    // ==================== Application Settings ====================

    fun getNumberCargoSpaces(): Int = prefs.getInt("number_cargo_spaces", 1)
    fun setNumberCargoSpaces(numberCargoSpaces: Int) = prefs.edit().putInt("number_cargo_spaces", numberCargoSpaces).apply()

    fun getCheckIntervalMinutes(): Int = prefs.getInt("check_interval_minutes", 15)
    fun setCheckIntervalMinutes(minutes: Int) = prefs.edit().putInt("check_interval_minutes", minutes).apply()

    fun getLimitForSelectionList(): Int = prefs.getInt("limit_for_selection_list", 1000)
    fun setLimitForSelectionList(limit: Int) = prefs.edit().putInt("limit_for_selection_list", limit).apply()

    fun getTypeFileQRCodes(): String = prefs.getString("type_file_qr_codes", "png") ?: "png"
    fun setTypeFileQRCodes(typeFileQRCodes: String) = prefs.edit().putString("type_file_qr_codes", typeFileQRCodes).apply()

    // ==================== Bot State (для автозапуска) ====================

    fun isBotEnabled(): Boolean = prefs.getBoolean("bot_enabled", false)
    fun setBotEnabled(enabled: Boolean) = prefs.edit().putBoolean("bot_enabled", enabled).apply()

    // ==================== Welcome Message ====================

    fun isWelcomeSent(): Boolean = prefs.getBoolean("welcome_sent", false)
    fun setWelcomeSent(sent: Boolean) = prefs.edit().putBoolean("welcome_sent", sent).apply()

    // ==================== Auto Create Supply ====================

    fun isAutoCreateSupply(): Boolean = prefs.getBoolean("auto_create_supply", true)
    fun setAutoCreateSupply(enabled: Boolean) = prefs.edit().putBoolean("auto_create_supply", enabled).apply()

    // ==================== Helper Methods ====================

    fun isBotConfigured(): Boolean {
        return getBotToken().isNotEmpty() && getWbApiToken().isNotEmpty()
    }

    fun resetAllSettings() {
        prefs.edit().clear().apply()
    }
}