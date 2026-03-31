package com.notifguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notifguard.MainViewModel
import com.notifguard.R
import com.notifguard.ui.components.InfoBanner
import com.notifguard.ui.components.NgSearchBar
import com.notifguard.ui.theme.NgColors

// AppsScreen is now read-only — a reference list of installed apps.
// Blocking is done via Rules (add a BLOCK rule for an app in the Rules tab).
@Composable
fun AppsScreen(vm: MainViewModel) {
    val state by vm.appsState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        InfoBanner(
            text = "Browse installed apps. To block or allow an app, add a rule in the Rules tab.",
            modifier = Modifier.padding(bottom = 12.dp),
        )

        NgSearchBar(
            value = state.searchQuery,
            onValueChange = vm::setAppsSearch,
            placeholder = stringResource(R.string.search_apps),
            modifier = Modifier.padding(bottom = 14.dp),
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.filtered, key = { it.packageName }) { app ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(NgColors.SurfaceHigh, RoundedCornerShape(10.dp))
                            .border(1.dp, NgColors.Border, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.appName, color = NgColors.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(app.packageName, color = NgColors.TextFaint, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            if (state.filtered.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Text("No apps found", color = NgColors.TextFaint, fontSize = 14.sp)
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
