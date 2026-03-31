package com.notifguard.data.db

import android.content.Context
import androidx.room.*
import com.notifguard.data.model.*

class Converters {
    @TypeConverter fun fromRuleAction(v: RuleAction?): String? = v?.name
    @TypeConverter fun toRuleAction(v: String?): RuleAction? = v?.let { RuleAction.valueOf(it) }
    @TypeConverter fun fromSaveRuleAction(v: SaveRuleAction?): String? = v?.name
    @TypeConverter fun toSaveRuleAction(v: String?): SaveRuleAction? = v?.let { SaveRuleAction.valueOf(it) }
    @TypeConverter fun fromScheduleType(v: ScheduleType?): String? = v?.name
    @TypeConverter fun toScheduleType(v: String?): ScheduleType? = v?.let { ScheduleType.valueOf(it) }
}

@Database(
    entities = [
        RuleGroup::class,
        FilterRule::class,
        SaveRule::class,
        SavedNotification::class,
        NotifHistory::class,
        LogEntry::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleGroupDao(): RuleGroupDao
    abstract fun filterRuleDao(): FilterRuleDao
    abstract fun saveRuleDao(): SaveRuleDao
    abstract fun savedNotificationDao(): SavedNotificationDao
    abstract fun notifHistoryDao(): NotifHistoryDao
    abstract fun logEntryDao(): LogEntryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "notifguard.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}