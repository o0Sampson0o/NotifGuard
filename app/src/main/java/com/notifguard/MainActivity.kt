package com.notifguard

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.notifguard.service.NotifListenerService
import com.notifguard.ui.screens.*
import com.notifguard.ui.theme.NgColors
import com.notifguard.ui.theme.NotifGuardTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            NotifGuardTheme {
                var hasPermission by remember { mutableStateOf(isListenerEnabled()) }
                if (!hasPermission) PermissionScreen()
                else MainScaffold(vm)
            }
        }
    }

    fun isListenerEnabled(): Boolean {
        val cn = ComponentName(this, NotifListenerService::class.java)
        val flat = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(cn.flattenToString())
    }
}

sealed class Screen(val route: String, val label: String, val icon: String) {
    object FilterRules : Screen("filter_rules", "Filter",  "⚡")
    object SaveRules   : Screen("save_rules",   "Saving",  "💾")
    object Apps        : Screen("apps",          "Apps",    "📱")
    object Saved       : Screen("saved",         "Saved",   "📦")
    object Activity    : Screen("activity",      "Activity","📋")
}

@Composable
fun MainScaffold(vm: MainViewModel) {
    val tabs = listOf(Screen.FilterRules, Screen.SaveRules, Screen.Apps, Screen.Saved, Screen.Activity)
    var selected by remember { mutableStateOf<Screen>(Screen.FilterRules) }
    val appsState by vm.appsState.collectAsState()

    Scaffold(
        containerColor = NgColors.Bg,
        topBar = { TopBar() },
        bottomBar = { BottomBar(tabs, selected) { selected = it } }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(NgColors.Bg)) {
            when (selected) {
                Screen.FilterRules -> FilterRulesScreen(vm, appsState.installedApps)
                Screen.SaveRules   -> SaveRulesScreen(vm, appsState.installedApps)
                Screen.Apps        -> AppsScreen(vm)
                Screen.Saved       -> SavedScreen(vm)
                Screen.Activity    -> ActivityScreen(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar() {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(32.dp).background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(NgColors.Accent, Color(0xFF7B5FFF))),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    )
                ) { Text("🛡", fontSize = 16.sp) }
                Column {
                    Text("NotifGuard", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = NgColors.Text)
                    Text("Notification Filter", fontSize = 9.sp, color = NgColors.TextFaint, letterSpacing = 0.5.sp)
                }
            }
        },
        actions = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(end = 16.dp)) {
                Box(modifier = Modifier.size(7.dp).background(NgColors.Green, CircleShape))
                Text("ACTIVE", color = NgColors.Green, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = NgColors.Surface)
    )
}

@Composable
fun BottomBar(tabs: List<Screen>, selected: Screen, onSelect: (Screen) -> Unit) {
    NavigationBar(containerColor = NgColors.Surface) {
        tabs.forEach { tab ->
            val isSelected = selected == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = { Text(tab.icon, fontSize = 16.sp) },
                label = { Text(tab.label, fontSize = 9.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NgColors.Accent, selectedTextColor = NgColors.Accent,
                    unselectedIconColor = NgColors.TextMuted, unselectedTextColor = NgColors.TextMuted,
                    indicatorColor = NgColors.AccentSoft
                )
            )
        }
    }
}
