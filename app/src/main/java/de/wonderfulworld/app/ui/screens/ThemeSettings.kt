package de.wonderfulworld.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.wonderfulworld.app.app
import de.wonderfulworld.app.data.ThemeSeeds
import de.wonderfulworld.app.ui.components.BackTopBar
import de.wonderfulworld.app.ui.components.CheckCircle
import de.wonderfulworld.app.ui.components.Muted
import de.wonderfulworld.app.ui.components.ProgressLine
import de.wonderfulworld.app.ui.components.TagChip
import de.wonderfulworld.app.ui.theme.SCHEMES
import de.wonderfulworld.app.ui.theme.ThemeFrom
import de.wonderfulworld.app.ui.theme.Ww
import de.wonderfulworld.app.ui.theme.hex
import de.wonderfulworld.app.ui.theme.parseHex
import de.wonderfulworld.app.ui.theme.schemeSeeds
import de.wonderfulworld.app.ui.theme.themeFor
import de.wonderfulworld.app.ui.theme.toHsv
import androidx.compose.ui.platform.LocalContext

// ------------------------------------------------------------------ Auswahl in den Einstellungen

/** Farbschema wählen: Vorschau-Karten für Original, vier Jahreszeiten und das eigene Schema. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemePicker(onEditCustom: () -> Unit) {
    val store = LocalContext.current.app.settings
    val settings by store.settings.collectAsStateWithLifecycle()
    val dark = isSystemInDarkTheme()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 3,
        ) {
            SCHEMES.forEach { s ->
                SchemeCard(s.name, themeFor(s.id, null, dark), settings.theme == s.id, Modifier.weight(1f)) { store.setTheme(s.id) }
            }
            val custom = settings.customTheme
            if (custom != null) {
                SchemeCard("Eigenes", themeFor("custom", custom, dark), settings.theme == "custom", Modifier.weight(1f)) {
                    store.setTheme("custom")
                }
            } else {
                Box(Modifier.weight(1f))
            }
        }
        OutlinedButton(onClick = onEditCustom) {
            Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (settings.customTheme == null) "Eigenes Farbschema erstellen" else "Eigenes Farbschema bearbeiten")
        }
    }
}

@Composable
private fun SchemeCard(
    name: String, colors: Pair<androidx.compose.material3.ColorScheme, de.wonderfulworld.app.ui.theme.WwColors>,
    selected: Boolean, modifier: Modifier, onClick: () -> Unit,
) {
    val (cs, areas) = colors
    val ring = MaterialTheme.colorScheme.primary
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .border(if (selected) 3.dp else 1.dp, if (selected) ring else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Farbschema $name"; this.selected = selected },
    ) {
        // Mini-Dashboard
        Column(Modifier.fillMaxWidth().background(cs.background).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(5.dp)).background(areas.ritual.container)) {
                Box(Modifier.padding(4.dp).size(8.dp).clip(CircleShape).background(areas.ritual.accent))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.weight(1f).height(22.dp).clip(RoundedCornerShape(5.dp)).background(areas.mission.container)
                    .border(1.5.dp, areas.mission.accent, RoundedCornerShape(5.dp)))
                Box(Modifier.weight(1f).height(22.dp).clip(RoundedCornerShape(5.dp)).background(areas.journal.container))
            }
            Box(Modifier.align(Alignment.End).size(12.dp).clip(RoundedCornerShape(4.dp)).background(cs.primary))
        }
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (selected) Icon(Icons.Rounded.Check, null, tint = ring, modifier = Modifier.size(16.dp))
        }
    }
}

// ------------------------------------------------------------------ Editor für das eigene Schema

private val FIELDS = listOf(
    "Hintergrund" to "Bleibt immer hell – für den Dunkelmodus wird automatisch eine dunkle Variante berechnet.",
    "Akzent" to "Knöpfe, Navigation, Hervorhebungen.",
    "Rituale" to "Rituale-Kachel und Häkchen.",
    "Missionen" to "Missions-Kacheln.",
    "Tagebuch" to "Erlebnisse.",
)

private fun ThemeSeeds.get(i: Int) = when (i) { 0 -> background; 1 -> primary; 2 -> ritual; 3 -> mission; else -> journal }
private fun ThemeSeeds.with(i: Int, c: Int) = when (i) {
    0 -> copy(background = c); 1 -> copy(primary = c); 2 -> copy(ritual = c); 3 -> copy(mission = c); else -> copy(journal = c)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeEditorScreen(onDone: () -> Unit) {
    val store = LocalContext.current.app.settings
    val settings by store.settings.collectAsStateWithLifecycle()
    val initial = remember { settings.customTheme ?: schemeSeeds(settings.theme, null) }
    var seeds by remember { mutableStateOf(initial) }
    var field by rememberSaveable { mutableIntStateOf(1) }
    var confirmLeave by remember { mutableStateOf(false) }
    val dark = isSystemInDarkTheme()
    val back = { if (seeds != settings.customTheme && seeds != initial) confirmLeave = true else onDone() }
    BackHandler(onBack = back)
    val save = { store.setCustomTheme(seeds); onDone() }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackTopBar("Eigenes Farbschema", back) },
        bottomBar = {
            Button(onClick = save, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Text("Speichern & verwenden", fontWeight = FontWeight.ExtraBold)
            }
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Live-Vorschau – zeigt die berechneten Farben (auch im Dunkelmodus)
            val (cs, areas) = remember(seeds, dark) { themeFor("custom", seeds, dark) }
            ThemeFrom(cs, areas) { Preview() }

            Text("Ausgehend von", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SCHEMES.forEach { s ->
                    FilterChip(selected = seeds == s.seeds, onClick = { seeds = s.seeds }, label = { Text(s.name) }, shape = RoundedCornerShape(50))
                }
            }

            Text("Grundfarben", style = MaterialTheme.typography.labelLarge)
            FIELDS.forEachIndexed { i, (label, hint) ->
                val sel = field == i
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (sel) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                        .clickable { field = i }.padding(10.dp)
                        .semantics { this.selected = sel },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color(seeds.get(i)))
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                    Column(Modifier.weight(1f)) {
                        Text(label, fontWeight = FontWeight.Bold)
                        Muted(hint)
                    }
                    Text(Color(seeds.get(i)).hex(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (sel) ColorPicker(Color(seeds.get(i))) { seeds = seeds.with(i, it.toArgb()) }
            }
            Muted("Die App passt Helligkeit und Kontrast automatisch an, damit alles gut lesbar bleibt – die Vorschau oben zeigt das Ergebnis.")
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmLeave) AlertDialog(
        onDismissRequest = { confirmLeave = false },
        title = { Text("Farbschema speichern?") },
        text = { Text("Deine Änderungen sind noch nicht gespeichert.") },
        confirmButton = { TextButton(onClick = { confirmLeave = false; save() }) { Text("Speichern") } },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmLeave = false; onDone() }) { Text("Verwerfen", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { confirmLeave = false }) { Text("Abbrechen") }
            }
        },
    )
}

/** Kleine Vorschau im gerade bearbeiteten Schema. */
@Composable
private fun Preview() {
    val c = Ww.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Dienstag, 24. November", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.ritual.container).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CheckCircle(true, c.ritual.accent, "Beispiel", {})
            Column(Modifier.weight(1f)) {
                Text("Morgenspaziergang", color = c.ritual.content, fontWeight = FontWeight.Bold)
                Text("20 Minuten raus", color = c.ritual.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.mission.container)
                .border(2.dp, c.mission.accent, RoundedCornerShape(16.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TagChip("Draußen", c.mission.accent)
                Text("2× wandern", color = c.mission.content, fontWeight = FontWeight.Bold)
                ProgressLine(0.5f, c.mission.accent, c.mission.track)
            }
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.journal.container).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Heute · 07:48", Modifier.clip(RoundedCornerShape(50)).background(c.journal.accent).padding(horizontal = 8.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.surface, style = MaterialTheme.typography.labelSmall)
                Text("Nebel über dem Feld …", color = c.journal.content, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {}) { Text("Knopf") }
            Text("Nebentext", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private val QUICK = listOf(
    0xFFF8F4EE, 0xFFF5F8EE, 0xFFFFF7E8, 0xFFF1F4F7,
    0xFFA4532F, 0xFFC4472D, 0xFFD25F6B, 0xFFB4487A, 0xFF8657B0, 0xFF3E6A93, 0xFF1C8AA8, 0xFF2F7F8F,
    0xFF4E8A56, 0xFF6F6A3A, 0xFFC99A06, 0xFFE0A100, 0xFFC77A12, 0xFF2D5E52,
).map { Color(it) }

/** Farbwähler: Schnellauswahl, Farbton/Sättigung/Helligkeit, Hex-Eingabe. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(color: Color, onChange: (Color) -> Unit) {
    val (h0, s0, v0) = remember(color) { color.toHsv() }
    var hue by remember(color) { mutableStateOf(h0) }
    var sat by remember(color) { mutableStateOf(s0) }
    var value by remember(color) { mutableStateOf(v0) }
    var hexText by remember(color) { mutableStateOf(color.hex()) }
    fun emit(h: Float = hue, s: Float = sat, v: Float = value) = onChange(Color.hsv(h.coerceIn(0f, 359.9f), s, v))

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            QUICK.forEach { q ->
                Box(Modifier.size(32.dp).clip(CircleShape).background(q)
                    .border(if (q.toArgb() == color.toArgb()) 3.dp else 1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (q.toArgb() == color.toArgb()) 1f else .2f), CircleShape)
                    .clickable { onChange(q) }.semantics { contentDescription = "Farbe ${q.hex()}" })
            }
        }
        GradientSlider("Farbton", hue, 0f..360f, Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) })) { hue = it; emit(h = it) }
        GradientSlider("Sättigung", sat, 0f..1f, Brush.horizontalGradient(listOf(Color.hsv(hue, 0f, value), Color.hsv(hue, 1f, value)))) { sat = it; emit(s = it) }
        GradientSlider("Helligkeit", value, 0f..1f, Brush.horizontalGradient(listOf(Color.Black, Color.hsv(hue, sat, 1f)))) { value = it; emit(v = it) }
        OutlinedTextField(
            hexText, { t -> hexText = t; parseHex(t)?.let(onChange) },
            label = { Text("Hex") }, singleLine = true, shape = RoundedCornerShape(12.dp),
            isError = parseHex(hexText) == null, modifier = Modifier.width(160.dp),
        )
    }
}

@Composable
private fun GradientSlider(label: String, v: Float, range: ClosedFloatingPointRange<Float>, track: Brush, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(track))
            Slider(
                value = v, onValueChange = onChange, valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
                ),
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
    }
}
