package com.yk.notiffilter.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yk.notiffilter.NotifFilterApp
import com.yk.notiffilter.service.FilterListenerService
import com.yk.notiffilter.ui.SystemSettings
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val application = app as NotifFilterApp
    private val settings = application.settings

    var apiKey by mutableStateOf(settings.apiKey)
    var model by mutableStateOf(settings.model)
    var baseUrl by mutableStateOf(settings.baseUrl)
    var status by mutableStateOf<String?>(null)
    var testing by mutableStateOf(false)

    fun save() {
        settings.apiKey = apiKey.trim()
        settings.model = model
        settings.baseUrl = baseUrl
        status = "Saved"
    }

    fun test() {
        save()
        testing = true
        status = "Testing…"
        viewModelScope.launch {
            val classifier = application.classifier()
            status = if (classifier == null) {
                "No API key set"
            } else {
                classifier.testConnection().fold(
                    onSuccess = { it },
                    onFailure = { it.message?.take(200) ?: "Failed" },
                )
            }
            testing = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    var listenerEnabled by remember { mutableStateOf(FilterListenerService.isEnabled(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Classifier", style = MaterialTheme.typography.titleMedium)
            Text(
                "The key is encrypted with an Android Keystore key and never leaves this " +
                    "device. Only text from apps you've added is ever sent to Groq.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = vm.apiKey,
                onValueChange = { vm.apiKey = it },
                label = { Text("Groq API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.model,
                onValueChange = { vm.model = it },
                label = { Text("Model") },
                supportingText = {
                    Text("Editable because Groq's free model lineup churns; a retired id " +
                        "would otherwise need a new build.")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.baseUrl,
                onValueChange = { vm.baseUrl = it },
                label = { Text("Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(onClick = vm::test, enabled = !vm.testing, modifier = Modifier.fillMaxWidth()) {
                Text("Save and test connection")
            }
            vm.status?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            Text("Permissions", style = MaterialTheme.typography.titleMedium)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (listenerEnabled) "Notification access: granted"
                        else "Notification access: not granted",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "Required. Without it nothing is read and nothing is filtered.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = {
                        SystemSettings.openListenerAccess(context)
                        listenerEnabled = FilterListenerService.isEnabled(context)
                    }) { Text("Open notification access") }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Keep running in the background", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "ColorOS kills bound services aggressively. Exempt NotifFilter from " +
                            "battery optimisation, and enable Auto-start for it in realme's " +
                            "own settings — that toggle can't be opened from here. If the " +
                            "listener dies, notifications simply stop being filtered.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { SystemSettings.requestBatteryExemption(context) }) {
                        Text("Battery settings")
                    }
                }
            }

            Text(
                "How filtering works",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "A notification stays on screen until something positively decides to hide " +
                    "it. If you're offline, the key is wrong, or Groq is down, everything is " +
                    "shown exactly as it would be without this app. Nothing is silently lost.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
