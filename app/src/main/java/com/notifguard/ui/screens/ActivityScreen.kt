package com.notifguard.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notifguard.MainViewModel
import com.notifguard.data.model.LogEntry
import com.notifguard.data.model.RuleAction
import com.notifguard.data.model.SaveRuleAction
import com.notifguard.ui.components.NgTag
import com.notifguard.ui.screens.ngTextFieldColors
import com.notifguard.ui.theme.NgColors
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@Composable
fun ActivityScreen(vm: MainViewModel) {
    val entries by vm.logEntries.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // ── Lifted test panel state so log rows can fill it ────────────────
    var panelExpanded by remember { mutableStateOf(false) }
    var testTitle by remember { mutableStateOf("") }
    var testBody by remember { mutableStateOf("") }
    var testPkg by remember { mutableStateOf("") }

    // Runtime POST_NOTIFICATIONS permission (Android 13+)
    var hasNotifPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotifPermission = granted }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        // ── Test panel ────────────────────────────────────────────────
        TestNotifPanel(
            expanded = panelExpanded,
            onExpandChange = { panelExpanded = it },
            title = testTitle, onTitleChange = { testTitle = it },
            body = testBody, onBodyChange = { testBody = it },
            pkg = testPkg, onPkgChange = { testPkg = it },
            hasPermission = hasNotifPermission,
            onRequestPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onSend = {
                vm.sendTestNotification(context, testTitle, testBody, testPkg)
            }
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = NgColors.Border)
        Spacer(Modifier.height(12.dp))

        Text("Last 300 entries · 7 day history · Tap to expand debug · ↺ to replay",
            color = NgColors.TextFaint, fontSize = 11.sp, modifier = Modifier.padding(bottom = 12.dp))

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📋", fontSize = 40.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("No activity yet", color = NgColors.TextMuted, fontSize = 15.sp)
                    Text("Fire a test notification above to see results here.",
                        color = NgColors.TextFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            return
        }

        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(entries, key = { it.id }) { entry ->
                LogRow(
                    entry = entry,
                    onReplay = {
                        // Fill panel fields and expand it, scroll to top
                        testTitle = entry.title
                        testBody = entry.body
                        testPkg = entry.packageName
                        panelExpanded = true
                        scope.launch { listState.animateScrollToItem(0) }
                    }
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ─── Test Notification Panel ───────────────────────────────────────────────

@Composable
private fun TestNotifPanel(
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    title: String, onTitleChange: (String) -> Unit,
    body: String, onBodyChange: (String) -> Unit,
    pkg: String, onPkgChange: (String) -> Unit,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onSend: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()
        .background(NgColors.SurfaceHigh, RoundedCornerShape(10.dp))
        .border(1.dp, NgColors.Border, RoundedCornerShape(10.dp))) {

        // Header
        Row(modifier = Modifier.fillMaxWidth().clickable { onExpandChange(!expanded) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🧪", fontSize = 16.sp)
                Column {
                    Text("Test Notification", color = NgColors.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Craft and fire a notification to test your rules",
                        color = NgColors.TextFaint, fontSize = 10.sp)
                }
            }
            Text(if (expanded) "▲" else "▼", color = NgColors.TextFaint, fontSize = 12.sp)
        }

        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(modifier = Modifier.fillMaxWidth()
                .background(NgColors.Bg, RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {

                HorizontalDivider(color = NgColors.Border, modifier = Modifier.padding(bottom = 2.dp))

                // Permission warning
                if (!hasPermission) {
                    Row(modifier = Modifier.fillMaxWidth()
                        .background(NgColors.YellowSoft, RoundedCornerShape(8.dp))
                        .border(1.dp, NgColors.Yellow.copy(.4f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Notification permission required to send test notifications.",
                            color = NgColors.Yellow, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = onRequestPermission) {
                            Text("Grant", color = NgColors.Yellow, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = title, onValueChange = onTitleChange,
                    placeholder = { Text("Notification title", color = NgColors.TextFaint, fontSize = 13.sp) },
                    label = { Text("Title", color = NgColors.TextMuted, fontSize = 11.sp) },
                    singleLine = true, colors = ngTextFieldColors(),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = body, onValueChange = onBodyChange,
                    placeholder = { Text("Notification body text", color = NgColors.TextFaint, fontSize = 13.sp) },
                    label = { Text("Body", color = NgColors.TextMuted, fontSize = 11.sp) },
                    maxLines = 3, colors = ngTextFieldColors(),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pkg, onValueChange = onPkgChange,
                    placeholder = { Text("com.example.app  (optional)", color = NgColors.TextFaint,
                        fontSize = 13.sp, fontFamily = FontFamily.Monospace) },
                    label = { Text("App package  (for rule matching)", color = NgColors.TextMuted, fontSize = 11.sp) },
                    singleLine = true, colors = ngTextFieldColors(),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()
                )

                Text("The notification will appear on your device and be intercepted by NotifGuard, " +
                     "so you can verify your rules in the log below.",
                    color = NgColors.TextFaint, fontSize = 10.sp, lineHeight = 15.sp)

                Button(
                    onClick = onSend,
                    enabled = hasPermission && (title.isNotBlank() || body.isNotBlank()),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NgColors.Accent),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("🧪  Send Test Notification", fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

// ─── Log Row ───────────────────────────────────────────────────────────────

@Composable
private fun LogRow(entry: LogEntry, onReplay: () -> Unit) {
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
        null                -> NgColors.TextFaint
    }

    Column(modifier = Modifier.fillMaxWidth()
        .background(NgColors.SurfaceHigh, RoundedCornerShape(10.dp))
        .border(1.dp, NgColors.Border, RoundedCornerShape(10.dp))
        .clickable { expanded = !expanded }) {

        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(3.dp).fillMaxHeight()
                .background(filterColor, RoundedCornerShape(
                    topStart = 10.dp, bottomStart = if (expanded) 0.dp else 10.dp)))

            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).weight(1f)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        NgTag(filterLabel, filterColor)
                        NgTag(saveLabel, saveColor)
                        if (entry.isUpdate) NgTag("Update", NgColors.Yellow)
                        if (entry.matchedRuleNote != null)
                            Text(entry.matchedRuleNote, color = NgColors.TextFaint, fontSize = 10.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(timeStr, color = NgColors.TextFaint, fontSize = 10.sp)
                        // Replay button
                        Box(modifier = Modifier
                            .background(NgColors.AccentSoft, RoundedCornerShape(4.dp))
                            .border(1.dp, NgColors.Accent.copy(.4f), RoundedCornerShape(4.dp))
                            .clickable(onClick = onReplay)
                            .padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("↺ Replay", color = NgColors.Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(if (expanded) "▲" else "▼", color = NgColors.TextFaint, fontSize = 9.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("${entry.appName}  ·  ${entry.title}",
                    color = NgColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(entry.body, color = NgColors.TextMuted, fontSize = 12.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 1)
            }
        }

        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(modifier = Modifier.fillMaxWidth()
                .background(NgColors.Bg, RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(color = NgColors.Border, modifier = Modifier.padding(bottom = 4.dp))
                Text("DEBUG INFO", color = NgColors.Accent, fontSize = 9.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                DebugSection("Raw notification") {
                    DebugField("Package", entry.packageName)
                    DebugField("Notif key", entry.notifKey)
                    DebugField("Is update", if (entry.isUpdate) "Yes — same slot, content changed" else "No — new notification")
                    DebugField("Title", entry.title)
                    DebugField("Body", entry.body)
                }
                DebugSection("Filter result") {
                    DebugField("Rules checked", "${entry.evaluatedRulesCount}")
                    DebugField("Outcome", filterLabel)
                    DebugField("Matched rule", entry.matchedRuleNote ?: entry.matchedRuleId ?: "None (default allow)")
                    DebugField("App condition", if (entry.matchedAppPackage != null) "✓  ${entry.matchedAppPackage}" else "—")
                    DebugField("Regex condition", if (entry.matchedRegex != null) "✓  ${entry.matchedRegex}" else "—")
                    if (entry.regexMatchSnippet != null) DebugField("Matched text", "\"${entry.regexMatchSnippet}\"")
                }
                DebugSection("Save result") {
                    DebugField("Outcome", saveLabel)
                    DebugField("Save rule", entry.saveAction?.name ?: "None (default save)")
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
            fontFamily = if (key in listOf("Package", "Notif key", "Matched text",
                "Regex condition", "App condition")) FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.weight(1f))
    }
}