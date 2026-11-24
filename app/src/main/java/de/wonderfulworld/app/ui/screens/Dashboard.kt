package de.wonderfulworld.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import de.wonderfulworld.app.WonderfulWorldApp
import de.wonderfulworld.app.data.JournalEntry
import de.wonderfulworld.app.data.Mission
import de.wonderfulworld.app.domain.Dates
import de.wonderfulworld.app.domain.fullDe
import de.wonderfulworld.app.domain.monthNameDe
import de.wonderfulworld.app.ui.appViewModel
import de.wonderfulworld.app.ui.components.MissionDialog
import de.wonderfulworld.app.ui.components.Muted
import de.wonderfulworld.app.ui.components.PaperCard
import de.wonderfulworld.app.ui.components.SectionTitle
import de.wonderfulworld.app.ui.rememberToday
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class DashboardViewModel(val app: WonderfulWorldApp) : ViewModel() {
    val repo = app.repo
    fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }

    /** "Wie letzte Woche": übernimmt den gültigen Plan unverändert für diese Woche. */
    fun keepLastWeek(week: LocalDate) = launch {
        val cal = repo.calendarNow()
        val plan = cal.effectivePlan(week) ?: return@launch
        val masks = repo.activeRituals().associate { it.id to cal.mask(it.id, week) }
        repo.saveWeekPlan(week, masks, plan.impulseMinutes)
        app.scheduler.rescheduleAll()
    }
}

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val dayHeaderFmt = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMANY)

fun JournalEntry.dateTime(): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())

@Composable
fun DashboardScreen(onPlan: () -> Unit, onNewEntry: () -> Unit, onOpenEntry: (Long) -> Unit) {
    val vm = appViewModel { DashboardViewModel(it) }
    val today = rememberToday()
    val cal by vm.repo.calendar.collectAsStateWithLifecycle(null)
    val missions by vm.repo.missions.collectAsStateWithLifecycle(emptyList())
    val monthPlans by vm.repo.monthPlans.collectAsStateWithLifecycle(null)
    val journal by vm.repo.journal.collectAsStateWithLifecycle(emptyList())

    val week = Dates.weekStart(today)
    val month = Dates.missionMonth(today)
    val monthMissions = missions.filter { it.month == month && it.resolution != Mission.RESOLUTION_ARCHIVED }
    val weekEntries = journal.filter { it.dateTime().toLocalDate().let { d -> d >= week && d <= week.plusDays(6) } }

    val c = cal
    val mp = monthPlans
    val needsWeek = c != null && !c.isWeekPlanned(week)
    val needsMonth = mp != null && mp.none { it.month == month } && (Dates.isMonthPlanningWeek(week) || mp.isEmpty())

    var missionDialog by remember { mutableStateOf<Mission?>(null) }
    var newMission by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewEntry,
                icon = { Icon(Icons.Outlined.EditNote, null) },
                text = { Text("Eintrag") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text(today.fullDe(), style = MaterialTheme.typography.headlineMedium)
                    Muted(greeting())
                }
            }

            if (needsWeek || needsMonth) item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (needsMonth) "Neuer Monat, neue Woche" else "Zeit für deine Sonntagsplanung",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (needsMonth) "Lege deine Missionen für den ${month.monthNameDe()} fest und plane deine Rituale."
                            else "Schau auf die letzte Woche zurück und plane deine Rituale.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = onPlan) { Text("Jetzt planen") }
                            if (!needsMonth && c?.effectivePlan(week) != null) {
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { vm.keepLastWeek(week) }) { Text("Wie letzte Woche") }
                            }
                        }
                    }
                }
            }

            // ---- Rituale ----
            item { SectionTitle("Heutige Rituale") }
            item {
                PaperCard {
                    val due = c?.dueRituals(today).orEmpty()
                    if (due.isEmpty()) {
                        Muted(if (c?.effectivePlan(week) == null) "Noch keine Rituale geplant." else "Heute sind keine Rituale fällig.")
                    }
                    due.forEach { r ->
                        val checked = c!!.isChecked(r.id, today)
                        val streak = c.streak(r.id, today)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable { vm.launch { vm.repo.setChecked(r.id, today, !checked) } },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { vm.launch { vm.repo.setChecked(r.id, today, it) } })
                            Text(
                                r.name, Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = if (checked) TextDecoration.LineThrough else null,
                                color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                            if (streak > 0) StreakBadge(streak)
                        }
                    }
                }
            }

            // ---- Missionen ----
            item {
                SectionTitle("Missionen · ${month.monthNameDe()}") {
                    IconButton(onClick = { newMission = true }) { Icon(Icons.Outlined.Add, "Mission hinzufügen") }
                }
            }
            item {
                PaperCard {
                    if (monthMissions.isEmpty()) Muted("Noch keine Missionen für diesen Monat.")
                    monthMissions.forEach { m ->
                        MissionRow(m, onEdit = { missionDialog = m }, onUpdate = { vm.launch { vm.repo.updateMission(it) } })
                    }
                }
            }

            // ---- Tagebuch ----
            item { SectionTitle("Tagebuch dieser Woche") }
            if (weekEntries.isEmpty()) item {
                PaperCard { Muted("Noch keine Einträge diese Woche. Was hat dich heute bewegt?") }
            }
            val byDay = weekEntries.groupBy { it.dateTime().toLocalDate() }
            byDay.forEach { (day, entries) ->
                item(key = "day-$day") {
                    Text(
                        if (day == today) "Heute" else day.format(dayHeaderFmt),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(entries, key = { it.id }) { e -> JournalCard(e, vm.app, onClick = { onOpenEntry(e.id) }) }
            }
        }
    }

    if (newMission) {
        MissionDialog(
            initial = null,
            onDismiss = { newMission = false },
            onSave = { t, counter, target ->
                vm.launch { vm.repo.addMission(month, t, counter, target) }
                newMission = false
            },
        )
    }
    missionDialog?.let { m ->
        MissionDialog(
            initial = m,
            onDismiss = { missionDialog = null },
            onSave = { t, counter, target ->
                vm.launch {
                    vm.repo.updateMission(
                        m.copy(title = t.trim(), counter = counter, target = if (counter) target else 1,
                            progress = if (counter) m.progress else minOf(m.progress, 1))
                    )
                }
                missionDialog = null
            },
            onDelete = {
                vm.launch { vm.repo.deleteMission(m) }
                missionDialog = null
            },
        )
    }
}

private fun greeting(): String {
    val h = LocalDateTime.now().hour
    return when (h) {
        in 5..10 -> "Guten Morgen. Was machst du heute aus dem Tag?"
        in 11..17 -> "Schön, dass du da bist. Ein kurzer Blick – und dann raus in die Welt."
        else -> "Guten Abend. Zeit, den Tag ausklingen zu lassen."
    }
}

@Composable
fun StreakBadge(streak: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.LocalFireDepartment, contentDescription = "Serie",
            tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp),
        )
        Text("$streak", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.width(8.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MissionRow(m: Mission, onEdit: () -> Unit, onUpdate: (Mission) -> Unit, readOnly: Boolean = false) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .combinedClickable(enabled = !readOnly, onClick = onEdit, onLongClick = onEdit)
            .padding(vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!m.counter) {
                Checkbox(
                    checked = m.isDone, enabled = !readOnly,
                    onCheckedChange = { onUpdate(m.copy(progress = if (it) 1 else 0)) },
                )
            }
            Text(
                m.title, Modifier.weight(1f).padding(start = if (m.counter) 12.dp else 0.dp),
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (m.isDone) TextDecoration.LineThrough else null,
                color = if (m.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            when (m.resolution) {
                Mission.RESOLUTION_CARRIED -> Muted("übernommen")
                Mission.RESOLUTION_ARCHIVED -> Muted("archiviert")
            }
            if (m.counter) {
                if (!readOnly) IconButton(onClick = { onUpdate(m.copy(progress = m.progress - 1)) }, enabled = m.progress > 0) {
                    Icon(Icons.Outlined.Remove, "weniger")
                }
                Text("${m.progress}/${m.target}", style = MaterialTheme.typography.labelLarge)
                if (!readOnly) IconButton(onClick = { onUpdate(m.copy(progress = m.progress + 1)) }, enabled = m.progress < m.target) {
                    Icon(Icons.Outlined.Add, "mehr")
                }
            }
        }
        if (m.counter) {
            LinearProgressIndicator(
                progress = { m.progress.toFloat() / m.target },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
fun JournalCard(e: JournalEntry, app: WonderfulWorldApp, onClick: () -> Unit) {
    PaperCard(Modifier.clickable(onClick = onClick)) {
        Muted(e.dateTime().format(timeFmt))
        if (e.photo != null) {
            AsyncImage(
                model = app.repo.photos.file(e.photo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
            )
        }
        if (e.text.isNotBlank()) Text(e.text, style = MaterialTheme.typography.bodyLarge)
    }
}
