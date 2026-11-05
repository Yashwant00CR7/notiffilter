package com.yk.notiffilter.ui.screens

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import com.yk.notiffilter.NotifFilterApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean = false,
    val isAdded: Boolean = false,
)

class AppPickerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as NotifFilterApp).repository
    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps = _apps.asStateFlow()

    init {
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) { loadAllApps() }
        }
    }

    private suspend fun loadAllApps(): List<InstalledApp> {
        val pm = getApplication<Application>().packageManager
        val selfPkg = getApplication<Application>().packageName

        // Existing added rules
        val addedRules = runCatching { repo.rules().first().map { it.packageName }.toSet() }
            .getOrDefault(emptySet())

        // Launchable apps query
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchablePackages = runCatching {
            pm.queryIntentActivities(launchIntent, PackageManager.MATCH_ALL)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        }.getOrDefault(emptySet())

        // Get all installed applications on the device
        val installed = runCatching {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }.getOrElse {
            runCatching { pm.getInstalledApplications(0) }.getOrDefault(emptyList())
        }

        val appMap = mutableMapOf<String, InstalledApp>()

        for (info in installed) {
            val pkg = info.packageName
            if (pkg == selfPkg) continue

            val isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            val isLaunchable = launchablePackages.contains(pkg)

            val rawLabel = runCatching { pm.getApplicationLabel(info).toString() }.getOrNull()
            val label = if (!rawLabel.isNullOrBlank()) rawLabel else pkg

            appMap[pkg] = InstalledApp(
                packageName = pkg,
                label = label,
                // If it has a launcher icon, treat as user-facing even if installed in /system
                isSystem = isSystemApp && !isLaunchable,
                isAdded = addedRules.contains(pkg),
            )
        }

        // Fallback for any launchable packages not in installedApplications
        for (pkg in launchablePackages) {
            if (pkg == selfPkg || appMap.containsKey(pkg)) continue
            val label = runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(info).toString()
            }.getOrDefault(pkg)
            appMap[pkg] = InstalledApp(
                packageName = pkg,
                label = label,
                isSystem = false,
                isAdded = addedRules.contains(pkg),
            )
        }

        // Sort: User apps first, then alphabetical by label
        return appMap.values.sortedWith(
            compareBy<InstalledApp> { it.isSystem }
                .thenBy { it.label.lowercase() }
        )
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
    var showAllApps by remember { mutableStateOf(false) }

    val userApps = remember(apps) { apps.filter { !it.isSystem } }

    val filtered = remember(apps, userApps, query, showAllApps) {
        val baseList = if (showAllApps || query.isNotBlank()) apps else userApps
        if (query.isBlank()) {
            baseList
        } else {
            val q = query.trim()
            baseList.filter {
                it.label.contains(q, ignoreCase = true) ||
                    it.packageName.contains(q, ignoreCase = true)
            }
        }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search by name or package") },
                placeholder = { Text("e.g. WhatsApp, System, com.android...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (query.isBlank() && apps.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !showAllApps,
                        onClick = { showAllApps = false },
                        label = { Text("User apps (${userApps.size})") },
                    )
                    FilterChip(
                        selected = showAllApps,
                        onClick = { showAllApps = true },
                        label = { Text("All apps inc. system (${apps.size})") },
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (filtered.isEmpty() && apps.isNotEmpty()) {
                    item {
                        Text(
                            "No apps match \"$query\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(filtered, key = InstalledApp::packageName) { app ->
                    ListItem(
                        headlineContent = { Text(app.label) },
                        supportingContent = { Text(app.packageName) },
                        trailingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (app.isAdded) {
                                    Text(
                                        "Added",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (app.isSystem) {
                                    Text(
                                        "System",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        modifier = Modifier.clickable { onPicked(app.packageName) },
                    )
                }
            }
        }
    }
}
