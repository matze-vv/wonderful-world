package de.wonderfulworld.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

val GERMAN: Locale = Locale.GERMANY

object Dates {
    /** Die Woche beginnt am Sonntag. */
    fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    fun weekDays(weekStart: LocalDate): List<LocalDate> = (0L..6L).map { weekStart.plusDays(it) }

    /**
     * Der "Missionsmonat" eines Tages. Die Woche, die den Monatsersten enthält, gehört schon
     * komplett zum neuen Monat – an ihrem Sonntag findet die Monatsplanung statt.
     */
    fun missionMonth(d: LocalDate): YearMonth = YearMonth.from(weekStart(d).plusDays(6))

    /** Erster und letzter Tag (inklusive) eines Missionsmonats. */
    fun missionMonthRange(m: YearMonth): Pair<LocalDate, LocalDate> {
        val first = weekStart(m.atDay(1))
        val last = weekStart(m.plusMonths(1).atDay(1)).minusDays(1)
        return first to last
    }

    /** Ist an diesem Wochenstart auch die Monatsplanung fällig? */
    fun isMonthPlanningWeek(weekStart: LocalDate): Boolean =
        missionMonth(weekStart) != missionMonth(weekStart.minusDays(7))

    /** Bit für den Tag in der Wochenmaske (Sonntag = Bit 0 … Samstag = Bit 6). */
    fun bit(d: LocalDate): Int = 1 shl (d.dayOfWeek.value % 7)

    fun bit(dow: DayOfWeek): Int = 1 shl (dow.value % 7)

    /** Wochentage in Anzeige-Reihenfolge der App (So … Sa). */
    val WEEK_ORDER: List<DayOfWeek> = listOf(
        DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
    )
}

fun DayOfWeek.shortDe(): String = getDisplayName(TextStyle.SHORT, GERMAN).removeSuffix(".")
fun DayOfWeek.longDe(): String = getDisplayName(TextStyle.FULL, GERMAN)
fun YearMonth.labelDe(): String = month.getDisplayName(TextStyle.FULL_STANDALONE, GERMAN) + " " + year
fun YearMonth.monthNameDe(): String = month.getDisplayName(TextStyle.FULL_STANDALONE, GERMAN)

private val fullDate = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", GERMAN)
private val shortDate = DateTimeFormatter.ofPattern("d. MMM", GERMAN)
private val dayMonth = DateTimeFormatter.ofPattern("dd.MM.", GERMAN)

fun LocalDate.fullDe(): String = format(fullDate)
fun LocalDate.shortDe(): String = format(shortDate)
fun LocalDate.dmDe(): String = format(dayMonth)

fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
