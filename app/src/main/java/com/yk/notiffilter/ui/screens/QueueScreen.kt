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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yk.notiffilter.NotifFilterApp
import com.yk.notiffilter.data.TemplateVerdict
import com.yk.notiffilter.data.Verdict
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QueueViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as NotifFilterApp).repository

    val pending = repo.queue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun answer(template: TemplateVerdict, keep: Boolean) =
        viewModelScope.launch { repo.answer(template, keep) }

    fun labelFor(pkg: String): String = runCatching {
        val pm = getApplication<Application>().packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}

/**
 * The bulk-label screen: the only place verdicts are actually decided.
 *
 * Everything here is one template, not one notification — an answer covers every
 * future message with that wording, which is why a queue that looks long on day three
 * collapses to a trickle by the end of the week.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(onBack: () -> Unit, vm: QueueViewModel = viewModel()) {
    val pending by vm.pending.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (pending.isEmpty()) "Nothing to decide" else "${pending.size} to decide") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (pending.isEmpty()) {
                item {
                    Text(
                        "Every template you've seen has a verdict. New wordings will " +
                            "show up here as they arrive — you'll be told silently, never " +
                            "interrupted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(pending, key = TemplateVerdict::fingerprint) { template ->
                QueueCard(
                    template = template,
                    appLabel = vm.labelFor(template.packageName),
                    onKeep = { vm.answer(template, keep = true) },
                    onHide = { vm.answer(template, keep = false) },
                )
            }
        }
    }
}

@Composable
private fun QueueCard(
    template: TemplateVerdict,
    appLabel: String,
    onKeep: () -> Unit,
    onHide: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                appLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (template.sampleTitle.isNotBlank()) {
                Text(template.sampleTitle, style = MaterialTheme.typography.titleSmall)
            }
            if (template.sampleText.isNotBlank()) {
                Text(
                    template.sampleText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // The lean is shown rather than hidden: it explains why this one buzzed
            // or stayed silent while you hadn't answered yet.
            Text(
                buildString {
                    append(template.reason)
                    append(" · while unanswered it ")
                    append(
                        if (template.verdict == Verdict.KEEP) "makes a sound"
                        else "stays silent"
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onHide, modifier = Modifier.weight(1f)) {
                    Text("Always hide")
                }
                Button(onClick = onKeep, modifier = Modifier.weight(1f)) {
                    Text("Always show")
                }
            }
        }
    }
}
