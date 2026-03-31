package com.notifguard.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// ─── Rule Group ────────────────────────────────────────────────────────────

enum class ScheduleType { ALWAYS, TIME_WINDOW, TIMER }

@Entity(tableName = "rule_groups")
data class RuleGroup(
    @PrimaryKey val id: String,
    val name: String,
    val enabled: Boolean = true,
    // Scheduling
    val scheduleType: ScheduleType = ScheduleType.ALWAYS,
    val scheduleWindowStart: String = "",  // "HH:mm" for TIME_WINDOW
    val scheduleWindowEnd: String = "",    // "HH:mm" for TIME_WINDOW
    val timerMinutes: Int = 0,             // for TIMER: duration in minutes
    val timerExpiresAt: Long = 0L          // epoch ms when timer expires
)

// ─── Filter Rule ───────────────────────────────────────────────────────────

enum class RuleAction { BLOCK, WHITELIST }

@Entity(tableName = "filter_rules")
data class FilterRule(
    @PrimaryKey val id: String,
    val action: RuleAction,
    val appPackage: String = "",
    val regexPattern: String = "",
    val regexFlags: String = "",       // comma-separated: IGNORE_CASE,MULTILINE,DOT_MATCHES_ALL
    val note: String = "",
    val enabled: Boolean = true,
    val priority: Int = 0,
    val groupId: String? = null,
    val customSoundUri: String? = null,
    // Per-rule scheduling (same as group)
    val scheduleType: ScheduleType = ScheduleType.ALWAYS,
    val scheduleWindowStart: String = "",
    val scheduleWindowEnd: String = "",
    val timerMinutes: Int = 0,
    val timerExpiresAt: Long = 0L
)

// ─── Save Rule ─────────────────────────────────────────────────────────────

enum class SaveRuleAction { SKIP, SAVE }

@Entity(tableName = "save_rules")
data class SaveRule(
    @PrimaryKey val id: String,
    val action: SaveRuleAction,
    val appPackage: String = "",
    val regexPattern: String = "",
    val regexFlags: String = "",
    val note: String = "",
    val enabled: Boolean = true,
    val priority: Int = 0,
    val groupId: String? = null,
    val scheduleType: ScheduleType = ScheduleType.ALWAYS,
    val scheduleWindowStart: String = "",
    val scheduleWindowEnd: String = "",
    val timerMinutes: Int = 0,
    val timerExpiresAt: Long = 0L
)

// ─── Saved Notification ────────────────────────────────────────────────────

@Entity(tableName = "saved_notifications")
data class SavedNotification(
    @PrimaryKey val id: String,
    val packageName: String,
    val appName: String,
    val notifKey: String,
    val title: String,
    val body: String,
    val latestAt: Long = System.currentTimeMillis(),
    val firstSeenAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = firstSeenAt + 30L * 24 * 60 * 60 * 1000,
    val updateCount: Int = 0,
    val intentUri: String? = null
)

// ─── Notification History ──────────────────────────────────────────────────

@Entity(tableName = "notif_history")
data class NotifHistory(
    @PrimaryKey val id: String,
    val threadId: String,
    val title: String,
    val body: String,
    val recordedAt: Long = System.currentTimeMillis()
)

// ─── Activity Log ──────────────────────────────────────────────────────────

@Entity(tableName = "activity_log")
data class LogEntry(
    @PrimaryKey val id: String,
    val packageName: String,
    val appName: String,
    val notifKey: String,
    val title: String,
    val body: String,
    val isUpdate: Boolean = false,
    val action: RuleAction?,
    val saveAction: SaveRuleAction?,
    val matchedRuleId: String?,
    val matchedRuleNote: String?,
    val matchedAppPackage: String?,
    val matchedRegex: String?,
    val regexMatchSnippet: String?,
    val evaluatedRulesCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)