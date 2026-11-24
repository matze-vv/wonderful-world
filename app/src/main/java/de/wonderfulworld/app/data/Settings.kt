package de.wonderfulworld.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    /** Uhrzeit der Sonntags-Erinnerung, Minuten seit Mitternacht. */
    val sundayReminderMinutes: Int = 10 * 60,
)

/** Kleine Einstellungen in SharedPreferences (alles andere liegt in Room). */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings

    private fun load() = AppSettings(
        sundayReminderMinutes = prefs.getInt("sundayReminderMinutes", AppSettings().sundayReminderMinutes),
    )

    fun update(s: AppSettings) {
        prefs.edit().putInt("sundayReminderMinutes", s.sundayReminderMinutes).apply()
        _settings.value = s
    }
}
