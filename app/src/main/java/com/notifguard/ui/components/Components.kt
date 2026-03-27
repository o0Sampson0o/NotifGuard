package com.notifguard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notifguard.ui.theme.NgColors

@Composable
fun NgTag(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = label.uppercase(),
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun NgBadge(count: Int) {
    if (count > 0) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .background(NgColors.Accent, CircleShape)
                .padding(horizontal = 6.dp, vertical = 1.dp)
        ) {
            Text(
                text = count.toString(),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
fun NgSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = NgColors.TextFaint, fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = NgColors.TextFaint, modifier = Modifier.size(18.dp)) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor   = NgColors.Bg,
            unfocusedContainerColor = NgColors.Bg,
            focusedBorderColor      = NgColors.Accent,
            unfocusedBorderColor    = NgColors.Border,
            cursorColor             = NgColors.Accent,
            focusedTextColor        = NgColors.Text,
            unfocusedTextColor      = NgColors.Text,
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
    )
}

@Composable
fun NgCard(
    modifier: Modifier = Modifier,
    borderColor: Color = NgColors.Border,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NgColors.SurfaceHigh, RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        content = content
    )
}

@Composable
fun SectionHeader(title: String, color: Color, count: Int = 0) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title.uppercase(),
            color = NgColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
        if (count > 0) {
            Spacer(Modifier.width(8.dp))
            NgBadge(count)
        }
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(color = NgColors.Border, modifier = Modifier.weight(1f))
    }
}

@Composable
fun InfoBanner(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(NgColors.AccentSoft, RoundedCornerShape(10.dp))
            .border(1.dp, NgColors.Accent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(text, color = NgColors.TextMuted, fontSize = 12.sp, lineHeight = 18.sp)
    }
}
