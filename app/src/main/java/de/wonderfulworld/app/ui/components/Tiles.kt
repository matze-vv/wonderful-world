package de.wonderfulworld.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Farbpalette für Tags (ARGB). */
val TAG_PALETTE: List<Int> = listOf(
    0xFF4C8A4A, 0xFF2F7F8F, 0xFF3E6A93, 0xFF8657B0,
    0xFFB4487A, 0xFFC4472D, 0xFFC77A12, 0xFF6F6A3A,
).map { it.toInt() }
val TAG_PALETTE_NAMES = listOf("Grün", "Petrol", "Blau", "Violett", "Beere", "Ziegel", "Orange", "Oliv")
/** Farbe der Kachel „Allgemein“ für Missionen ohne Tag. */
val GENERAL_TAG_COLOR = Color(0xFF8F8576)

fun nextTagColor(used: Collection<Int>): Int =
    TAG_PALETTE.firstOrNull { it !in used } ?: TAG_PALETTE[used.size % TAG_PALETTE.size]

/** Runder Abhak-Knopf (48 dp Touchfläche) mit kleiner Pop-Animation. */
@Composable
fun CheckCircle(
    checked: Boolean,
    color: Color,
    label: String,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val scale = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(checked) {
        if (first) { first = false; return@LaunchedEffect }
        if (checked) {
            scale.animateTo(1.3f, tween(110))
            scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
        }
    }
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .size(size + 24.dp)
            .clip(CircleShape)
            .toggleable(value = checked, role = Role.Checkbox) {
                if (it) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle(it)
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .clip(CircleShape)
                .background(if (checked) color else Color.Transparent)
                .border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(size * 0.7f))
        }
    }
}

/** Animierter Fortschrittsring. */
@Composable
fun ProgressRing(
    fraction: Float,
    color: Color,
    track: Color,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    stroke: Dp = 7.dp,
    content: @Composable () -> Unit = {},
) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val tl = Offset(sw / 2, sw / 2)
            val s = Size(this.size.width - sw, this.size.height - sw)
            drawArc(track, 0f, 360f, false, topLeft = tl, size = s, style = Stroke(sw))
            if (f > 0f) drawArc(color, -90f, 360f * f, false, topLeft = tl, size = s, style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Animierter Fortschrittsbalken. */
@Composable
fun ProgressLine(fraction: Float, color: Color, track: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(450), label = "bar")
    LinearProgressIndicator(
        progress = { f },
        modifier = modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)),
        color = color, trackColor = track, gapSize = 0.dp, drawStopIndicator = {},
    )
}

@Composable
fun TagChip(name: String, color: Color, modifier: Modifier = Modifier, large: Boolean = false) {
    Text(
        name,
        modifier = modifier.clip(RoundedCornerShape(50)).background(color)
            .padding(horizontal = if (large) 14.dp else 10.dp, vertical = if (large) 6.dp else 4.dp),
        color = Color.White,
        style = if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        softWrap = false,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}

@Composable
fun Streak(n: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.LocalFireDepartment, contentDescription = "Serie", tint = color, modifier = Modifier.size(16.dp))
        Text("$n", style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.width(4.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(
    title: String,
    onBack: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") } },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor, titleContentColor = contentColor,
            navigationIconContentColor = contentColor, actionIconContentColor = contentColor,
        ),
        windowInsets = WindowInsets(0),
    )
}

/** Wochenpunkte So–Sa: erledigt (gefüllt), fällig (Ring), nicht fällig (blass). */
@Composable
fun WeekDots(states: List<DotState>, color: Color, faint: Color, labels: List<String>) {
    Row(Modifier.fillMaxWidth()) {
        states.forEachIndexed { i, st ->
            androidx.compose.foundation.layout.Column(
                Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(labels[i], style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = .8f))
                Spacer(Modifier.height(4.dp))
                val m = Modifier.size(12.dp).clip(CircleShape)
                when (st) {
                    DotState.DONE -> Box(m.background(color))
                    DotState.MISSED -> Box(m.border(1.5.dp, color, CircleShape))
                    DotState.OPEN -> Box(m.border(1.5.dp, color.copy(alpha = .5f), CircleShape))
                    DotState.NONE -> Box(m.border(1.5.dp, faint, CircleShape))
                }
            }
        }
    }
}

enum class DotState { DONE, MISSED, OPEN, NONE }

@Composable
fun Gap(h: Int) = Spacer(Modifier.height(h.dp))

