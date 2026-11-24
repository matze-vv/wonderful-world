package de.wonderfulworld.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import de.wonderfulworld.app.WonderfulWorldApp
import de.wonderfulworld.app.data.Mission
import de.wonderfulworld.app.domain.Dates
import de.wonderfulworld.app.domain.DEFAULT_IMPULSE_MINUTES
import de.wonderfulworld.app.domain.dmDe
import de.wonderfulworld.app.domain.formatMinutes
import de.wonderfulworld.app.domain.monthNameDe
import de.wonderfulworld.app.ui.appViewModel
import de.wonderfulworld.app.ui.components.DayChips
import de.wonderfulworld.app.ui.components.Muted
import de.wonderfulworld.app.ui.components.PaperCard
import de.wonderfulworld.app.ui.components.SectionTitle
import de.wonderfulworld.app.ui.components.TimePickerDialog
import de.wonderfulworld.app.ui.rememberToday
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val ALL_DAYS = 0b1111111

class PlanningViewModel(val app: WonderfulWorldApp) : ViewModel() {
    val repo = app.repo

    /** Wochentags-Masken der aktiven Rituale (Bearbeitungsstand). */
    val masks = mutableStateMapOf<Long, Int>()
    var impulseMinutes by mutableIntStateOf(DEFAULT_IMPULSE_MINUTES)
    var loaded by mutableStateOf(false)
        private set

    fun load(week: LocalDate) {
        if (loaded) return
        viewModelScope.launch {
            val cal = repo.calendarNow()
            // Schon geplant: diesen Plan zeigen. Sonst: Standard-Tage der aktiven Rituale.
            val planned = cal.isWeekPlanned(week)
            repo.activeRituals().forEach { masks[it.id] = if (planned) cal.mask(it.id, week) else it.defaultMask }
            impulseMinutes = cal.effectivePlan(week)?.impulseMinutes ?: DEFAULT_IMPULSE_MINUTES
            loaded = true
        }
    }

    fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }

    fun addRitual(name: String, today: LocalDate) = launch {
        val r = repo.addRitual(name, today)
        masks[r.id] = ALL_DAYS
    }

    fun save(week: LocalDate, planMonth: Boolean, onDone: () -> Unit) = launch {
        val active = repo.activeRituals().map { it.id }.toSet()
        repo.saveWeekPlan(week, masks.filterKeys { it in active }, impulseMinutes)
        if (planMonth) repo.markMonthPlanned(Dates.missionMonth(week))
        app.scheduler.rescheduleAll()
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanningScreen(onDone: () -> Unit, onEditMission: (Long) -> Unit, onNewMission: () -> Unit) {
    val vm = appViewModel { PlanningViewModel(it) }
    val today = rememberToday()
    val week = Dates.weekStart(today)
    val prevWeek = week.minusDays(7)
    LaunchedEffect(week) { vm.load(week) }

    val cal by vm.repo.calendar.collectAsStateWithLifecycle(null)
    val rituals by vm.repo.rituals.collectAsStateWithLifecycle(emptyList())
    val missions by vm.repo.missions.collectAsStateWithLifecycle(emptyList())
    val monthPlans by vm.repo.monthPlans.collectAsStateWithLifecycle(null)
    val journal by vm.repo.journal.collectAsStateWithLifecycle(emptyList())
    val tags by vm.repo.tags.collectAsStateWithLifecycle(emptyList())
    val tagById = tags.associateBy { it.id }

    val month = Dates.missionMonth(week)
    val prevMonth = month.minusMonths(1)
    val mp = monthPlans
    val planMonth = mp != null && mp.none { it.month == month } && (Dates.isMonthPlanningWeek(week) || mp.isEmpty())
    val activeRituals = rituals.filter { it.isActive }

    var newRitual by remember { mutableStateOf("") }
    var pickImpulse by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Sonntagsplanung") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0),
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner).imePadding(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------- 1. Wochenrückblick ----------
            item { SectionTitle("Rückblick: ${prevWeek.dmDe()} – ${prevWeek.plusDays(6).dmDe()}") }
            item {
                val c = cal
                PaperCard {
                    if (c == null) return@PaperCard
                    val q = c.quote(prevWeek, prevWeek.plusDays(6))
                    if (q.due == 0) {
                        Muted("Letzte Woche waren keine Rituale geplant.")
                    } else {
                        Text("Rituale: ${q.done} von ${q.due} erledigt (${q.percent} %)", style = MaterialTheme.typography.bodyLarge)
                        ProgressBar(q.done.toFloat() / q.due)
                        rituals.forEach { r ->
                            val rq = c.quote(prevWeek, prevWeek.plusDays(6), r.id)
                            if (rq.due > 0) Row {
                                Text(r.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Muted("${rq.done}/${rq.due}")
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    val lastMonth = Dates.missionMonth(prevWeek)
                    val ms = missions.filter { it.month == lastMonth && it.resolution != Mission.RESOLUTION_ARCHIVED }
                    if (ms.isEmpty()) Muted("Keine Missionen im ${lastMonth.monthNameDe()}.")
                    else {
                        Text("Missionen ${lastMonth.monthNameDe()}: ${ms.count { it.isDone }} von ${ms.size} geschafft",
                            style = MaterialTheme.typography.bodyLarge)
                        ms.forEach { m ->
                            Row {
                                Text(m.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Muted(if (m.counter) "${m.progress}/${m.target}" else if (m.isDone) "✓" else "offen")
                            }
                        }
                    }
                    val photos = journal.filter {
                        val d = it.dateTime().toLocalDate(); d >= prevWeek && d < week && it.photo != null
                    }
                    val entryCount = journal.count { val d = it.dateTime().toLocalDate(); d >= prevWeek && d < week }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Tagebuch: $entryCount " + if (entryCount == 1) "Erlebnis" else "Erlebnisse", style = MaterialTheme.typography.bodyLarge)
                    if (photos.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(photos, key = { it.id }) { e ->
                                AsyncImage(
                                    model = vm.repo.photos.file(e.photo!!), contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(10.dp)),
                                )
                            }
                        }
                    }
                }
            }

            // ---------- 2. Missionen ----------
            if (planMonth) {
                val open = missions.filter { it.month == prevMonth && it.isOpen && !it.weekly }
                if (open.isNotEmpty()) {
                    item { SectionTitle("Offen aus dem ${prevMonth.monthNameDe()}") }
                    item {
                        PaperCard {
                            open.forEach { m ->
                                Column {
                                    Text(m.title + if (m.counter) "  (${m.progress}/${m.target})" else "",
                                        style = MaterialTheme.typography.bodyLarge)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { vm.launch { vm.repo.carryOver(m, month) } }) { Text("Übernehmen") }
                                        TextButton(onClick = { vm.launch { vm.repo.archiveMission(m) } }) { Text("Archivieren") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                SectionTitle(if (planMonth) "Missionen für den ${month.monthNameDe()}" else "Missionen ergänzen") {
                    IconButton(onClick = onNewMission) { Icon(Icons.Outlined.Add, "Mission hinzufügen") }
                }
            }
            item {
                PaperCard {
                    val ms = missions.filter { it.showsIn(month) }
                    if (ms.isEmpty()) Muted("Kleine Ziele für diesen Monat – z. B. „2× wandern“ oder „Buch fertig lesen“.")
                    ms.forEach { m -> MissionRow(m, m.tagId?.let(tagById::get), onClick = { onEditMission(m.id) }) }
                }
            }

            // ---------- 3. Rituale ----------
            item { SectionTitle("Rituale: ${week.dmDe()} – ${week.plusDays(6).dmDe()}") }
            item {
                PaperCard {
                    if (activeRituals.isEmpty()) Muted("Noch keine Rituale. Leg unten dein erstes an.")
                    else Muted("Vorbelegt mit den Standard-Tagen. Pausieren und Details bearbeiten kannst du unter „Mehr“.")
                    activeRituals.forEach { r ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Column(Modifier.padding(top = 4.dp)) {
                                Text(r.name, style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                if (r.detail.isNotBlank()) Muted(r.detail.lineSequence().first().take(80))
                            }
                            DayChips(vm.masks[r.id] ?: 0) { vm.masks[r.id] = it }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val add = {
                            if (newRitual.isNotBlank()) { vm.addRitual(newRitual, today); newRitual = "" }
                        }
                        OutlinedTextField(
                            value = newRitual, onValueChange = { newRitual = it },
                            label = { Text("Neues Ritual") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { add() }),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = add, enabled = newRitual.isNotBlank()) { Icon(Icons.Outlined.Add, "Ritual hinzufügen") }
                    }
                }
            }

            // ---------- 4. Morgen-Impuls ----------
            item { SectionTitle("Morgen-Impuls") }
            item {
                PaperCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("An Tagen ohne Wecker um", style = MaterialTheme.typography.bodyLarge)
                            Muted("Mit aktivem Wecker kommt der Impuls direkt nach dem Ausschalten.")
                        }
                        TextButton(onClick = { pickImpulse = true }) {
                            Text(formatMinutes(vm.impulseMinutes), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.save(week, planMonth, onDone) },
                    enabled = vm.loaded,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Planung speichern") }
            }
        }
    }

    if (pickImpulse) {
        TimePickerDialog(vm.impulseMinutes, "Morgen-Impuls", onDismiss = { pickImpulse = false }) {
            vm.impulseMinutes = it; pickImpulse = false
        }
    }
}

@Composable
fun ProgressBar(fraction: Float) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = MaterialTheme.colorScheme.secondary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        drawStopIndicator = {},
    )
}

