package com.yk.notiffilter.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun verdictToString(v: Verdict?): String? = v?.name
    @TypeConverter fun stringToVerdict(s: String?): Verdict? = s?.let(Verdict::valueOf)

    @TypeConverter fun sourceToString(v: VerdictSource?): String? = v?.name
    @TypeConverter fun stringToSource(s: String?): VerdictSource? = s?.let(VerdictSource::valueOf)

    @TypeConverter fun actionToString(v: LogAction?): String? = v?.name
    @TypeConverter fun stringToAction(s: String?): LogAction? = s?.let(LogAction::valueOf)
}

@Database(
    entities = [AppRule::class, TemplateVerdict::class, LogEntry::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class NotifFilterDatabase : RoomDatabase() {
    abstract fun appRules(): AppRuleDao
    abstract fun verdicts(): TemplateVerdictDao
    abstract fun log(): LogDao

    companion object {
        @Volatile private var instance: NotifFilterDatabase? = null

        fun get(context: Context): NotifFilterDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                NotifFilterDatabase::class.java,
                "notiffilter.db",
            ).build().also { instance = it }
        }
    }
}
