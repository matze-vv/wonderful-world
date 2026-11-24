package de.wonderfulworld.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.wonderfulworld.app.WonderfulWorldApp
import de.wonderfulworld.app.data.JournalEntry
import de.wonderfulworld.app.data.Mission
import de.wonderfulworld.app.data.Tag
import de.wonderfulworld.app.ui.components.GENERAL_TAG_COLOR
import de.wonderfulworld.app.ui.components.Muted
import de.wonderfulworld.app.ui.components.ProgressLine
import de.wonderfulworld.app.ui.theme.Ww
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun JournalEntry.dateTime(): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())

val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val shortDay = DateTimeFormatter.ofPattern("EE", Locale.GERMANY)

fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Heute"
    today.minusDays(1) -> "Gestern"
    else -> d.format(shortDay).removeSuffix(".")
}

fun tagColor(tag: Tag?): Color = tag?.let { Color(it.color) } ?: GENERAL_TAG_COLOR

/** Kompakte, nur lesende Missionszeile (Planung, Archiv). Antippen öffnet die Bearbeitung. */
@Composable
fun MissionRow(m: Mission, tag: Tag?, onClick: (() -> Unit)?) {
    val c = Ww.colors.mission
    val color = tagColor(tag)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Text(
                m.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                textDecoration = if (m.isDone) TextDecoration.LineThrough else null,
            )
            when (m.resolution) {
                Mission.RESOLUTION_CARRIED -> Muted("übernommen")
                Mission.RESOLUTION_ARCHIVED -> Muted("archiviert")
                else -> Text(
                    if (m.counter) "${m.progress}/${m.target}" else if (m.isDone) "✓" else "offen",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (m.counter) ProgressLine(m.progress.toFloat() / m.target, color, c.track)
    }
}

/** Tagebuch-Kachel: Foto als Hintergrund, Text darüber. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun JournalTile(
    e: JournalEntry, app: WonderfulWorldApp, today: LocalDate, onClick: () -> Unit,
    modifier: Modifier = Modifier, height: Dp = 190.dp, onLongClick: (() -> Unit)? = null,
) {
    val c = Ww.colors.journal
    val dt = e.dateTime()
    val hasPhoto = e.photo != null
    Box(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(22.dp))
            .background(c.container).combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (hasPhoto) {
            AsyncImage(
                model = app.repo.photos.file(e.photo!!), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xC8122234))
                )
            )
        }
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${dayLabel(dt.toLocalDate(), today)} · ${dt.format(timeFmt)}",
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (hasPhoto) Color.White.copy(alpha = .92f) else c.accent)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = if (hasPhoto) Color(0xFF1E3D5A) else MaterialTheme.colorScheme.surface,
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold,
            )
            if (e.text.isNotBlank()) Text(
                e.text, maxLines = 3, overflow = TextOverflow.Ellipsis,
                color = if (hasPhoto) Color.White else c.content,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Kartenrahmen in einer Bereichsfarbe. */
fun Modifier.areaCard(bg: Color, border: Color? = null, radius: Dp = 22.dp): Modifier =
    clip(RoundedCornerShape(radius)).background(bg)
        .then(if (border != null) Modifier.border(3.dp, border, RoundedCornerShape(radius)) else Modifier)
