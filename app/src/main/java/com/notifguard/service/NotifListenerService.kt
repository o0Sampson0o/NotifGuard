package com.notifguard.service

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
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
    private lateinit var nm: NotificationManager

    // In-memory set of seen notification keys for edit detection
    private val seenKeys = mutableSetOf<String>()

    // Silent repost channel — no sound, no vibration
    private val silentChannelId = "notifguard_silent_repost"

    override fun onCreate() {
        super.onCreate()
        repo = NotifGuardRepository.getInstance(this)
        nm = getSystemService(NotificationManager::class.java)
        createForegroundChannel()
        createSilentChannel()
        startForeground(FOREGROUND_ID, buildForegroundNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val targetPkg = extras.getString("notifguard_target_pkg")

        // Ignore our own non-test notifications (foreground service etc.)
        if (sbn.packageName == packageName && targetPkg == null) return

        val notifKey = "${sbn.packageName}|${sbn.id}|${sbn.tag ?: ""}"
        val isUpdate = notifKey in seenKeys
        seenKeys.add(notifKey)

        val title   = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val body    = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val pkg     = targetPkg ?: sbn.packageName
        val appName = resolveAppName(pkg)

        val resolvedIntentUri: String? = runCatching {
            packageManager.getLaunchIntentForPackage(pkg)
                ?.apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                ?.toUri(android.content.Intent.URI_INTENT_SCHEME)
        }.getOrNull()

        scope.launch {
            val filterResult = repo.evaluate(pkg, title, body)
            val saveResult   = repo.evaluateSave(pkg, title, body)

            when (filterResult.action) {
                RuleAction.BLOCK -> {
                    cancelNotification(sbn.key)
                    repo.addLog(pkg, appName, notifKey, title, body, isUpdate, filterResult, saveResult)
                }
                RuleAction.WHITELIST, null -> {
                    val customSound = filterResult.customSoundUri

                    if (customSound != null) {
                        // Has custom sound: cancel original (removes its system sound),
                        // repost silently so the notification still appears visually,
                        // then play our custom sound on the notification audio stream.
                        cancelNotification(sbn.key)
                        repostSilently(sbn, title, body)
                        playNotificationSound(customSound)
                    }
                    // If no custom sound: leave notification untouched — system plays its own sound normally

                    val shouldSave = when (saveResult.action) {
                        SaveRuleAction.SKIP -> false
                        SaveRuleAction.SAVE -> true
                        null               -> true
                    }
                    if (shouldSave) {
                        repo.saveOrUpdateNotification(pkg, appName, notifKey, title, body, resolvedIntentUri)
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

    // ─── Silent repost ────────────────────────────────────────────────────

    /**
     * Reposts the notification visually (so it still appears in the shade)
     * but with no sound or vibration. Called when we want to play a custom
     * sound instead of the notification's original sound.
     */
    private fun repostSilently(sbn: StatusBarNotification, title: String, body: String) {
        runCatching {
            val pendingIntent = sbn.notification.contentIntent

            // Use the original app's launcher icon as the large icon so it looks natural.
            // We can't use another app's icon as the small icon (system restriction),
            // but large icon in the notification drawer is what the user sees.
            val appIconBitmap: Bitmap? = runCatching {
                val drawable = packageManager.getApplicationIcon(sbn.packageName)
                val size = resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
                    .coerceAtLeast(96)
                val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
                bmp
            }.getOrNull()

            val builder = NotificationCompat.Builder(this, silentChannelId)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setSilent(true)

            if (appIconBitmap != null) {
                builder.setLargeIcon(appIconBitmap)
            }

            nm.notify(sbn.id, builder.build())
        }
    }

    // ─── Custom sound playback ────────────────────────────────────────────

    /**
     * Plays a sound on the NOTIFICATION audio stream at notification volume.
     * This is the correct stream for notification sounds — not STREAM_RING or STREAM_MUSIC.
     */
    private fun playNotificationSound(uriString: String) {
        runCatching {
            val uri = Uri.parse(uriString)
            val ringtone = RingtoneManager.getRingtone(this, uri)
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone?.play()
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

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
        nm.createNotificationChannel(channel)
    }

    private fun createSilentChannel() {
        val channel = NotificationChannel(
            silentChannelId,
            "NotifGuard Silent Repost",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setSound(null, null)         // no sound on the channel level
            enableVibration(false)
            setShowBadge(true)
        }
        nm.createNotificationChannel(channel)
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
        const val CHANNEL_ID    = "notifguard_service"
        const val FOREGROUND_ID = 1
    }
}