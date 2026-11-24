package de.wonderfulworld.app.domain

import de.wonderfulworld.app.data.Ritual
import de.wonderfulworld.app.data.RitualCheck
import de.wonderfulworld.app.data.RitualSchedule
import de.wonderfulworld.app.data.WeekPlan
import java.time.LocalDate

const val DEFAULT_IMPULSE_MINUTES = 8 * 60

/**
 * Beantwortet "Ist Ritual X am Tag Y fällig?" anhand der Wochenpläne.
 * Wurde eine Woche nicht geplant, gilt der letzte Plan davor weiter.
 */
class RitualCalendar(
    plans: List<WeekPlan>,
    schedules: List<RitualSchedule>,
    rituals: List<Ritual>,
    checks: List<RitualCheck>,
) {
    private val plansSorted = plans.sortedBy { it.weekStart }
    private val masks: Map<LocalDate, Map<Long, Int>> =
        schedules.groupBy { it.weekStart }.mapValues { (_, l) -> l.associate { it.ritualId to it.daysMask } }
    private val ritualsById = rituals.associateBy { it.id }
    private val checkSet: Set<Pair<Long, LocalDate>> = checks.mapTo(HashSet()) { it.ritualId to it.date }

    /** Der für diese Woche gültige Plan (eigener oder der letzte davor). */
    fun effectivePlan(weekStart: LocalDate): WeekPlan? = plansSorted.lastOrNull { it.weekStart <= weekStart }

    fun isWeekPlanned(weekStart: LocalDate): Boolean = plansSorted.any { it.weekStart == weekStart }

    fun impulseMinutes(date: LocalDate): Int =
        effectivePlan(Dates.weekStart(date))?.impulseMinutes ?: DEFAULT_IMPULSE_MINUTES

    fun mask(ritualId: Long, weekStart: LocalDate): Int {
        val plan = effectivePlan(weekStart) ?: return 0
        return masks[plan.weekStart]?.get(ritualId) ?: 0
    }

    fun isDue(ritualId: Long, date: LocalDate): Boolean {
        val r = ritualsById[ritualId] ?: return false
        if (date < r.createdOn) return false
        if (r.archivedOn != null && date >= r.archivedOn) return false
        return mask(ritualId, Dates.weekStart(date)) and Dates.bit(date) != 0
    }

    fun isChecked(ritualId: Long, date: LocalDate) = (ritualId to date) in checkSet

    fun dueRituals(date: LocalDate): List<Ritual> = ritualsById.values
        .filter { isDue(it.id, date) }
        .sortedWith(compareBy({ it.sortOrder }, { it.id }))

    /**
     * Serie = Anzahl der fälligen Tage in Folge, an denen das Ritual erledigt wurde.
     * Ein heute fälliges, noch nicht erledigtes Ritual unterbricht die Serie nicht.
     */
    fun streak(ritualId: Long, today: LocalDate): Int {
        val r = ritualsById[ritualId] ?: return 0
        var streak = 0
        var d = today
        while (d >= r.createdOn) {
            if (isDue(ritualId, d)) {
                if (isChecked(ritualId, d)) streak++
                else if (d != today) break
            }
            d = d.minusDays(1)
        }
        return streak
    }

    data class Quote(val done: Int, val due: Int) {
        val percent: Int get() = if (due == 0) 0 else (done * 100 / due)
    }

    /** Erledigt/fällig für eine Tagesspanne (inklusive), optional nur für ein Ritual. */
    fun quote(from: LocalDate, to: LocalDate, ritualId: Long? = null): Quote {
        var due = 0
        var done = 0
        var d = from
        while (d <= to) {
            for (r in ritualsById.values) {
                if (ritualId != null && r.id != ritualId) continue
                if (isDue(r.id, d)) {
                    due++
                    if (isChecked(r.id, d)) done++
                }
            }
            d = d.plusDays(1)
        }
        return Quote(done, due)
    }
}
