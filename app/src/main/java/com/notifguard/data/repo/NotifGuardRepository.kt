package com.notifguard.data.repo

import android.content.Context
import com.notifguard.data.db.AppDatabase
import com.notifguard.data.model.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

data class EvalResult(
    val action: RuleAction?,
    val rule: FilterRule?,
    val matchedAppPackage: String?,
    val matchedRegex: String?,
    val regexMatchSnippet: String?,
    val evaluatedRulesCount: Int
)

data class SaveEvalResult(
    val action: SaveRuleAction?,   // null = default save
    val rule: SaveRule?
)

class NotifGuardRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val filterRuleDao = db.filterRuleDao()
    private val saveRuleDao   = db.saveRuleDao()
    private val notifDao      = db.savedNotificationDao()
    private val historyDao    = db.notifHistoryDao()
    private val logDao        = db.logEntryDao()

    // ─── Filter Rules ─────────────────────────────────────────────────────

    fun allFilterRules(): Flow<List<FilterRule>> = filterRuleDao.getAllRules()

    suspend fun addFilterRule(action: RuleAction, appPackage: String, regexPattern: String, note: String, priority: Int) {
        filterRuleDao.insert(FilterRule(UUID.randomUUID().toString(), action, appPackage, regexPattern, note, true, priority))
    }

    suspend fun deleteFilterRule(id: String) = filterRuleDao.deleteById(id)

    suspend fun updateFilterRule(rule: FilterRule) = filterRuleDao.update(rule)
    suspend fun toggleFilterRule(rule: FilterRule) = filterRuleDao.update(rule.copy(enabled = !rule.enabled))
    suspend fun reorderFilterRules(rules: List<FilterRule>) = filterRuleDao.reorderRules(rules)

    // ─── Save Rules ───────────────────────────────────────────────────────

    fun allSaveRules(): Flow<List<SaveRule>> = saveRuleDao.getAllRules()

    suspend fun addSaveRule(action: SaveRuleAction, appPackage: String, regexPattern: String, note: String, priority: Int) {
        saveRuleDao.insert(SaveRule(UUID.randomUUID().toString(), action, appPackage, regexPattern, note, true, priority))
    }

    suspend fun deleteSaveRule(id: String) = saveRuleDao.deleteById(id)

    suspend fun updateSaveRule(rule: SaveRule) = saveRuleDao.update(rule)
    suspend fun toggleSaveRule(rule: SaveRule) = saveRuleDao.update(rule.copy(enabled = !rule.enabled))
    suspend fun reorderSaveRules(rules: List<SaveRule>) = saveRuleDao.reorderRules(rules)

    // ─── Filter Engine ────────────────────────────────────────────────────

    suspend fun evaluate(packageName: String, title: String, body: String): EvalResult {
        val rules = filterRuleDao.getAllRulesOnce().filter { it.enabled }.sortedBy { it.priority }
        val text = "$title $body"
        var checked = 0
        for (rule in rules) {
            checked++
            val appMatches = rule.appPackage.isBlank() || rule.appPackage == packageName
            var regexMatches = true
            var snippet: String? = null
            var matchedRegex: String? = null
            if (rule.regexPattern.isNotBlank()) {
                regexMatches = runCatching {
                    val found = Regex(rule.regexPattern).find(text)
                    if (found != null) { snippet = found.value.take(60); matchedRegex = rule.regexPattern; true }
                    else false
                }.getOrDefault(false)
            }
            if (appMatches && regexMatches) {
                return EvalResult(rule.action, rule,
                    if (rule.appPackage.isNotBlank()) rule.appPackage else null,
                    matchedRegex, snippet, checked)
            }
        }
        return EvalResult(null, null, null, null, null, checked)
    }

    // ─── Save Engine ──────────────────────────────────────────────────────

    suspend fun evaluateSave(packageName: String, title: String, body: String): SaveEvalResult {
        val rules = saveRuleDao.getAllRulesOnce().filter { it.enabled }.sortedBy { it.priority }
        val text = "$title $body"
        for (rule in rules) {
            val appMatches = rule.appPackage.isBlank() || rule.appPackage == packageName
            var regexMatches = true
            if (rule.regexPattern.isNotBlank()) {
                regexMatches = runCatching { Regex(rule.regexPattern).containsMatchIn(text) }.getOrDefault(false)
            }
            if (appMatches && regexMatches) return SaveEvalResult(rule.action, rule)
        }
        return SaveEvalResult(null, null) // default: save
    }

    // ─── Saved Notifications (thread model) ───────────────────────────────

    fun notificationsByPackage(pkg: String): Flow<List<SavedNotification>> = notifDao.getByPackage(pkg)
    fun distinctPackages(): Flow<List<String>> = notifDao.getDistinctPackages()
    fun searchNotifications(pkg: String, query: String): Flow<List<SavedNotification>> = notifDao.search(pkg, "%$query%")
    fun getHistory(threadId: String): Flow<List<NotifHistory>> = historyDao.getHistory(threadId)

    /**
     * Save or update a notification.
     * If notifKey already exists → archive current content to history, update the thread row.
     * If new → insert fresh thread row.
     */
    suspend fun saveOrUpdateNotification(
        packageName: String,
        appName: String,
        notifKey: String,
        title: String,
        body: String
    ): Boolean { // returns true if this was an update
        val existing = notifDao.findByNotifKey(notifKey)
        return if (existing != null) {
            // Archive the old body to history before overwriting
            if (existing.body != body || existing.title != title) {
                historyDao.insert(NotifHistory(
                    id = UUID.randomUUID().toString(),
                    threadId = existing.id,
                    title = existing.title,
                    body = existing.body,
                    recordedAt = existing.latestAt
                ))
                notifDao.update(existing.copy(
                    title = title,
                    body = body,
                    latestAt = System.currentTimeMillis(),
                    updateCount = existing.updateCount + 1
                ))
            }
            true
        } else {
            notifDao.insert(SavedNotification(
                id = UUID.randomUUID().toString(),
                packageName = packageName,
                appName = appName,
                notifKey = notifKey,
                title = title,
                body = body
            ))
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

    /**
     * Log a notification event.
     * If a log entry with the same notifKey already exists AND content changed,
     * update that entry (increment update count implicitly via isUpdate flag + new body)
     * rather than inserting a duplicate row.
     */
    suspend fun addLog(
        packageName: String,
        appName: String,
        notifKey: String,
        title: String,
        body: String,
        isUpdate: Boolean,
        filterResult: EvalResult,
        saveResult: SaveEvalResult
    ) {
        logDao.insert(LogEntry(
            id = UUID.randomUUID().toString(),
            packageName = packageName,
            appName = appName,
            notifKey = notifKey,
            title = title,
            body = body,
            isUpdate = isUpdate,
            action = filterResult.action,
            saveAction = saveResult.action,
            matchedRuleId = filterResult.rule?.id,
            matchedRuleNote = filterResult.rule?.note,
            matchedAppPackage = filterResult.matchedAppPackage,
            matchedRegex = filterResult.matchedRegex,
            regexMatchSnippet = filterResult.regexMatchSnippet,
            evaluatedRulesCount = filterResult.evaluatedRulesCount
        ))
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
