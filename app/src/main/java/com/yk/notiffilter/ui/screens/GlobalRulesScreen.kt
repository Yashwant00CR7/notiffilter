package com.yk.notiffilter.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yk.notiffilter.NotifFilterApp
import com.yk.notiffilter.data.MatchTarget
import com.yk.notiffilter.data.NotificationRule
import com.yk.notiffilter.data.Verdict
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GlobalRulesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as NotifFilterApp).repository

    val rules = repo.globalRules().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    fun saveRule(rule: NotificationRule) = viewModelScope.launch {
        repo.saveNotificationRule(rule)
    }

    fun toggleRule(rule: NotificationRule) = viewModelScope.launch {
        repo.saveNotificationRule(rule.copy(enabled = !rule.enabled))
    }

    fun deleteRule(id: Long) = viewModelScope.launch {
        repo.deleteNotificationRule(id)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalRulesScreen(
    onBack: () -> Unit,
    vm: GlobalRulesViewModel = viewModel(),
) {
    val rules by vm.rules.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var filterVerdict by remember { mutableStateOf<Verdict?>(null) }

    val filteredRules = rules.filter { filterVerdict == null || it.verdict == filterVerdict }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Global Rules") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Global Rule")
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "Global rules run across all opted-in apps before the AI classifier. " +
                    "Whitelist KEEP rules guarantee transactional alerts (OTPs, deliveries) are never missed; " +
                    "Blacklist HIDE rules silence recurring marketing spam patterns.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filterVerdict == null,
                    onClick = { filterVerdict = null },
                    label = { Text("All (${rules.size})") },
                )
                FilterChip(
                    selected = filterVerdict == Verdict.KEEP,
                    onClick = { filterVerdict = if (filterVerdict == Verdict.KEEP) null else Verdict.KEEP },
                    label = { Text("Keep (${rules.count { it.verdict == Verdict.KEEP }})") },
                )
                FilterChip(
                    selected = filterVerdict == Verdict.HIDE,
                    onClick = { filterVerdict = if (filterVerdict == Verdict.HIDE) null else Verdict.HIDE },
                    label = { Text("Hide (${rules.count { it.verdict == Verdict.HIDE }})") },
                )
            }

            if (filteredRules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No global rules match the filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 88.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(filteredRules, key = { it.id }) { rule ->
                        RuleItemCard(
                            rule = rule,
                            onToggle = { vm.toggleRule(rule) },
                            onDelete = { vm.deleteRule(rule.id) },
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            AddRuleDialog(
                packageName = null,
                onDismiss = { showAddDialog = false },
                onSave = { rule ->
                    vm.saveRule(rule)
                    showAddDialog = false
                },
            )
        }
    }
}

@Composable
fun RuleItemCard(
    rule: NotificationRule,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val verdictColor = if (rule.verdict == Verdict.KEEP) Color(0xFF2E7D32) else Color(0xFFC62828)
                    Box(
                        modifier = Modifier
                            .background(verdictColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            rule.verdict.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }

                    if (rule.isRegex) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "Regex",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }

                    if (rule.matchTarget != MatchTarget.ANY) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                rule.matchTarget.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    rule.pattern,
                    style = MaterialTheme.typography.bodyMedium,
                )

                rule.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = rule.enabled, onCheckedChange = { onToggle() })
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete rule",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
fun AddRuleDialog(
    packageName: String?,
    onDismiss: () -> Unit,
    onSave: (NotificationRule) -> Unit,
) {
    var pattern by remember { mutableStateOf("") }
    var verdict by remember { mutableStateOf(Verdict.HIDE) }
    var matchTarget by remember { mutableStateOf(MatchTarget.ANY) }
    var isRegex by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (packageName == null) "Add Global Rule" else "Add App Rule") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Pattern / Keyword") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Verdict Action", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = verdict == Verdict.KEEP,
                        onClick = { verdict = Verdict.KEEP },
                    )
                    Text("KEEP (Whitelist)", modifier = Modifier.padding(end = 12.dp))

                    RadioButton(
                        selected = verdict == Verdict.HIDE,
                        onClick = { verdict = Verdict.HIDE },
                    )
                    Text("HIDE (Blacklist)")
                }

                Text("Match In", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(MatchTarget.ANY, MatchTarget.TITLE, MatchTarget.TEXT, MatchTarget.CHANNEL).forEach { target ->
                        FilterChip(
                            selected = matchTarget == target,
                            onClick = { matchTarget = target },
                            label = { Text(target.name, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Checkbox(checked = isRegex, onCheckedChange = { isRegex = it })
                    Text("Treat pattern as regular expression (Regex)")
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pattern.isNotBlank()) {
                        onSave(
                            NotificationRule(
                                packageName = packageName,
                                pattern = pattern.trim(),
                                isRegex = isRegex,
                                matchTarget = matchTarget,
                                verdict = verdict,
                                enabled = true,
                                description = description.trim().takeIf { it.isNotBlank() },
                            )
                        )
                    }
                },
                enabled = pattern.isNotBlank(),
            ) {
                Text("Add Rule")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
