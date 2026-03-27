package com.notifguard.data.db

import androidx.room.*
import com.notifguard.data.model.*
import kotlinx.coroutines.flow.Flow

// ─── Filter Rule DAO ───────────────────────────────────────────────────────

@Dao
interface FilterRuleDao {
    @Query("SELECT * FROM filter_rules ORDER BY priority ASC")
    fun getAllRules(): Flow<List<FilterRule>>

    @Query("SELECT * FROM filter_rules ORDER BY priority ASC")
    suspend fun getAllRulesOnce(): List<FilterRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: FilterRule)

    @Update
    suspend fun update(rule: FilterRule)

    @Query("DELETE FROM filter_rules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE filter_rules SET priority = :priority WHERE id = :id")
    suspend fun updatePriority(id: String, priority: Int)

    @Transaction
    suspend fun reorderRules(rules: List<FilterRule>) {
        rules.forEachIndexed { index, rule -> updatePriority(rule.id, index) }
    }
}

// ─── Save Rule DAO ─────────────────────────────────────────────────────────

@Dao
interface SaveRuleDao {
    @Query("SELECT * FROM save_rules ORDER BY priority ASC")
    fun getAllRules(): Flow<List<SaveRule>>

    @Query("SELECT * FROM save_rules ORDER BY priority ASC")
    suspend fun getAllRulesOnce(): List<SaveRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: SaveRule)

    @Update
    suspend fun update(rule: SaveRule)

    @Query("DELETE FROM save_rules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE save_rules SET priority = :priority WHERE id = :id")
    suspend fun updatePriority(id: String, priority: Int)

    @Transaction
    suspend fun reorderRules(rules: List<SaveRule>) {
        rules.forEachIndexed { index, rule -> updatePriority(rule.id, index) }
    }
}

// ─── Saved Notification DAO ────────────────────────────────────────────────

@Dao
interface SavedNotificationDao {
    @Query("SELECT * FROM saved_notifications ORDER BY latestAt DESC")
    fun getAll(): Flow<List<SavedNotification>>

    @Query("SELECT * FROM saved_notifications WHERE packageName = :pkg ORDER BY latestAt DESC")
    fun getByPackage(pkg: String): Flow<List<SavedNotification>>

    @Query("SELECT DISTINCT packageName FROM saved_notifications ORDER BY packageName ASC")
    fun getDistinctPackages(): Flow<List<String>>

    @Query("SELECT * FROM saved_notifications WHERE notifKey = :key LIMIT 1")
    suspend fun findByNotifKey(key: String): SavedNotification?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notif: SavedNotification)

    @Update
    suspend fun update(notif: SavedNotification)

    @Query("DELETE FROM saved_notifications WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM saved_notifications WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())

    @Query("""SELECT * FROM saved_notifications
              WHERE packageName = :pkg AND (title LIKE :q OR body LIKE :q)
              ORDER BY latestAt DESC""")
    fun search(pkg: String, q: String): Flow<List<SavedNotification>>
}

// ─── Notification History DAO ──────────────────────────────────────────────

@Dao
interface NotifHistoryDao {
    @Query("SELECT * FROM notif_history WHERE threadId = :threadId ORDER BY recordedAt DESC")
    fun getHistory(threadId: String): Flow<List<NotifHistory>>

    @Query("SELECT * FROM notif_history WHERE threadId = :threadId ORDER BY recordedAt DESC")
    suspend fun getHistoryOnce(threadId: String): List<NotifHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: NotifHistory)

    @Query("DELETE FROM notif_history WHERE threadId IN (:threadIds)")
    suspend fun deleteByThreadIds(threadIds: List<String>)

    @Query("DELETE FROM notif_history WHERE threadId IN (SELECT id FROM saved_notifications WHERE expiresAt < :now)")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())
}

// ─── Activity Log DAO ──────────────────────────────────────────────────────

@Dao
interface LogEntryDao {
    @Query("SELECT * FROM activity_log ORDER BY timestamp DESC LIMIT 300")
    fun getRecent(): Flow<List<LogEntry>>

    // Find existing log entry by notifKey to detect if this is an update
    @Query("SELECT * FROM activity_log WHERE notifKey = :key ORDER BY timestamp DESC LIMIT 1")
    suspend fun findLatestByKey(key: String): LogEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LogEntry)

    @Update
    suspend fun update(entry: LogEntry)

    @Query("DELETE FROM activity_log WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
