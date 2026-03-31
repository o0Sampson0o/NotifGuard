package com.notifguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notifguard.AppInfo
import com.notifguard.data.model.FilterRule
import com.notifguard.data.model.RuleAction
import com.notifguard.data.model.RuleGroup
import com.notifguard.data.model.ScheduleType
import com.notifguard.ui.components.NgSearchBar
import com.notifguard.ui.theme.NgColors
import androidx.compose.foundation.BorderStroke

@Composable
fun RuleDetailScreen(
    rule: FilterRule,
    installedApps: List<AppInfo>,
    groups: List<RuleGroup>,
    onBack: () -> Unit,
    onSave: (FilterRule) -> Unit,
    onDelete: (FilterRule) -> Unit
) {
    var action by remember { mutableStateOf(rule.action) }
    var selectedApp by remember { mutableStateOf(rule.appPackage) }
    var regexPattern by remember { mutableStateOf(rule.regexPattern) }
    var regexFlags by remember { mutableStateOf(rule.regexFlags) }
    var note by remember { mutableStateOf(rule.note) }
    var groupId by remember { mutableStateOf(rule.groupId) }
    var customSoundUri by remember { mutableStateOf(rule.customSoundUri) }
    var scheduleType by remember { mutableStateOf(rule.scheduleType) }
    var windowStart by remember { mutableStateOf(rule.scheduleWindowStart) }
    var windowEnd by remember { mutableStateOf(rule.scheduleWindowEnd) }
    var timerMinutes by remember { mutableStateOf(rule.timerMinutes.takeIf { it > 0 } ?: 30) }
    var appSearch by remember { mutableStateOf("") }

    val filteredApps = remember(appSearch) {
        installedApps.filter { appSearch.isBlank() || it.appName.contains(appSearch, true) || it.packageName.contains(appSearch, true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(NgColors.Surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = NgColors.TextMuted)
            }
            Text("Edit Rule", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                color = NgColors.Text, modifier = Modifier.weight(1f))
            IconButton(onClick = { onDelete(rule) }) {
                Icon(Icons.Default.Delete, "Delete", tint = NgColors.Red)
            }
        }
        HorizontalDivider(color = NgColors.Border)

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {

            // ── Action ─────────────────────────────────────────────────
            SectionHeader("Action")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(RuleAction.BLOCK to "🚫  Block", RuleAction.WHITELIST to "✅  Allow").forEach { (a, lbl) ->
                    val sel = action == a
                    val c = if (a == RuleAction.BLOCK) NgColors.Red else NgColors.Green
                    OutlinedButton(onClick = { action = a }, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (sel) c.copy(.15f) else Color.Transparent),
                        border = BorderStroke(1.dp, if (sel) c else NgColors.Border),
                        contentPadding = PaddingValues(vertical = 8.dp)) {
                        Text(lbl, fontSize = 12.sp, color = if (sel) NgColors.Text else NgColors.TextMuted)
                    }
                }
            }

            // ── App ────────────────────────────────────────────────────
            SectionHeader("App  (blank = any app)")
            NgSearchBar(appSearch, { appSearch = it }, "Search apps…")
            Spacer(Modifier.height(2.dp))
            AppPickerList(filteredApps, selectedApp) { selectedApp = it }

            // ── Note ───────────────────────────────────────────────────
            SectionHeader("Note")
            OutlinedTextField(value = note, onValueChange = { note = it },
                placeholder = { Text("Describe this rule…", color = NgColors.TextFaint, fontSize = 13.sp) },
                singleLine = true, colors = ngTextFieldColors(),
                shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())

            // ── Regex ──────────────────────────────────────────────────
            SectionHeader("Regex Pattern & Flags")
            RegexInputWithFlags(
                pattern = regexPattern, onPatternChange = { regexPattern = it },
                flags = regexFlags, onFlagsChange = { regexFlags = it }
            )

            // ── Custom Sound ───────────────────────────────────────────
            SectionHeader("Custom Sound  (on Allow match only)")
            SoundPicker(customSoundUri) { customSoundUri = it }

            // ── Schedule ───────────────────────────────────────────────
            SectionHeader("Schedule")
            ScheduleEditor(
                scheduleType = scheduleType, onTypeChange = { scheduleType = it },
                windowStart = windowStart, onStartChange = { windowStart = it },
                windowEnd = windowEnd, onEndChange = { windowEnd = it },
                timerMinutes = timerMinutes, onTimerChange = { timerMinutes = it }
            )

            // ── Group assignment ───────────────────────────────────────
            SectionHeader("Group  (optional)")
            if (groups.isEmpty()) {
                Text("No groups yet. Create groups in the Groups tab.",
                    color = NgColors.TextFaint, fontSize = 12.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // None option
                    GroupPill(label = "No group", selected = groupId == null) { groupId = null }
                    groups.forEach { group ->
                        GroupPill(label = group.name, selected = groupId == group.id) { groupId = group.id }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        // Save button
        HorizontalDivider(color = NgColors.Border)
        Button(
            onClick = {
                val expires = if (scheduleType == ScheduleType.TIMER)
                    System.currentTimeMillis() + timerMinutes * 60_000L else rule.timerExpiresAt
                onSave(rule.copy(
                    action = action, appPackage = selectedApp,
                    regexPattern = regexPattern, regexFlags = regexFlags,
                    note = note, groupId = groupId, customSoundUri = customSoundUri,
                    scheduleType = scheduleType, scheduleWindowStart = windowStart,
                    scheduleWindowEnd = windowEnd, timerMinutes = timerMinutes,
                    timerExpiresAt = expires
                ))
                onBack()
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NgColors.Accent),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Save Changes", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text.uppercase(), color = NgColors.TextMuted, fontSize = 10.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
}

@Composable
private fun GroupPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()
        .background(if (selected) NgColors.AccentSoft else NgColors.Bg, RoundedCornerShape(8.dp))
        .border(1.dp, if (selected) NgColors.Accent else NgColors.Border, RoundedCornerShape(8.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label, color = if (selected) NgColors.Text else NgColors.TextMuted,
            fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}