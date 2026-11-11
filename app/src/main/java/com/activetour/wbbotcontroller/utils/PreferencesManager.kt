package com.activetour.wbbotcontroller.utils

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("bot_prefs", Context.MODE_PRIVATE)

    // ==================== Telegram Settings ====================

    fun getBotToken(): String = prefs.getString("bot_token", "") ?: ""
    fun setBotToken(token: String) = prefs.edit().putString("bot_token", token).apply()

    // ==================== Bot ID ====================
//    fun getChatId(): String = prefs.getString("bot_id", "") ?: ""
//    fun setChatId(botId: String) = prefs.edit().putString("bot_id", botId).apply()

    fun getMessageThreadId(): Int = prefs.getInt("message_thread_id", 0)
    fun getMessageThreadId(chatId: String): Int {
        val chatThreadKey = "message_thread_id_$chatId"
        if (prefs.contains(chatThreadKey)) return prefs.getInt(chatThreadKey, 0)

        val configuredChatId = prefs.getString("message_thread_chat_id", null)
        if (configuredChatId != null && configuredChatId != chatId) return 0

        // A Telegram topic can only belong to a group/supergroup, never a private chat.
        if ((chatId.toLongOrNull() ?: 0L) >= 0L) return 0
        return getMessageThreadId()
    }

    fun setMessageThreadId(threadId: Int) = prefs.edit()
        .putInt("message_thread_id", threadId)
        .remove("message_thread_chat_id")
        .apply()

    fun setMessageThreadId(chatId: String, threadId: Int) {
        val editor = prefs.edit().putInt("message_thread_id_$chatId", threadId)
        if (threadId > 0) {
            editor.putString("message_thread_chat_id", chatId)
                .putInt("message_thread_id", threadId)
        } else if (prefs.getString("message_thread_chat_id", null) == chatId) {
            editor.putInt("message_thread_id", 0)
                .remove("message_thread_chat_id")
        }
        editor.apply()
    }

    fun clearMessageThreadId(chatId: String, threadId: Int) {
        val editor = prefs.edit().putInt("message_thread_id_$chatId", 0)
            .putInt("orders_thread_id_$chatId", 0)
            .putInt("supply_thread_id_$chatId", 0)
        if (prefs.getString("message_thread_chat_id", null) == chatId && getMessageThreadId() == threadId) {
            editor.putInt("message_thread_id", 0).remove("message_thread_chat_id")
        } else if (prefs.getString("message_thread_chat_id", null) == null && getMessageThreadId() == threadId) {
            editor.putInt("message_thread_id", 0)
        }
        editor.apply()
    }

    // ==================== Orders & Supply Specific Chats and Threads ====================

    fun getOrdersChatIds(): Set<String> {
        val botId = getBotId()
        val set = prefs.getStringSet("orders_chat_ids", emptySet()) ?: emptySet()
        val cleaned = set.filter { it.isNotBlank() && it != botId }.toSet()
        if (cleaned.isNotEmpty()) return cleaned
        return getAllChatIds()
    }

    fun addOrdersChatId(chatId: String) {
        if (chatId.isBlank()) return
        val botId = getBotId()
        if (botId.isNotEmpty() && chatId == botId) return
        val current = (prefs.getStringSet("orders_chat_ids", emptySet()) ?: emptySet()).toMutableSet()
        if (current.add(chatId)) {
            prefs.edit().putStringSet("orders_chat_ids", current).apply()
        }
        addChatId(chatId)
    }

    fun getSupplyChatIds(): Set<String> {
        val botId = getBotId()
        val set = prefs.getStringSet("supply_chat_ids", emptySet()) ?: emptySet()
        val cleaned = set.filter { it.isNotBlank() && it != botId }.toSet()
        if (cleaned.isNotEmpty()) return cleaned
        return getOrdersChatIds()
    }

    fun addSupplyChatId(chatId: String) {
        if (chatId.isBlank()) return
        val botId = getBotId()
        if (botId.isNotEmpty() && chatId == botId) return
        val current = (prefs.getStringSet("supply_chat_ids", emptySet()) ?: emptySet()).toMutableSet()
        if (current.add(chatId)) {
            prefs.edit().putStringSet("supply_chat_ids", current).apply()
        }
        addChatId(chatId)
    }

    fun getOrdersChatId(): String = getOrdersChatIds().firstOrNull() ?: ""

    fun setOrdersChatId(chatId: String) {
        addOrdersChatId(chatId)
    }

    fun getSupplyChatId(): String = getSupplyChatIds().firstOrNull() ?: ""

    fun setSupplyChatId(chatId: String) {
        addSupplyChatId(chatId)
    }

    fun getOrdersThreadId(): Int {
        val chatId = getOrdersChatId()
        if (chatId.isNotEmpty()) {
            val key = "orders_thread_id_$chatId"
            if (prefs.contains(key)) return prefs.getInt(key, 0)
        }
        return prefs.getInt("orders_thread_id", getMessageThreadId())
    }

    fun setOrdersThreadId(threadId: Int) {
        val editor = prefs.edit()
            .putInt("orders_thread_id", threadId)
            .putInt("message_thread_id", threadId)
        if (threadId == 0) {
            editor.remove("orders_thread_id")
        }
        editor.apply()
    }

    fun getOrdersThreadId(chatId: String): Int {
        val key = "orders_thread_id_$chatId"
        if (prefs.contains(key)) return prefs.getInt(key, 0)
        if (prefs.contains("orders_thread_id")) return prefs.getInt("orders_thread_id", 0)
        return getMessageThreadId(chatId)
    }

    fun setOrdersThreadId(chatId: String, threadId: Int) {
        addOrdersChatId(chatId)
        val editor = prefs.edit()
            .putInt("orders_thread_id_$chatId", threadId)
            .putInt("orders_thread_id", threadId)
            .putInt("message_thread_id_$chatId", threadId)
        if (threadId > 0) {
            editor.putString("message_thread_chat_id", chatId)
                .putInt("message_thread_id", threadId)
        }
        editor.apply()
    }

    fun getSupplyThreadId(): Int {
        val chatId = getSupplyChatId()
        if (chatId.isNotEmpty()) {
            val key = "supply_thread_id_$chatId"
            if (prefs.contains(key)) return prefs.getInt(key, 0)
        }
        return prefs.getInt("supply_thread_id", getMessageThreadId())
    }

    fun setSupplyThreadId(threadId: Int) {
        val editor = prefs.edit().putInt("supply_thread_id", threadId)
        if (threadId == 0) {
            editor.remove("supply_thread_id")
        }
        editor.apply()
    }

    fun getSupplyThreadId(chatId: String): Int {
        val key = "supply_thread_id_$chatId"
        if (prefs.contains(key)) return prefs.getInt(key, 0)
        if (prefs.contains("supply_thread_id")) return prefs.getInt("supply_thread_id", 0)
        return getMessageThreadId(chatId)
    }

    fun setSupplyThreadId(chatId: String, threadId: Int) {
        addSupplyChatId(chatId)
        prefs.edit()
            .putInt("supply_thread_id_$chatId", threadId)
            .putInt("supply_thread_id", threadId)
            .apply()
    }


    // ==================== Multiple Chats ====================
    fun getBotId(): String {
        val token = getBotToken()
        return if (token.contains(":")) token.split(":").firstOrNull() ?: "" else ""
    }

    fun getAllChatIds(): Set<String> {
        val botId = getBotId()
        val all = prefs.getStringSet("all_chat_ids", emptySet()) ?: emptySet()
        if (botId.isNotEmpty() && all.contains(botId)) {
            val cleaned = all.filter { it != botId }.toSet()
            prefs.edit().putStringSet("all_chat_ids", cleaned).apply()
            return cleaned
        }
        return all
    }

    fun addChatId(chatId: String) {
        if (chatId.isBlank()) return
        val botId = getBotId()
        if (botId.isNotEmpty() && chatId == botId) return

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
        "https://marketplace-api.wildberries.ru/api/v3/orders/new"
    ) ?: "https://marketplace-api.wildberries.ru/api/v3/orders/new"

    fun setWbOrdersUrl(url: String) = prefs.edit().putString("wb_orders_url", url).apply()

    fun getWbSuppliesUrl(): String = prefs.getString(       // URL поставок
        "wb_supplies_url",
        "https://marketplace-api.wildberries.ru/api/v3/supplies"
    ) ?: "https://marketplace-api.wildberries.ru/api/v3/supplies"

    fun setWbSuppliesUrl(url: String) = prefs.edit().putString("wb_supplies_url", url).apply()

    fun getWbAddOrdersUrl(): String = prefs.getString(   // URL добавления заказов
        "wb_add_orders_url",
        "https://marketplace-api.wildberries.ru/api/marketplace/v3/supplies/{supplyId}/orders"
    ) ?: "https://marketplace-api.wildberries.ru/api/marketplace/v3/supplies/{supplyId}/orders"

    fun setWbAddOrdersUrl(url: String) = prefs.edit().putString("wb_add_orders_url", url).apply()

    fun getWbIdOrdersInSupplyUrl(): String = prefs.getString(   // URL id всех заказов в поставке c определенным id
        "wb_id_orders_in_supply_url",
        "https://marketplace-api.wildberries.ru/api/marketplace/v3/supplies/{supplyId}/order-ids"
    ) ?: "https://marketplace-api.wildberries.ru/api/marketplace/v3/supplies/{supplyId}/order-ids"

    fun setWbIdOrdersInSupplyUrl(url: String) = prefs.edit().putString("wb_id_orders_in_supply_url", url).apply()

    fun getWbStickersUrl(): String = prefs.getString(  // URL стикеров заказов
        "wb_stickers_url",
        "https://marketplace-api.wildberries.ru/api/v3/orders/stickers"
    ) ?: "https://marketplace-api.wildberries.ru/api/v3/orders/stickers"

    fun setWbStickersUrl(url: String) = prefs.edit().putString("wb_stickers_url", url).apply()

    fun getWBListBoxIDsUrl(): String = prefs.getString(   // URL Получить список id грузомест поставки
        "wb_list_ids_box_url",
        "https://marketplace-api.wildberries.ru/api/v3/supplies/{supplyId}/trbx"
    ) ?: "https://marketplace-api.wildberries.ru/api/v3/supplies/{supplyId}/trbx"

    fun setWBListBoxIDsUrl(url: String) = prefs.edit().putString("wb_list_ids_box_url", url).apply()

    fun getWBQRCodeSuppliesUrl(): String = prefs.getString(   // URL QR-кода грузомест поставки
        "wb_qr_codes_url",
        "https://marketplace-api.wildberries.ru/api/v3/supplies/{supplyId}/trbx/stickers"
    ) ?: "https://marketplace-api.wildberries.ru/api/v3/supplies/{supplyId}/trbx/stickers"

    fun setWBQRCodeSuppliesUrl(url: String) = prefs.edit().putString("wb_qr_codes_url", url).apply()

    fun getWbAddNumberCargoSpacesUrl(): String = prefs.getString(   // URL добавления грузомест
        "wb_add_number_cargo_spaces_url",
        "https://marketplace-api.wildberries.ru/api/v3/supplies/{supplyId}/trbx"
    ) ?: "https://marketplace-api.wildberries.ru/api/v3/supplies/{supplyId}/trbx"

    fun setWbAddNumberCargoSpacesUrl(url: String) = prefs.edit().putString("wb_add_number_cargo_spaces_url", url).apply()

    fun getWBOrdersDetailsUrl(): String = prefs.getString(   // URL деталей заказов
        "wb_orders_details_url",
        "https://marketplace-api.wildberries.ru/api/v3/orders"
    ) ?: "https://marketplace-api.wildberries.ru/api/v3/orders"

    fun setOrdersDetailsUrl(url: String) = prefs.edit().putString("wb_orders_details_url", url).apply()

    // ==================== Application Settings ====================

    fun getNextForListSupplies(): Long = prefs.getLong("next_for_list_supplies", 0)
    fun setNextForListSupplies(next: Long) = prefs.edit().putLong("next_for_list_supplies", next).apply()

    fun getCheckIntervalMinutes(): Int = prefs.getInt("check_interval_minutes", 15)
    fun setCheckIntervalMinutes(minutes: Int) = prefs.edit().putInt("check_interval_minutes", minutes).apply()

    fun getNumberCargoSpaces(): Int = prefs.getInt("number_cargo_spaces", 1)
    fun setNumberCargoSpaces(numberCargoSpaces: Int) = prefs.edit().putInt("number_cargo_spaces", numberCargoSpaces).apply()

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

    fun resetUrlsToDefaults() {
        prefs.edit()
            .remove("wb_orders_url")
            .remove("wb_supplies_url")
            .remove("wb_add_orders_url")
            .remove("wb_id_orders_in_supply_url")
            .remove("wb_stickers_url")
            .remove("wb_list_ids_box_url")
            .remove("wb_qr_codes_url")
            .remove("wb_add_number_cargo_spaces_url")
            .remove("wb_orders_details_url")
            .apply()
    }

    fun resetAllSettings() {
        prefs.edit().clear().apply()
    }
}