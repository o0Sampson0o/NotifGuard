package com.notifguard

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notifguard.data.model.*
import com.notifguard.data.repo.NotifGuardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(val packageName: String, val appName: String)

// ─── Filter Rules state ────────────────────────────────────────────────────

data class RulesUiState(
    val rules: List<FilterRule> = emptyList(),
    val searchQuery: String = ""
) {
    val filtered get() = if (searchQuery.isBlank()) rules else rules.filter {
        it.appPackage.contains(searchQuery, true) ||
        it.regexPattern.contains(searchQuery, true) ||
        it.note.contains(searchQuery, true)
    }
}

// ─── Save Rules state ──────────────────────────────────────────────────────

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

// ─── Apps state ────────────────────────────────────────────────────────────

data class AppsUiState(
    val installedApps: List<AppInfo> = emptyList(),
    val searchQuery: String = ""
) {
    val filtered get() = if (searchQuery.isBlank()) installedApps else installedApps.filter {
        it.appName.contains(searchQuery, true) || it.packageName.contains(searchQuery, true)
    }
}

// ─── Saved state ───────────────────────────────────────────────────────────

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

    private val _saveRulesState = MutableStateFlow(SaveRulesUiState())
    val saveRulesState: StateFlow<SaveRulesUiState> = _saveRulesState.asStateFlow()

    private val _appsState      = MutableStateFlow(AppsUiState())
    val appsState: StateFlow<AppsUiState> = _appsState.asStateFlow()

    private val _savedState     = MutableStateFlow(SavedUiState())
    val savedState: StateFlow<SavedUiState> = _savedState.asStateFlow()

    val logEntries: StateFlow<List<LogEntry>> = repo.recentLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadFilterRules()
        loadSaveRules()
        loadInstalledApps()
        loadSavedPackages()
        viewModelScope.launch { repo.deleteExpiredNotifications() }
    }

    // ─── Filter Rules ─────────────────────────────────────────────────────

    private fun loadFilterRules() {
        viewModelScope.launch {
            repo.allFilterRules().collect { rules -> _rulesState.update { it.copy(rules = rules) } }
        }
    }

    fun setFilterRulesSearch(q: String) = _rulesState.update { it.copy(searchQuery = q) }

    fun addFilterRule(action: RuleAction, appPackage: String, regexPattern: String, note: String) {
        viewModelScope.launch {
            repo.addFilterRule(action, appPackage, regexPattern, note, _rulesState.value.rules.size)
        }
    }

    fun deleteFilterRule(id: String) = viewModelScope.launch { repo.deleteFilterRule(id) }
    fun toggleFilterRule(rule: FilterRule) = viewModelScope.launch { repo.toggleFilterRule(rule) }

    fun moveFilterRule(rules: List<FilterRule>, fromIndex: Int, direction: Int) {
        val m = rules.toMutableList()
        val to = fromIndex + direction
        if (to < 0 || to >= m.size) return
        val tmp = m[fromIndex]; m[fromIndex] = m[to]; m[to] = tmp
        viewModelScope.launch { repo.reorderFilterRules(m) }
    }

    // ─── Save Rules ───────────────────────────────────────────────────────

    private fun loadSaveRules() {
        viewModelScope.launch {
            repo.allSaveRules().collect { rules -> _saveRulesState.update { it.copy(rules = rules) } }
        }
    }

    fun setSaveRulesSearch(q: String) = _saveRulesState.update { it.copy(searchQuery = q) }

    fun addSaveRule(action: SaveRuleAction, appPackage: String, regexPattern: String, note: String) {
        viewModelScope.launch {
            repo.addSaveRule(action, appPackage, regexPattern, note, _saveRulesState.value.rules.size)
        }
    }

    fun deleteSaveRule(id: String) = viewModelScope.launch { repo.deleteSaveRule(id) }
    fun toggleSaveRule(rule: SaveRule) = viewModelScope.launch { repo.toggleSaveRule(rule) }

    fun moveSaveRule(rules: List<SaveRule>, fromIndex: Int, direction: Int) {
        val m = rules.toMutableList()
        val to = fromIndex + direction
        if (to < 0 || to >= m.size) return
        val tmp = m[fromIndex]; m[fromIndex] = m[to]; m[to] = tmp
        viewModelScope.launch { repo.reorderSaveRules(m) }
    }

    // ─── Apps ─────────────────────────────────────────────────────────────

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val allApps = pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
            val apps = allApps.filter { info ->
                (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            }.filter { info ->
                pm.getLaunchIntentForPackage(info.packageName) != null
            }.map { info ->
                AppInfo(info.packageName, pm.getApplicationLabel(info).toString())
            }.sortedBy { it.appName.lowercase() }
            withContext(Dispatchers.Main) {
                _appsState.value = _appsState.value.copy(installedApps = apps)
            }
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
}
