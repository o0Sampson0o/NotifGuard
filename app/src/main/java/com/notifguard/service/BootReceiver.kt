package com.notifguard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.notifguard.data.repo.NotifGuardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val repo = NotifGuardRepository.getInstance(context)
            CoroutineScope(Dispatchers.IO).launch {
                repo.deleteExpiredNotifications()
            }
        }
    }
}
