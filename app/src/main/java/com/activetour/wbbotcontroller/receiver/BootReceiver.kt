package com.activetour.wbbotcontroller.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.activetour.wbbotcontroller.service.TelegramBotService
import com.activetour.wbbotcontroller.utils.PreferencesManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = PreferencesManager(context)
            if (prefs.isBotEnabled()) {
                val serviceIntent = Intent(context, TelegramBotService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}