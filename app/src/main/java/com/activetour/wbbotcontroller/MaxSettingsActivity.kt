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

class MaxSettingsActivity : ComponentActivity() {

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
                    MaxSettingsScreen(
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
fun MaxSettingsScreen(
    preferencesManager: PreferencesManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // MAX Settings State
    var maxBotToken by remember { mutableStateOf(preferencesManager.getMaxBotToken()) }
    var maxOrdersChatId by remember { mutableStateOf(preferencesManager.getMaxOrdersChatId()) }
    var maxSupplyChatId by remember { mutableStateOf(preferencesManager.getMaxSupplyChatId()) }
    var maxOrdersThreadId by remember { mutableStateOf(preferencesManager.getMaxOrdersThreadId().let { if (it == 0) "" else it.toString() }) }
    var maxSupplyThreadId by remember { mutableStateOf(preferencesManager.getMaxSupplyThreadId().let { if (it == 0) "" else it.toString() }) }
    var checkInterval by remember { mutableStateOf(preferencesManager.getMaxCheckIntervalMinutes().toString()) }

    var isModified by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showTokenInfoDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var saveSuccess by remember { mutableStateOf(false) }

    fun saveAllSettings() {
        preferencesManager.setMaxBotToken(maxBotToken)
        preferencesManager.setMaxOrdersChatId(maxOrdersChatId)
        preferencesManager.setMaxSupplyChatId(maxSupplyChatId)

        val oThread = maxOrdersThreadId.toIntOrNull() ?: 0
        preferencesManager.setMaxOrdersThreadId(oThread)

        val sThread = maxSupplyThreadId.toIntOrNull() ?: 0
        preferencesManager.setMaxSupplyThreadId(sThread)

        checkInterval.toIntOrNull()?.let {
            if (it >= 15) {
                preferencesManager.setMaxCheckIntervalMinutes(it)
            } else {
                Toast.makeText(context, "Интервал не может быть меньше 15 минут", Toast.LENGTH_SHORT).show()
            }
        }

        isModified = false
        saveSuccess = true
        Toast.makeText(context, "Настройки MAX сохранены", Toast.LENGTH_SHORT).show()

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
                title = { Text("💬 Настройки MAX Мессенджера") },
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
                Text("💾 Сохранить настройки MAX")
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (saveSuccess) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        text = "✅ Настройки MAX Мессенджера сохранены!",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // === КНОПКА ВСПЛЫВАЮЩЕЙ ИНФОРМАЦИИ О ТОКЕНЕ MAX ===
            OutlinedButton(
                onClick = { showTokenInfoDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("ℹ️ Как получить токен MAX")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // === MAX Messenger Credentials ===
            Text(
                text = "💬 MAX Мессенджер Бот",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            OutlinedTextField(
                value = maxBotToken,
                onValueChange = {
                    maxBotToken = it
                    isModified = true
                },
                label = { Text("Токен бота MAX") },
                placeholder = { Text("Введите токен бота MAX") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Обязательное поле для MAX Мессенджера") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = maxOrdersChatId,
                onValueChange = {
                    maxOrdersChatId = it
                    isModified = true
                },
                label = { Text("ID чата Заказов (MAX)") },
                placeholder = { Text("ID чата в MAX для заказов") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Определится автоматически при команде СТАРТ в чате MAX или укажите вручную") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = maxSupplyChatId,
                onValueChange = {
                    maxSupplyChatId = it
                    isModified = true
                },
                label = { Text("ID чата Поставок (MAX)") },
                placeholder = { Text("ID чата в MAX для стикеров и QR-кодов") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Определится автоматически при команде ПОСТАВКА в чате MAX или укажите вручную") }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = maxOrdersThreadId,
                    onValueChange = {
                        maxOrdersThreadId = it
                        isModified = true
                    },
                    label = { Text("Тема Заказов") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedTextField(
                    value = maxSupplyThreadId,
                    onValueChange = {
                        maxSupplyThreadId = it
                        isModified = true
                    },
                    label = { Text("Тема Поставок") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "💡 О работе с чатами MAX",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Chat ID и ID подчатов можно указать вручную выше или определить автоматически\n" +
                                "• Команда СТАРТ в чате MAX активирует бота и привязывает заказы и поставки WB к текущему чату MAX\n" +
                                "• Команда ПОСТАВКА в чате MAX перенаправляет стикеры, QR-коды и листы подбора в нужную тему MAX\n" +
                                "• Вы можете разделять рабочие каналы для логистов и упаковщиков в MAX",
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
                label = { Text("Интервал проверки MAX (минуты)") },
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
                Text("💾 Сохранить настройки MAX")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text("🔄 Сбросить настройки MAX")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ℹ️ Как настроить MAX Мессенджер",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "🔹 Токен MAX Мессенджера:\n" +
                                "   1. Скопируйте API токен бота из консоли администрирования MAX Мессенджера\n" +
                                "   2. Вставьте полученный токен в поле выше\n\n" +
                                "🔹 Настройка чатов MAX:\n" +
                                "   1. Введите Chat ID чатов MAX вручную или отправьте команду СТАРТ в чате MAX\n" +
                                "   2. Все уведомления о заказах, стикеры PDF, QR-коды и листы подбора WB будут автоматически отправляться в MAX Мессенджер.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    // ВСПЛЫВАЮЩИЙ ДИАЛОГ - КАК ПОЛУЧИТЬ ТОКЕН MAX
    if (showTokenInfoDialog) {
        AlertDialog(
            onDismissRequest = { showTokenInfoDialog = false },
            title = { Text("ℹ️ Как получить токен MAX Мессенджера") },
            text = {
                Column {
                    Text(
                        text = "Пошаговая инструкция:\n\n" +
                                "1. Откройте панель администрирования корпоративного мессенджера MAX.\n" +
                                "2. Перейдите в раздел «Боты и интеграции» (или Ключи API).\n" +
                                "3. Нажмите «Создать бота» и введите название бота.\n" +
                                "4. Скопируйте полученный API Токен (Secret Key / Token) и вставьте в поле выше.",
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
            title = { Text("Сброс настроек MAX") },
            text = { Text("Вы уверены, что хотите сбросить настройки MAX Мессенджера?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        preferencesManager.setMaxBotToken("")
                        preferencesManager.setMaxOrdersChatId("")
                        preferencesManager.setMaxSupplyChatId("")
                        preferencesManager.setMaxOrdersThreadId(0)
                        preferencesManager.setMaxSupplyThreadId(0)
                        maxBotToken = ""
                        maxOrdersChatId = ""
                        maxSupplyChatId = ""
                        maxOrdersThreadId = ""
                        maxSupplyThreadId = ""
                        checkInterval = "15"
                        isModified = false
                        showResetDialog = false
                        Toast.makeText(context, "Настройки MAX сброшены", Toast.LENGTH_SHORT).show()
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