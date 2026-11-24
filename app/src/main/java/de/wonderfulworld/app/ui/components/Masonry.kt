package de.wonderfulworld.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.wonderfulworld.app.data.TileLayout
import de.wonderfulworld.app.data.TileSize
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/** Feste Höhen der Größenstufen. XS = nur Titel, S = genau eine Mission, M = bisherige Mindestgröße. */
fun TileSize.height(): Dp? = when (this) {
    TileSize.AUTO -> null
    TileSize.XS -> 76.dp
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
 * Gespeicherte Reihenfolge mit den aktuell vorhandenen Kacheln zusammenführen:
 * bekannte Kacheln in gespeicherter Reihenfolge, neue an ihrer Standardposition.
 */
fun mergeOrder(saved: List<String>, defaults: List<String>): List<String> {
    val present = defaults.toSet()
    val result = saved.filter { it in present }.distinct().toMutableList()
    defaults.forEachIndexed { i, k -> if (k !in result) result.add(minOf(i, result.size), k) }
    return result
}

/** Eine Kachel im verschiebbaren Mauerwerk. */
class MasonryItem(
    val key: String,
    val wide: Boolean,
    val movable: Boolean,
    /** Gedrückt halten und loslassen, ohne zu verschieben. */
    val onHold: (() -> Unit)?,
    val content: @Composable () -> Unit,
)

/** Zustand während eine Kachel gezogen wird. */
class MasonryState {
    var dragKey by mutableStateOf<String?>(null)
        private set
    var dragPos by mutableStateOf(Offset.Zero)
        private set
    var order by mutableStateOf<List<String>>(emptyList())
        private set
    internal val slots = HashMap<String, Rect>()
    internal var grab = Offset.Zero
    internal var windowY = 0f
    private var lastTarget: String? = null

    internal fun start(key: String, base: List<String>, grabAt: Offset) {
        order = base
        dragPos = slots[key]?.topLeft ?: Offset.Zero
        grab = grabAt
        lastTarget = null
        dragKey = key
    }

    internal fun dragBy(d: Offset) {
        dragPos += d
        retarget()
    }

    internal fun scrolled(dy: Float) {
        dragPos += Offset(0f, dy)
        retarget()
    }

    /** Kachel unter der Mitte der gezogenen Kachel finden und die gezogene an deren Stelle setzen. */
    private fun retarget() {
        val k = dragKey ?: return
        val r = slots[k] ?: return
        val center = dragPos + Offset(r.width / 2, r.height / 2)
        val target = order.firstOrNull { it != k && slots[it]?.contains(center) == true }
        if (target == null) { lastTarget = null; return }
        if (target == lastTarget) return
        lastTarget = target
        val from = order.indexOf(k)
        val to = order.indexOf(target)
        order = order.toMutableList().apply { removeAt(from); add(to, k) }
    }

    internal fun end(): List<String> {
        dragKey = null
        return order
    }
}

/**
 * Zweispaltiges Mauerwerk: schmale Kacheln in die kürzere Spalte, breite über beide.
 * Jede Kachel hat ihre eigene Höhe. Gedrückt halten + ziehen verschiebt eine Kachel,
 * gedrückt halten + loslassen ruft [MasonryItem.onHold] auf.
 */
@Composable
fun ReorderableMasonry(
    items: List<MasonryItem>,
    order: List<String>,
    onReorder: (List<String>) -> Unit,
    listState: LazyListState?,
    viewport: () -> Pair<Float, Float>?,
    modifier: Modifier = Modifier,
    gap: Dp = 12.dp,
) {
    val state = remember { MasonryState() }
    val byKey = items.associateBy { it.key }
    val shown = (if (state.dragKey != null) state.order else order).filter { it in byKey }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val edge = with(density) { 96.dp.toPx() }
    val maxSpeed = with(density) { 18.dp.toPx() }
    val currentOrder by rememberUpdatedState(shown)
    val reorder by rememberUpdatedState(onReorder)

    // Automatisch scrollen, wenn die gezogene Kachel an den Rand kommt.
    LaunchedEffect(state.dragKey) {
        if (state.dragKey == null || listState == null) return@LaunchedEffect
        while (isActive) {
            withFrameNanos { }
            val (top, bottom) = viewport() ?: continue
            val y = state.windowY + state.dragPos.y + state.grab.y
            val speed = when {
                y < top + edge -> -maxSpeed * ((top + edge - y) / edge).coerceIn(0f, 1f)
                y > bottom - edge -> maxSpeed * ((y - (bottom - edge)) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (speed != 0f) {
                val consumed = listState.scrollBy(speed)
                if (consumed != 0f) state.scrolled(consumed)
            }
        }
    }

    Layout(
        content = {
            shown.forEach { k ->
                val item = byKey.getValue(k)
                key(k) {
                    val hold by rememberUpdatedState(item.onHold)
                    Box(
                        Modifier.layoutId(k).then(
                            if (!item.movable && item.onHold == null) Modifier
                            else Modifier.pointerInput(k, item.movable) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    // Warten: Loslassen oder Bewegen vor Ablauf = normaler Tap bzw. Scrollen.
                                    val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                        while (true) {
                                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!ch.pressed || ch.isConsumed) break
                                            if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                                        }
                                        true
                                    }
                                    if (early != null) return@awaitEachGesture
                                    // Gedrückt gehalten: ab jetzt gehört die Geste der Kachel.
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (item.movable) state.start(k, currentOrder, down.position)
                                    var total = Offset.Zero
                                    var moved = false
                                    while (true) {
                                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                        val d = ch.positionChange()
                                        ch.consume()
                                        if (!ch.pressed) break
                                        total += d
                                        if (!moved && total.getDistance() > viewConfiguration.touchSlop) moved = true
                                        if (moved && item.movable) state.dragBy(d)
                                    }
                                    if (item.movable) {
                                        val result = state.end()
                                        if (moved) reorder(result)
                                    }
                                    if (!moved) hold?.invoke()
                                }
                            }
                        ),
                    ) { item.content() }
                }
            }
        },
        modifier = modifier.onGloballyPositioned { state.windowY = it.positionInWindow().y },
    ) { measurables, constraints ->
        val g = gap.roundToPx()
        val w = constraints.maxWidth
        val half = (w - g) / 2
        val col = intArrayOf(0, 0)
        val dragging = state.dragKey
        val placed = measurables.map { m ->
            val k = m.layoutId as String
            val isWide = byKey[k]?.wide ?: false
            val p = m.measure(Constraints.fixedWidth(if (isWide) w else half))
            val (x, y) = if (isWide) {
                val y = maxOf(col[0], col[1])
                col[0] = y + p.height + g; col[1] = col[0]
                0 to y
            } else {
                val c = if (col[0] <= col[1]) 0 else 1
                val y = col[c]
                col[c] = y + p.height + g
                (if (c == 0) 0 else half + g) to y
            }
            state.slots[k] = Rect(x.toFloat(), y.toFloat(), (x + p.width).toFloat(), (y + p.height).toFloat())
            Triple(k, p, IntOffset(x, y))
        }
        state.slots.keys.retainAll(placed.map { it.first }.toSet())
        val h = (maxOf(col[0], col[1]) - g).coerceAtLeast(0)
        layout(w, h) {
            placed.forEach { (k, p, pos) ->
                if (k == dragging) {
                    val dp = state.dragPos
                    p.placeWithLayer(dp.x.roundToInt(), dp.y.roundToInt(), zIndex = 1f) {
                        scaleX = 1.04f; scaleY = 1.04f; shadowElevation = 24f; alpha = 0.95f
                    }
                } else {
                    p.place(pos)
                }
            }
        }
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
            if (size != TileSize.XS) Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(24.dp)
                    .background(Brush.verticalGradient(listOf(fade.copy(alpha = 0f), fade)))
            )
        }
    }
}

/** Menü zum Einstellen der Kachelgröße. [allowXs]: nur für Kacheln mit einer einzelnen Mission. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TileSizeSheet(
    title: String, layout: TileLayout, allowXs: Boolean, onChange: (TileLayout) -> Unit,
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
                TileSize.entries.filter { allowXs || it != TileSize.XS }.forEach { s ->
                    FilterChip(
                        selected = layout.size == s, onClick = { onChange(layout.copy(size = s)) },
                        label = { Text(s.label, fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(50),
                    )
                }
            }
            Muted("„Variabel“ passt die Höhe automatisch an, sodass alles sichtbar ist." + if (allowXs) " „XS“ zeigt nur den Titel." else "")
            Muted("Tipp: Gedrückt halten und ziehen verschiebt die Kachel.")
            TextButton(onClick = onReset) { Text("Auf Standard zurücksetzen") }
        }
    }
}
