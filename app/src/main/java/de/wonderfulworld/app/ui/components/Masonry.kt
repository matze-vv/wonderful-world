package de.wonderfulworld.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.wonderfulworld.app.data.TileLayout
import de.wonderfulworld.app.data.TileSize

/** Feste Höhen der Größenstufen. S = genau eine Mission, M = bisherige Mindestgröße. */
fun TileSize.height(): Dp? = when (this) {
    TileSize.AUTO -> null
    TileSize.S -> 120.dp
    TileSize.M -> 170.dp
    TileSize.L -> 240.dp
    TileSize.XL -> 320.dp
}

val TileSize.label: String get() = when (this) {
    TileSize.AUTO -> "Variabel"
    else -> name
}

/**
 * Zweispaltiges Mauerwerk-Layout: schmale Kacheln kommen in die kürzere Spalte,
 * breite Kacheln über beide Spalten. Jede Kachel behält ihre eigene Höhe –
 * Nachbarn wachsen nicht mit.
 */
@Composable
fun Masonry(wide: List<Boolean>, modifier: Modifier = Modifier, gap: Dp = 12.dp, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val g = gap.roundToPx()
        val w = constraints.maxWidth
        val half = (w - g) / 2
        val col = intArrayOf(0, 0)
        val placed = measurables.mapIndexed { i, m ->
            val isWide = wide.getOrElse(i) { false }
            val p = m.measure(Constraints.fixedWidth(if (isWide) w else half))
            if (isWide) {
                val y = maxOf(col[0], col[1])
                col[0] = y + p.height + g; col[1] = col[0]
                Triple(p, 0, y)
            } else {
                val c = if (col[0] <= col[1]) 0 else 1
                val y = col[c]
                col[c] = y + p.height + g
                Triple(p, if (c == 0) 0 else half + g, y)
            }
        }
        val h = (maxOf(col[0], col[1]) - g).coerceAtLeast(0)
        layout(w, h) { placed.forEach { (p, x, y) -> p.place(x, y) } }
    }
}

/**
 * Rahmen für den Inhalt einer Kachel mit fester oder variabler Höhe.
 * Bei fester Höhe wird überstehender Inhalt abgeschnitten und unten sanft ausgeblendet.
 */
@Composable
fun TileBox(
    size: TileSize, autoMin: Dp, fade: Color, modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val fixed = size.height()
    if (fixed == null) {
        Box(modifier.heightIn(min = autoMin), content = content)
    } else {
        Box(modifier.height(fixed).clipToBounds()) {
            Box(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true), content = content)
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(24.dp)
                    .background(Brush.verticalGradient(listOf(fade.copy(alpha = 0f), fade)))
            )
        }
    }
}

/** Menü zum Einstellen der Kachelgröße. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TileSizeSheet(
    title: String, layout: TileLayout, onChange: (TileLayout) -> Unit,
    onReset: () -> Unit, onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Kachelgröße", style = MaterialTheme.typography.titleLarge)
            Muted(title)
            Text("Breite", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(false to "Schmal", true to "Breit").forEachIndexed { i, (v, l) ->
                    SegmentedButton(
                        selected = layout.wide == v, onClick = { onChange(layout.copy(wide = v)) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2), icon = {},
                    ) { Text(l, fontWeight = FontWeight.Bold) }
                }
            }
            Text("Höhe", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TileSize.entries.forEach { s ->
                    FilterChip(
                        selected = layout.size == s, onClick = { onChange(layout.copy(size = s)) },
                        label = { Text(s.label, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(50),
                    )
                }
            }
            Muted("„Variabel“ passt die Höhe automatisch an, sodass alles sichtbar ist.")
            TextButton(onClick = onReset) { Text("Auf Standard zurücksetzen") }
        }
    }
}

