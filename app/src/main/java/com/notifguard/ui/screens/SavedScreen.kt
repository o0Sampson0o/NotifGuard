package com.notifguard.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.notifguard.AppInfo
import com.notifguard.MainViewModel
import com.notifguard.R
import com.notifguard.data.model.NotifHistory
import com.notifguard.data.model.RuleAction
import com.notifguard.data.model.SavedNotification
import com.notifguard.ui.components.NgSearchBar
import com.notifguard.ui.theme.NgColors
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SavedScreen(vm: MainViewModel, installedApps: List<AppInfo>) {
    val state by vm.savedState.collectAsState()
    val context = LocalContext.current
    var quickAddForPackage by remember { mutableStateOf<String?>(null) }

    if (state.packages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📭", fontSize = 48.sp)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.no_notifications), color = NgColors.TextMuted, fontSize = 15.sp)
                Text("Saved notifications appear here automatically.",
                    color = NgColors.TextFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        return
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // ── App sidebar ────────────────────────────────────────────────
        Column(modifier = Modifier.width(140.dp).fillMaxHeight().background(NgColors.Surface).padding(8.dp)) {
            NgSearchBar(state.appSearchQuery, vm::setAppSearch, "Search…", modifier = Modifier.padding(bottom = 8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(state.filteredPackages) { pkg ->
                    val appName = state.appNameMap[pkg] ?: pkg
                    val isSelected = state.selectedPackage == pkg
                    Box(modifier = Modifier.fillMaxWidth()
                        .background(if (isSelected) NgColors.AccentSoft else Color.Transparent, RoundedCornerShape(8.dp))
                        .border(1.dp, if (isSelected) NgColors.Accent.copy(.5f) else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { vm.selectPackage(pkg) }
                        .padding(horizontal = 8.dp, vertical = 8.dp)) {
                        Text(appName, color = if (isSelected) NgColors.Text else NgColors.TextMuted,
                            fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, maxLines = 2)
                    }
                }
            }
        }

        // ── Notification list ──────────────────────────────────────────
        Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 10.dp)) {
                NgSearchBar(state.searchQuery, vm::setSavedSearch, stringResource(R.string.search_notifications), Modifier.weight(1f))
                if (state.selectedIds.isNotEmpty()) {
                    IconButton(onClick = {
                        val text = vm.exportSelected(context)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                        }, "Export notifications"))
                    }, modifier = Modifier.size(36.dp).background(NgColors.GreenSoft, RoundedCornerShape(8.dp))
                        .border(1.dp, NgColors.Green.copy(.4f), RoundedCornerShape(8.dp))) {
                        Icon(Icons.Default.Share, null, tint = NgColors.Green, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { vm.deleteSelected() },
                        modifier = Modifier.size(36.dp).background(NgColors.RedSoft, RoundedCornerShape(8.dp))
                            .border(1.dp, NgColors.Red.copy(.4f), RoundedCornerShape(8.dp))) {
                        Icon(Icons.Default.Delete, null, tint = NgColors.Red, modifier = Modifier.size(16.dp))
                    }
                }
            }
            if (state.selectedIds.isNotEmpty()) {
                Text("${state.selectedIds.size} selected", color = NgColors.Accent, fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 6.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.filteredNotifications, key = { it.id }) { notif ->
                    NotifThreadCard(
                        notif = notif,
                        isSelected = notif.id in state.selectedIds,
                        daysLeft = state.daysLeft(notif),
                        getHistory = { vm.getHistory(notif.id) },
                        onTap = { vm.toggleSelectNotif(notif.id) },
                        onAddRule = { quickAddForPackage = notif.packageName }
                    )
                }
                if (state.filteredNotifications.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.no_notifications), color = NgColors.TextFaint, fontSize = 13.sp)
                        }
                    }
                }
                item {
                    Text("Auto-deletes after 30 days · Tap to select",
                        color = NgColors.TextFaint, fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 80.dp))
                }
            }
        }
    }

    // Quick-add rule dialog triggered from notification card
    quickAddForPackage?.let { pkg ->
        val appName = state.appNameMap[pkg] ?: pkg
        QuickAddFromNotifDialog(
            packageName = pkg,
            appName = appName,
            installedApps = installedApps,
            onDismiss = { quickAddForPackage = null },
            onConfirm = { action, app, note ->
                vm.addFilterRule(action, app, note = note)
                quickAddForPackage = null
            }
        )
    }
}

@Composable
private fun NotifThreadCard(
    notif: SavedNotification,
    isSelected: Boolean,
    daysLeft: Int,
    getHistory: () -> kotlinx.coroutines.flow.Flow<List<NotifHistory>>,
    onTap: () -> Unit,
    onAddRule: () -> Unit
) {
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val expiringSoon = daysLeft <= 5
    var expanded by remember { mutableStateOf(false) }
    val history by getHistory().collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxWidth()
        .background(if (isSelected) NgColors.AccentSoft else NgColors.SurfaceHigh, RoundedCornerShape(10.dp))
        .border(1.dp, if (isSelected) NgColors.Accent else NgColors.Border, RoundedCornerShape(10.dp))) {

        Box(modifier = Modifier.fillMaxWidth().clickable(onClick = onTap).padding(12.dp)) {
            Column {
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Text(notif.title, color = NgColors.Text, fontWeight = FontWeight.Bold,
                        fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(sdf.format(Date(notif.latestAt)), color = NgColors.TextFaint, fontSize = 10.sp)
                        Text(if (expiringSoon) "⚠ ${daysLeft}d" else "${daysLeft}d",
                            color = if (expiringSoon) NgColors.Yellow else NgColors.TextFaint, fontSize = 10.sp,
                            fontWeight = if (expiringSoon) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(notif.body, color = NgColors.TextMuted, fontSize = 13.sp, lineHeight = 18.sp)

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Open button
                    Box(modifier = Modifier
                        .background(NgColors.AccentSoft, RoundedCornerShape(6.dp))
                        .border(1.dp, NgColors.Accent.copy(.4f), RoundedCornerShape(6.dp))
                        .pointerInput(notif.id) {
                            detectTapGestures(onTap = {
                                runCatching {
                                    val intent = if (notif.intentUri != null)
                                        Intent.parseUri(notif.intentUri, Intent.URI_INTENT_SCHEME)
                                    else context.packageManager.getLaunchIntentForPackage(notif.packageName)
                                    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    intent?.let { context.startActivity(it) }
                                }
                            })
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("↗ Open", color = NgColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    // Add Rule button
                    Box(modifier = Modifier
                        .background(NgColors.RedSoft, RoundedCornerShape(6.dp))
                        .border(1.dp, NgColors.Red.copy(.4f), RoundedCornerShape(6.dp))
                        .pointerInput(notif.packageName) {
                            detectTapGestures(onTap = { onAddRule() })
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("+ Block Rule", color = NgColors.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    // History toggle
                    if (notif.updateCount > 0) {
                        Text(
                            if (expanded) "▲ Hide" else "▼ ${notif.updateCount} prev",
                            color = NgColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { expanded = !expanded }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(modifier = Modifier.fillMaxWidth()
                .background(NgColors.Bg, RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HorizontalDivider(color = NgColors.Border, modifier = Modifier.padding(bottom = 4.dp))
                Text("HISTORY", color = NgColors.TextFaint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                history.forEach { entry ->
                    Column(modifier = Modifier.fillMaxWidth()
                        .background(NgColors.SurfaceHigh, RoundedCornerShape(8.dp))
                        .border(1.dp, NgColors.Border, RoundedCornerShape(8.dp)).padding(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(entry.title, color = NgColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(sdf.format(Date(entry.recordedAt)), color = NgColors.TextFaint, fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(entry.body, color = NgColors.TextFaint, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAddFromNotifDialog(
    packageName: String,
    appName: String,
    installedApps: List<AppInfo>,
    onDismiss: () -> Unit,
    onConfirm: (RuleAction, String, String) -> Unit
) {
    var note by remember { mutableStateOf("") }
    var action by remember { mutableStateOf(RuleAction.BLOCK) }

    Dialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()
            .background(NgColors.Surface, RoundedCornerShape(14.dp))
            .border(1.dp, NgColors.Border, RoundedCornerShape(14.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Add Rule", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NgColors.Text)
            // Show which app is pre-selected
            Row(modifier = Modifier.fillMaxWidth()
                .background(NgColors.AccentSoft, RoundedCornerShape(8.dp))
                .border(1.dp, NgColors.Accent.copy(.3f), RoundedCornerShape(8.dp))
                .padding(10.dp)) {
                Text("📱 $appName", color = NgColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            // Action
            RuleDialogLabel("Action")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(RuleAction.BLOCK to "🚫  Block", RuleAction.WHITELIST to "✅  Allow").forEach { (a, lbl) ->
                    val sel = action == a
                    val c = if (a == RuleAction.BLOCK) NgColors.Red else NgColors.Green
                    OutlinedButton(onClick = { action = a }, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (sel) c.copy(.15f) else Color.Transparent),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (sel) c else NgColors.Border),
                        contentPadding = PaddingValues(vertical = 8.dp)) {
                        Text(lbl, fontSize = 12.sp, color = if (sel) NgColors.Text else NgColors.TextMuted)
                    }
                }
            }
            // Note
            RuleDialogLabel("Note (optional)")
            OutlinedTextField(value = note, onValueChange = { note = it },
                placeholder = { Text("Describe this rule…", color = NgColors.TextFaint, fontSize = 13.sp) },
                singleLine = true, colors = ngTextFieldColors(),
                shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
            // Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NgColors.Border)) {
                    Text("Cancel", color = NgColors.TextMuted)
                }
                Button(onClick = { onConfirm(action, packageName, note) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NgColors.Accent),
                    shape = RoundedCornerShape(10.dp)) {
                    Text("Add Rule", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}