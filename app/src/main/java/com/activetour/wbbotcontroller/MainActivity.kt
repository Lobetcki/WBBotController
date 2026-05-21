package com.activetour.wbbotcontroller

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.activetour.wbbotcontroller.service.TelegramBotService
import com.activetour.wbbotcontroller.ui.theme.WBBotControllerTheme
import com.activetour.wbbotcontroller.utils.PreferencesManager

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var preferencesManager: PreferencesManager
    private var isBotRunning = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        Log.d(TAG, "Результат запроса разрешений: $permissions")
        if (allGranted) {
            android.widget.Toast.makeText(
                this, "Все разрешения получены", android.widget.Toast.LENGTH_SHORT
            ).show()
        } else {
            android.widget.Toast.makeText(
                this, "Некоторые разрешения не получены", android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate START")

        try {
            enableEdgeToEdge()
            Log.d(TAG, "enableEdgeToEdge OK")

            preferencesManager = PreferencesManager(this)
            Log.d(TAG, "PreferencesManager инициализирован")

            checkPermissions()
            Log.d(TAG, "checkPermissions OK")

            checkBotStatus()
            Log.d(TAG, "checkBotStatus OK: isBotRunning = ${isBotRunning.value}")

            setContent {
                WBBotControllerTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        BotControlScreen(
                            isBotRunning = isBotRunning.value,
                            onStartBot = { startBot() },
                            onStopBot = { stopBot() },
                            onSaveSettings = { token -> saveSettings(token) },
                            onCheckNow = { checkNow() },
                            onOpenSettings = { openSettings() },
                            getBotToken = { preferencesManager.getBotToken() }
                        )
                    }
                }
                Log.d(TAG, "setContent OK")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка в onCreate", e)
            android.widget.Toast.makeText(
                this,
                "Ошибка: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
        Log.d(TAG, "onCreate END")
    }

    private fun checkPermissions() {
        Log.d(TAG, "checkPermissions: начало")
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            Log.d(TAG, "Добавлено разрешение POST_NOTIFICATIONS")
        }
        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        Log.d(TAG, "Добавлены разрешения для хранилища")

        if (permissions.isNotEmpty()) {
            Log.d(TAG, "Запуск запроса разрешений: $permissions")
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun checkBotStatus() {
        Log.d(TAG, "checkBotStatus: начало")
        val isEnabled = preferencesManager.isBotEnabled()
        val isServiceRunning = TelegramBotService.isRunning
        Log.d(TAG, "isBotEnabled: $isEnabled, TelegramBotService.isRunning: $isServiceRunning")

        isBotRunning.value = isEnabled && isServiceRunning
        Log.d(TAG, "checkBotStatus: isBotRunning = ${isBotRunning.value}")
    }

    private fun startBot() {
        Log.d(TAG, "startBot: НАЧАЛО")

        try {
            val token = preferencesManager.getBotToken()
            Log.d(TAG, "startBot: токен = ${token.take(10)}..., длина = ${token.length}")

            if (token.isEmpty()) {
                Log.w(TAG, "startBot: токен пуст!")
                android.widget.Toast.makeText(
                    this,
                    "Сначала настройте токен бота",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return
            }

            val serviceIntent = Intent(this, TelegramBotService::class.java)
            Log.d(TAG, "startBot: Intent создан")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "startBot: startForegroundService (Android 8+)")
                startForegroundService(serviceIntent)
            } else {
                Log.d(TAG, "startBot: startService (Android <8)")
                startService(serviceIntent)
            }

            preferencesManager.setBotEnabled(true)
            Log.d(TAG, "startBot: botEnabled сохранён как true")

            isBotRunning.value = true
            Log.d(TAG, "startBot: isBotRunning установлен в true")

            android.widget.Toast.makeText(this, "Бот запущен", android.widget.Toast.LENGTH_SHORT)
                .show()
            Log.d(TAG, "startBot: УСПЕШНО ЗАВЕРШЁН")
        } catch (e: Exception) {
            Log.e(TAG, "startBot: ОШИБКА!", e)
            android.widget.Toast.makeText(
                this,
                "Ошибка запуска: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun stopBot() {
        Log.d(TAG, "stopBot: НАЧАЛО")

        try {
            val serviceIntent = Intent(this, TelegramBotService::class.java)
            stopService(serviceIntent)
            Log.d(TAG, "stopBot: stopService вызван")

            preferencesManager.setBotEnabled(false)
            Log.d(TAG, "stopBot: botEnabled сохранён как false")

            isBotRunning.value = false
            android.widget.Toast.makeText(this, "Бот остановлен", android.widget.Toast.LENGTH_SHORT)
                .show()
            Log.d(TAG, "stopBot: УСПЕШНО ЗАВЕРШЁН")
        } catch (e: Exception) {
            Log.e(TAG, "stopBot: ОШИБКА!", e)
        }
    }

    private fun checkNow() {

        if (preferencesManager.getChatId().isEmpty()) {
            Log.d(TAG, "startBot: чат ещё не активирован, проверка отложена до получения сообщения")
            return
        }

        val intent = Intent(this, TelegramBotService::class.java)
        intent.putExtra("command", "checkNow")
        startService(intent)
        Toast.makeText(this, "Проверка запущена", Toast.LENGTH_SHORT).show()
    }

    private fun saveSettings(token: String) {
        Log.d(TAG, "saveSettings: token = ${token.take(10)}...")
        try {
            preferencesManager.setBotToken(token)
            android.widget.Toast.makeText(
                this,
                "Настройки сохранены",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            Log.d(TAG, "saveSettings: УСПЕШНО")
        } catch (e: Exception) {
            Log.e(TAG, "saveSettings: ОШИБКА!", e)
        }
    }

    private fun openSettings() {
        Log.d(TAG, "openSettings: открываем SettingsActivity")
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }
}

@Composable
fun BotControlScreen(
    isBotRunning: Boolean,
    onStartBot: () -> Unit,
    onStopBot: () -> Unit,
    onSaveSettings: (String) -> Unit,
    onCheckNow: () -> Unit,
    onOpenSettings: () -> Unit,
    getBotToken: () -> String
) {
    var showSettingsDialog by remember { mutableStateOf(false) }
    var tempToken by remember { mutableStateOf(getBotToken()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🤖 WB Bot Controller",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isBotRunning)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isBotRunning) "🟢 БОТ РАБОТАЕТ" else "🔴 БОТ ОСТАНОВЛЕН",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = if (isBotRunning) "Отслеживание заказов активно" else "Нажмите 'Включить' для старта",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onStartBot,
                enabled = !isBotRunning,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("▶ Включить")
            }

            Button(
                onClick = onStopBot,
                enabled = isBotRunning,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("⏹ Выключить")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onCheckNow,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("🔍 Проверить заказы сейчас")
        }

        Spacer(modifier = Modifier.height(24.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(16.dp))

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
        ) {
            Text("⚙ Настройки")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ℹ️ Информация",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Бот проверяет заказы каждые 15 минут\n" +
                            "• Для работы требуется интернет\n" +
                            "• Токен бота можно получить у @BotFather\n" +
                            "• После запуска напишите в чат, где добавлен телеграмм бот, слово - СТАРТ, для активации",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Настройки бота") },
            text = {
                Column {
                    OutlinedTextField(
                        value = tempToken,
                        onValueChange = { tempToken = it },
                        label = { Text("Токен Telegram бота") },
                        placeholder = { Text("Введите токен от @BotFather") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveSettings(tempToken)
                        showSettingsDialog = false
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}