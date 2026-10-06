package com.yk.notiffilter.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yk.notiffilter.NotifFilterApp
import com.yk.notiffilter.data.AppRule
import com.yk.notiffilter.data.FilterMode
import com.yk.notiffilter.service.FilterListenerService
import com.yk.notiffilter.ui.SystemSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.compose.runtime.DisposableEffect

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as NotifFilterApp).repository
    val rules = repo.rules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pending = repo.queue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddApp: () -> Unit,
    onOpenRule: (String) -> Unit,
    onOpenLog: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenQueue: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val rules by vm.rules.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()

    // Listener access is changed outside this app, so re-read it whenever the screen
    // comes back to the foreground rather than caching it.
    var listenerEnabled by remember { mutableStateOf(FilterListenerService.isEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                listenerEnabled = FilterListenerService.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NotifFilter") },
                actions = {
                    IconButton(onClick = onOpenLog) {
                        Icon(Icons.Default.History, contentDescription = "Log")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddApp) {
                Icon(Icons.Default.Add, contentDescription = "Add app")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!listenerEnabled) {
                item {
                    SetupCard(
                        title = "Notification access is off",
                        body = "Without it nothing is filtered at all. Grant NotifFilter " +
                            "notification access to get started.",
                        action = "Open settings",
                        onAction = { SystemSettings.openListenerAccess(context) },
                    )
                }
            }
            if (pending.isNotEmpty()) {
                item {
                    Card(onClick = onOpenQueue, modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    if (pending.size == 1) "1 template needs your verdict"
                                    else "${pending.size} templates need your verdict"
                                )
                            },
                            supportingContent = {
                                Text(
                                    "Each answer covers every future notification with that " +
                                        "wording, and teaches the model the ones it hasn't seen."
                                )
                            },
                        )
                    }
                }
            }
            if (SystemSettings.isBatteryOptimised(context)) {
                item {
                    SetupCard(
                        title = "Battery optimisation is on",
                        body = "realme's ColorOS kills background services aggressively. " +
                            "If the listener is killed, notifications stop being filtered.",
                        action = "Exempt NotifFilter",
                        onAction = { SystemSettings.requestBatteryExemption(context) },
                    )
                }
            }

            item {
                Text(
                    "Filtered apps",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (rules.isEmpty()) {
                item {
                    Text(
                        "No apps yet. Apps you don't add here are never inspected at all " +
                            "— and nothing any app posts ever leaves this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(rules, key = AppRule::packageName) { rule ->
                Card(onClick = { onOpenRule(rule.packageName) }, modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(rule.appLabel) },
                        supportingContent = {
                            Text(
                                when {
                                    !rule.enabled -> "Off — notifications pass through untouched"
                                    rule.mode == FilterMode.OBSERVE ->
                                        "Observing — collecting templates, hiding nothing"
                                    else -> "Filtering"
                                },
                                maxLines = 2,
                            )
                        },
                        trailingContent = {
                            Text(
                                when {
                                    !rule.enabled -> "Off"
                                    rule.mode == FilterMode.OBSERVE -> "Observe"
                                    else -> "Active"
                                },
                                color = if (rule.enabled && rule.mode == FilterMode.ACTIVE)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupCard(title: String, body: String, action: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onAction) { Text(action) }
            }
        }
    }
}
