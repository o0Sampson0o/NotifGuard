package com.notifguard.ui.screens

import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notifguard.AppInfo
import com.notifguard.data.model.ScheduleType
import com.notifguard.ui.components.NgSearchBar
import com.notifguard.ui.theme.NgColors

// ─── Label ────────────────────────────────────────────────────────────────

@Composable
fun RuleDialogLabel(text: String) {
    Text(text.uppercase(), color = NgColors.TextMuted, fontSize = 10.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
}

// ─── App picker ───────────────────────────────────────────────────────────

@Composable
fun AppPickerList(apps: List<AppInfo>, selected: String, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.fillMaxWidth()
            .background(if (selected.isEmpty()) NgColors.AccentSoft else NgColors.Bg, RoundedCornerShape(8.dp))
            .border(1.dp, if (selected.isEmpty()) NgColors.Accent else NgColors.Border, RoundedCornerShape(8.dp))
            .clickable { onSelect("") }.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text("Any app", color = if (selected.isEmpty()) NgColors.Text else NgColors.TextMuted,
                fontSize = 13.sp, fontWeight = if (selected.isEmpty()) FontWeight.SemiBold else FontWeight.Normal)
        }
        apps.forEach { app ->
            val sel = selected == app.packageName
            Box(modifier = Modifier.fillMaxWidth()
                .background(if (sel) NgColors.AccentSoft else NgColors.Bg, RoundedCornerShape(8.dp))
                .border(1.dp, if (sel) NgColors.Accent else NgColors.Border, RoundedCornerShape(8.dp))
                .clickable { onSelect(app.packageName) }.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Column {
                    Text(app.appName, color = NgColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(app.packageName, color = NgColors.TextFaint, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// ─── Regex input with flags ───────────────────────────────────────────────

val ALL_REGEX_FLAGS = listOf(
    "IGNORE_CASE"     to "Case insensitive",
    "MULTILINE"       to "Multiline (^ and $ match line breaks)",
    "DOT_MATCHES_ALL" to "Dot matches all (including newline)"
)

@Composable
fun RegexInputWithFlags(
    pattern: String,
    onPatternChange: (String) -> Unit,
    flags: String,
    onFlagsChange: (String) -> Unit
) {
    val enabledFlags = remember(flags) { flags.split(",").filter { it.isNotBlank() }.toMutableSet() }
    val regexError = remember(pattern) {
        if (pattern.isBlank()) null
        else runCatching { Regex(pattern); null }.getOrElse { it.message }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Pattern field — scrollable horizontally for long patterns
        OutlinedTextField(
            value = pattern,
            onValueChange = onPatternChange,
            placeholder = { Text("(?i)(promo|sale|限时)", color = NgColors.TextFaint, fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
            maxLines = 3,
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            colors = ngTextFieldColors(),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )
        if (regexError != null) {
            Text("⚠ $regexError", color = NgColors.Red, fontSize = 10.sp)
        } else if (pattern.isNotBlank()) {
            Text("✓ Valid pattern", color = NgColors.Green, fontSize = 10.sp)
        } else {
            Text("Blank = match any content. Supports Unicode (中文, etc.)", color = NgColors.TextFaint, fontSize = 10.sp)
        }

        // Flag toggles
        Text("REGEX FLAGS", color = NgColors.TextFaint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        ALL_REGEX_FLAGS.forEach { (flag, label) ->
            val active = flag in enabledFlags
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(if (active) NgColors.AccentSoft else NgColors.SurfaceHigh, RoundedCornerShape(8.dp))
                    .border(1.dp, if (active) NgColors.Accent.copy(.5f) else NgColors.Border, RoundedCornerShape(8.dp))
                    .clickable {
                        if (active) enabledFlags.remove(flag) else enabledFlags.add(flag)
                        onFlagsChange(enabledFlags.joinToString(","))
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.size(16.dp)
                    .background(if (active) NgColors.Accent else NgColors.Border, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center) {
                    if (active) Text("✓", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(flag, color = if (active) NgColors.Text else NgColors.TextMuted,
                        fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                    Text(label, color = NgColors.TextFaint, fontSize = 10.sp)
                }
            }
        }
    }
}

// ─── Schedule editor ──────────────────────────────────────────────────────

@Composable
fun ScheduleEditor(
    scheduleType: ScheduleType,
    onTypeChange: (ScheduleType) -> Unit,
    windowStart: String,
    onStartChange: (String) -> Unit,
    windowEnd: String,
    onEndChange: (String) -> Unit,
    timerMinutes: Int,
    onTimerChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Type selector
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                ScheduleType.ALWAYS      to "Always",
                ScheduleType.TIME_WINDOW to "Time window",
                ScheduleType.TIMER       to "Timer"
            ).forEach { (type, label) ->
                val sel = scheduleType == type
                OutlinedButton(
                    onClick = { onTypeChange(type) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (sel) NgColors.AccentSoft else Color.Transparent),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (sel) NgColors.Accent else NgColors.Border),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) { Text(label, fontSize = 11.sp, color = if (sel) NgColors.Text else NgColors.TextMuted) }
            }
        }

        when (scheduleType) {
            ScheduleType.ALWAYS -> {
                Text("Rule is always active.", color = NgColors.TextFaint, fontSize = 11.sp)
            }
            ScheduleType.TIME_WINDOW -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = windowStart, onValueChange = onStartChange,
                        placeholder = { Text("08:00", color = NgColors.TextFaint) },
                        label = { Text("From", color = NgColors.TextMuted, fontSize = 11.sp) },
                        singleLine = true, colors = ngTextFieldColors(),
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)
                    )
                    Text("→", color = NgColors.TextMuted, fontSize = 16.sp)
                    OutlinedTextField(
                        value = windowEnd, onValueChange = onEndChange,
                        placeholder = { Text("22:00", color = NgColors.TextFaint) },
                        label = { Text("To", color = NgColors.TextMuted, fontSize = 11.sp) },
                        singleLine = true, colors = ngTextFieldColors(),
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)
                    )
                }
                Text("Rule is active only within this daily time window.", color = NgColors.TextFaint, fontSize = 10.sp)
            }
            ScheduleType.TIMER -> {
                OutlinedTextField(
                    value = timerMinutes.toString(),
                    onValueChange = { onTimerChange(it.toIntOrNull()?.coerceAtLeast(1) ?: 1) },
                    label = { Text("Minutes", color = NgColors.TextMuted, fontSize = 11.sp) },
                    singleLine = true, colors = ngTextFieldColors(),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()
                )
                Text("Rule will auto-disable after the timer expires.", color = NgColors.TextFaint, fontSize = 10.sp)
            }
        }
    }
}

// ─── Sound picker ─────────────────────────────────────────────────────────

@Composable
fun SoundPicker(currentUri: String?, onPick: (String?) -> Unit) {
    val context = LocalContext.current
    // Track currently playing ringtone so we can stop it
    var playingRingtone by remember { mutableStateOf<Ringtone?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        playingRingtone?.stop(); playingRingtone = null
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        onPick(uri?.toString())
    }

    // Stop playing when composable leaves
    DisposableEffect(Unit) { onDispose { playingRingtone?.stop() } }

    val soundName = if (currentUri != null) {
        runCatching {
            RingtoneManager.getRingtone(context, Uri.parse(currentUri))?.getTitle(context) ?: "Custom sound"
        }.getOrDefault("Custom sound")
    } else "Default system sound"

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(soundName, color = NgColors.Text, fontSize = 13.sp)
                Text("Tap Pick to change", color = NgColors.TextFaint, fontSize = 10.sp)
            }
            // Preview button — plays/stops the current sound
            OutlinedButton(
                onClick = {
                    if (playingRingtone?.isPlaying == true) {
                        playingRingtone?.stop()
                        playingRingtone = null
                    } else {
                        playingRingtone?.stop()
                        val uri = if (currentUri != null) Uri.parse(currentUri)
                            else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        val r = RingtoneManager.getRingtone(context, uri)
                        r?.audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        r?.play()
                        playingRingtone = r
                    }
                },
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NgColors.Accent.copy(.5f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (playingRingtone?.isPlaying == true) NgColors.AccentSoft else Color.Transparent)
            ) {
                Text(if (playingRingtone?.isPlaying == true) "■ Stop" else "▶ Play",
                    color = NgColors.Accent, fontSize = 12.sp)
            }
            // Pick button
            OutlinedButton(
                onClick = {
                    playingRingtone?.stop(); playingRingtone = null
                    val intent = android.content.Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                        if (currentUri != null) putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(currentUri))
                    }
                    launcher.launch(intent)
                },
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NgColors.Border)
            ) { Text("Pick", color = NgColors.TextMuted, fontSize = 12.sp) }
        }
        // Clear button on its own row to avoid cramping
        if (currentUri != null) {
            OutlinedButton(
                onClick = { playingRingtone?.stop(); playingRingtone = null; onPick(null) },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, NgColors.Red.copy(.4f))
            ) { Text("Clear custom sound (use default)", color = NgColors.Red, fontSize = 12.sp) }
        }
    }
}

// ─── Summary preview ──────────────────────────────────────────────────────

@Composable
fun RuleSummary(actionLabel: String, appLabel: String, contentLabel: String) {
    Box(modifier = Modifier.fillMaxWidth()
        .background(NgColors.SurfaceHigh, RoundedCornerShape(8.dp))
        .border(1.dp, NgColors.Border, RoundedCornerShape(8.dp)).padding(10.dp)) {
        Text("$actionLabel notifications from $appLabel matching $contentLabel",
            color = NgColors.TextMuted, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

// ─── Shared text field colors ─────────────────────────────────────────────

@Composable
fun ngTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor   = NgColors.Bg,
    unfocusedContainerColor = NgColors.Bg,
    focusedBorderColor      = NgColors.Accent,
    unfocusedBorderColor    = NgColors.Border,
    cursorColor             = NgColors.Accent,
    focusedTextColor        = NgColors.Text,
    unfocusedTextColor      = NgColors.Text,
)