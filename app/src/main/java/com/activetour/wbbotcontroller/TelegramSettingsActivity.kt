package com.activetour.wbbotcontroller

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.activetour.wbbotcontroller.ui.theme.WBBotControllerTheme
import com.activetour.wbbotcontroller.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TelegramSettingsActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        preferencesManager = PreferencesManager(this)

        setContent {
            WBBotControllerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TelegramSettingsScreen(
                        preferencesManager = preferencesManager,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramSettingsScreen(
    preferencesManager: PreferencesManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Telegram Settings State
    var botToken by remember { mutableStateOf(preferencesManager.getBotToken()) }
    var ordersThreadId by remember { mutableStateOf(preferencesManager.getOrdersThreadId().let { if (it == 0) "" else it.toString() }) }
    var supplyThreadId by remember { mutableStateOf(preferencesManager.getSupplyThreadId().let { if (it == 0) "" else it.toString() }) }
    var checkInterval by remember { mutableStateOf(preferencesManager.getCheckIntervalMinutes().toString()) }

    var isModified by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showTokenInfoDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var saveSuccess by remember { mutableStateOf(false) }

    fun saveAllSettings() {
        preferencesManager.setBotToken(botToken)

        val oThread = ordersThreadId.toIntOrNull() ?: 0
        preferencesManager.setOrdersThreadId(oThread)

        val sThread = supplyThreadId.toIntOrNull() ?: 0
        preferencesManager.setSupplyThreadId(sThread)

        checkInterval.toIntOrNull()?.let {
            if (it >= 15) {
                preferencesManager.setCheckIntervalMinutes(it)
            } else {
                Toast.makeText(context, "Интервал не может быть меньше 15 минут", Toast.LENGTH_SHORT).show()
            }
        }

        isModified = false
        saveSuccess = true
        Toast.makeText(context, "Настройки Telegram сохранены", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.Main).launch {
            delay(2000)
            saveSuccess = false
        }
    }

    fun handleBackPress() {
        if (isModified) {
            showUnsavedDialog = true
        } else {
            onBack()
        }
    }

    BackHandler {
        handleBackPress()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🤖 Настройки Telegram Бота") },
                navigationIcon = {
                    IconButton(onClick = { handleBackPress() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { saveAllSettings() }) {
                        Icon(Icons.Default.Check, contentDescription = "Сохранить", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // КНОПКА СОХРАНИТЬ В САМОМ ВЕРХУ ФОРМЫ
            Button(
                onClick = { saveAllSettings() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("💾 Сохранить настройки Telegram")
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (saveSuccess) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        text = "✅ Настройки Telegram Бота сохранены!",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // === КНОПКА ВСПЛЫВАЮЩЕЙ ИНФОРМАЦИИ О ТОКЕНЕ TELEGRAM ===
            OutlinedButton(
                onClick = { showTokenInfoDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("ℹ️ Как получить токен Telegram")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // === Telegram Bot Credentials ===
            Text(
                text = "🤖 Telegram Бот",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            OutlinedTextField(
                value = botToken,
                onValueChange = {
                    botToken = it
                    isModified = true
                },
                label = { Text("Токен бота Telegram") },
                placeholder = { Text("Введите токен от @BotFather") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Обязательное поле для Telegram") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = ordersThreadId,
                onValueChange = {
                    ordersThreadId = it
                    isModified = true
                },
                label = { Text("ID подчата/темы для Заказов") },
                placeholder = { Text("Определится автоматически при команде СТАРТ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = supplyThreadId,
                onValueChange = {
                    supplyThreadId = it
                    isModified = true
                },
                label = { Text("ID подчата/темы для Поставок") },
                placeholder = { Text("Определится автоматически при команде ПОСТАВКА") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Если указать один и тот же ID для обоих подчатов (или оставить пустыми в обычном чате), все сообщения будут приходить в один подчат/чат.") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "💡 О работе с чатами Telegram",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Chat ID и ID подчатов определяются автоматически при отправке команд боту\n" +
                                "• Команда СТАРТ активирует бота и привязывает вся информацию (заказы, стикеры, QR-коды, листы подбора) к текущему подчату/чату\n" +
                                "• Команда ПОСТАВКА перенаправляет стикеры, QR-коды и листы подбора в нужную тему\n" +
                                "• Вы также можете вручную изменить или очистить ID подчатов выше",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()

            // === Application Settings ===
            Text(
                text = "⚙️ Настройки интервала",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            OutlinedTextField(
                value = checkInterval,
                onValueChange = {
                    checkInterval = it
                    isModified = true
                },
                label = { Text("Интервал проверки Telegram (минуты)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("По умолчанию: 15 минут. Минимальный: 15 минут") }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Кнопки сохранения и сброса внизу
            Button(
                onClick = { saveAllSettings() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("💾 Сохранить настройки Telegram")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text("🔄 Сбросить настройки Telegram")
            }
        }
    }

    // ВСПЛЫВАЮЩИЙ ДИАЛОГ - КАК ПОЛУЧИТЬ ТОКЕН TELEGRAM
    if (showTokenInfoDialog) {
        AlertDialog(
            onDismissRequest = { showTokenInfoDialog = false },
            title = { Text("ℹ️ Как получить токен Telegram Бота") },
            text = {
                Column {
                    Text(
                        text = "Пошаговая инструкция:\n\n" +
                                "1. Найдите в Telegram официального бота @BotFather.\n" +
                                "2. Напишите ему команду /newbot.\n" +
                                "3. Введите название бота (например, Мой Склад WB).\n" +
                                "4. Введите уникальный юзернейм бота, оканчивающийся на bot или _bot (например, my_wb_controller_bot).\n" +
                                "5. Скопируйте полученный HTTP API токен и вставьте в поле выше.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTokenInfoDialog = false }) {
                    Text("Понятно")
                }
            }
        )
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Несохраненные изменения") },
            text = { Text("Вы изменили настройки. Нажмите 'Сохранить', чтобы сохранить изменения перед выходом.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        saveAllSettings()
                        showUnsavedDialog = false
                        onBack()
                    }
                ) {
                    Text("Сохранить и выйти")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUnsavedDialog = false
                        onBack()
                    }
                ) {
                    Text("Выйти без сохранения", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Сброс настроек Telegram") },
            text = { Text("Вы уверены, что хотите сбросить настройки Telegram Бота?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        preferencesManager.setBotToken("")
                        preferencesManager.setOrdersThreadId(0)
                        preferencesManager.setSupplyThreadId(0)
                        botToken = ""
                        ordersThreadId = ""
                        supplyThreadId = ""
                        checkInterval = "15"
                        isModified = false
                        showResetDialog = false
                        Toast.makeText(context, "Настройки Telegram сброшены", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Сбросить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}