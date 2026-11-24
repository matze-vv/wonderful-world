package de.wonderfulworld.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import de.wonderfulworld.app.app
import de.wonderfulworld.app.domain.Dates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** Führt Arbeit im Receiver asynchron aus, ohne dass der Prozess vorzeitig beendet wird. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try { block() } finally { pending.finish() }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.app
        when (intent.action) {
            ACTION_ALARM -> {
                val count = intent.getIntExtra(AlarmService.EXTRA_SNOOZE_COUNT, 0)
                ContextCompat.startForegroundService(context, AlarmService.startIntent(context, count))
                runAsync { app.scheduler.scheduleNextAlarm(LocalDateTime.now().plusMinutes(1)) }
            }
            ACTION_IMPULSE -> runAsync {
                Notifications.showImpulse(context)
                app.scheduler.scheduleNextImpulse(LocalDateTime.now().plusMinutes(1))
            }
            ACTION_SUNDAY -> runAsync {
                val week = Dates.weekStart(LocalDate.now())
                if (!app.repo.calendarNow().isWeekPlanned(week)) Notifications.showPlanningReminder(context)
                app.scheduler.scheduleSundayReminder(LocalDateTime.now().plusMinutes(1))
            }
        }
    }

    companion object {
        const val ACTION_ALARM = "de.wonderfulworld.app.ALARM"
        const val ACTION_IMPULSE = "de.wonderfulworld.app.IMPULSE"
        const val ACTION_SUNDAY = "de.wonderfulworld.app.SUNDAY"
    }
}

/** Nach Neustart, App-Update oder Zeitänderung alle Wecker und Erinnerungen neu setzen. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runAsync { context.app.scheduler.rescheduleAll() }
    }
}
