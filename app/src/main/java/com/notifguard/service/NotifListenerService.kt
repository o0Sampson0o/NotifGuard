package com.notifguard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.notifguard.data.model.RuleAction
import com.notifguard.data.model.SaveRuleAction
import com.notifguard.data.repo.NotifGuardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotifListenerService : NotificationListenerService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var repo: NotifGuardRepository

    // In-memory set of notification keys we have already seen this session.
    // When onNotificationPosted fires, if the key is already here it's an edit.
    // Cleared entry on onNotificationRemoved.
    private val seenKeys = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        repo = NotifGuardRepository.getInstance(this)
        createForegroundChannel()
        startForeground(FOREGROUND_ID, buildForegroundNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return

        val notifKey = "${sbn.packageName}|${sbn.id}|${sbn.tag ?: ""}"
        val isUpdate = notifKey in seenKeys
        seenKeys.add(notifKey)

        val extras  = sbn.notification.extras
        val title   = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val body    = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val pkg     = sbn.packageName
        val appName = resolveAppName(pkg)

        scope.launch {
            val filterResult = repo.evaluate(pkg, title, body)
            val saveResult   = repo.evaluateSave(pkg, title, body)

            when (filterResult.action) {
                RuleAction.BLOCK -> {
                    // Block: cancel the notification, don't save, just log
                    cancelNotification(sbn.key)
                    repo.addLog(pkg, appName, notifKey, title, body, isUpdate, filterResult, saveResult)
                }
                RuleAction.WHITELIST, null -> {
                    // Passed filter — now check save rules
                    val shouldSave = when (saveResult.action) {
                        SaveRuleAction.SKIP -> false
                        SaveRuleAction.SAVE -> true
                        null               -> true  // default: save
                    }
                    if (shouldSave) {
                        repo.saveOrUpdateNotification(pkg, appName, notifKey, title, body)
                    }
                    repo.addLog(pkg, appName, notifKey, title, body, isUpdate, filterResult, saveResult)
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val notifKey = "${sbn.packageName}|${sbn.id}|${sbn.tag ?: ""}"
        seenKeys.remove(notifKey)
    }

    private fun resolveAppName(packageName: String): String = runCatching {
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(packageName, 0)
        ).toString()
    }.getOrDefault(packageName)

    private fun createForegroundChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(com.notifguard.R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = getString(com.notifguard.R.string.notif_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildForegroundNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(com.notifguard.R.string.app_name))
            .setContentText(getString(com.notifguard.R.string.service_running))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()

    companion object {
        const val CHANNEL_ID   = "notifguard_service"
        const val FOREGROUND_ID = 1
    }
}
