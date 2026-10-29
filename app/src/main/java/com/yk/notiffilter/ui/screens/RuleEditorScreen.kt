package com.yk.notiffilter.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yk.notiffilter.NotifFilterApp
import com.yk.notiffilter.data.AppRule
import com.yk.notiffilter.data.FilterMode
import com.yk.notiffilter.ui.SystemSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import androidx.compose.runtime.collectAsState
import com.yk.notiffilter.data.NotificationRule

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf

class RuleEditorViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as NotifFilterApp).repository

    suspend fun load(pkg: String): AppRule? = repo.rule(pkg).first()

    /** How many templates from this app the user has personally ruled on. */
    suspend fun labelCount(pkg: String): Int =
        repo.verdicts(pkg).first().count { !it.needsVerdict && it.source.name == "USER" }

    fun labelFor(pkg: String): String = runCatching {
        val pm = getApplication<Application>().packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    fun save(rule: AppRule) = viewModelScope.launch { repo.saveRule(rule) }

    fun delete(pkg: String, then: () -> Unit) = viewModelScope.launch {
        repo.deleteRule(pkg)
        then()
    }

    fun customRules(pkg: String) = repo.notificationRulesForApp(pkg)

    fun saveNotificationRule(rule: NotificationRule) = viewModelScope.launch {
        repo.saveNotificationRule(rule)
    }

    fun deleteNotificationRule(id: Long) = viewModelScope.launch {
        repo.deleteNotificationRule(id)
    }

    fun toggleNotificationRule(rule: NotificationRule) = viewModelScope.launch {
        repo.saveNotificationRule(rule.copy(enabled = !rule.enabled))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorScreen(
    packageName: String,
    onBack: () -> Unit,
    vm: RuleEditorViewModel = viewModel(),
) {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf(false) }
    var isNew by remember { mutableStateOf(true) }
    var label by remember { mutableStateOf(packageName) }
    var mode by remember { mutableStateOf(FilterMode.ACTIVE) }
    var enabled by remember { mutableStateOf(true) }
    var repost by remember { mutableStateOf(false) }
    var labelled by remember { mutableIntStateOf(0) }
    var createdAt by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun saveCurrent() {
        vm.save(
            AppRule(
                packageName = packageName,
                appLabel = label,
                mode = mode,
                enabled = enabled,
                repostKeepers = repost,
                createdAt = createdAt,
            )
        )
    }

    BackHandler {
        saveCurrent()
        onBack()
    }

    LaunchedEffect(packageName) {
        val existing = vm.load(packageName)
        if (existing != null) {
            isNew = false
            label = existing.appLabel
            mode = existing.mode
            enabled = existing.enabled
            repost = existing.repostKeepers
            createdAt = existing.createdAt
            labelled = vm.labelCount(packageName)
        } else {
            label = vm.labelFor(packageName)
        }
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(label) },
                navigationIcon = {
                    IconButton(onClick = {
                        saveCurrent()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { vm.delete(packageName, onBack) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove app")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Observe first, filter second. A classifier that has been taught nothing
            // has no business hiding anything, and observing costs nothing because it
            // cannot change what you see.
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (mode == FilterMode.OBSERVE) "Observing" else "Filtering",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        if (mode == FilterMode.OBSERVE) {
                            "Nothing is hidden and nothing is re-posted. Notifications from " +
                                "$label are logged so their templates can be collected — leave " +
                                "it here for a few days, answer the questions it gathers, then " +
                                "turn filtering on. You have labelled $labelled so far."
                        } else {
                            "Templates you've answered are applied immediately. Anything the " +
                                "model isn't confident about is queued for you instead of " +
                                "guessed at. You have labelled $labelled template(s)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = {
                            mode = if (mode == FilterMode.OBSERVE) FilterMode.ACTIVE
                            else FilterMode.OBSERVE
                            saveCurrent()
                        },
                    ) {
                        Text(
                            if (mode == FilterMode.OBSERVE) "Start filtering"
                            else "Back to observing"
                        )
                    }
                }
            }

            SwitchRow(
                title = "Filter this app",
                subtitle = "When off, notifications from this app pass through untouched.",
                checked = enabled,
                onChange = {
                    enabled = it
                    saveCurrent()
                },
            )
            SwitchRow(
                title = "Re-post kept notifications",
                subtitle = "Turn this on only after silencing this app's own channels. " +
                    "Our copy replaces the original, so it loses replies, buttons and " +
                    "images. Notifications that carry those are always left untouched.",
                checked = repost,
                onChange = {
                    repost = it
                    saveCurrent()
                },
            )

            // Only surfaced once filtering is on: silencing an app while still
            // observing would mute real messages for no benefit at all.
            if (mode == FilterMode.ACTIVE) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Silence this app", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Filtering happens after the phone has already buzzed, so hiding " +
                                "junk alone won't stop the interruptions. Set every channel for " +
                                "this app to Silent — NotifFilter supplies the sound for keepers " +
                                "instead. Android gives no way to do this for you.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(
                            onClick = {
                                SystemSettings.openAppNotificationSettings(context, packageName)
                            },
                        ) { Text("Open ${label}'s notification settings") }
                    }
                }
            }

            val customRules by vm.customRules(packageName).collectAsState(initial = emptyList())
            var showAddRuleDialog by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Custom Rules", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { showAddRuleDialog = true }) {
                    Text("+ Add Rule")
                }
            }
            Text(
                "App-specific keyword, regex, or channel rules that override the classifier for $label.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (customRules.isEmpty()) {
                Text(
                    "No custom rules for this app. Global rules and the AI model handle its filtering.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                customRules.forEach { customRule ->
                    RuleItemCard(
                        rule = customRule,
                        onToggle = { vm.toggleNotificationRule(customRule) },
                        onDelete = { vm.deleteNotificationRule(customRule.id) },
                    )
                }
            }

            if (showAddRuleDialog) {
                AddRuleDialog(
                    packageName = packageName,
                    onDismiss = { showAddRuleDialog = false },
                    onSave = { newRule ->
                        vm.saveNotificationRule(newRule)
                        showAddRuleDialog = false
                    },
                )
            }

            Button(
                onClick = {
                    saveCurrent()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (isNew) "Add app" else "Save") }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
