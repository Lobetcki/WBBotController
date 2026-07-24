package com.activetour.wbbotcontroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

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
                    SettingsScreen(
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
fun SettingsScreen(
    preferencesManager: PreferencesManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Telegram Settings (только токен ID подгруппы(темы чата), остальное автоматически)
    var botToken by remember { mutableStateOf(preferencesManager.getBotToken()) }
    var messageThreadId by remember { mutableStateOf(preferencesManager.getMessageThreadId().toString()) }

    // Wildberries Settings
    var wbApiToken by remember { mutableStateOf(preferencesManager.getWbApiToken()) }
    var wbOrdersUrl by remember { mutableStateOf(preferencesManager.getWbOrdersUrl()) }
    var wbSuppliesUrl by remember { mutableStateOf(preferencesManager.getWbSuppliesUrl()) }
    var wbAddOrdersUrl by remember { mutableStateOf(preferencesManager.getWbAddOrdersUrl()) }

    // Application Settings
    var checkInterval by remember { mutableStateOf(preferencesManager.getCheckIntervalMinutes().toString()) }

    var showResetDialog by remember { mutableStateOf(false) }
    var saveSuccess by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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
            // Сообщение об успешном сохранении
            if (saveSuccess) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        text = "✅ Настройки сохранены!",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // === Telegram Settings ===
            Text(
                text = "🤖 Telegram Настройки",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            OutlinedTextField(
                value = botToken,
                onValueChange = { botToken = it },
                label = { Text("Токен бота") },
                placeholder = { Text("Введите токен от @BotFather") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Обязательное поле") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = messageThreadId,
                onValueChange = { messageThreadId = it },
                label = { Text("ID подгруппы (темы чата)") },
                placeholder = { Text("Оставьте пустым – определится автоматически") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Если бот отправляет не в ту тему, остановите бота, очистите поле, сохраните настройки, запустите бота, а затем напишите слово СТАРТ в нужной теме – ID определится автоматически после запуска") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Информация о чатах (автоматическое определение)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "💡 О работе с чатами",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Chat ID определяется автоматически при первом сообщении боту\n" +
                                "• ID подгруппы (темы) тоже определяется автоматически из первого сообщения\n" +
                                "• Если нужно сменить тему – остановите бота, очистите поле ID подгруппы, сохраните настройки, запустите бота и напишите боту слово СТАРТ в новой теме",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()

            // === Wildberries API Settings ===
            Text(
                text = "📦 Wildberries API",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            OutlinedTextField(
                value = wbApiToken,
                onValueChange = { wbApiToken = it },
                label = { Text("API Токен WB") },
                placeholder = { Text("Введите токен из кабинета WB") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Обязательное поле. Получите в разделе Настройки → Доступ к API") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = wbOrdersUrl,
                onValueChange = { wbOrdersUrl = it },
                label = { Text("URL заказов") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = wbSuppliesUrl,
                onValueChange = { wbSuppliesUrl = it },
                label = { Text("URL поставок") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = wbAddOrdersUrl,
                onValueChange = { wbAddOrdersUrl = it },
                label = { Text("URL добавления заказов") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()

            // === Application Settings ===
            Text(
                text = "⚙️ Настройки приложения",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            OutlinedTextField(
                value = checkInterval,
                onValueChange = { checkInterval = it },
                label = { Text("Интервал проверки (минуты)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("По умолчанию: 15 минут. Минимальный: 15 минут") }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // === Кнопки действий ===
            Button(
                onClick = {
                    // Сохраняем только нужные настройки
                    preferencesManager.setBotToken(botToken)
                    preferencesManager.setWbApiToken(wbApiToken)
                    preferencesManager.setWbOrdersUrl(wbOrdersUrl)
                    preferencesManager.setWbSuppliesUrl(wbSuppliesUrl)
                    preferencesManager.setWbAddOrdersUrl(wbAddOrdersUrl)

                    // Сохраняем ID подгруппы (если введено число)
                    val threadId = messageThreadId.toIntOrNull() ?: 0
                    preferencesManager.setMessageThreadId(threadId)

                    checkInterval.toIntOrNull()?.let {
                        if (it >= 15) {
                            preferencesManager.setCheckIntervalMinutes(it)
                        } else {
                            android.widget.Toast.makeText(context, "Интервал не может быть меньше 15 минут",
                                android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }

                    saveSuccess = true
                    android.widget.Toast.makeText(context, "Настройки сохранены", android.widget.Toast.LENGTH_SHORT).show()

                    // Скрыть сообщение через 2 секунды
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        kotlinx.coroutines.delay(2000)
                        saveSuccess = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("💾 Сохранить настройки")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text("🔄 Сбросить все настройки")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Информационная карточка
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ℹ️ Как получить токены",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "🔹 Telegram Bot Token:\n" +
                                "   1. Найдите в Telegram @BotFather\n" +
                                "   2. Напишите @BotFather команду для создания бота - /newbot\n" +
                                "   3. Скопируйте полученный токен\n\n" +
                                "🔹 Wildberries API Token:\n" +
                                "   1. Зайдите в кабинет WB\n" +
                                "   2. В правом верхнем углу нажмите на название вашей организации " +
                                "-> Интеграции по API -> Создать токен -> Для интеграции вручную\n" +
                                "   3. Выберите <Персональный токен> и <Чтение и запись>, " +
                                "галочку поставте на <Маркетплейс> и " +
                                "<Я понимаю, что не следует передавать токен третьим лицам>, дайте наименование токену.\n" +
                                "   4. Нажмите <Создать токен> \n\n" +
                                "📌 После того как нажмете на кнопку включить в приложении, напишите в чате телеграмма(в который подключили бота) слово СТАРТ для активации",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    // Диалог подтверждения сброса
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Сброс настроек") },
            text = { Text("Вы уверены, что хотите сбросить все настройки? Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        preferencesManager.resetAllSettings()
                        // Обновляем все значения
                        botToken = ""
                        wbApiToken = ""
                        wbOrdersUrl = "https://marketplace-api.wildberries.ru/api/v3/orders/new"
                        wbSuppliesUrl = "https://marketplace-api.wildberries.ru/api/v3/supplies"
                        wbAddOrdersUrl = "https://marketplace-api.wildberries.ru/api/marketplace/v3/supplies/%s/orders"
                        checkInterval = "15"
                        showResetDialog = false
                        saveSuccess = false
                        android.widget.Toast.makeText(context, "Настройки сброшены", android.widget.Toast.LENGTH_SHORT).show()
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