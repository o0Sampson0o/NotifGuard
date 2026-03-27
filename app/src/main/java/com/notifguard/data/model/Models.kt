package com.notifguard.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// ─── Filter Rule (blocking) ────────────────────────────────────────────────

enum class RuleAction { BLOCK, WHITELIST }

@Entity(tableName = "filter_rules")
data class FilterRule(
    @PrimaryKey val id: String,
    val action: RuleAction,
    val appPackage: String = "",
    val regexPattern: String = "",
    val note: String = "",
    val enabled: Boolean = true,
    val priority: Int = 0
)

// ─── Save Rule (saving) ────────────────────────────────────────────────────

enum class SaveRuleAction { SKIP, SAVE }

/**
 * Same structure as FilterRule but controls whether a notification gets saved.
 * Default (no match) = SAVE everything.
 * SKIP = don't save. SAVE = force save (overrides a SKIP higher up).
 */
@Entity(tableName = "save_rules")
data class SaveRule(
    @PrimaryKey val id: String,
    val action: SaveRuleAction,
    val appPackage: String = "",
    val regexPattern: String = "",
    val note: String = "",
    val enabled: Boolean = true,
    val priority: Int = 0
)

// ─── Saved Notification (thread model) ────────────────────────────────────

/**
 * Represents a notification "thread" — grouped by packageName + notifId + notifTag.
 * When the same notification is updated, a new NotifHistory entry is added
 * instead of replacing this row. This row always reflects the latest content.
 */
@Entity(tableName = "saved_notifications")
data class SavedNotification(
    @PrimaryKey val id: String,
    val packageName: String,
    val appName: String,
    // The Android notification identity — used to detect edits
    val notifKey: String,           // "pkg|id|tag" — unique per notification slot
    // Latest content (always up to date)
    val title: String,
    val body: String,
    val latestAt: Long = System.currentTimeMillis(),
    val firstSeenAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = firstSeenAt + 30L * 24 * 60 * 60 * 1000,
    val updateCount: Int = 0        // how many times this was updated
)

// ─── Notification History entry ────────────────────────────────────────────

/**
 * Each time a SavedNotification is updated, the old body is archived here.
 * threadId references SavedNotification.id.
 */
@Entity(tableName = "notif_history")
data class NotifHistory(
    @PrimaryKey val id: String,
    val threadId: String,           // FK → SavedNotification.id
    val title: String,
    val body: String,
    val recordedAt: Long = System.currentTimeMillis()
)

// ─── Activity Log Entry ────────────────────────────────────────────────────

@Entity(tableName = "activity_log")
data class LogEntry(
    @PrimaryKey val id: String,
    val packageName: String,
    val appName: String,
    val notifKey: String,           // used for dedup — same key = update
    val title: String,
    val body: String,
    val isUpdate: Boolean = false,  // true if this notification slot already existed
    val action: RuleAction?,
    val saveAction: SaveRuleAction?, // what the save engine decided
    val matchedRuleId: String?,
    val matchedRuleNote: String?,
    val matchedAppPackage: String?,
    val matchedRegex: String?,
    val regexMatchSnippet: String?,
    val evaluatedRulesCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
