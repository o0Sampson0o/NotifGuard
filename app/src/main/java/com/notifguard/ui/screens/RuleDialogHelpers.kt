package com.notifguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notifguard.AppInfo
import com.notifguard.ui.components.NgSearchBar
import com.notifguard.ui.theme.NgColors

@Composable
fun RuleDialogLabel(text: String) {
    Text(
        text.uppercase(),
        color = NgColors.TextMuted, fontSize = 10.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp
    )
}

@Composable
fun AppPickerList(apps: List<AppInfo>, selected: String, onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier
            .heightIn(max = 180.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // "Any app" row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (selected.isEmpty()) NgColors.AccentSoft else NgColors.Bg,
                    RoundedCornerShape(8.dp)
                )
                .border(
                    1.dp,
                    if (selected.isEmpty()) NgColors.Accent else NgColors.Border,
                    RoundedCornerShape(8.dp)
                )
                .clickable { onSelect("") }
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                "Any app",
                color = if (selected.isEmpty()) NgColors.Text else NgColors.TextMuted,
                fontSize = 13.sp,
                fontWeight = if (selected.isEmpty()) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        apps.forEach { app ->
            val sel = selected == app.packageName
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (sel) NgColors.AccentSoft else NgColors.Bg, RoundedCornerShape(8.dp))
                    .border(1.dp, if (sel) NgColors.Accent else NgColors.Border, RoundedCornerShape(8.dp))
                    .clickable { onSelect(app.packageName) }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Column {
                    Text(app.appName, color = NgColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(app.packageName, color = NgColors.TextFaint, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun RuleSummary(actionLabel: String, appLabel: String, contentLabel: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(NgColors.SurfaceHigh, RoundedCornerShape(8.dp))
            .border(1.dp, NgColors.Border, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Text(
            "$actionLabel notifications from $appLabel matching $contentLabel",
            color = NgColors.TextMuted, fontSize = 12.sp, lineHeight = 18.sp
        )
    }
}

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
