package de.wonderfulworld.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import de.wonderfulworld.app.MainActivity
import de.wonderfulworld.app.data.Repository
import de.wonderfulworld.app.data.SettingsStore
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Plant alle zeitgesteuerten Ereignisse über den AlarmManager:
 * - den nächsten Wecker (setAlarmClock, klingelt auch im Doze-Modus)
 * - Schlummern (3 Minuten)
 * - den Morgen-Impuls an Tagen ohne Wecker
 * - die Sonntags-Erinnerung
 */
class Scheduler(
    private val context: Context,
    private val repo: Repository,
    private val settings: SettingsStore,
) {
    private val am = context.getSystemService(AlarmManager::class.java)

    suspend fun rescheduleAll() {
        scheduleNextAlarm()
        scheduleNextImpulse()
        scheduleSundayReminder()
    }

    /** Nächster aktivierter Wecker nach [now] oder null. */
    suspend fun nextAlarmTime(now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val enabled = repo.alarmsNow().filter { it.enabled }.associateBy { it.dayOfWeek }
        for (i in 0L..7L) {
            val day = now.toLocalDate().plusDays(i)
            val a = enabled[day.dayOfWeek.value] ?: continue
            val t = day.atTime(a.hour, a.minute)
            if (t.isAfter(now)) return t
        }
        return null
    }

    /** [after]: Beim Auslösen eine Minute weiter rechnen, damit derselbe Termin nicht erneut geplant wird. */
    suspend fun scheduleNextAlarm(after: LocalDateTime = LocalDateTime.now()) {
        val pi = receiverIntent(REQ_ALARM, AlarmReceiver.ACTION_ALARM)
        val t = nextAlarmTime(after)
        if (t == null) am.cancel(pi) else setAlarmClock(t.millis(), pi)
    }

    fun scheduleSnooze(snoozeCount: Int) {
        val t = System.currentTimeMillis() + AlarmService.SNOOZE_MINUTES * 60_000L
        setAlarmClock(t, receiverIntent(REQ_SNOOZE, AlarmReceiver.ACTION_ALARM, snoozeCount))
    }

    fun cancelSnooze() = am.cancel(receiverIntent(REQ_SNOOZE, AlarmReceiver.ACTION_ALARM, 0))

    /** Zum Ausprobieren: Wecker in 10 Sekunden. */
    fun scheduleTestAlarm() {
        setAlarmClock(System.currentTimeMillis() + 10_000L, receiverIntent(REQ_TEST, AlarmReceiver.ACTION_ALARM))
    }

    /** Morgen-Impuls zur festen Zeit – nur an Tagen ohne aktiven Wecker (sonst folgt er dem Wecker). */
    suspend fun scheduleNextImpulse(after: LocalDateTime = LocalDateTime.now()) {
        val pi = receiverIntent(REQ_IMPULSE, AlarmReceiver.ACTION_IMPULSE)
        val now = after
        val alarmDays = repo.alarmsNow().filter { it.enabled }.map { it.dayOfWeek }.toSet()
        val cal = repo.calendarNow()
        for (i in 0L..8L) {
            val day = now.toLocalDate().plusDays(i)
            if (day.dayOfWeek.value in alarmDays) continue
            val minutes = cal.impulseMinutes(day)
            val t = day.atTime(minutes / 60, minutes % 60)
            if (t.isAfter(now)) {
                setExact(t.millis(), pi)
                return
            }
        }
        am.cancel(pi)
    }

    fun scheduleSundayReminder(after: LocalDateTime = LocalDateTime.now()) {
        val minutes = settings.settings.value.sundayReminderMinutes
        val now = after
        var t = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            .atTime(minutes / 60, minutes % 60)
        if (!t.isAfter(now)) t = t.plusWeeks(1)
        setExact(t.millis(), receiverIntent(REQ_SUNDAY, AlarmReceiver.ACTION_SUNDAY))
    }

    private fun setAlarmClock(millis: Long, pi: PendingIntent) {
        val show = PendingIntent.getActivity(
            context, REQ_SHOW, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_DEST, "alarms"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(millis, show), pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun setExact(millis: Long, pi: PendingIntent) {
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
    }

    private fun receiverIntent(req: Int, action: String, snoozeCount: Int = 0): PendingIntent {
        val i = Intent(context, AlarmReceiver::class.java)
            .setAction(action)
            .putExtra(AlarmService.EXTRA_SNOOZE_COUNT, snoozeCount)
        return PendingIntent.getBroadcast(context, req, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun LocalDateTime.millis() = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    companion object {
        private const val REQ_ALARM = 100
        private const val REQ_SNOOZE = 101
        private const val REQ_TEST = 102
        private const val REQ_SHOW = 103
        private const val REQ_IMPULSE = 200
        private const val REQ_SUNDAY = 300
    }
}
