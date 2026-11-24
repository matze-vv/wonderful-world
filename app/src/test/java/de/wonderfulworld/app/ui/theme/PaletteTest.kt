package de.wonderfulworld.app.ui.theme

import androidx.compose.ui.graphics.Color
import de.wonderfulworld.app.data.ThemeSeeds
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Alle Schemata – auch zufällige eigene – müssen gut lesbar sein (WCAG). */
class PaletteTest {
    private fun check(name: String, seeds: ThemeSeeds) {
        for (dark in listOf(false, true)) {
            val (cs, areas) = deriveTheme(seeds, dark)
            val tag = "$name ${if (dark) "dunkel" else "hell"}"
            fun need(what: String, fg: Color, bg: Color, min: Float) {
                val c = contrast(fg, bg)
                assertTrue("$tag: $what Kontrast $c < $min", c >= min)
            }
            need("Text/Hintergrund", cs.onBackground, cs.background, 7f)
            need("Nebentext/Hintergrund", cs.onSurfaceVariant, cs.background, 4.5f)
            need("Text auf Primär", cs.onPrimary, cs.primary, 4.5f)
            need("Text auf Primär-Container", cs.onPrimaryContainer, cs.primaryContainer, 4.5f)
            for ((area, a) in listOf("Rituale" to areas.ritual, "Missionen" to areas.mission, "Tagebuch" to areas.journal)) {
                need("$area Text", a.content, a.container, 4.5f)
                need("$area Nebentext", a.muted, a.container, 4.5f)
                need("$area Text auf Bereichs-Hintergrund", a.content, a.background, 4.5f)
                need("$area Akzent/Hintergrund (Häkchen, Knöpfe)", a.accent, cs.background, 3f)
            }
        }
    }

    @Test fun presetsAreReadable() = SCHEMES.forEach { check(it.name, it.seeds) }

    @Test fun randomCustomSchemesAreReadable() {
        val r = Random(42)
        repeat(1000) { i ->
            fun c() = (0xFF000000.toInt() or r.nextInt(0x1000000))
            check("zufällig #$i", ThemeSeeds(c(), c(), c(), c(), c()))
        }
    }

    @Test fun hslRoundTrip() {
        val c = Color(0xFFA4532F)
        val back = c.toHsl().toColor()
        assertTrue(contrast(c, back) < 1.02f)
    }
}
