package com.yk.notiffilter.ui.screens

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val aliases: List<String> = emptyList(),
) {
    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim()
        if (label.contains(q, ignoreCase = true)) return true
        if (packageName.contains(q, ignoreCase = true)) return true
        return aliases.any { it.contains(q, ignoreCase = true) }
    }
}

class AppPickerViewModel(app: Application) : AndroidViewModel(app) {
    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps = _apps.asStateFlow()

    init {
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) { loadAllApps() }
        }
    }

    private fun loadAllApps(): List<InstalledApp> {
        val pm = getApplication<Application>().packageManager
        val selfPkg = getApplication<Application>().packageName

        // Launchable apps only: Keeps the list clean, fast, and readable without system daemons
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launcherApps = runCatching {
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }.getOrDefault(emptyList())

        val appMap = mutableMapOf<String, InstalledApp>()

        for (resolve in launcherApps) {
            val pkg = resolve.activityInfo?.packageName ?: continue
            if (pkg == selfPkg) continue

            val label = runCatching {
                resolve.loadLabel(pm)?.toString()?.trim()
            }.getOrNull().takeUnless { it.isNullOrBlank() }
                ?: runCatching { pm.getApplicationLabel(resolve.activityInfo.applicationInfo).toString().trim() }.getOrDefault(pkg)

            val aliases = mutableListOf<String>()
            if (pkg.contains("amazon", ignoreCase = true)) {
                aliases.addAll(listOf("Amazon Shopping", "Amazon Pay", "Pay", "UPI", "Apay"))
            } else if (pkg.contains("messaging", ignoreCase = true) || pkg.contains("mms", ignoreCase = true)) {
                aliases.addAll(listOf("SMS", "Messages", "Text"))
            }

            appMap[pkg] = InstalledApp(
                packageName = pkg,
                label = label,
                aliases = aliases,
            )
        }

        // Amazon Pay is part of Amazon Shopping (in.amazon.mShop.android.shopping).
        // Add an explicit first-class entry for Amazon Pay so users can see and pick it directly.
        val amazonEntry = appMap.values.firstOrNull { it.packageName.contains("amazon", ignoreCase = true) }
        val result = appMap.values.toMutableList()

        if (amazonEntry != null) {
            result.add(
                InstalledApp(
                    packageName = amazonEntry.packageName,
                    label = "Amazon Pay",
                    aliases = listOf("Amazon", "Pay", "UPI", "Apay", "Amazon Shopping", "Wallet"),
                )
            )
        }

        return result.sortedBy { it.label.lowercase() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(
    onBack: () -> Unit,
    onPicked: (String) -> Unit,
    vm: AppPickerViewModel = viewModel(),
) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.matches(query) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add an app") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
            items(filtered, key = { "${it.packageName}_${it.label}" }) { app ->
                ListItem(
                    headlineContent = { Text(app.label) },
                    supportingContent = { Text(app.packageName) },
                    modifier = Modifier.clickable { onPicked(app.packageName) },
                )
            }
        }
    }
}
