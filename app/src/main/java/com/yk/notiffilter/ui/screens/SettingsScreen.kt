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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yk.notiffilter.NotifFilterApp
import com.yk.notiffilter.service.FilterListenerService
import com.yk.notiffilter.ui.SystemSettings
import kotlin.math.roundToInt

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = (app as NotifFilterApp).settings

    var confidenceMargin by mutableFloatStateOf(settings.confidenceMargin)
    var minExamples by mutableIntStateOf(settings.minExamples)

    fun saveMargin(value: Float) {
        confidenceMargin = value
        settings.confidenceMargin = value
    }

    fun saveMinExamples(value: Int) {
        minExamples = value
        settings.minExamples = value
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
            Text("Privacy", style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nothing leaves this device", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "NotifFilter has no internet permission at all. Classification runs " +
                            "here, from the verdicts you've given it — there is no API key, no " +
                            "server and no account, and the operating system will not let this " +
                            "app open a network connection even if it tried to.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Text("How cautious it is", style = MaterialTheme.typography.titleMedium)
            Text(
                "The model may act on its own only when the evidence clears this much. " +
                    "Raise it to be asked more often and surprised less; lower it to answer " +
                    "fewer questions and let it guess more.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Confidence needed: ${"%.1f".format(vm.confidenceMargin)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = vm.confidenceMargin,
                onValueChange = vm::saveMargin,
                valueRange = 1f..12f,
                steps = 21,
            )

            Text(
                "Your labels before it may decide alone: ${vm.minExamples}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Below this, everything is queued for you. With only a handful of examples " +
                    "the arithmetic looks confident while meaning nothing, so it stays quiet " +
                    "until you've actually taught it something.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = vm.minExamples.toFloat(),
                onValueChange = { vm.saveMinExamples(it.roundToInt()) },
                valueRange = 0f..40f,
                steps = 39,
            )

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

            Text("How filtering works", style = MaterialTheme.typography.titleMedium)
            Text(
                "A notification stays on screen until something positively decides to hide " +
                    "it, and only a template you've answered — or one the model is certain " +
                    "matches something you've answered — is ever hidden. Everything else is " +
                    "queued for you and shown in the meantime. Nothing is silently lost.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
