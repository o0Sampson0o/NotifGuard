package com.notifguard.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.notifguard.data.model.SaveRule
import com.notifguard.data.model.SaveRuleAction
import com.notifguard.ui.components.*
import com.notifguard.ui.theme.NgColors

@Composable
fun SaveRulesScreen(vm: MainViewModel, installedApps: List<AppInfo>) {
    val state by vm.saveRulesState.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        InfoBanner(
            text = "💾  Controls what gets saved. Default = save everything. First match wins.\nSKIP = don't save.  SAVE = force save (overrides a SKIP above).",
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 14.dp)) {
            NgSearchBar(state.searchQuery, vm::setSaveRulesSearch, stringResource(R.string.search_rules), Modifier.weight(1f))
            Button(onClick = { showAdd = true },
                colors = ButtonDefaults.buttonColors(containerColor = NgColors.Accent),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.add_rule), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (state.filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Text("No rules yet. All allowed notifications are saved by default.\nTap + to add a rule.",
                            color = NgColors.TextFaint, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            itemsIndexed(state.filtered, key = { _, r -> r.id }) { idx, rule ->
                SaveRuleRow(rule, idx, idx == 0, idx == state.filtered.lastIndex, installedApps,
                    { vm.moveSaveRule(state.rules, state.rules.indexOf(rule), -1) },
                    { vm.moveSaveRule(state.rules, state.rules.indexOf(rule), +1) },
                    { vm.toggleSaveRule(rule) }, { vm.deleteSaveRule(rule.id) })
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAdd) {
        SaveRuleDialog(installedApps, { showAdd = false }) { action, app, regex, note ->
            vm.addSaveRule(action, app, regex, note)
            showAdd = false
        }
    }
}

@Composable
private fun SaveRuleRow(
    rule: SaveRule, index: Int, isFirst: Boolean, isLast: Boolean,
    installedApps: List<AppInfo>,
    onMoveUp: () -> Unit, onMoveDown: () -> Unit,
    onToggle: () -> Unit, onDelete: () -> Unit
) {
    val accent = if (rule.action == SaveRuleAction.SKIP) NgColors.Red else NgColors.Green
    val appName = installedApps.find { it.packageName == rule.appPackage }?.appName

    NgCard(borderColor = if (rule.enabled) accent.copy(alpha = 0.4f) else NgColors.Border) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(contentAlignment = Alignment.Center,
                modifier = Modifier.size(24.dp).background(NgColors.Border, RoundedCornerShape(5.dp))) {
                Text("${index + 1}", color = NgColors.TextMuted, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
            }
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
            Column(modifier = Modifier.weight(1f)) {
                NgTag(if (rule.action == SaveRuleAction.SKIP) "Skip" else "Save", accent)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("📱", fontSize = 11.sp)
                    Text(if (rule.appPackage.isNotBlank()) appName ?: rule.appPackage else "Any app",
                        color = if (rule.appPackage.isNotBlank()) NgColors.Text else NgColors.TextFaint,
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                    Text("🔤", fontSize = 11.sp)
                    Text(if (rule.regexPattern.isNotBlank()) rule.regexPattern else "Any content",
                        color = if (rule.regexPattern.isNotBlank()) NgColors.TextMuted else NgColors.TextFaint,
                        fontSize = 11.sp,
                        fontFamily = if (rule.regexPattern.isNotBlank()) FontFamily.Monospace else FontFamily.Default,
                        maxLines = 1)
                }
                if (rule.note.isNotBlank()) Text(rule.note, color = NgColors.TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Switch(checked = rule.enabled, onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = NgColors.Green,
                    uncheckedThumbColor = NgColors.TextFaint, uncheckedTrackColor = NgColors.Border),
                modifier = Modifier.height(24.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, null, tint = NgColors.TextFaint, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun SaveRuleDialog(
    installedApps: List<AppInfo>, onDismiss: () -> Unit,
    onConfirm: (SaveRuleAction, String, String, String) -> Unit
) {
    var action by remember { mutableStateOf(SaveRuleAction.SKIP) }
    var selectedApp by remember { mutableStateOf("") }
    var regex by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var appSearch by remember { mutableStateOf("") }
    val isValid = selectedApp.isNotBlank() || regex.isNotBlank()
    val filteredApps = remember(appSearch) {
        installedApps.filter { appSearch.isBlank() || it.appName.contains(appSearch, true) || it.packageName.contains(appSearch, true) }
    }
    val regexError = remember(regex) { if (regex.isBlank()) null else runCatching { Regex(regex); null }.getOrElse { it.message } }

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
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Action
                RuleDialogLabel("Action")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(SaveRuleAction.SKIP to "🚫  Skip", SaveRuleAction.SAVE to "💾  Save").forEach { (a, lbl) ->
                        val sel = action == a
                        val c = if (a == SaveRuleAction.SKIP) NgColors.Red else NgColors.Green
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
                // Regex
                RuleDialogLabel("Regex pattern  (blank = any content)")
                OutlinedTextField(value = regex, onValueChange = { regex = it },
                    placeholder = { Text("(?i)(promo|sale|限时)", color = NgColors.TextFaint, fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    colors = ngTextFieldColors(), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                if (regexError != null) Text("⚠ $regexError", color = NgColors.Red, fontSize = 10.sp)
                // Note
                RuleDialogLabel(stringResource(R.string.note_optional))
                OutlinedTextField(value = note, onValueChange = { note = it },
                    placeholder = { Text("Describe this rule…", color = NgColors.TextFaint, fontSize = 13.sp) },
                    singleLine = true, colors = ngTextFieldColors(), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                // Preview
                if (isValid) RuleSummary(
                    actionLabel = if (action == SaveRuleAction.SKIP) "Skip saving" else "Force save",
                    appLabel = if (selectedApp.isBlank()) "any app" else installedApps.find { it.packageName == selectedApp }?.appName ?: selectedApp,
                    contentLabel = if (regex.isBlank()) "any content" else "\"$regex\""
                )
                // Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, NgColors.Border)) { Text(stringResource(R.string.cancel), color = NgColors.TextMuted) }
                    Button(onClick = { if (isValid) onConfirm(action, selectedApp, regex, note) },
                        enabled = isValid, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NgColors.Accent), shape = RoundedCornerShape(10.dp)) {
                        Text(stringResource(R.string.add_rule), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
