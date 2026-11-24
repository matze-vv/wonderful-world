package de.wonderfulworld.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import de.wonderfulworld.app.data.Mission
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import de.wonderfulworld.app.WonderfulWorldApp
import de.wonderfulworld.app.data.JournalEntry
import de.wonderfulworld.app.domain.Dates
import de.wonderfulworld.app.domain.monthNameDe
import de.wonderfulworld.app.domain.shortDe
import de.wonderfulworld.app.ui.appViewModel
import de.wonderfulworld.app.ui.components.BackTopBar
import de.wonderfulworld.app.ui.components.CheckCircle
import de.wonderfulworld.app.ui.components.DotState
import de.wonderfulworld.app.ui.components.ProgressLine
import de.wonderfulworld.app.ui.components.TagChip
import de.wonderfulworld.app.ui.components.WeekDots
import de.wonderfulworld.app.ui.rememberToday
import de.wonderfulworld.app.ui.theme.Ww
import kotlinx.coroutines.launch
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

class DetailViewModel(val app: WonderfulWorldApp) : ViewModel() {
    val repo = app.repo
    fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }
}

// ---------------------------------------------------------------- Rituale heute

@Composable
fun RitualsTodayScreen(onBack: () -> Unit) {
    val vm = appViewModel { DetailViewModel(it) }
    val today = rememberToday()
    val cal by vm.repo.calendar.collectAsStateWithLifecycle(null)
    val c = Ww.colors.ritual
    val cal0 = cal
    val due = cal0?.dueRituals(today).orEmpty()
    val done = due.count { cal0!!.isChecked(it.id, today) }
    val week = Dates.weekDays(Dates.weekStart(today))
    val labels = week.map { it.dayOfWeek.shortDe().take(2) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = c.background,
        topBar = {
            BackTopBar("Rituale heute", onBack, containerColor = c.background, contentColor = c.content) {
                Text("$done/${due.size}", Modifier.padding(end = 16.dp), style = MaterialTheme.typography.titleMedium, color = c.accent)
            }
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (due.isEmpty()) item {
                Text("Heute sind keine Rituale fällig. Genieß den Tag!", color = c.muted, style = MaterialTheme.typography.bodyLarge)
            }
            items(due, key = { it.id }) { r ->
                val checked = cal0!!.isChecked(r.id, today)
                val streak = cal0.streak(r.id, today)
                Column(Modifier.fillMaxWidth().areaCard(c.container).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CheckCircle(checked, c.accent, if (checked) "${r.name} nicht erledigt" else "${r.name} abhaken",
                            { vm.launch { vm.repo.setChecked(r.id, today, it) } }, size = 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.titleMedium, color = c.content,
                                textDecoration = if (checked) TextDecoration.LineThrough else null)
                            Text(
                                if (streak > 0) "Serie: $streak fällige Tage in Folge" else "Neue Serie starten",
                                style = MaterialTheme.typography.labelLarge, color = c.accent,
                            )
                        }
                    }
                    if (r.detail.isNotBlank()) Text(r.detail, style = MaterialTheme.typography.bodyLarge, color = c.content)
                    WeekDots(
                        week.map { d ->
                            when {
                                cal0.isChecked(r.id, d) -> DotState.DONE
                                !cal0.isDue(r.id, d) -> DotState.NONE
                                d < today -> DotState.MISSED
                                else -> DotState.OPEN
                            }
                        },
                        c.accent, c.track, labels,
                    )
                }
            }
            if (due.isNotEmpty()) item {
                AnimatedVisibility(done == due.size, enter = fadeIn() + scaleIn(initialScale = .8f)) {
                    Text(
                        "Alles erledigt – stark! Zeit für die Welt da draußen.",
                        Modifier.fillMaxWidth().areaCard(c.accent).padding(16.dp),
                        color = MaterialTheme.colorScheme.surface, textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Missionen eines Tags

@Composable
fun TagMissionsScreen(tagId: Long, onBack: () -> Unit, onEditMission: (Long) -> Unit, onNewMission: (Long) -> Unit) {
    val vm = appViewModel { DetailViewModel(it) }
    val today = rememberToday()
    val missions by vm.repo.missions.collectAsStateWithLifecycle(emptyList())
    val tags by vm.repo.tags.collectAsStateWithLifecycle(emptyList())
    val c = Ww.colors.mission
    val month = Dates.missionMonth(today)
    val tag = tags.firstOrNull { it.id == tagId }
    val known = tags.map { it.id }.toSet()
    var pendingDelete by remember { mutableStateOf<Mission?>(null) }
    val list = missions.filter {
        it.showsIn(month) &&
            (if (tagId == 0L) it.tagId == null || it.tagId !in known else it.tagId == tagId)
    }
    val color = tagColor(tag)

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = c.background,
        topBar = {
            BackTopBar("", onBack, containerColor = c.background, contentColor = c.content)
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f, fill = false)) { TagChip(tag?.name ?: "Ohne Tag", color, large = true) }
                    Text(month.monthNameDe(), style = MaterialTheme.typography.labelLarge, color = c.muted, maxLines = 1)
                }
                if (list.isNotEmpty()) Text(
                    "Tipp: Nach links wischen löscht eine Mission.",
                    Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = c.muted,
                )
            }
            if (list.isEmpty()) item { Text("Keine Missionen mit diesem Tag in diesem Monat.", color = c.muted) }
            items(list, key = { it.id }) { m ->
                SwipeToDelete(onDelete = { pendingDelete = m }) {
                Column(
                    Modifier.fillMaxWidth().areaCard(c.container, border = color, radius = 20.dp)
                        .clickable { onEditMission(m.id) }.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = c.content,
                            textDecoration = if (m.isDone) TextDecoration.LineThrough else null)
                        if (m.counter) {
                            OutlinedIconButton(
                                onClick = { vm.launch { vm.repo.updateMission(m.copy(progress = m.progress - 1)) } },
                                enabled = m.progress > 0, border = BorderStroke(2.dp, color),
                            ) { Icon(Icons.Rounded.Remove, "Weniger", tint = color) }
                            Text("${m.progress}/${m.target}", Modifier.widthIn(min = 44.dp), textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleMedium, color = c.content)
                            FilledIconButton(
                                onClick = { vm.launch { vm.repo.updateMission(m.copy(progress = m.progress + 1)) } },
                                enabled = m.progress < m.target,
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = Color.White),
                            ) { Icon(Icons.Rounded.Add, "Mehr") }
                        } else {
                            QuickButton(m, color) { vm.launch { vm.repo.updateMission(m.copy(progress = if (m.isDone) 0 else 1)) } }
                        }
                    }
                    if (m.weekly) WeeklyBadge(c.muted)
                    ProgressLine(m.progress.toFloat() / m.target, color, c.track, height = 8.dp)
                    if (m.detail.isNotBlank()) Text(m.detail, style = MaterialTheme.typography.bodyLarge, color = c.content)
                }
                }
            }
            item {
                OutlinedButton(
                    onClick = { onNewMission(tagId) },
                    border = BorderStroke(2.dp, color),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("+ Mission mit diesem Tag", color = c.content, fontWeight = FontWeight.ExtraBold) }
            }
        }
    }

    pendingDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Mission löschen?") },
            text = { Text("„${m.title}“ wird endgültig gelöscht.") },
            confirmButton = {
                TextButton(onClick = { vm.launch { vm.repo.deleteMission(m) }; pendingDelete = null }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Abbrechen") } },
        )
    }
}

/** Nach links wischen: roter Hintergrund mit Papierkorb; die Karte springt zurück, gelöscht wird erst nach Rückfrage. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val current by androidx.compose.runtime.rememberUpdatedState(onDelete)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) current()
            false
        },
        positionalThreshold = { it * 0.35f },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.error).padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Rounded.Delete, "Löschen", tint = MaterialTheme.colorScheme.onError) }
        },
    ) { content() }
}


// ---------------------------------------------------------------- Tagebucheintrag ansehen

private val longDayFmt = DateTimeFormatter.ofPattern("EEEE, d. MMMM · HH:mm", Locale.GERMANY)

@Composable
fun JournalViewScreen(id: Long, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val vm = appViewModel { DetailViewModel(it) }
    val journal by vm.repo.journal.collectAsStateWithLifecycle(emptyList())
    var entry by remember { mutableStateOf<JournalEntry?>(null) }
    LaunchedEffect(journal) { entry = journal.firstOrNull { it.id == id } ?: entry }
    val e = entry
    val c = Ww.colors.journal
    var fullscreen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (e?.photo != null) {
                AsyncImage(
                    model = vm.repo.photos.file(e.photo), contentDescription = "Foto des Erlebnisses",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(320.dp).clickable { fullscreen = true },
                )
            } else {
                Box(Modifier.height(72.dp))
            }
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (e != null) {
                    Text(
                        e.dateTime().format(longDayFmt),
                        Modifier.clip(RoundedCornerShape(50)).background(c.accent).padding(horizontal = 12.dp, vertical = 5.dp),
                        color = MaterialTheme.colorScheme.surface, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold,
                    )
                    if (e.text.isNotBlank()) Text(e.text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize), color = c.content)
                    OutlinedButton(onClick = { onEdit(e.id) }, border = BorderStroke(2.dp, c.accent)) {
                        Text("Bearbeiten", color = c.accent, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(12.dp).size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = .9f)),
        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", tint = Color(0xFF1E3D5A)) }
    }

    if (fullscreen && e?.photo != null) PhotoViewer(vm.repo.photos.file(e.photo)) { fullscreen = false }
}

/** Foto im Vollbild auf schwarzem Grund. */
@Composable
fun PhotoViewer(file: File, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0D1620)).clickable(onClick = onDismiss)) {
            AsyncImage(
                model = file, contentDescription = "Foto im Vollbild",
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)
                    .clip(CircleShape).background(Color.White.copy(alpha = .16f)),
            ) { Icon(Icons.Rounded.Close, "Vollbild schließen", tint = Color.White) }
        }
    }
}

