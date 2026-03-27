package com.notifguard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import com.notifguard.MainViewModel
import com.notifguard.R
import com.notifguard.data.model.NotifHistory
import com.notifguard.data.model.SavedNotification
import com.notifguard.ui.components.NgSearchBar
import com.notifguard.ui.theme.NgColors
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SavedScreen(vm: MainViewModel) {
    val state by vm.savedState.collectAsState()
    val context = LocalContext.current

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
        Column(modifier = Modifier.width(140.dp).fillMaxHeight()
            .background(NgColors.Surface).padding(8.dp)) {
            NgSearchBar(state.appSearchQuery, vm::setAppSearch, "Search…",
                modifier = Modifier.padding(bottom = 8.dp))
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
            // Toolbar
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 10.dp)) {
                NgSearchBar(state.searchQuery, vm::setSavedSearch, stringResource(R.string.search_notifications), Modifier.weight(1f))
                if (state.selectedIds.isNotEmpty()) {
                    IconButton(onClick = {
                        val text = vm.exportSelected(context)
                        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                        context.startActivity(Intent.createChooser(intent, "Export notifications"))
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
                Text("${state.selectedIds.size} selected — tap to deselect",
                    color = NgColors.Accent, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.filteredNotifications, key = { it.id }) { notif ->
                    NotifThreadCard(
                        notif = notif,
                        isSelected = notif.id in state.selectedIds,
                        daysLeft = state.daysLeft(notif),
                        getHistory = { vm.getHistory(notif.id) },
                        onTap = { vm.toggleSelectNotif(notif.id) }
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
                    Text("Auto-deletes after 30 days · Tap to select · Long-press to expand history",
                        color = NgColors.TextFaint, fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 80.dp))
                }
            }
        }
    }
}

@Composable
private fun NotifThreadCard(
    notif: SavedNotification,
    isSelected: Boolean,
    daysLeft: Int,
    getHistory: () -> kotlinx.coroutines.flow.Flow<List<NotifHistory>>,
    onTap: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val expiringSoon = daysLeft <= 5
    var expanded by remember { mutableStateOf(false) }
    val history by getHistory().collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxWidth()
        .background(if (isSelected) NgColors.AccentSoft else NgColors.SurfaceHigh, RoundedCornerShape(10.dp))
        .border(1.dp, if (isSelected) NgColors.Accent else NgColors.Border, RoundedCornerShape(10.dp))) {

        // ── Latest content ─────────────────────────────────────────────
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

                // Show history toggle if there are previous versions
                if (notif.updateCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.clickable { expanded = !expanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (expanded) "▲ Hide history" else "▼ ${notif.updateCount} earlier version${if (notif.updateCount > 1) "s" else ""}",
                            color = NgColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ── History (expandable) ───────────────────────────────────────
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(modifier = Modifier.fillMaxWidth()
                .background(NgColors.Bg, RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HorizontalDivider(color = NgColors.Border, modifier = Modifier.padding(bottom = 4.dp))
                Text("HISTORY", color = NgColors.TextFaint, fontSize = 9.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                history.forEach { entry ->
                    Column(modifier = Modifier.fillMaxWidth()
                        .background(NgColors.SurfaceHigh, RoundedCornerShape(8.dp))
                        .border(1.dp, NgColors.Border, RoundedCornerShape(8.dp))
                        .padding(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween) {
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
