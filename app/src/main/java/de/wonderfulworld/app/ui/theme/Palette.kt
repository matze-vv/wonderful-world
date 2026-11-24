package de.wonderfulworld.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import de.wonderfulworld.app.data.ThemeSeeds
import kotlin.math.max
import kotlin.math.min

/** Ein auswählbares Farbschema. */
data class SchemeDef(val id: String, val name: String, val description: String, val seeds: ThemeSeeds)

private fun seeds(bg: Long, primary: Long, ritual: Long, mission: Long, journal: Long) =
    ThemeSeeds(bg.toInt(), primary.toInt(), ritual.toInt(), mission.toInt(), journal.toInt())

/** Original + vier Jahreszeiten. Rituale bleiben rötlich, Missionen gelblich, Tagebuch bläulich. */
val SCHEMES: List<SchemeDef> = listOf(
    SchemeDef("original", "Original", "Papier, Terrakotta, Ocker",
        seeds(0xFFF8F4EE, 0xFFA4532F, 0xFFC4472D, 0xFFC99A06, 0xFF3E6A93)),
    SchemeDef("spring", "Frühling", "Frisches Grün, Kirschblüte, Zitrone",
        seeds(0xFFF5F8EE, 0xFF4E8A56, 0xFFD25F6B, 0xFFC4A00A, 0xFF4A88B8)),
    SchemeDef("summer", "Sommer", "Sand, Sonnenuntergang, Meer",
        seeds(0xFFFFF7E8, 0xFFD2622A, 0xFFE0503C, 0xFFE0A100, 0xFF1C8AA8)),
    SchemeDef("autumn", "Herbst", "Laub, Rost, Kürbis, Nebel",
        seeds(0xFFF5ECDF, 0xFF8A4321, 0xFFAE3E24, 0xFFC27C0E, 0xFF52708C)),
    SchemeDef("winter", "Winter", "Schnee, Tanne, Preiselbeere",
        seeds(0xFFF1F4F7, 0xFF2D5E52, 0xFFA8344F, 0xFFAD8F2A, 0xFF4C74A6)),
)

fun schemeSeeds(id: String, custom: ThemeSeeds?): ThemeSeeds =
    if (id == "custom" && custom != null) custom else (SCHEMES.firstOrNull { it.id == id } ?: SCHEMES[0]).seeds

// ------------------------------------------------------------------ Farbhilfen (rein, testbar)

/** WCAG-Kontrastverhältnis. */
fun contrast(a: Color, b: Color): Float {
    val la = a.luminance(); val lb = b.luminance()
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

/** Farbe in HSL (h 0..360, s/l 0..1). */
data class Hsl(val h: Float, val s: Float, val l: Float)

fun Color.toHsl(): Hsl {
    val mx = max(red, max(green, blue)); val mn = min(red, min(green, blue))
    val l = (mx + mn) / 2
    if (mx == mn) return Hsl(0f, 0f, l)
    val d = mx - mn
    val s = if (l > 0.5f) d / (2 - mx - mn) else d / (mx + mn)
    val h = when (mx) {
        red -> ((green - blue) / d + (if (green < blue) 6 else 0))
        green -> ((blue - red) / d + 2)
        else -> ((red - green) / d + 4)
    } * 60f
    return Hsl(h, s, l)
}

fun Hsl.toColor(): Color = Color.hsl(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

/** Helligkeit (HSL) auf einen Bereich begrenzen – so bleiben auch extreme eigene Farben brauchbar. */
fun Color.clampLightness(minL: Float, maxL: Float): Color {
    val hsl = toHsl()
    return if (hsl.l in minL..maxL) this else hsl.copy(l = hsl.l.coerceIn(minL, maxL)).toColor()
}

/** [fg] Richtung [toward] (schwarz/weiß) schieben, bis der Kontrast zu [bg] reicht. */
private fun ensure(fg: Color, bg: Color, ratio: Float, toward: Color): Color {
    var c = fg
    var t = 0f
    while (contrast(c, bg) < ratio && t < 1f) { t += 0.04f; c = lerp(fg, toward, t) }
    return c
}

private val Black = Color(0xFF000000)
private val White = Color(0xFFFFFFFF)

/** Gut lesbare Schrift auf einer Füllfarbe: helles Papier oder fast schwarz. */
private fun onColor(fill: Color, light: Color = Color(0xFFFFFBF6), dark: Color = Color(0xFF1C1A17)) =
    if (contrast(light, fill) >= contrast(dark, fill)) light else dark

// ------------------------------------------------------------------ Ableitung

fun deriveArea(accentSeed: Color, bg: Color, dark: Boolean): AreaColors = if (!dark) {
    // Akzent trägt Häkchen und Knopftexte: mindestens 3:1 zum Hintergrund
    val accent = ensure(accentSeed.clampLightness(0.28f, 0.52f), bg, 3f, Black)
    val container = lerp(bg, accent, 0.24f)
    AreaColors(
        container = container,
        content = ensure(lerp(accent, Black, 0.62f), container, 7f, Black),
        muted = ensure(lerp(accent, Black, 0.35f), container, 4.5f, Black),
        accent = accent,
        track = lerp(bg, accent, 0.42f),
        background = lerp(bg, accent, 0.07f),
    )
} else {
    val accent = ensure(lerp(accentSeed, White, 0.3f).clampLightness(0.6f, 0.74f), bg, 3f, White)
    val container = lerp(bg, accent, 0.22f)
    AreaColors(
        container = container,
        content = ensure(lerp(accent, White, 0.8f), container, 7f, White),
        muted = ensure(lerp(accent, White, 0.45f), container, 4.5f, White),
        accent = accent,
        track = lerp(bg, accent, 0.4f),
        background = lerp(bg, accent, 0.06f),
    )
}

/** Vollständiges Farbschema (Material + Bereichsfarben) aus den Grundfarben. */
fun deriveTheme(s: ThemeSeeds, dark: Boolean): Pair<ColorScheme, WwColors> {
    val primarySeed = Color(s.primary)
    val bg = if (!dark) Color(s.background).clampLightness(0.9f, 0.97f)
    else lerp(Color(0xFF141210), primarySeed, 0.08f)
    val areas = WwColors(
        ritual = deriveArea(Color(s.ritual), bg, dark),
        mission = deriveArea(Color(s.mission), bg, dark),
        journal = deriveArea(Color(s.journal), bg, dark),
    )
    val toward = if (dark) White else Black
    val away = if (dark) Black else White
    // Primärfarbe so weit abdunkeln (hell) bzw. aufhellen (dunkel), dass ihre Schrift lesbar ist
    val primary = if (!dark) ensure(primarySeed.clampLightness(0.25f, 0.45f), Color(0xFFFFFBF6), 4.5f, Black)
    else ensure(lerp(primarySeed, White, 0.35f).clampLightness(0.62f, 0.75f), Color(0xFF1C1A17), 4.5f, White)
    val ink = ensure(lerp(primary, toward, 0.85f), bg, 12f, toward)
    val inkVariant = ensure(lerp(ink, bg, 0.4f), bg, 4.5f, toward)
    val primaryContainer = lerp(bg, primary, if (dark) 0.3f else 0.2f)
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = primary,
        onPrimary = onColor(primary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = ensure(lerp(primary, toward, 0.6f), primaryContainer, 7f, toward),
        secondary = areas.journal.accent,
        onSecondary = onColor(areas.journal.accent),
        secondaryContainer = areas.journal.container,
        onSecondaryContainer = areas.journal.content,
        tertiary = areas.mission.accent,
        onTertiary = onColor(areas.mission.accent),
        tertiaryContainer = areas.mission.container,
        onTertiaryContainer = areas.mission.content,
        background = bg, onBackground = ink,
        surface = bg, onSurface = ink,
        surfaceVariant = lerp(bg, ink, 0.08f), onSurfaceVariant = inkVariant,
        surfaceContainerLowest = lerp(bg, away, 0.6f),
        surfaceContainerLow = lerp(bg, away, 0.3f),
        surfaceContainer = lerp(bg, ink, 0.04f),
        surfaceContainerHigh = lerp(bg, ink, 0.07f),
        surfaceContainerHighest = lerp(bg, ink, 0.10f),
        outline = lerp(bg, ink, 0.4f),
        outlineVariant = lerp(bg, ink, 0.14f),
        inverseSurface = ink, inverseOnSurface = bg, inversePrimary = lerp(primary, away, 0.5f),
        surfaceTint = primary,
    )
    return scheme to areas
}

/** Wie wird eine eigene Grundfarbe tatsächlich verwendet (nach Begrenzung)? Für die Vorschau. */
fun effectiveAccent(seed: Int, bg: Int) =
    ensure(Color(seed).clampLightness(0.28f, 0.52f), effectiveBackground(bg), 3f, Black)
fun effectiveBackground(seed: Int) = Color(seed).clampLightness(0.9f, 0.97f)

/** HSV (h 0..360, s/v 0..1). */
fun Color.toHsv(): Triple<Float, Float, Float> {
    val mx = max(red, max(green, blue)); val mn = min(red, min(green, blue))
    val d = mx - mn
    val h = when {
        d == 0f -> 0f
        mx == red -> 60f * (((green - blue) / d) % 6f)
        mx == green -> 60f * ((blue - red) / d + 2f)
        else -> 60f * ((red - green) / d + 4f)
    }.let { if (it < 0) it + 360f else it }
    return Triple(h, if (mx == 0f) 0f else d / mx, mx)
}

fun parseHex(text: String): Color? {
    val t = text.trim().removePrefix("#")
    if (t.length != 6 || !t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return Color(0xFF000000 or t.toLong(16))
}

fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

