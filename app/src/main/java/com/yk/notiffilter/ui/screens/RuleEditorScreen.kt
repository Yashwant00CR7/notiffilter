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
import androidx.compose.material3.OutlinedTextField
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
import com.yk.notiffilter.ui.SystemSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RuleEditorViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as NotifFilterApp).repository

    suspend fun load(pkg: String): AppRule? = repo.rule(pkg).first()

    fun labelFor(pkg: String): String = runCatching {
        val pm = getApplication<Application>().packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    fun save(rule: AppRule) = viewModelScope.launch { repo.saveRule(rule) }

    fun delete(pkg: String, then: () -> Unit) = viewModelScope.launch {
        repo.deleteRule(pkg)
        then()
    }
}

private const val PROSE_HINT =
    "Keep order status, delivery updates, and refund confirmations. " +
        "Drop coupons, discount offers, restaurant recommendations, and re-order nudges."

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
    var prose by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }
    var repost by remember { mutableStateOf(true) }
    var createdAt by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(packageName) {
        val existing = vm.load(packageName)
        if (existing != null) {
            isNew = false
            label = existing.appLabel
            prose = existing.prose
            enabled = existing.enabled
            repost = existing.repostKeepers
            createdAt = existing.createdAt
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
                    IconButton(onClick = onBack) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(packageName, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("What matters from this app", style = MaterialTheme.typography.titleMedium)
            Text(
                "Written in your own words and handed to the model verbatim. Be concrete " +
                    "about both halves — what to keep and what to drop.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = prose,
                onValueChange = { prose = it },
                placeholder = { Text(PROSE_HINT) },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )

            SwitchRow(
                title = "Filter this app",
                subtitle = "When off, notifications from this app pass through untouched.",
                checked = enabled,
                onChange = { enabled = it },
            )

            SwitchRow(
                title = "Re-post kept notifications",
                subtitle = "Keepers are re-posted on a channel that makes a sound, so you can " +
                    "set this app to Silent and still be interrupted by the ones that matter.",
                checked = repost,
                onChange = { repost = it },
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Silence this app", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Filtering happens after the phone has already buzzed, so hiding junk " +
                            "alone won't stop the interruptions. Set every channel for this app " +
                            "to Silent — NotifFilter supplies the sound for keepers instead. " +
                            "Android gives no way to do this for you.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = { SystemSettings.openAppNotificationSettings(context, packageName) },
                    ) { Text("Open ${label}'s notification settings") }
                }
            }

            Button(
                onClick = {
                    vm.save(
                        AppRule(
                            packageName = packageName,
                            appLabel = label,
                            prose = prose.trim(),
                            enabled = enabled,
                            repostKeepers = repost,
                            createdAt = createdAt,
                        )
                    )
                    onBack()
                },
                enabled = prose.isNotBlank(),
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
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
