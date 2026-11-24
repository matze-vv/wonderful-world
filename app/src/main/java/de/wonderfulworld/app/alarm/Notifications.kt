package de.wonderfulworld.app.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.wonderfulworld.app.MainActivity
import de.wonderfulworld.app.R
import de.wonderfulworld.app.app
import de.wonderfulworld.app.domain.Dates
import kotlinx.coroutines.flow.first
import java.time.LocalDate

object Notifications {
    const val CH_ALARM = "wecker"
    const val CH_IMPULSE = "impuls"
    const val CH_PLANNING = "planung"

    private const val ID_IMPULSE = 2001
    private const val ID_PLANNING = 2002

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_ALARM, "Wecker", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Klingelnder Wecker"
                    setSound(null, null) // Ton spielt der AlarmService selbst
                    enableVibration(false)
                    setBypassDnd(true)
                },
                NotificationChannel(CH_IMPULSE, "Morgen-Impuls", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Heutige Rituale und eine offene Mission"
                },
                NotificationChannel(CH_PLANNING, "Sonntagsplanung", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Erinnerung an die Wochenplanung"
                },
            )
        )
    }

    fun mainIntent(context: Context, dest: String? = null, req: Int = 0): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (dest != null) i.putExtra(MainActivity.EXTRA_DEST, dest)
        return PendingIntent.getActivity(context, req, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Morgen-Impuls: heutige Rituale + eine zufällige offene Mission. */
    suspend fun showImpulse(context: Context) {
        val repo = context.app.repo
        val today = LocalDate.now()
        val rituals = repo.calendarNow().dueRituals(today).map { it.name }
        val month = Dates.missionMonth(today)
        val mission = repo.missions.first().filter { it.showsIn(month) && it.isOpen }.randomOrNull()

        val lines = buildList {
            add(if (rituals.isEmpty()) "Heute keine Rituale – Raum für etwas Schönes." else "Heute: " + rituals.joinToString(", "))
            if (mission != null) {
                add("Mission: " + mission.title + if (mission.counter) " (${mission.progress}/${mission.target})" else "")
            }
        }
        notify(
            context, ID_IMPULSE,
            NotificationCompat.Builder(context, CH_IMPULSE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Guten Morgen ☀")
                .setContentText(lines.first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
                .setContentIntent(mainIntent(context, req = ID_IMPULSE))
                .setAutoCancel(true)
                .build()
        )
    }

    fun showPlanningReminder(context: Context) {
        notify(
            context, ID_PLANNING,
            NotificationCompat.Builder(context, CH_PLANNING)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Zeit für deine Sonntagsplanung")
                .setContentText("Schau auf die letzte Woche zurück und plane deine Rituale.")
                .setContentIntent(mainIntent(context, dest = "planning", req = ID_PLANNING))
                .setAutoCancel(true)
                .build()
        )
    }

    private fun notify(context: Context, id: Int, n: android.app.Notification) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        try { nm.notify(id, n) } catch (_: SecurityException) { }
    }
}
