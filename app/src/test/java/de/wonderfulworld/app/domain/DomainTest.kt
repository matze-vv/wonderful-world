package de.wonderfulworld.app.domain

import de.wonderfulworld.app.data.Ritual
import de.wonderfulworld.app.data.RitualCheck
import de.wonderfulworld.app.data.RitualSchedule
import de.wonderfulworld.app.data.WeekPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class DomainTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun weekStartsOnSunday() {
        assertEquals(d("2026-11-22"), Dates.weekStart(d("2026-11-24"))) // Di → So davor
        assertEquals(d("2026-11-22"), Dates.weekStart(d("2026-11-22"))) // So bleibt So
        assertEquals(d("2026-11-22"), Dates.weekStart(d("2026-11-28"))) // Sa
    }

    @Test fun weekContainingFirstBelongsToNewMonth() {
        // 29.11.2026 ist ein Sonntag; die Woche enthält den 1.12.
        assertEquals(YearMonth.of(2026, 12), Dates.missionMonth(d("2026-11-29")))
        assertEquals(YearMonth.of(2026, 11), Dates.missionMonth(d("2026-11-28")))
        assertTrue(Dates.isMonthPlanningWeek(d("2026-11-29")))
        assertFalse(Dates.isMonthPlanningWeek(d("2026-11-22")))
        // Monat beginnt an einem Sonntag (1.3.2026)
        assertTrue(Dates.isMonthPlanningWeek(d("2026-03-01")))
        assertEquals(YearMonth.of(2026, 2), Dates.missionMonth(d("2026-02-28")))
    }

    @Test fun maskBits() {
        assertEquals(1, Dates.bit(DayOfWeek.SUNDAY))
        assertEquals(1 shl 6, Dates.bit(DayOfWeek.SATURDAY))
    }

    private val monWedFri = Dates.bit(DayOfWeek.MONDAY) or Dates.bit(DayOfWeek.WEDNESDAY) or Dates.bit(DayOfWeek.FRIDAY)

    private fun calendar(checks: List<String>, plans: List<Pair<String, Int>> = listOf("2026-11-08" to monWedFri)) =
        RitualCalendar(
            plans = plans.map { WeekPlan(d(it.first), 0, 480) },
            schedules = plans.map { RitualSchedule(d(it.first), 1, it.second) },
            rituals = listOf(Ritual(1, "Laufen", createdOn = d("2026-11-08"))),
            checks = checks.map { RitualCheck(1, d(it), 0) },
        )

    @Test fun unplannedWeekKeepsPreviousPlan() {
        val cal = calendar(emptyList())
        assertTrue(cal.isDue(1, d("2026-11-23"))) // Montag, zwei Wochen später, ohne neue Planung
        assertFalse(cal.isDue(1, d("2026-11-24"))) // Dienstag
        assertFalse(cal.isWeekPlanned(d("2026-11-22")))
        assertFalse(cal.isDue(1, d("2026-11-02"))) // vor dem ersten Plan
    }

    @Test fun streakCountsDueDaysOnly() {
        // Fällig Mo/Mi/Fr. Erledigt: 16., 18., 20., 23. → Serie 4, Dienstage zählen nicht.
        val cal = calendar(listOf("2026-11-16", "2026-11-18", "2026-11-20", "2026-11-23"))
        assertEquals(4, cal.streak(1, d("2026-11-24")))
        // Heute (Mi 25.) fällig, aber noch offen → Serie bleibt 4
        assertEquals(4, cal.streak(1, d("2026-11-25")))
        // Am Freitag ist Mittwoch verpasst → Serie 0
        assertEquals(0, cal.streak(1, d("2026-11-27")))
    }

    @Test fun quoteForWeek() {
        val cal = calendar(listOf("2026-11-16", "2026-11-20"))
        val q = cal.quote(d("2026-11-15"), d("2026-11-21"))
        assertEquals(2, q.done)
        assertEquals(3, q.due)
    }

    @Test fun newPlanOverridesFromItsWeek() {
        val sundayOnly = Dates.bit(DayOfWeek.SUNDAY)
        val cal = calendar(emptyList(), listOf("2026-11-08" to monWedFri, "2026-11-22" to sundayOnly))
        assertTrue(cal.isDue(1, d("2026-11-16")))
        assertFalse(cal.isDue(1, d("2026-11-23")))
        assertTrue(cal.isDue(1, d("2026-11-22")))
    }
}
