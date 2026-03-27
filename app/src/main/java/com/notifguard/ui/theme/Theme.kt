package com.notifguard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object NgColors {
    val Bg           = Color(0xFF0D0F14)
    val Surface      = Color(0xFF151820)
    val SurfaceHigh  = Color(0xFF1C2130)
    val Border       = Color(0xFF252C3E)
    val BorderLight  = Color(0xFF2E3850)
    val Accent       = Color(0xFF4F8EF7)
    val AccentSoft   = Color(0xFF1A2D52)
    val Green        = Color(0xFF2ECC71)
    val GreenSoft    = Color(0xFF0D2E1A)
    val Red          = Color(0xFFE74C3C)
    val RedSoft      = Color(0xFF2E0D0D)
    val Yellow       = Color(0xFFF39C12)
    val YellowSoft   = Color(0xFF2E1E0D)
    val Text         = Color(0xFFE8EDF8)
    val TextMuted    = Color(0xFF7A8BA8)
    val TextFaint    = Color(0xFF3D4F6B)
}

private val ColorScheme = darkColorScheme(
    primary         = NgColors.Accent,
    background      = NgColors.Bg,
    surface         = NgColors.Surface,
    onPrimary       = Color.White,
    onBackground    = NgColors.Text,
    onSurface       = NgColors.Text,
    outline         = NgColors.Border,
)

@Composable
fun NotifGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        content = content
    )
}
