package com.notifguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.notifguard.AppInfo
import com.notifguard.MainViewModel
import com.notifguard.data.model.RuleAction
import com.notifguard.data.model.RuleGroup
import com.notifguard.data.model.ScheduleType
import com.notifguard.ui.components.NgSearchBar
import com.notifguard.ui.components.NgTag
import com.notifguard.ui.theme.NgColors
import androidx.compose.foundation.BorderStroke

@Composable
fun GroupsScreen(vm: MainViewModel, installedApps: List<AppInfo>) {
    val groupsState by vm.groupsState.collectAsState()
    var selectedGroup by remember { mutableStateOf<RuleGroup?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Group detail sub-screen
    if (selectedGroup != null) {
        val group = selectedGroup!!
        GroupDetailScreen(
            group = group,
            rulesInGroup = groupsState.rulesInGroup(group.id),
            derivedAction = groupsState.derivedAction(group.id),
            derivedEnabled = groupsState.derivedEnabled(group.id),
            allRules = groupsState.filterRules,
            onBack = { selectedGroup = null },
            onSetEnabled = { vm.setGroupEnabled(group.id, it) },
            onSetAction = { vm.setGroupAction(group.id, it) },
            onSetSchedule = { type, start, end, mins -> vm.setGroupSchedule(group.id, type, start, end, mins) },
            onRename = { vm.updateGroup(group.copy(name = it)); selectedGroup = group.copy(name = it) },
            onUngroup = { vm.ungroupRules(group.id); selectedGroup = null },
            onDelete = { vm.deleteGroup(group.id); selectedGroup = null },
            onAssignRule = { rule -> vm.assignRuleToGroup(rule, group.id) },
            onRemoveRule = { rule -> vm.assignRuleToGroup(rule, null) }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.padding(bottom = 14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Rule Groups", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = NgColors.Text)
            Button(onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = NgColors.Accent),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("New Group", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (groupsState.groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🗂", fontSize = 40.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("No groups yet", color = NgColors.TextMuted, fontSize = 15.sp)
                    Text("Groups let you mass-enable/disable or reschedule rules together.\nTap + to create one.",
                        color = NgColors.TextFaint, fontSize = 12.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                }
            }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(groupsState.groups, key = { it.id }) { group ->
                val memberCount = groupsState.rulesInGroup(group.id).size
                val derivedAction = groupsState.derivedAction(group.id)
                val derivedEnabled = groupsState.derivedEnabled(group.id)

                Row(modifier = Modifier.fillMaxWidth()
                    .background(NgColors.SurfaceHigh, RoundedCornerShape(10.dp))
                    .border(1.dp, NgColors.Border, RoundedCornerShape(10.dp))
                    .clickable { selectedGroup = group }
                    .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(group.name, color = NgColors.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (derivedAction != null) {
                                NgTag(if (derivedAction == RuleAction.BLOCK) "All Block" else "All Allow",
                                    if (derivedAction == RuleAction.BLOCK) NgColors.Red else NgColors.Green)
                            } else if (memberCount > 0) {
                                NgTag("Mixed", NgColors.Yellow)
                            }
                        }
                        Text("$memberCount rule${if (memberCount != 1) "s" else ""} · ${
                            when (group.scheduleType) {
                                ScheduleType.ALWAYS      -> "Always active"
                                ScheduleType.TIME_WINDOW -> "${group.scheduleWindowStart}–${group.scheduleWindowEnd}"
                                ScheduleType.TIMER       -> "Timer: ${group.timerMinutes}min"
                            }
                        }", color = NgColors.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        Text("Tap to manage", color = NgColors.TextFaint, fontSize = 9.sp, modifier = Modifier.padding(top = 1.dp))
                    }
                    Switch(
                        checked = derivedEnabled ?: false,
                        onCheckedChange = { vm.setGroupEnabled(group.id, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White, checkedTrackColor = NgColors.Green,
                            uncheckedThumbColor = NgColors.TextFaint, uncheckedTrackColor = NgColors.Border)
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        } // end LazyColumn
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name -> vm.addGroup(name); showCreateDialog = false }
        )
    }
}

@Composable
private fun GroupDetailScreen(
    group: RuleGroup,
    rulesInGroup: List<com.notifguard.data.model.FilterRule>,
    derivedAction: RuleAction?,
    derivedEnabled: Boolean?,
    allRules: List<com.notifguard.data.model.FilterRule>,
    onBack: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSetAction: (RuleAction) -> Unit,
    onSetSchedule: (ScheduleType, String, String, Int) -> Unit,
    onRename: (String) -> Unit,
    onUngroup: () -> Unit,
    onDelete: () -> Unit,
    onAssignRule: (com.notifguard.data.model.FilterRule) -> Unit,
    onRemoveRule: (com.notifguard.data.model.FilterRule) -> Unit
) {
    var name by remember { mutableStateOf(group.name) }
    var scheduleType by remember { mutableStateOf(group.scheduleType) }
    var windowStart by remember { mutableStateOf(group.scheduleWindowStart) }
    var windowEnd by remember { mutableStateOf(group.scheduleWindowEnd) }
    var timerMinutes by remember { mutableStateOf(group.timerMinutes.takeIf { it > 0 } ?: 30) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(modifier = Modifier.fillMaxWidth().background(NgColors.Surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = NgColors.TextMuted) }
            Text(group.name, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                color = NgColors.Text, modifier = Modifier.weight(1f))
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, "Delete group", tint = NgColors.Red)
            }
        }
        HorizontalDivider(color = NgColors.Border)

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {

            // Rename
            SectionLabel("Group Name")
            OutlinedTextField(value = name, onValueChange = { name = it },
                singleLine = true, colors = ngTextFieldColors(),
                shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { onRename(name) }, shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, NgColors.Border)) {
                Text("Rename", color = NgColors.TextMuted, fontSize = 12.sp)
            }

            // Mass enable/disable
            SectionLabel("Mass Enable / Disable")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onSetEnabled(true) }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NgColors.Green.copy(.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = NgColors.GreenSoft)) {
                    Text("Enable All", color = NgColors.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { onSetEnabled(false) }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NgColors.Red.copy(.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = NgColors.RedSoft)) {
                    Text("Disable All", color = NgColors.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Mass set action
            SectionLabel("Mass Set Action")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onSetAction(RuleAction.BLOCK) }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, NgColors.Red.copy(.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = NgColors.RedSoft)) {
                    Text("🚫 All Block", color = NgColors.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { onSetAction(RuleAction.WHITELIST) }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, NgColors.Green.copy(.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = NgColors.GreenSoft)) {
                    Text("✅ All Allow", color = NgColors.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Schedule
            SectionLabel("Group Schedule")
            ScheduleEditor(scheduleType, { scheduleType = it },
                windowStart, { windowStart = it }, windowEnd, { windowEnd = it },
                timerMinutes, { timerMinutes = it })
            Button(onClick = { onSetSchedule(scheduleType, windowStart, windowEnd, timerMinutes) },
                colors = ButtonDefaults.buttonColors(containerColor = NgColors.Accent),
                shape = RoundedCornerShape(8.dp)) {
                Text("Apply Schedule to All", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            // Rules in group
            SectionLabel("Rules in this group  (${rulesInGroup.size})")
            rulesInGroup.forEach { rule ->
                Row(modifier = Modifier.fillMaxWidth()
                    .background(NgColors.SurfaceHigh, RoundedCornerShape(8.dp))
                    .border(1.dp, NgColors.Border, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        NgTag(if (rule.action == RuleAction.BLOCK) "Block" else "Allow",
                            if (rule.action == RuleAction.BLOCK) NgColors.Red else NgColors.Green)
                        Spacer(Modifier.height(3.dp))
                        Text(if (rule.appPackage.isNotBlank()) rule.appPackage else "Any app",
                            color = NgColors.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        if (rule.note.isNotBlank()) Text(rule.note, color = NgColors.TextMuted, fontSize = 10.sp)
                    }
                    TextButton(onClick = { onRemoveRule(rule) }) {
                        Text("Remove", color = NgColors.Red, fontSize = 11.sp)
                    }
                }
            }

            // Add rules to group
            val ungroupedRules = allRules.filter { it.groupId == null }
            if (ungroupedRules.isNotEmpty()) {
                SectionLabel("Add ungrouped rules")
                ungroupedRules.forEach { rule ->
                    Row(modifier = Modifier.fillMaxWidth()
                        .background(NgColors.Bg, RoundedCornerShape(8.dp))
                        .border(1.dp, NgColors.Border, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (rule.appPackage.isNotBlank()) rule.appPackage else "Any app",
                                color = NgColors.TextMuted, fontSize = 12.sp)
                            if (rule.note.isNotBlank()) Text(rule.note, color = NgColors.TextFaint, fontSize = 10.sp)
                        }
                        TextButton(onClick = { onAssignRule(rule) }) {
                            Text("Add", color = NgColors.Accent, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Ungroup / Danger zone
            SectionLabel("Danger Zone")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onUngroup, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, NgColors.Yellow.copy(.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = NgColors.YellowSoft)) {
                    Text("Ungroup (keep rules)", color = NgColors.Yellow, fontSize = 12.sp)
                }
                OutlinedButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, NgColors.Red.copy(.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = NgColors.RedSoft)) {
                    Text("Delete group + rules", color = NgColors.Red, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete group?", color = NgColors.Text) },
            text = { Text("This will delete the group and all ${rulesInGroup.size} rules inside it.",
                color = NgColors.TextMuted) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = NgColors.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = NgColors.TextMuted) }
            },
            containerColor = NgColors.Surface
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), color = NgColors.TextMuted, fontSize = 10.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
}

@Composable
private fun CreateGroupDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()
            .background(NgColors.Surface, RoundedCornerShape(14.dp))
            .border(1.dp, NgColors.Border, RoundedCornerShape(14.dp))
            .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Create Group", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NgColors.Text)
            OutlinedTextField(value = name, onValueChange = { name = it },
                placeholder = { Text("Group name…", color = NgColors.TextFaint) },
                singleLine = true, colors = ngTextFieldColors(),
                shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, NgColors.Border)) {
                    Text("Cancel", color = NgColors.TextMuted)
                }
                Button(onClick = { if (name.isNotBlank()) onCreate(name) },
                    enabled = name.isNotBlank(), modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = NgColors.Accent),
                    shape = RoundedCornerShape(10.dp)) {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}