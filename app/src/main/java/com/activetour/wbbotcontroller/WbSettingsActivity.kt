package com.activetour.wbbotcontroller

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.activetour.wbbotcontroller.ui.theme.WBBotControllerTheme
import com.activetour.wbbotcontroller.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WbSettingsActivity : ComponentActivity() {

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
                    WbSettingsScreen(
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
fun WbSettingsScreen(
    preferencesManager: PreferencesManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var wbApiToken by remember { mutableStateOf(preferencesManager.getWbApiToken()) }

    var isModified by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showTokenInfoDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var saveSuccess by remember { mutableStateOf(false) }

    fun saveAllSettings() {
        preferencesManager.setWbApiToken(wbApiToken)
        isModified = false
        saveSuccess = true
        Toast.makeText(context, "Настройки Wildberries сохранены", Toast.LENGTH_SHORT).show()

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
                title = { Text("📦 Настройки Wildberries") },
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
                Text("💾 Сохранить настройки WB")
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (saveSuccess) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        text = "✅ Настройки Wildberries сохранены!",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // === КНОПКА ВСПЛЫВАЮЩЕЙ ИНФОРМАЦИИ О ТОКЕНЕ WB ===
            OutlinedButton(
                onClick = { showTokenInfoDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("ℹ️ Как получить токен WB")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // === Wildberries API Credentials ===
            Text(
                text = "📦 Wildberries API",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            OutlinedTextField(
                value = wbApiToken,
                onValueChange = {
                    wbApiToken = it
                    isModified = true
                },
                label = { Text("API Токен WB") },
                placeholder = { Text("Введите токен из кабинета WB") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Обязательное поле для получения заказов и поставок") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val intent = Intent(context, UrlSettingsActivity::class.java)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("🔗 Настройка URL адресов API WB")
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()

            // Кнопки сохранения и сброса внизу
            Button(
                onClick = { saveAllSettings() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("💾 Сохранить настройки WB")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text("🔄 Сбросить URL адреса WB к умолчаниям")
            }
        }
    }

    // ВСПЛЫВАЮЩИЙ ДИАЛОГ - КАК ПОЛУЧИТЬ ТОКЕН WB
    if (showTokenInfoDialog) {
        AlertDialog(
            onDismissRequest = { showTokenInfoDialog = false },
            title = { Text("ℹ️ Как получить токен Wildberries API") },
            text = {
                Column {
                    Text(
                        text = "Пошаговая инструкция для кабинета продавца WB:\n\n" +
                                "1. Зайдите в личный кабинет продавца Wildberries (seller.wildberries.ru).\n" +
                                "2. В правом верхнем углу нажмите на имя профиля или наименование компании.\n" +
                                "3. Перейдите в раздел «Настройки» → «Доступ к API» (Интеграции по API).\n" +
                                "4. Нажмите кнопку «Создать новый токен».\n" +
                                "5. Введите название токена (например, WBBotController).\n" +
                                "6. Выберите категории: «Маркетплейс», «Статистика» и «Контент» с правами «Чтение и запись».\n" +
                                "7. Нажмите «Создать токен» и скопируйте полученный ключ в поле выше.",
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
            title = { Text("Сброс URL адресов WB") },
            text = { Text("Сбросить все URL адреса WB API к стандартным значениям по умолчанию?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        preferencesManager.resetUrlsToDefaults()
                        showResetDialog = false
                        Toast.makeText(context, "URL адреса WB сброшены", Toast.LENGTH_SHORT).show()
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