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

        // 1. Collect all activity labels and aliases (e.g. Amazon Pay, sub-shortcuts)
        val aliasesByPkg = mutableMapOf<String, MutableSet<String>>()
        val activities = runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN), PackageManager.MATCH_ALL)
        }.getOrDefault(emptyList())

        for (resolve in activities) {
            val pkg = resolve.activityInfo?.packageName ?: continue
            val actLabel = runCatching { resolve.loadLabel(pm)?.toString()?.trim() }.getOrNull()
            if (!actLabel.isNullOrBlank()) {
                aliasesByPkg.getOrPut(pkg) { mutableSetOf() }.add(actLabel)
            }
        }

        // 2. Query all installed packages
        val installed = runCatching {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }.getOrElse {
            runCatching { pm.getInstalledApplications(0) }.getOrDefault(emptyList())
        }

        val appMap = mutableMapOf<String, InstalledApp>()

        for (info in installed) {
            val pkg = info.packageName
            if (pkg == selfPkg) continue

            val rawLabel = runCatching { pm.getApplicationLabel(info).toString().trim() }.getOrNull()
            val label = if (!rawLabel.isNullOrBlank()) rawLabel else pkg

            val aliases = aliasesByPkg[pkg] ?: mutableSetOf()
            aliases.remove(label)

            // Amazon Pay is built into the main Amazon Shopping app in India/worldwide.
            // Map these aliases so searching "Amazon Pay" or "Pay" immediately finds it.
            if (pkg.contains("amazon", ignoreCase = true)) {
                aliases.add("Amazon Pay")
                aliases.add("Amazon Shopping")
                aliases.add("Apay")
                aliases.add("Amazon UPI")
            }

            appMap[pkg] = InstalledApp(
                packageName = pkg,
                label = label,
                aliases = aliases.toList(),
            )
        }

        // 3. Fallback for any packages in activities that were not returned by getInstalledApplications
        for ((pkg, aliases) in aliasesByPkg) {
            if (pkg == selfPkg || appMap.containsKey(pkg)) continue
            val label = runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(info).toString().trim()
            }.getOrDefault(pkg)
            aliases.remove(label)
            if (pkg.contains("amazon", ignoreCase = true)) {
                aliases.add("Amazon Pay")
            }
            appMap[pkg] = InstalledApp(
                packageName = pkg,
                label = label,
                aliases = aliases.toList(),
            )
        }

        return appMap.values.sortedBy { it.label.lowercase() }
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
            items(filtered, key = InstalledApp::packageName) { app ->
                val matchedAlias = if (query.isNotBlank()) {
                    app.aliases.firstOrNull { it.contains(query.trim(), ignoreCase = true) }
                } else null

                val title = if (matchedAlias != null && !app.label.contains(matchedAlias, ignoreCase = true)) {
                    "${app.label} ($matchedAlias)"
                } else {
                    app.label
                }

                ListItem(
                    headlineContent = { Text(title) },
                    supportingContent = { Text(app.packageName) },
                    modifier = Modifier.clickable { onPicked(app.packageName) },
                )
            }
        }
    }
}
