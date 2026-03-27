package com.notifguard.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notifguard.R
import com.notifguard.ui.theme.NgColors

@Composable
fun PermissionScreen() {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NgColors.Bg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(32.dp)
                .background(NgColors.Surface, RoundedCornerShape(16.dp))
                .border(1.dp, NgColors.Border, RoundedCornerShape(16.dp))
                .padding(28.dp)
        ) {
            Text("🛡", fontSize = 52.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.app_name),
                color = NgColors.Text,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.permission_required),
                color = NgColors.Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.permission_desc),
                color = NgColors.TextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(8.dp))

            // Steps
            listOf(
                "1. Tap \"Grant Access\" below",
                "2. Find NotifGuard in the list",
                "3. Toggle \"Allow notification access\" ON",
                "4. Confirm and return here"
            ).forEach { step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Text(step, color = NgColors.TextMuted, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                colors = ButtonDefaults.buttonColors(containerColor = NgColors.Accent),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.grant_permission),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
