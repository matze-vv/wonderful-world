package de.wonderfulworld.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Ritual::class, WeekPlan::class, RitualSchedule::class, RitualCheck::class,
        Mission::class, MonthPlan::class, JournalEntry::class, AlarmSetting::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ritualDao(): RitualDao
    abstract fun missionDao(): MissionDao
    abstract fun journalDao(): JournalDao
    abstract fun alarmDao(): AlarmDao
    abstract fun backupDao(): BackupDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "wonderful-world.db").build()
    }
}
