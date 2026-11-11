package com.activetour.wbbotcontroller

import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.ui.unit.dp
import com.activetour.wbbotcontroller.ui.theme.WBBotControllerTheme
import com.activetour.wbbotcontroller.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class UrlSettingsActivity : ComponentActivity() {

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
                    UrlSettingsScreen(
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
fun UrlSettingsScreen(
    preferencesManager: PreferencesManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var wbOrdersUrl by remember { mutableStateOf(preferencesManager.getWbOrdersUrl()) }
    var wbSuppliesUrl by remember { mutableStateOf(preferencesManager.getWbSuppliesUrl()) }
    var wbAddOrdersUrl by remember { mutableStateOf(preferencesManager.getWbAddOrdersUrl()) }
    var wbIdOrdersInSupplyUrl by remember { mutableStateOf(preferencesManager.getWbIdOrdersInSupplyUrl()) }
    var wbStickersUrl by remember { mutableStateOf(preferencesManager.getWbStickersUrl()) }
    var wbListBoxIDsUrl by remember { mutableStateOf(preferencesManager.getWBListBoxIDsUrl()) }
    var wbQRCodeSuppliesUrl by remember { mutableStateOf(preferencesManager.getWBQRCodeSuppliesUrl()) }
    var wbAddNumberCargoSpacesUrl by remember { mutableStateOf(preferencesManager.getWbAddNumberCargoSpacesUrl()) }
    var wbOrdersDetailsUrl by remember { mutableStateOf(preferencesManager.getWBOrdersDetailsUrl()) }

    var showResetDialog by remember { mutableStateOf(false) }
    var saveSuccess by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔗 Настройка URL API") },
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
            if (saveSuccess) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        text = "✅ URL успешного сохранены!",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = "🌐 Wildberries API Эндпоинты",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "ℹ️ Обратите внимание",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "В адресах параметров поставки используйте {supplyId}. При отправке запроса программа автоматически подставит ID текущей поставки.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = wbOrdersUrl,
                onValueChange = { wbOrdersUrl = it },
                label = { Text("1. URL новых сборочных заданий (заказов)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = wbSuppliesUrl,
                onValueChange = { wbSuppliesUrl = it },
                label = { Text("2. URL списка поставок / создания поставки") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = wbAddOrdersUrl,
                onValueChange = { wbAddOrdersUrl = it },
                label = { Text("3. URL добавления заказов в поставку") },
                supportingText = { Text("Формат: .../supplies/{supplyId}/orders") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = wbIdOrdersInSupplyUrl,
                onValueChange = { wbIdOrdersInSupplyUrl = it },
                label = { Text("4. URL получения ID всех заказов поставки") },
                supportingText = { Text("Формат: .../supplies/{supplyId}/order-ids") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = wbStickersUrl,
                onValueChange = { wbStickersUrl = it },
                label = { Text("5. URL стикеров заказов") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = wbListBoxIDsUrl,
                onValueChange = { wbListBoxIDsUrl = it },
                label = { Text("6. URL получения ID грузомест (коробок)") },
                supportingText = { Text("Формат: .../supplies/{supplyId}/trbx") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = wbQRCodeSuppliesUrl,
                onValueChange = { wbQRCodeSuppliesUrl = it },
                label = { Text("7. URL QR-кода грузомест поставки") },
                supportingText = { Text("Формат: .../supplies/{supplyId}/trbx/stickers") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = wbAddNumberCargoSpacesUrl,
                onValueChange = { wbAddNumberCargoSpacesUrl = it },
                label = { Text("8. URL добавления грузомест (коробок)") },
                supportingText = { Text("Формат: .../supplies/{supplyId}/trbx") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = wbOrdersDetailsUrl,
                onValueChange = { wbOrdersDetailsUrl = it },
                label = { Text("9. URL деталей заказов (для листа подбора)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    preferencesManager.setWbOrdersUrl(wbOrdersUrl)
                    preferencesManager.setWbSuppliesUrl(wbSuppliesUrl)
                    preferencesManager.setWbAddOrdersUrl(wbAddOrdersUrl)
                    preferencesManager.setWbIdOrdersInSupplyUrl(wbIdOrdersInSupplyUrl)
                    preferencesManager.setWbStickersUrl(wbStickersUrl)
                    preferencesManager.setWBListBoxIDsUrl(wbListBoxIDsUrl)
                    preferencesManager.setWBQRCodeSuppliesUrl(wbQRCodeSuppliesUrl)
                    preferencesManager.setWbAddNumberCargoSpacesUrl(wbAddNumberCargoSpacesUrl)
                    preferencesManager.setOrdersDetailsUrl(wbOrdersDetailsUrl)

                    saveSuccess = true
                    Toast.makeText(context, "URL настройки сохранены", Toast.LENGTH_SHORT).show()

                    CoroutineScope(Dispatchers.Main).launch {
                        delay(2000)
                        saveSuccess = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("💾 Сохранить URL настройки")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text("🔄 Сбросить URL по умолчанию")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Сбросить URL?") },
            text = { Text("Все адреса URL будут восстановлены к стандартным значениям Wildberries API v3.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        preferencesManager.resetUrlsToDefaults()
                        wbOrdersUrl = preferencesManager.getWbOrdersUrl()
                        wbSuppliesUrl = preferencesManager.getWbSuppliesUrl()
                        wbAddOrdersUrl = preferencesManager.getWbAddOrdersUrl()
                        wbIdOrdersInSupplyUrl = preferencesManager.getWbIdOrdersInSupplyUrl()
                        wbStickersUrl = preferencesManager.getWbStickersUrl()
                        wbListBoxIDsUrl = preferencesManager.getWBListBoxIDsUrl()
                        wbQRCodeSuppliesUrl = preferencesManager.getWBQRCodeSuppliesUrl()
                        wbAddNumberCargoSpacesUrl = preferencesManager.getWbAddNumberCargoSpacesUrl()
                        wbOrdersDetailsUrl = preferencesManager.getWBOrdersDetailsUrl()

                        showResetDialog = false
                        Toast.makeText(context, "URL сброшены к дефолтным", Toast.LENGTH_SHORT).show()
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
