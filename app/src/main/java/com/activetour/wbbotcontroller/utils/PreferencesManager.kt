package com.activetour.wbbotcontroller.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("bot_prefs", Context.MODE_PRIVATE)

    // ================= Тестировочный режим (мок-режим) =====================
    fun isMockMode(): Boolean = prefs.getBoolean("mock_mode", true)
    fun setMockMode(enabled: Boolean) = prefs.edit().putBoolean("mock_mode", enabled).apply()

    // ==================== Telegram Settings ====================

    fun getBotToken(): String = prefs.getString("bot_token", "") ?: ""
    fun setBotToken(token: String) = prefs.edit { putString("bot_token", token) }

    // ==================== Bot ID ====================
//    fun getChatId(): String = prefs.getString("bot_id", "") ?: ""
//    fun setChatId(botId: String) = prefs.edit().putString("bot_id", botId).apply()

    fun getMessageThreadId(): Int = prefs.getInt("message_thread_id", 0)
    fun setMessageThreadId(threadId: Int) = prefs.edit { putInt("message_thread_id", threadId) }


    // ==================== Multiple Chats ====================
    fun getAllChatIds(): Set<String> = prefs.getStringSet("all_chat_ids", emptySet()) ?: emptySet()

    fun addChatId(chatId: String) {
        val current = getAllChatIds().toMutableSet()
        if (current.add(chatId)) {
            prefs.edit { putStringSet("all_chat_ids", current) }
        }
    }

    fun removeChatId(chatId: String) {
        val current = getAllChatIds().toMutableSet()
        if (current.remove(chatId)) {
            prefs.edit { putStringSet("all_chat_ids", current) }
        }
    }

    // ==================== Wildberries API Settings ====================

    fun getWbApiToken(): String = prefs.getString("wb_api_token", "") ?: ""
    fun setWbApiToken(token: String) = prefs.edit { putString("wb_api_token", token) }

    // ==================== URLs ====================

    fun getWbOrdersUrl(): String = prefs.getString(     // URL заказов
        "wb_orders_url",
        "https://marketplace-api-sandbox.wildberries.ru/api/v3/orders/new"
    ) ?: "https://marketplace-api-sandbox.wildberries.ru/api/v3/orders/new"

    fun setWbOrdersUrl(url: String) = prefs.edit { putString("wb_orders_url", url) }

    fun getWbSuppliesUrl(): String = prefs.getString(       // URL поставок
        "wb_supplies_url",
        "https://marketplace-api-sandbox.wildberries.ru/api/v3/supplies"
    ) ?: "https://marketplace-api-sandbox.wildberries.ru/api/v3/supplies"

    fun setWbSuppliesUrl(url: String) = prefs.edit { putString("wb_supplies_url", url) }

    fun getWbAddOrdersUrl(): String = prefs.getString(   // URL добавления заказов
        "wb_add_orders_url",
        "https://marketplace-api-sandbox.wildberries.ru/api/marketplace/v3/supplies/%s/orders"
    ) ?: "https://marketplace-api-sandbox.wildberries.ru/api/marketplace/v3/supplies/%s/orders"

    fun setWbAddOrdersUrl(url: String) = prefs.edit { putString("wb_add_orders_url", url) }

    // ==================== Application Settings ====================

    fun getCheckIntervalMinutes(): Int = prefs.getInt("check_interval_minutes", 15)
    fun setCheckIntervalMinutes(minutes: Int) = prefs.edit { putInt("check_interval_minutes", minutes) }

    // ==================== Bot State (для автозапуска) ====================

    fun isBotEnabled(): Boolean = prefs.getBoolean("bot_enabled", false)
    fun setBotEnabled(enabled: Boolean) = prefs.edit { putBoolean("bot_enabled", enabled) }

    // ==================== Welcome Message ====================

    fun setWelcomeSent(sent: Boolean) = prefs.edit { putBoolean("welcome_sent", sent) }

    // ==================== Helper Methods ====================

    fun resetAllSettings() {
        prefs.edit { clear() }
    }
}