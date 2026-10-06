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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yk.notiffilter.NotifFilterApp
import com.yk.notiffilter.data.LogAction
import com.yk.notiffilter.data.LogEntry
import com.yk.notiffilter.data.VerdictSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as NotifFilterApp).repository

    val entries = repo.log()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun override(entry: LogEntry) = viewModelScope.launch { repo.override(entry) }
    fun clear() = viewModelScope.launch { repo.clearLog() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onBack: () -> Unit, vm: LogViewModel = viewModel()) {
    val entries by vm.entries.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = vm::clear) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear log")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (entries.isEmpty()) {
                item {
                    Text(
                        "Nothing yet. Only apps you've added are logged.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(entries, key = LogEntry::id) { entry ->
                LogCard(entry = entry, onOverride = { vm.override(entry) })
            }
        }
    }
}

@Composable
private fun LogCard(entry: LogEntry, onOverride: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${entry.appLabel} · ${formatTime(entry.postedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ActionChip(entry.action)
            }

            if (entry.title.isNotBlank()) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall)
            }
            if (entry.text.isNotBlank()) {
                Text(
                    entry.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                buildString {
                    append(entry.reason)
                    append(" · ")
                    append(
                        when {
                            entry.source == VerdictSource.USER -> "your rule"
                            entry.fromCache -> "known template"
                            else -> "new template"
                        }
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // One tap pins the opposite verdict for this whole template. Nothing
            // matching it is ever reconsidered, and the row starts training the model.
            if (entry.action != LogAction.PENDING && entry.action != LogAction.ERROR) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onOverride) {
                        Text(
                            if (entry.action == LogAction.HIDDEN) "Wrong — always show this"
                            else "Wrong — always hide this"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(action: LogAction) {
    val (label, color) = when (action) {
        LogAction.KEPT -> "Kept" to MaterialTheme.colorScheme.primary
        LogAction.HIDDEN -> "Hidden" to MaterialTheme.colorScheme.onSurfaceVariant
        LogAction.ASKED -> "Asked you" to MaterialTheme.colorScheme.tertiary
        LogAction.OBSERVED -> "Observed" to MaterialTheme.colorScheme.onSurfaceVariant
        // Only ever seen on rows written before classification moved on-device.
        LogAction.PENDING -> "Classifying" to MaterialTheme.colorScheme.tertiary
        LogAction.ERROR -> "Shown (error)" to MaterialTheme.colorScheme.error
    }
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            disabledLabelColor = color,
            disabledContainerColor = Color.Transparent,
        ),
    )
}

private fun formatTime(timeMs: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(timeMs))
