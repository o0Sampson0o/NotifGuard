package com.notifguard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notifguard.data.model.*
import com.notifguard.data.repo.NotifGuardRepository
import com.notifguard.data.repo.isRuleActiveNow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(val packageName: String, val appName: String)

// ─── UI States ────────────────────────────────────────────────────────────

data class RulesUiState(
    val rules: List<FilterRule> = emptyList(),
    val groups: List<RuleGroup> = emptyList(),
    val searchQuery: String = ""    // can be group name to filter by group
) {
    val filtered: List<FilterRule> get() {
        if (searchQuery.isBlank()) return rules
        // Check if query matches a group name → show only rules in that group
        val matchedGroup = groups.firstOrNull { it.name.equals(searchQuery.trim(), ignoreCase = true) }
        return if (matchedGroup != null) {
            rules.filter { it.groupId == matchedGroup.id }
        } else {
            rules.filter {
                it.appPackage.contains(searchQuery, true) ||
                it.regexPattern.contains(searchQuery, true) ||
                it.note.contains(searchQuery, true)
            }
        }
    }
}

data class GroupsUiState(
    val groups: List<RuleGroup> = emptyList(),
    val filterRules: List<FilterRule> = emptyList()
) {
    fun rulesInGroup(groupId: String) = filterRules.filter { it.groupId == groupId }
    fun derivedAction(groupId: String): RuleAction? {
        val members = rulesInGroup(groupId)
        if (members.isEmpty()) return null
        return when {
            members.all { it.action == RuleAction.BLOCK }     -> RuleAction.BLOCK
            members.all { it.action == RuleAction.WHITELIST } -> RuleAction.WHITELIST
            else                                               -> null // mixed
        }
    }
    fun derivedEnabled(groupId: String): Boolean? {
        val members = rulesInGroup(groupId)
        if (members.isEmpty()) return null
        return when {
            members.all { it.enabled }  -> true
            members.none { it.enabled } -> false
            else                        -> null // mixed
        }
    }
}

data class SaveRulesUiState(
    val rules: List<SaveRule> = emptyList(),
    val searchQuery: String = ""
) {
    val filtered get() = if (searchQuery.isBlank()) rules else rules.filter {
        it.appPackage.contains(searchQuery, true) ||
        it.regexPattern.contains(searchQuery, true) ||
        it.note.contains(searchQuery, true)
    }
}

data class AppsUiState(
    val installedApps: List<AppInfo> = emptyList(),
    val searchQuery: String = ""
) {
    val filtered get() = if (searchQuery.isBlank()) installedApps else installedApps.filter {
        it.appName.contains(searchQuery, true) || it.packageName.contains(searchQuery, true)
    }
}

data class SavedUiState(
    val packages: List<String> = emptyList(),
    val appNameMap: Map<String, String> = emptyMap(),
    val selectedPackage: String = "",
    val notifications: List<SavedNotification> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val appSearchQuery: String = ""
) {
    val filteredNotifications get() = if (searchQuery.isBlank()) notifications else notifications.filter {
        it.title.contains(searchQuery, true) || it.body.contains(searchQuery, true)
    }
    val filteredPackages get() = if (appSearchQuery.isBlank()) packages else packages.filter {
        (appNameMap[it] ?: it).contains(appSearchQuery, true)
    }
    fun daysLeft(notif: SavedNotification): Int =
        ((notif.expiresAt - System.currentTimeMillis()) / 86_400_000).toInt().coerceAtLeast(0)
}

// ─── ViewModel ────────────────────────────────────────────────────────────

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = NotifGuardRepository.getInstance(app)
    private val pm: PackageManager = app.packageManager

    private val _rulesState     = MutableStateFlow(RulesUiState())
    val rulesState: StateFlow<RulesUiState> = _rulesState.asStateFlow()

    private val _groupsState    = MutableStateFlow(GroupsUiState())
    val groupsState: StateFlow<GroupsUiState> = _groupsState.asStateFlow()

    private val _saveRulesState = MutableStateFlow(SaveRulesUiState())
    val saveRulesState: StateFlow<SaveRulesUiState> = _saveRulesState.asStateFlow()

    private val _appsState      = MutableStateFlow(AppsUiState())
    val appsState: StateFlow<AppsUiState> = _appsState.asStateFlow()

    private val _savedState     = MutableStateFlow(SavedUiState())
    val savedState: StateFlow<SavedUiState> = _savedState.asStateFlow()

    val logEntries: StateFlow<List<LogEntry>> = repo.recentLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Currently selected rule for detail screen (null = show list)
    private val _selectedRule = MutableStateFlow<FilterRule?>(null)
    val selectedRule: StateFlow<FilterRule?> = _selectedRule.asStateFlow()

    init {
        loadFilterRules()
        loadGroups()
        loadSaveRules()
        loadInstalledApps()
        loadSavedPackages()
        viewModelScope.launch { repo.deleteExpiredNotifications() }
    }

    // ─── Filter Rules ─────────────────────────────────────────────────────

    private fun loadFilterRules() {
        viewModelScope.launch {
            repo.allFilterRules().collect { rules ->
                _rulesState.update { it.copy(rules = rules) }
                _groupsState.update { it.copy(filterRules = rules) }
            }
        }
    }

    fun setFilterRulesSearch(q: String) = _rulesState.update { it.copy(searchQuery = q) }
    fun selectRule(rule: FilterRule?) { _selectedRule.value = rule }

    fun addFilterRule(
        action: RuleAction, appPackage: String, regexPattern: String = "",
        regexFlags: String = "", note: String, groupId: String? = null,
        customSoundUri: String? = null,
        scheduleType: ScheduleType = ScheduleType.ALWAYS,
        scheduleWindowStart: String = "", scheduleWindowEnd: String = "",
        timerMinutes: Int = 0
    ) {
        viewModelScope.launch {
            repo.addFilterRule(action, appPackage, regexPattern, regexFlags, note,
                _rulesState.value.rules.size, groupId, customSoundUri,
                scheduleType, scheduleWindowStart, scheduleWindowEnd, timerMinutes)
        }
    }

    fun deleteFilterRule(id: String) = viewModelScope.launch {
        repo.deleteFilterRule(id)
        if (_selectedRule.value?.id == id) _selectedRule.value = null
    }

    fun updateFilterRule(rule: FilterRule) = viewModelScope.launch {
        repo.updateFilterRule(rule)
        if (_selectedRule.value?.id == rule.id) _selectedRule.value = rule
    }

    fun toggleFilterRule(rule: FilterRule) = viewModelScope.launch { repo.toggleFilterRule(rule) }

    fun moveFilterRule(rules: List<FilterRule>, fromIndex: Int, direction: Int) {
        val m = rules.toMutableList()
        val to = fromIndex + direction
        if (to < 0 || to >= m.size) return
        val tmp = m[fromIndex]; m[fromIndex] = m[to]; m[to] = tmp
        viewModelScope.launch { repo.reorderFilterRules(m) }
    }

    fun reorderFilterRules(rules: List<FilterRule>) {
        viewModelScope.launch { repo.reorderFilterRules(rules) }
    }

    fun isRuleCurrentlyActive(rule: FilterRule): Boolean =
        isRuleActiveNow(rule.scheduleType, rule.scheduleWindowStart, rule.scheduleWindowEnd, rule.timerExpiresAt)

    // ─── Groups ───────────────────────────────────────────────────────────

    private fun loadGroups() {
        viewModelScope.launch {
            repo.allGroups().collect { groups ->
                _groupsState.update { it.copy(groups = groups) }
                _rulesState.update { it.copy(groups = groups) }
            }
        }
    }

    fun addGroup(name: String) = viewModelScope.launch { repo.addGroup(name) }
    fun updateGroup(group: RuleGroup) = viewModelScope.launch { repo.updateGroup(group) }
    fun deleteGroup(groupId: String) = viewModelScope.launch { repo.deleteGroup(groupId) }
    fun ungroupRules(groupId: String) = viewModelScope.launch { repo.ungroupRules(groupId) }
    fun setGroupEnabled(groupId: String, enabled: Boolean) = viewModelScope.launch { repo.setGroupEnabled(groupId, enabled) }
    fun setGroupAction(groupId: String, action: RuleAction) = viewModelScope.launch { repo.setGroupAction(groupId, action) }
    fun setGroupSchedule(groupId: String, type: ScheduleType, start: String, end: String, mins: Int) =
        viewModelScope.launch { repo.setGroupSchedule(groupId, type, start, end, mins) }

    fun assignRuleToGroup(rule: FilterRule, groupId: String?) =
        updateFilterRule(rule.copy(groupId = groupId))

    // ─── Save Rules ───────────────────────────────────────────────────────

    private fun loadSaveRules() {
        viewModelScope.launch {
            repo.allSaveRules().collect { rules -> _saveRulesState.update { it.copy(rules = rules) } }
        }
    }

    fun setSaveRulesSearch(q: String) = _saveRulesState.update { it.copy(searchQuery = q) }

    fun addSaveRule(
        action: SaveRuleAction, appPackage: String, regexPattern: String = "",
        regexFlags: String = "", note: String,
        scheduleType: ScheduleType = ScheduleType.ALWAYS,
        scheduleWindowStart: String = "", scheduleWindowEnd: String = "",
        timerMinutes: Int = 0
    ) {
        viewModelScope.launch {
            repo.addSaveRule(action, appPackage, regexPattern, regexFlags, note,
                _saveRulesState.value.rules.size, null,
                scheduleType, scheduleWindowStart, scheduleWindowEnd, timerMinutes)
        }
    }

    fun deleteSaveRule(id: String) = viewModelScope.launch { repo.deleteSaveRule(id) }
    fun updateSaveRule(rule: SaveRule) = viewModelScope.launch { repo.updateSaveRule(rule) }
    fun toggleSaveRule(rule: SaveRule) = viewModelScope.launch { repo.toggleSaveRule(rule) }

    fun moveSaveRule(rules: List<SaveRule>, fromIndex: Int, direction: Int) {
        val m = rules.toMutableList()
        val to = fromIndex + direction
        if (to < 0 || to >= m.size) return
        val tmp = m[fromIndex]; m[fromIndex] = m[to]; m[to] = tmp
        viewModelScope.launch { repo.reorderSaveRules(m) }
    }

    fun reorderSaveRules(rules: List<SaveRule>) {
        viewModelScope.launch { repo.reorderSaveRules(rules) }
    }

    // ─── Apps ─────────────────────────────────────────────────────────────

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                          (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0 }
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { AppInfo(it.packageName, pm.getApplicationLabel(it).toString()) }
                .sortedBy { it.appName.lowercase() }
            withContext(Dispatchers.Main) { _appsState.value = _appsState.value.copy(installedApps = apps) }
        }
    }

    fun setAppsSearch(q: String) = _appsState.update { it.copy(searchQuery = q) }

    // ─── Saved ────────────────────────────────────────────────────────────

    private fun loadSavedPackages() {
        viewModelScope.launch {
            repo.distinctPackages().collect { pkgs ->
                val nameMap = pkgs.associateWith { pkg ->
                    runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                }
                val current = _savedState.value.selectedPackage
                val newSelected = if (current.isEmpty() && pkgs.isNotEmpty()) pkgs.first() else current
                _savedState.update { it.copy(packages = pkgs, appNameMap = nameMap, selectedPackage = newSelected) }
                if (newSelected.isNotEmpty()) loadNotificationsFor(newSelected)
            }
        }
    }

    fun selectPackage(pkg: String) {
        _savedState.update { it.copy(selectedPackage = pkg, selectedIds = emptySet(), searchQuery = "") }
        loadNotificationsFor(pkg)
    }

    private var notifJob: kotlinx.coroutines.Job? = null
    private fun loadNotificationsFor(pkg: String) {
        notifJob?.cancel()
        notifJob = viewModelScope.launch {
            repo.notificationsByPackage(pkg).collect { notifs ->
                _savedState.update { it.copy(notifications = notifs) }
            }
        }
    }

    fun setSavedSearch(q: String) = _savedState.update { it.copy(searchQuery = q) }
    fun setAppSearch(q: String)   = _savedState.update { it.copy(appSearchQuery = q) }

    fun toggleSelectNotif(id: String) {
        _savedState.update {
            val set = it.selectedIds.toMutableSet()
            if (id in set) set.remove(id) else set.add(id)
            it.copy(selectedIds = set)
        }
    }

    fun deleteSelected() {
        val ids = _savedState.value.selectedIds.toList()
        viewModelScope.launch {
            repo.deleteNotifications(ids)
            _savedState.update { it.copy(selectedIds = emptySet()) }
        }
    }

    fun getHistory(threadId: String) = repo.getHistory(threadId)

    fun exportSelected(context: Context): String {
        val state = _savedState.value
        return state.filteredNotifications.filter { it.id in state.selectedIds }.joinToString("\n") { n ->
            val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(n.latestAt))
            "[$ts] ${n.title}: ${n.body}"
        }
    }

    // ─── Test notification ────────────────────────────────────────────────

    private var testNotifCounter = 0

    fun sendTestNotification(context: Context, title: String, body: String, targetPackage: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val channelId = "notifguard_test"
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Test Notifications", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "NotifGuard rule testing" }
            )
        }
        // Embed the target package as an extra so our NotifListenerService
        // uses it for rule evaluation instead of com.notifguard.
        // This lets users simulate notifications from any app.
        val extras = android.os.Bundle().apply {
            if (targetPackage.isNotBlank()) putString("notifguard_target_pkg", targetPackage)
        }
        val notif = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title.ifBlank { "Test" })
            .setContentText(body.ifBlank { "(empty)" })
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .addExtras(extras)
            .build()
        nm.notify(testNotifCounter++, notif)
    }
}