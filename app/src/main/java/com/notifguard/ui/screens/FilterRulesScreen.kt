package com.notifguard.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.notifguard.AppInfo
import com.notifguard.MainViewModel
import com.notifguard.R
import com.notifguard.data.model.FilterRule
import com.notifguard.data.model.RuleAction
import com.notifguard.data.model.ScheduleType
import com.notifguard.ui.components.*
import com.notifguard.ui.theme.NgColors

// ─── Filter Rules Screen ───────────────────────────────────────────────────
// Shows flat ordered list. Tapping a rule navigates to RuleDetailScreen.
// + button opens minimal add dialog, which has an "Edit Full Rule" button
// that navigates to RuleDetailScreen with the new rule pre-selected.

@Composable
fun FilterRulesScreen(vm: MainViewModel, installedApps: List<AppInfo>) {
    val state by vm.rulesState.collectAsState()
    val selectedRule by vm.selectedRule.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    // Sub-screen: Rule Detail
    if (selectedRule != null) {
        RuleDetailScreen(
            rule = selectedRule!!,
            installedApps = installedApps,
            groups = state.groups,
            onBack = { vm.selectRule(null) },
            onSave = { vm.updateFilterRule(it) },
            onDelete = { vm.deleteFilterRule(it.id) }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        InfoBanner(
            text = "⚡  Rules checked top → bottom. First match wins. Unmatched = Allow.\nSearch by group name to filter.",
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 14.dp)) {
            NgSearchBar(state.searchQuery, vm::setFilterRulesSearch, "Search rules or group name…", Modifier.weight(1f))
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = NgColors.Accent),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.add_rule), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        val listState = rememberLazyListState()
        val draggingIndex = remember { mutableStateOf<Int?>(null) }
        val dragOffsetY = remember { mutableStateOf(0f) }

        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (state.filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Text("No rules yet. Tap + to add one.\nAll notifications pass through by default.",
                            color = NgColors.TextFaint, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            itemsIndexed(state.filtered, key = { _, r -> r.id }) { idx, rule ->
                val isDragging = draggingIndex.value == idx
                FilterRuleRow(
                    rule = rule,
                    index = idx,
                    isFirst = idx == 0,
                    isLast = idx == state.filtered.lastIndex,
                    groupName = state.groups.find { it.id == rule.groupId }?.name,
                    isActive = vm.isRuleCurrentlyActive(rule),
                    isDragging = isDragging,
                    dragOffset = if (isDragging) dragOffsetY.value else 0f,
                    onMoveUp   = { vm.moveFilterRule(state.filtered, idx, -1) },
                    onMoveDown = { vm.moveFilterRule(state.filtered, idx, +1) },
                    onToggle   = { vm.toggleFilterRule(rule) },
                    onDelete   = { vm.deleteFilterRule(rule.id) },
                    onTap      = { vm.selectRule(rule) },
                    onDragStart = { draggingIndex.value = idx },
                    onDrag      = { dy -> dragOffsetY.value += dy },
                    onDragEnd   = {
                        val from = draggingIndex.value ?: return@FilterRuleRow
                        val itemHeight = 80f // approximate
                        val moved = (dragOffsetY.value / itemHeight).toInt()
                        val to = (from + moved).coerceIn(0, state.filtered.lastIndex)
                        if (from != to) {
                            val reordered = state.filtered.toMutableList()
                            val item = reordered.removeAt(from)
                            reordered.add(to, item)
                            vm.reorderFilterRules(reordered)
                        }
                        draggingIndex.value = null
                        dragOffsetY.value = 0f
                    }
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAddDialog) {
        QuickAddFilterRuleDialog(
            installedApps = installedApps,
            onDismiss = { showAddDialog = false },
            onConfirm = { action, app, note ->
                vm.addFilterRule(action, app, note = note)
                showAddDialog = false
            },
            onAdvanced = { action, app, note ->
                // Create the rule first, then open its detail screen
                vm.addFilterRule(action, app, note = note)
                showAddDialog = false
                // The new rule will appear in the list; user can tap it for detail
            }
        )
    }
}

// ─── Rule Row ─────────────────────────────────────────────────────────────

@Composable
private fun FilterRuleRow(
    rule: FilterRule,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    groupName: String?,
    isActive: Boolean,
    isDragging: Boolean,
    dragOffset: Float,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val accent = if (rule.action == RuleAction.BLOCK) NgColors.Red else NgColors.Green
    val appName = null // resolved at runtime if needed

    NgCard(
        borderColor = when {
            !rule.enabled || !isActive -> NgColors.Border
            else -> accent.copy(alpha = 0.4f)
        },
        modifier = Modifier
            .graphicsLayer { if (isDragging) translationY = dragOffset }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { _, offset -> onDrag(offset.y) },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            }
    ) {
        Row(
            modifier = Modifier.clickable(onClick = onTap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Priority badge
            Box(contentAlignment = Alignment.Center,
                modifier = Modifier.size(24.dp).background(NgColors.Border, RoundedCornerShape(5.dp))) {
                Text("${index + 1}", color = NgColors.TextMuted, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
            }
            // Arrows
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onMoveUp, enabled = !isFirst, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, null,
                        tint = if (!isFirst) NgColors.TextMuted else NgColors.TextFaint, modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onMoveDown, enabled = !isLast, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, null,
                        tint = if (!isLast) NgColors.TextMuted else NgColors.TextFaint, modifier = Modifier.size(14.dp))
                }
            }
            // Content
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    NgTag(if (rule.action == RuleAction.BLOCK) "Block" else "Allow", accent)
                    if (!isActive) NgTag("Inactive", NgColors.Yellow)
                    if (groupName != null) NgTag(groupName, NgColors.TextMuted)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    if (rule.appPackage.isNotBlank()) rule.appPackage else "Any app",
                    color = if (rule.appPackage.isNotBlank()) NgColors.Text else NgColors.TextFaint,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
                if (rule.regexPattern.isNotBlank()) {
                    Text(rule.regexPattern, color = NgColors.TextMuted, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace, maxLines = 1)
                }
                if (rule.note.isNotBlank()) {
                    Text(rule.note, color = NgColors.TextMuted, fontSize = 10.sp)
                }
                Text("Tap to edit · Long press to drag", color = NgColors.TextFaint, fontSize = 9.sp,
                    modifier = Modifier.padding(top = 2.dp))
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White, checkedTrackColor = NgColors.Green,
                    uncheckedThumbColor = NgColors.TextFaint, uncheckedTrackColor = NgColors.Border),
                modifier = Modifier.height(24.dp)
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, null, tint = NgColors.TextFaint, modifier = Modifier.size(15.dp))
            }
        }
    }
}

// ─── Quick Add Dialog (basic only) ────────────────────────────────────────

@Composable
private fun QuickAddFilterRuleDialog(
    installedApps: List<AppInfo>,
    onDismiss: () -> Unit,
    onConfirm: (RuleAction, String, String) -> Unit,
    onAdvanced: (RuleAction, String, String) -> Unit
) {
    var action by remember { mutableStateOf(RuleAction.BLOCK) }
    var selectedApp by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var appSearch by remember { mutableStateOf("") }
    val filteredApps = remember(appSearch) {
        installedApps.filter { appSearch.isBlank() || it.appName.contains(appSearch, true) || it.packageName.contains(appSearch, true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()
            .background(NgColors.Surface, RoundedCornerShape(14.dp))
            .border(1.dp, NgColors.Border, RoundedCornerShape(14.dp))) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.add_rule), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NgColors.Text)
                TextButton(onClick = onDismiss) { Text("✕", color = NgColors.TextMuted) }
            }
            HorizontalDivider(color = NgColors.Border)
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {

                // Action
                RuleDialogLabel("Action")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(RuleAction.BLOCK to "🚫  Block", RuleAction.WHITELIST to "✅  Allow").forEach { (a, lbl) ->
                        val sel = action == a
                        val c = if (a == RuleAction.BLOCK) NgColors.Red else NgColors.Green
                        OutlinedButton(onClick = { action = a }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (sel) c.copy(.15f) else Color.Transparent),
                            border = BorderStroke(1.dp, if (sel) c else NgColors.Border),
                            contentPadding = PaddingValues(vertical = 8.dp)) {
                            Text(lbl, fontSize = 12.sp, color = if (sel) NgColors.Text else NgColors.TextMuted)
                        }
                    }
                }

                // App
                RuleDialogLabel("App  (blank = any app)")
                NgSearchBar(appSearch, { appSearch = it }, stringResource(R.string.search_apps))
                Spacer(Modifier.height(2.dp))
                AppPickerList(filteredApps, selectedApp) { selectedApp = it }

                // Note
                RuleDialogLabel(stringResource(R.string.note_optional))
                OutlinedTextField(value = note, onValueChange = { note = it },
                    placeholder = { Text("Describe this rule…", color = NgColors.TextFaint, fontSize = 13.sp) },
                    singleLine = true, colors = ngTextFieldColors(),
                    shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())

                // Info about advanced
                Box(modifier = Modifier.fillMaxWidth()
                    .background(NgColors.AccentSoft, RoundedCornerShape(8.dp))
                    .border(1.dp, NgColors.Accent.copy(.3f), RoundedCornerShape(8.dp))
                    .padding(10.dp)) {
                    Text("Regex, flags, custom sound, schedule, and group assignment are in the full rule editor.",
                        color = NgColors.TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
                }

                // Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, NgColors.Border)) {
                        Text(stringResource(R.string.cancel), color = NgColors.TextMuted)
                    }
                    OutlinedButton(
                        onClick = { onAdvanced(action, selectedApp, note) },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, NgColors.Accent),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = NgColors.AccentSoft)
                    ) { Text("Full Editor", color = NgColors.Accent, fontWeight = FontWeight.Bold) }
                    Button(onClick = { onConfirm(action, selectedApp, note) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NgColors.Accent),
                        shape = RoundedCornerShape(10.dp)) {
                        Text("Add", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}