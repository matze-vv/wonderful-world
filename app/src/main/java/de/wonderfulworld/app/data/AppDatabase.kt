package de.wonderfulworld.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Ritual::class, WeekPlan::class, RitualSchedule::class, RitualCheck::class,
        Mission::class, MonthPlan::class, JournalEntry::class, AlarmSetting::class, Tag::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ritualDao(): RitualDao
    abstract fun missionDao(): MissionDao
    abstract fun journalDao(): JournalDao
    abstract fun alarmDao(): AlarmDao
    abstract fun backupDao(): BackupDao
    abstract fun tagDao(): TagDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "wonderful-world.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v2: Detailtexte, Standard-Tage und Pausieren für Rituale, Tags für Missionen. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rituals ADD COLUMN detail TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rituals ADD COLUMN defaultMask INTEGER NOT NULL DEFAULT 127")
                db.execSQL("ALTER TABLE rituals ADD COLUMN pausedOn INTEGER")
                db.execSQL("ALTER TABLE missions ADD COLUMN detail TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE missions ADD COLUMN tagId INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS tags (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, color INTEGER NOT NULL, sortOrder INTEGER NOT NULL)"
                )
            }
        }
    }
}
