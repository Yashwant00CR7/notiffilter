package com.yk.notiffilter.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yk.notiffilter.ui.screens.AppPickerScreen
import com.yk.notiffilter.ui.screens.HomeScreen
import com.yk.notiffilter.ui.screens.LogScreen
import com.yk.notiffilter.ui.screens.RuleEditorScreen
import com.yk.notiffilter.ui.screens.SettingsScreen
import com.yk.notiffilter.ui.theme.NotifFilterTheme

class MainActivity : ComponentActivity() {

    private val requestPostNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Without this, targetSdk 35 silently drops every re-posted keeper — which
        // would look exactly like the filter eating important notifications.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            NotifFilterTheme {
                NotifFilterNav(rememberNavController())
            }
        }
    }
}

object Routes {
    const val HOME = "home"
    const val PICKER = "picker"
    const val LOG = "log"
    const val SETTINGS = "settings"
    const val RULE = "rule/{pkg}"
    fun rule(pkg: String) = "rule/$pkg"
}

@Composable
fun NotifFilterNav(nav: NavHostController) {
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onAddApp = { nav.navigate(Routes.PICKER) },
                onOpenRule = { pkg -> nav.navigate(Routes.rule(pkg)) },
                onOpenLog = { nav.navigate(Routes.LOG) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.PICKER) {
            AppPickerScreen(
                onBack = { nav.popBackStack() },
                onPicked = { pkg ->
                    nav.popBackStack()
                    nav.navigate(Routes.rule(pkg))
                },
            )
        }
        composable(Routes.RULE) { entry ->
            RuleEditorScreen(
                packageName = entry.arguments?.getString("pkg").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.LOG) {
            LogScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
