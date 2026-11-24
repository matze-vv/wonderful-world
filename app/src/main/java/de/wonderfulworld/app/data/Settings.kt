package de.wonderfulworld.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Höhe einer Dashboard-Kachel. AUTO = variabel (passt sich dem Inhalt an). */
enum class TileSize { AUTO, XS, S, M, L, XL }

/** Größe einer Dashboard-Kachel: breit (ganze Zeile) oder schmal (halbe Zeile) und Höhe. */
@Serializable
data class TileLayout(val wide: Boolean, val size: TileSize = TileSize.AUTO)

@Serializable
data class AppSettings(
    /** Uhrzeit der Sonntags-Erinnerung, Minuten seit Mitternacht. */
    val sundayReminderMinutes: Int = 10 * 60,
    /** Vom Nutzer eingestellte Kachelgrößen, Schlüssel z. B. "rituals", "tag:3", "mission:7", "entry:12". */
    val tiles: Map<String, TileLayout> = emptyMap(),
    /** Vom Nutzer festgelegte Reihenfolge der Kacheln (Schlüssel wie bei [tiles]). */
    val tileOrder: List<String> = emptyList(),
)

/** Kleine Einstellungen in SharedPreferences (alles andere liegt in Room). */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val tilesSerializer = MapSerializer(String.serializer(), TileLayout.serializer())
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings

    private fun load() = AppSettings(
        sundayReminderMinutes = prefs.getInt("sundayReminderMinutes", AppSettings().sundayReminderMinutes),
        tiles = prefs.getString("tiles", null)
            ?.let { runCatching { json.decodeFromString(tilesSerializer, it) }.getOrNull() }
            ?: emptyMap(),
        tileOrder = prefs.getString("tileOrder", null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
    )

    fun update(s: AppSettings) {
        prefs.edit()
            .putInt("sundayReminderMinutes", s.sundayReminderMinutes)
            .putString("tiles", json.encodeToString(tilesSerializer, s.tiles))
            .putString("tileOrder", s.tileOrder.joinToString("\n"))
            .apply()
        _settings.value = s
    }

    /** Neue Reihenfolge speichern; Schlüssel gerade nicht sichtbarer Kacheln bleiben hinten erhalten. */
    fun setTileOrder(visible: List<String>) {
        val s = _settings.value
        update(s.copy(tileOrder = visible + s.tileOrder.filter { it !in visible }))
    }

    /** Kachelgröße setzen; null = zurück auf Standard. */
    fun setTile(key: String, layout: TileLayout?) {
        val s = _settings.value
        update(s.copy(tiles = if (layout == null) s.tiles - key else s.tiles + (key to layout)))
    }
}
