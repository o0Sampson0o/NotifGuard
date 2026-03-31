package com.notifguard.data.repo

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import com.notifguard.data.db.AppDatabase
import com.notifguard.data.model.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class EvalResult(
    val action: RuleAction?,
    val rule: FilterRule?,
    val matchedAppPackage: String?,
    val matchedRegex: String?,
    val regexMatchSnippet: String?,
    val evaluatedRulesCount: Int,
    val customSoundUri: String? = null   // set when matched rule has a custom sound
)

data class SaveEvalResult(
    val action: SaveRuleAction?,
    val rule: SaveRule?
)

// ─── Schedule helpers ──────────────────────────────────────────────────────

fun isRuleActiveNow(
    scheduleType: ScheduleType,
    windowStart: String,
    windowEnd: String,
    timerExpiresAt: Long
): Boolean = when (scheduleType) {
    ScheduleType.ALWAYS -> true
    ScheduleType.TIMER  -> System.currentTimeMillis() < timerExpiresAt
    ScheduleType.TIME_WINDOW -> runCatching {
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        val now = LocalTime.now()
        val start = LocalTime.parse(windowStart, fmt)
        val end   = LocalTime.parse(windowEnd, fmt)
        if (start <= end) now in start..end else now >= start || now <= end
    }.getOrDefault(true)
}

// ─── Regex helpers ─────────────────────────────────────────────────────────

fun buildRegex(pattern: String, flags: String): Regex {
    val options = mutableSetOf<RegexOption>()
    if (flags.contains("IGNORE_CASE"))     options += RegexOption.IGNORE_CASE
    if (flags.contains("MULTILINE"))       options += RegexOption.MULTILINE
    if (flags.contains("DOT_MATCHES_ALL")) options += RegexOption.DOT_MATCHES_ALL
    return Regex(pattern, options)
}

class NotifGuardRepository(context: Context) {

    private val ctx           = context.applicationContext
    private val db            = AppDatabase.getInstance(context)
    private val groupDao      = db.ruleGroupDao()
    private val filterRuleDao = db.filterRuleDao()
    private val saveRuleDao   = db.saveRuleDao()
    private val notifDao      = db.savedNotificationDao()
    private val historyDao    = db.notifHistoryDao()
    private val logDao        = db.logEntryDao()

    // ─── Groups ───────────────────────────────────────────────────────────

    fun allGroups(): Flow<List<RuleGroup>> = groupDao.getAll()

    suspend fun addGroup(name: String): String {
        val id = UUID.randomUUID().toString()
        groupDao.insert(RuleGroup(id = id, name = name))
        return id
    }

    suspend fun updateGroup(group: RuleGroup) = groupDao.update(group)

    suspend fun deleteGroup(groupId: String) {
        filterRuleDao.deleteByGroupId(groupId)
        saveRuleDao.deleteByGroupId(groupId)
        groupDao.deleteById(groupId)
    }

    suspend fun ungroupRules(groupId: String) {
        filterRuleDao.ungroupByGroupId(groupId)
        saveRuleDao.ungroupByGroupId(groupId)
        groupDao.deleteById(groupId)
    }

    suspend fun setGroupEnabled(groupId: String, enabled: Boolean) {
        val group: RuleGroup? = groupDao.getAllOnce().find { g -> g.id == groupId }
        if (group != null) groupDao.update(group.copy(enabled = enabled))
        filterRuleDao.setEnabledForGroup(groupId, enabled)
    }

    suspend fun setGroupAction(groupId: String, action: RuleAction) {
        filterRuleDao.setActionForGroup(groupId, action)
    }

    suspend fun setGroupSchedule(groupId: String, type: ScheduleType, start: String, end: String, mins: Int) {
        val expires = if (type == ScheduleType.TIMER)
            System.currentTimeMillis() + mins * 60_000L else 0L
        filterRuleDao.setScheduleForGroup(groupId, type, start, end, mins, expires)
        val group: RuleGroup? = groupDao.getAllOnce().find { g -> g.id == groupId }
        if (group != null) groupDao.update(group.copy(scheduleType = type,
            scheduleWindowStart = start, scheduleWindowEnd = end,
            timerMinutes = mins, timerExpiresAt = expires))
    }

    // ─── Filter Rules ─────────────────────────────────────────────────────

    fun allFilterRules(): Flow<List<FilterRule>> = filterRuleDao.getAllRules()

    suspend fun addFilterRule(
        action: RuleAction, appPackage: String, regexPattern: String,
        regexFlags: String, note: String, priority: Int,
        groupId: String? = null, customSoundUri: String? = null,
        scheduleType: ScheduleType = ScheduleType.ALWAYS,
        scheduleWindowStart: String = "", scheduleWindowEnd: String = "",
        timerMinutes: Int = 0
    ) {
        val expires = if (scheduleType == ScheduleType.TIMER)
            System.currentTimeMillis() + timerMinutes * 60_000L else 0L
        filterRuleDao.insert(FilterRule(
            id = UUID.randomUUID().toString(), action = action,
            appPackage = appPackage, regexPattern = regexPattern, regexFlags = regexFlags,
            note = note, enabled = true, priority = priority, groupId = groupId,
            customSoundUri = customSoundUri, scheduleType = scheduleType,
            scheduleWindowStart = scheduleWindowStart, scheduleWindowEnd = scheduleWindowEnd,
            timerMinutes = timerMinutes, timerExpiresAt = expires
        ))
    }

    suspend fun deleteFilterRule(id: String) = filterRuleDao.deleteById(id)
    suspend fun updateFilterRule(rule: FilterRule) = filterRuleDao.update(rule)
    suspend fun toggleFilterRule(rule: FilterRule) = filterRuleDao.update(rule.copy(enabled = !rule.enabled))
    suspend fun reorderFilterRules(rules: List<FilterRule>) = filterRuleDao.reorderRules(rules)

    // ─── Save Rules ───────────────────────────────────────────────────────

    fun allSaveRules(): Flow<List<SaveRule>> = saveRuleDao.getAllRules()

    suspend fun addSaveRule(
        action: SaveRuleAction, appPackage: String, regexPattern: String,
        regexFlags: String, note: String, priority: Int,
        groupId: String? = null,
        scheduleType: ScheduleType = ScheduleType.ALWAYS,
        scheduleWindowStart: String = "", scheduleWindowEnd: String = "",
        timerMinutes: Int = 0
    ) {
        val expires = if (scheduleType == ScheduleType.TIMER)
            System.currentTimeMillis() + timerMinutes * 60_000L else 0L
        saveRuleDao.insert(SaveRule(
            id = UUID.randomUUID().toString(), action = action,
            appPackage = appPackage, regexPattern = regexPattern, regexFlags = regexFlags,
            note = note, enabled = true, priority = priority, groupId = groupId,
            scheduleType = scheduleType, scheduleWindowStart = scheduleWindowStart,
            scheduleWindowEnd = scheduleWindowEnd, timerMinutes = timerMinutes,
            timerExpiresAt = expires
        ))
    }

    suspend fun deleteSaveRule(id: String) = saveRuleDao.deleteById(id)
    suspend fun updateSaveRule(rule: SaveRule) = saveRuleDao.update(rule)
    suspend fun toggleSaveRule(rule: SaveRule) = saveRuleDao.update(rule.copy(enabled = !rule.enabled))
    suspend fun reorderSaveRules(rules: List<SaveRule>) = saveRuleDao.reorderRules(rules)

    // ─── Filter Engine ────────────────────────────────────────────────────

    suspend fun evaluate(packageName: String, title: String, body: String): EvalResult {
        val rules: List<FilterRule> = filterRuleDao.getAllRulesOnce()
            .filter { r -> r.enabled }
            .filter { r -> isRuleActiveNow(r.scheduleType, r.scheduleWindowStart, r.scheduleWindowEnd, r.timerExpiresAt) }
            .sortedBy { r -> r.priority }
        val text = "$title $body"
        var checked = 0
        for (rule: FilterRule in rules) {
            checked++
            val appMatches = rule.appPackage.isBlank() || rule.appPackage == packageName
            var regexMatches = true
            var snippet: String? = null
            var matchedRegex: String? = null
            if (rule.regexPattern.isNotBlank()) {
                regexMatches = runCatching {
                    val rx = buildRegex(rule.regexPattern, rule.regexFlags)
                    val found = rx.find(text)
                    if (found != null) { snippet = found.value.take(60); matchedRegex = rule.regexPattern; true }
                    else false
                }.getOrDefault(false)
            }
            if (appMatches && regexMatches) {
                return EvalResult(
                    action = rule.action,
                    rule = rule,
                    matchedAppPackage = if (rule.appPackage.isNotBlank()) rule.appPackage else null,
                    matchedRegex = matchedRegex,
                    regexMatchSnippet = snippet,
                    evaluatedRulesCount = checked,
                    customSoundUri = rule.customSoundUri
                )
            }
        }
        return EvalResult(null, null, null, null, null, checked)
    }

    // ─── Save Engine ──────────────────────────────────────────────────────

    suspend fun evaluateSave(packageName: String, title: String, body: String): SaveEvalResult {
        val rules: List<SaveRule> = saveRuleDao.getAllRulesOnce()
            .filter { r -> r.enabled }
            .filter { r -> isRuleActiveNow(r.scheduleType, r.scheduleWindowStart, r.scheduleWindowEnd, r.timerExpiresAt) }
            .sortedBy { r -> r.priority }
        val text = "$title $body"
        for (rule: SaveRule in rules) {
            val appMatches = rule.appPackage.isBlank() || rule.appPackage == packageName
            var regexMatches = true
            if (rule.regexPattern.isNotBlank()) {
                regexMatches = runCatching {
                    buildRegex(rule.regexPattern, rule.regexFlags).containsMatchIn(text)
                }.getOrDefault(false)
            }
            if (appMatches && regexMatches) return SaveEvalResult(rule.action, rule)
        }
        return SaveEvalResult(null, null)
    }

    // ─── Saved Notifications ──────────────────────────────────────────────

    fun notificationsByPackage(pkg: String): Flow<List<SavedNotification>> = notifDao.getByPackage(pkg)
    fun distinctPackages(): Flow<List<String>> = notifDao.getDistinctPackages()
    fun getHistory(threadId: String): Flow<List<NotifHistory>> = historyDao.getHistory(threadId)

    suspend fun saveOrUpdateNotification(
        packageName: String, appName: String, notifKey: String,
        title: String, body: String, intentUri: String? = null
    ): Boolean {
        val existing: SavedNotification? = notifDao.findByNotifKey(notifKey)
        return if (existing != null) {
            if (existing.body != body || existing.title != title) {
                historyDao.insert(NotifHistory(UUID.randomUUID().toString(),
                    existing.id, existing.title, existing.body, existing.latestAt))
                notifDao.update(existing.copy(title = title, body = body,
                    latestAt = System.currentTimeMillis(),
                    updateCount = existing.updateCount + 1,
                    intentUri = intentUri ?: existing.intentUri))
            }
            true
        } else {
            notifDao.insert(SavedNotification(UUID.randomUUID().toString(),
                packageName, appName, notifKey, title, body, intentUri = intentUri))
            false
        }
    }

    suspend fun deleteNotifications(ids: List<String>) {
        notifDao.deleteByIds(ids)
        historyDao.deleteByThreadIds(ids)
    }

    suspend fun deleteExpiredNotifications() {
        historyDao.deleteExpired()
        notifDao.deleteExpired()
    }

    // ─── Activity Log ─────────────────────────────────────────────────────

    fun recentLog(): Flow<List<LogEntry>> = logDao.getRecent()

    suspend fun addLog(
        packageName: String, appName: String, notifKey: String,
        title: String, body: String, isUpdate: Boolean,
        filterResult: EvalResult, saveResult: SaveEvalResult
    ) {
        logDao.insert(LogEntry(UUID.randomUUID().toString(), packageName, appName,
            notifKey, title, body, isUpdate, filterResult.action, saveResult.action,
            filterResult.rule?.id, filterResult.rule?.note, filterResult.matchedAppPackage,
            filterResult.matchedRegex, filterResult.regexMatchSnippet, filterResult.evaluatedRulesCount))
        logDao.deleteOlderThan(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000)
    }

    companion object {
        @Volatile private var INSTANCE: NotifGuardRepository? = null
        fun getInstance(context: Context): NotifGuardRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotifGuardRepository(context).also { INSTANCE = it }
            }
    }
}