package de.wonderfulworld.app.data

import androidx.room.withTransaction
import de.wonderfulworld.app.domain.Dates
import de.wonderfulworld.app.domain.RitualCalendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class Repository(val db: AppDatabase, val photos: PhotoStore) {
    private val ritualDao = db.ritualDao()
    private val missionDao = db.missionDao()
    private val journalDao = db.journalDao()
    private val alarmDao = db.alarmDao()
    private val tagDao = db.tagDao()

    // ---------- Rituale ----------

    val rituals: Flow<List<Ritual>> = ritualDao.observeRituals()

    val calendar: Flow<RitualCalendar> = combine(
        ritualDao.observePlans(), ritualDao.observeSchedules(),
        ritualDao.observeRituals(), ritualDao.observeChecks(),
    ) { p, s, r, c -> RitualCalendar(p, s, r, c) }

    suspend fun calendarNow(): RitualCalendar = RitualCalendar(
        ritualDao.plans(), ritualDao.schedules(), ritualDao.rituals(), db.backupDao().checks(),
    )

    suspend fun activeRituals(): List<Ritual> = ritualDao.rituals().filter { it.isActive }

    suspend fun setChecked(ritualId: Long, date: LocalDate, checked: Boolean) {
        if (checked) ritualDao.insertCheck(RitualCheck(ritualId, date, System.currentTimeMillis()))
        else ritualDao.deleteCheck(ritualId, date)
    }

    suspend fun addRitual(name: String, today: LocalDate, detail: String = "", defaultMask: Int = 0b1111111): Ritual {
        val order = (ritualDao.rituals().maxOfOrNull { it.sortOrder } ?: 0) + 1
        val r = Ritual(name = name.trim(), createdOn = today, sortOrder = order, detail = detail.trim(), defaultMask = defaultMask)
        return r.copy(id = ritualDao.insertRitual(r))
    }

    suspend fun updateRitual(r: Ritual) = ritualDao.updateRitual(r.copy(name = r.name.trim(), detail = r.detail.trim()))

    suspend fun setPaused(r: Ritual, paused: Boolean, today: LocalDate) =
        ritualDao.updateRitual(r.copy(pausedOn = if (paused) today else null))

    suspend fun archiveRitual(r: Ritual, today: LocalDate) = ritualDao.updateRitual(r.copy(archivedOn = today))

    /** Speichert die Sonntagsplanung für die Woche ab [weekStart]. */
    suspend fun saveWeekPlan(weekStart: LocalDate, masks: Map<Long, Int>, impulseMinutes: Int) {
        db.withTransaction {
            ritualDao.upsertPlan(WeekPlan(weekStart, System.currentTimeMillis(), impulseMinutes))
            ritualDao.deleteSchedules(weekStart)
            ritualDao.insertSchedules(masks.map { (id, mask) -> RitualSchedule(weekStart, id, mask) })
        }
    }

    // ---------- Missionen ----------

    val missions: Flow<List<Mission>> = missionDao.observeAll()
    val monthPlans: Flow<List<MonthPlan>> = missionDao.observeMonthPlans()

    suspend fun missionsFor(month: YearMonth) = missionDao.forMonth(month)

    suspend fun mission(id: Long) = missionDao.byId(id)

    suspend fun addMission(
        month: YearMonth, title: String, counter: Boolean, target: Int,
        detail: String = "", tagId: Long? = null, weekly: Boolean = false,
    ) {
        missionDao.insert(
            Mission(
                month = month, title = title.trim(), counter = counter,
                target = if (counter) target.coerceAtLeast(1) else 1,
                createdAt = System.currentTimeMillis(),
                detail = detail.trim(), tagId = tagId, weekly = weekly,
                progressWeek = if (weekly) Dates.weekStart(LocalDate.now()) else null,
            )
        )
    }

    /** Wöchentliche Missionen zu Beginn einer neuen Woche (Sonntag) zurücksetzen. */
    suspend fun resetWeeklyMissions(today: LocalDate = LocalDate.now()) =
        missionDao.resetWeekly(Dates.weekStart(today))

    // ---------- Tags ----------

    val tags: Flow<List<Tag>> = tagDao.observeAll()

    suspend fun addTag(name: String, color: Int): Tag {
        val order = (tagDao.all().maxOfOrNull { it.sortOrder } ?: 0) + 1
        val t = Tag(name = name.trim(), color = color, sortOrder = order)
        return t.copy(id = tagDao.insert(t))
    }

    suspend fun updateTag(t: Tag) = tagDao.update(t.copy(name = t.name.trim()))

    suspend fun deleteTag(t: Tag) = db.withTransaction {
        tagDao.untagMissions(t.id)
        tagDao.delete(t.id)
    }

    suspend fun updateMission(m: Mission) = missionDao.update(
        m.copy(
            progress = m.progress.coerceIn(0, m.target),
            progressWeek = if (m.weekly) Dates.weekStart(LocalDate.now()) else null,
        )
    )

    suspend fun deleteMission(m: Mission) = missionDao.delete(m.id)

    /** Offene Mission in den neuen Monat übernehmen (Fortschritt bleibt erhalten). */
    suspend fun carryOver(m: Mission, to: YearMonth) = db.withTransaction {
        missionDao.insert(m.copy(id = 0, month = to, resolution = null, createdAt = System.currentTimeMillis()))
        missionDao.update(m.copy(resolution = Mission.RESOLUTION_CARRIED))
    }

    suspend fun archiveMission(m: Mission) = missionDao.update(m.copy(resolution = Mission.RESOLUTION_ARCHIVED))

    suspend fun markMonthPlanned(month: YearMonth) =
        missionDao.upsertMonthPlan(MonthPlan(month, System.currentTimeMillis()))

    // ---------- Tagebuch ----------

    val journal: Flow<List<JournalEntry>> = journalDao.observeAll()

    suspend fun entry(id: Long) = journalDao.byId(id)

    suspend fun saveEntry(e: JournalEntry): Long =
        if (e.id == 0L) journalDao.insert(e) else { journalDao.update(e); e.id }

    suspend fun deleteEntry(e: JournalEntry) {
        journalDao.delete(e.id)
        e.photo?.let(photos::delete)
    }

    // ---------- Wecker ----------

    val alarms: Flow<List<AlarmSetting>> = alarmDao.observeAll()

    suspend fun alarmsNow() = alarmDao.all()

    suspend fun saveAlarm(a: AlarmSetting) = alarmDao.upsert(a)

    suspend fun ensureDefaults() {
        alarmDao.insertDefaults(DayOfWeek.entries.map { AlarmSetting(it.value, false, 7, 0) })
    }

    fun weekStart(d: LocalDate) = Dates.weekStart(d)
}
