@file:UseSerializers(LocalDateSerializer::class, YearMonthSerializer::class)

package de.wonderfulworld.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate
import java.time.YearMonth

/** Ein Ritual. Welche Wochentage fällig sind, steht im Wochenplan ([RitualSchedule]). */
@Serializable
@Entity(tableName = "rituals")
data class Ritual(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdOn: LocalDate,
    val archivedOn: LocalDate? = null,
    val sortOrder: Int = 0,
)

/** Ergebnis einer Sonntagsplanung für die Woche ab [weekStart] (immer ein Sonntag). */
@Serializable
@Entity(tableName = "week_plans")
data class WeekPlan(
    @PrimaryKey val weekStart: LocalDate,
    val plannedAt: Long,
    /** Uhrzeit des Morgen-Impulses an Tagen ohne Wecker, Minuten seit Mitternacht. */
    val impulseMinutes: Int,
)

/** Fällige Wochentage eines Rituals in einer geplanten Woche. Bit 0 = Sonntag … Bit 6 = Samstag. */
@Serializable
@Entity(tableName = "ritual_schedules", primaryKeys = ["weekStart", "ritualId"])
data class RitualSchedule(
    val weekStart: LocalDate,
    val ritualId: Long,
    val daysMask: Int,
)

@Serializable
@Entity(tableName = "ritual_checks", primaryKeys = ["ritualId", "date"])
data class RitualCheck(
    val ritualId: Long,
    val date: LocalDate,
    val checkedAt: Long,
)

@Serializable
@Entity(tableName = "missions")
data class Mission(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val month: YearMonth,
    val title: String,
    /** true = Mission mit Zähler (z. B. 2/4), false = einfach erledigt/offen. */
    val counter: Boolean,
    val target: Int,
    val progress: Int = 0,
    /** null = aktiv, sonst [RESOLUTION_CARRIED] oder [RESOLUTION_ARCHIVED]. */
    val resolution: String? = null,
    val createdAt: Long,
) {
    val isDone: Boolean get() = progress >= target
    val isOpen: Boolean get() = !isDone && resolution == null

    companion object {
        const val RESOLUTION_CARRIED = "CARRIED"
        const val RESOLUTION_ARCHIVED = "ARCHIVED"
    }
}

/** Markiert, dass die Monatsplanung (Missionen) für [month] erledigt ist. */
@Serializable
@Entity(tableName = "month_plans")
data class MonthPlan(
    @PrimaryKey val month: YearMonth,
    val plannedAt: Long,
)

@Serializable
@Entity(tableName = "journal_entries")
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val text: String,
    /** Dateiname im App-Ordner "photos" oder null. */
    val photo: String? = null,
)

/** Wecker für einen Wochentag. [dayOfWeek] wie java.time.DayOfWeek (1 = Montag … 7 = Sonntag). */
@Serializable
@Entity(tableName = "alarms")
data class AlarmSetting(
    @PrimaryKey val dayOfWeek: Int,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
)
