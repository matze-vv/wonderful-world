package de.wonderfulworld.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

@Dao
interface RitualDao {
    @Query("SELECT * FROM rituals ORDER BY sortOrder, id") fun observeRituals(): Flow<List<Ritual>>
    @Query("SELECT * FROM rituals ORDER BY sortOrder, id") suspend fun rituals(): List<Ritual>
    @Insert suspend fun insertRitual(r: Ritual): Long
    @Update suspend fun updateRitual(r: Ritual)

    @Query("SELECT * FROM week_plans ORDER BY weekStart") fun observePlans(): Flow<List<WeekPlan>>
    @Query("SELECT * FROM week_plans ORDER BY weekStart") suspend fun plans(): List<WeekPlan>
    @Upsert suspend fun upsertPlan(p: WeekPlan)

    @Query("SELECT * FROM ritual_schedules") fun observeSchedules(): Flow<List<RitualSchedule>>
    @Query("SELECT * FROM ritual_schedules") suspend fun schedules(): List<RitualSchedule>
    @Query("DELETE FROM ritual_schedules WHERE weekStart = :week") suspend fun deleteSchedules(week: LocalDate)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSchedules(s: List<RitualSchedule>)

    @Query("SELECT * FROM ritual_checks") fun observeChecks(): Flow<List<RitualCheck>>
    @Query("SELECT * FROM ritual_checks WHERE date = :date") suspend fun checksOn(date: LocalDate): List<RitualCheck>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCheck(c: RitualCheck)
    @Query("DELETE FROM ritual_checks WHERE ritualId = :ritualId AND date = :date") suspend fun deleteCheck(ritualId: Long, date: LocalDate)
}

@Dao
interface MissionDao {
    @Query("SELECT * FROM missions ORDER BY month, createdAt") fun observeAll(): Flow<List<Mission>>
    @Query("SELECT * FROM missions WHERE id = :id") suspend fun byId(id: Long): Mission?
    @Query("SELECT * FROM missions WHERE month = :month ORDER BY createdAt") suspend fun forMonth(month: YearMonth): List<Mission>
    @Insert suspend fun insert(m: Mission): Long
    @Update suspend fun update(m: Mission)
    @Query("DELETE FROM missions WHERE id = :id") suspend fun delete(id: Long)

    /** Wöchentliche Missionen zurücksetzen, deren Fortschritt aus einer früheren Woche stammt. */
    @Query(
        "UPDATE missions SET progress = 0, progressWeek = :week " +
            "WHERE weekly = 1 AND (progressWeek IS NULL OR progressWeek < :week)"
    )
    suspend fun resetWeekly(week: LocalDate): Int

    @Query("SELECT * FROM month_plans") fun observeMonthPlans(): Flow<List<MonthPlan>>
    @Upsert suspend fun upsertMonthPlan(p: MonthPlan)
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries ORDER BY timestamp DESC") fun observeAll(): Flow<List<JournalEntry>>
    @Query("SELECT * FROM journal_entries WHERE id = :id") suspend fun byId(id: Long): JournalEntry?
    @Insert suspend fun insert(e: JournalEntry): Long
    @Update suspend fun update(e: JournalEntry)
    @Query("DELETE FROM journal_entries WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY dayOfWeek") fun observeAll(): Flow<List<AlarmSetting>>
    @Query("SELECT * FROM alarms ORDER BY dayOfWeek") suspend fun all(): List<AlarmSetting>
    @Upsert suspend fun upsert(a: AlarmSetting)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertDefaults(a: List<AlarmSetting>)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY sortOrder, id") fun observeAll(): Flow<List<Tag>>
    @Query("SELECT * FROM tags ORDER BY sortOrder, id") suspend fun all(): List<Tag>
    @Insert suspend fun insert(t: Tag): Long
    @Update suspend fun update(t: Tag)
    @Query("DELETE FROM tags WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE missions SET tagId = NULL WHERE tagId = :id") suspend fun untagMissions(id: Long)
}

/** Nur für Backup/Import. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM rituals") suspend fun rituals(): List<Ritual>
    @Query("SELECT * FROM week_plans") suspend fun plans(): List<WeekPlan>
    @Query("SELECT * FROM ritual_schedules") suspend fun schedules(): List<RitualSchedule>
    @Query("SELECT * FROM ritual_checks") suspend fun checks(): List<RitualCheck>
    @Query("SELECT * FROM missions") suspend fun missions(): List<Mission>
    @Query("SELECT * FROM month_plans") suspend fun monthPlans(): List<MonthPlan>
    @Query("SELECT * FROM journal_entries") suspend fun journal(): List<JournalEntry>
    @Query("SELECT * FROM alarms") suspend fun alarms(): List<AlarmSetting>
    @Query("SELECT * FROM tags") suspend fun tags(): List<Tag>

    @Insert suspend fun insertRituals(v: List<Ritual>)
    @Insert suspend fun insertPlans(v: List<WeekPlan>)
    @Insert suspend fun insertSchedules(v: List<RitualSchedule>)
    @Insert suspend fun insertChecks(v: List<RitualCheck>)
    @Insert suspend fun insertMissions(v: List<Mission>)
    @Insert suspend fun insertMonthPlans(v: List<MonthPlan>)
    @Insert suspend fun insertJournal(v: List<JournalEntry>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAlarms(v: List<AlarmSetting>)
    @Insert suspend fun insertTags(v: List<Tag>)
}
