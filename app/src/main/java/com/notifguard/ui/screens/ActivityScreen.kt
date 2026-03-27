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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notifguard.MainViewModel
import com.notifguard.data.model.LogEntry
import com.notifguard.data.model.RuleAction
import com.notifguard.data.model.SaveRuleAction
import com.notifguard.ui.components.NgTag
import com.notifguard.ui.theme.NgColors
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ActivityScreen(vm: MainViewModel) {
    val entries by vm.logEntries.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Last 300 entries · 7 day history · Tap to expand debug info",
            color = NgColors.TextFaint, fontSize = 11.sp, modifier = Modifier.padding(bottom = 12.dp))

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📋", fontSize = 40.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("No activity yet", color = NgColors.TextMuted, fontSize = 15.sp)
                    Text("Filtered notifications appear here.", color = NgColors.TextFaint, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(entries, key = { it.id }) { entry -> LogRow(entry) }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(entry.timestamp) { sdf.format(Date(entry.timestamp)) }
    var expanded by remember { mutableStateOf(false) }

    val filterColor = when (entry.action) {
        RuleAction.BLOCK     -> NgColors.Red
        RuleAction.WHITELIST -> NgColors.Green
        null                 -> NgColors.TextMuted
    }
    val filterLabel = when (entry.action) {
        RuleAction.BLOCK     -> "Blocked"
        RuleAction.WHITELIST -> "Whitelisted"
        null                 -> "Allowed"
    }
    val saveLabel = when (entry.saveAction) {
        SaveRuleAction.SKIP -> "Not saved"
        SaveRuleAction.SAVE -> "Force saved"
        null                -> if (entry.action == RuleAction.BLOCK) "Not saved" else "Saved"
    }
    val saveColor = when (entry.saveAction) {
        SaveRuleAction.SKIP -> NgColors.Yellow
        SaveRuleAction.SAVE -> NgColors.Green
        null                -> if (entry.action == RuleAction.BLOCK) NgColors.TextFaint else NgColors.TextFaint
    }

    Column(modifier = Modifier.fillMaxWidth()
        .background(NgColors.SurfaceHigh, RoundedCornerShape(10.dp))
        .border(1.dp, NgColors.Border, RoundedCornerShape(10.dp))
        .clickable { expanded = !expanded }) {

        // ── Main row ──────────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(3.dp).fillMaxHeight()
                .background(filterColor, RoundedCornerShape(topStart = 10.dp, bottomStart = if (expanded) 0.dp else 10.dp)))

            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).weight(1f)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        NgTag(filterLabel, filterColor)
                        NgTag(saveLabel, saveColor)
                        // Update badge
                        if (entry.isUpdate) {
                            NgTag("Update", NgColors.Yellow)
                        }
                        if (entry.matchedRuleNote != null) {
                            Text(entry.matchedRuleNote, color = NgColors.TextFaint, fontSize = 10.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(timeStr, color = NgColors.TextFaint, fontSize = 10.sp)
                        Text(if (expanded) "▲" else "▼", color = NgColors.TextFaint, fontSize = 9.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("${entry.appName}  ·  ${entry.title}", color = NgColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(entry.body, color = NgColors.TextMuted, fontSize = 12.sp, maxLines = if (expanded) Int.MAX_VALUE else 1)
            }
        }

        // ── Debug panel ───────────────────────────────────────────────
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(modifier = Modifier.fillMaxWidth()
                .background(NgColors.Bg, RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(color = NgColors.Border, modifier = Modifier.padding(bottom = 4.dp))
                Text("DEBUG INFO", color = NgColors.Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                DebugSection("Raw notification") {
                    DebugField("Package", entry.packageName)
                    DebugField("Notif key", entry.notifKey)
                    DebugField("Is update", if (entry.isUpdate) "Yes — same notification slot, content changed" else "No — new notification")
                    DebugField("Title", entry.title)
                    DebugField("Body", entry.body)
                }
                DebugSection("Filter result") {
                    DebugField("Rules checked", "${entry.evaluatedRulesCount}")
                    DebugField("Outcome", filterLabel)
                    DebugField("Matched rule", entry.matchedRuleNote ?: if (entry.matchedRuleId != null) entry.matchedRuleId else "None (default allow)")
                    DebugField("App condition", if (entry.matchedAppPackage != null) "✓  ${entry.matchedAppPackage}" else "—")
                    DebugField("Regex condition", if (entry.matchedRegex != null) "✓  ${entry.matchedRegex}" else "—")
                    if (entry.regexMatchSnippet != null) DebugField("Matched text", "\"${entry.regexMatchSnippet}\"")
                }
                DebugSection("Save result") {
                    DebugField("Outcome", saveLabel)
                    DebugField("Save rule", if (entry.saveAction != null) entry.saveAction.name else "None (default save)")
                }
            }
        }
    }
}

@Composable
private fun DebugSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = NgColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun DebugField(key: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(key, color = NgColors.TextFaint, fontSize = 11.sp, modifier = Modifier.width(110.dp))
        Text(value, color = NgColors.Text, fontSize = 11.sp,
            fontFamily = if (key in listOf("Package", "Notif key", "Matched text", "Regex condition", "App condition"))
                FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.weight(1f))
    }
}
