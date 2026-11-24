package de.wonderfulworld.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import de.wonderfulworld.app.WonderfulWorldApp
import de.wonderfulworld.app.data.JournalEntry
import de.wonderfulworld.app.data.Mission
import de.wonderfulworld.app.domain.Dates
import de.wonderfulworld.app.domain.RitualCalendar
import de.wonderfulworld.app.domain.dmDe
import de.wonderfulworld.app.domain.fullDe
import de.wonderfulworld.app.domain.labelDe
import de.wonderfulworld.app.domain.monthNameDe
import de.wonderfulworld.app.domain.shortDe
import de.wonderfulworld.app.ui.appViewModel
import de.wonderfulworld.app.ui.components.Muted
import de.wonderfulworld.app.ui.components.PaperCard
import de.wonderfulworld.app.ui.components.SectionTitle
import de.wonderfulworld.app.ui.rememberToday
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class ArchiveViewModel(val app: WonderfulWorldApp) : ViewModel() {
    val repo = app.repo
    fun toggle(ritualId: Long, date: LocalDate, checked: Boolean) =
        viewModelScope.launch { repo.setChecked(ritualId, date, checked) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(onOpenEntry: (Long) -> Unit) {
    val vm = appViewModel { ArchiveViewModel(it) }
    val today = rememberToday()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var dayDetail by rememberSaveable { mutableStateOf<Long?>(null) } // epochDay

    val cal by vm.repo.calendar.collectAsStateWithLifecycle(null)
    val rituals by vm.repo.rituals.collectAsStateWithLifecycle(emptyList())
    val missions by vm.repo.missions.collectAsStateWithLifecycle(emptyList())
    val journal by vm.repo.journal.collectAsStateWithLifecycle(emptyList())
    val c = cal ?: return

    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Tab(tab == 0, onClick = { tab = 0 }, text = { Text("Woche") })
            Tab(tab == 1, onClick = { tab = 1 }, text = { Text("Monat") })
        }

        if (tab == 0) {
            val week = Dates.weekStart(today).plusWeeks(weekOffset.toLong())
            val days = Dates.weekDays(week)
            val entries = journal.filter { it.dateTime().toLocalDate() in days }
            val weekRituals = rituals.filter { r -> days.any { c.isDue(r.id, it) || c.isChecked(r.id, it) } }
            val q = c.quote(week, minOf(week.plusDays(6), today))
            val month = Dates.missionMonth(week)
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Navigator(
                        title = "${week.dmDe()} – ${week.plusDays(6).dmDe()}${week.year}",
                        onPrev = { weekOffset-- },
                        onNext = { weekOffset++ }, nextEnabled = weekOffset < 0,
                    )
                }
                item { SectionTitle("Rituale") }
                item {
                    PaperCard {
                        if (weekRituals.isEmpty()) Muted("Keine Rituale in dieser Woche.")
                        else {
                            Text("${q.done} von ${q.due} erledigt" + if (q.due > 0) " (${q.percent} %)" else "")
                            Row {
                                Spacer(Modifier.weight(2.2f))
                                days.forEach {
                                    Text(it.dayOfWeek.shortDe().take(2), Modifier.weight(1f), textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            weekRituals.forEach { r ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(r.name, Modifier.weight(2.2f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                    days.forEach { d ->
                                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                            CheckDot(c.isDue(r.id, d), c.isChecked(r.id, d), d > today)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item { SectionTitle("Missionen · ${month.monthNameDe()}") }
                item { MissionList(missions.filter { it.month == month }) }
                item { SectionTitle("Tagebuch") }
                if (entries.isEmpty()) item { PaperCard { Muted("Keine Einträge in dieser Woche.") } }
                items(entries.sortedBy { it.timestamp }, key = { it.id }) { e ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(e.dateTime().toLocalDate().fullDe(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        JournalCard(e, vm.app, onClick = { onOpenEntry(e.id) })
                    }
                }
            }
        } else {
            val ym = YearMonth.from(today).plusMonths(monthOffset.toLong())
            val (from, to) = Dates.missionMonthRange(ym)
            val monthQuote = c.quote(ym.atDay(1), minOf(ym.atEndOfMonth(), today))
            val entryDays = journal.groupBy { it.dateTime().toLocalDate() }
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Navigator(ym.labelDe(), onPrev = { monthOffset-- }, onNext = { monthOffset++ }, nextEnabled = monthOffset < 0)
                PaperCard {
                    MonthGrid(ym, today, c, entryDays) { dayDetail = it.toEpochDay() }
                    if (monthQuote.due > 0) Muted("Rituale: ${monthQuote.done} von ${monthQuote.due} erledigt (${monthQuote.percent} %)")
                    Muted("${entryDays.filterKeys { YearMonth.from(it) == ym }.values.sumOf { it.size }} Tagebucheinträge")
                }
                SectionTitle("Missionen")
                Muted("Missionsmonat ${from.dmDe()} – ${to.dmDe()}")
                MissionList(missions.filter { it.month == ym })
            }
        }
    }

    dayDetail?.let { epoch ->
        val d = LocalDate.ofEpochDay(epoch)
        val entries = journal.filter { it.dateTime().toLocalDate() == d }.sortedBy { it.timestamp }
        val due = c.dueRituals(d)
        AlertDialog(
            onDismissRequest = { dayDetail = null },
            title = { Text(d.fullDe()) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (due.isEmpty()) Muted("Keine Rituale fällig.")
                    due.forEach { r ->
                        val checked = c.isChecked(r.id, d)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked, onCheckedChange = { vm.toggle(r.id, d, it) }, enabled = d <= today)
                            Text(r.name)
                        }
                    }
                    if (entries.isNotEmpty()) Text("Tagebuch", fontWeight = FontWeight.SemiBold)
                    entries.forEach { e -> DayEntry(e) { dayDetail = null; onOpenEntry(e.id) } }
                }
            },
            confirmButton = { TextButton(onClick = { dayDetail = null }) { Text("Schließen") } },
        )
    }
}

@Composable
private fun DayEntry(e: JournalEntry, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(4.dp)) {
        Muted(e.dateTime().toLocalTime().toString().take(5) + if (e.photo != null) " · 📷" else "")
        Text(e.text.ifBlank { "(Foto)" }, maxLines = 3)
    }
}

@Composable
private fun Navigator(title: String, onPrev: () -> Unit, onNext: () -> Unit, nextEnabled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Zurück") }
        Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onNext, enabled = nextEnabled) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Weiter") }
    }
}

@Composable
private fun CheckDot(due: Boolean, checked: Boolean, future: Boolean) {
    val cs = MaterialTheme.colorScheme
    val m = Modifier.size(14.dp).clip(CircleShape)
    when {
        checked -> Box(m.background(cs.secondary))
        due && !future -> Box(m.border(1.5.dp, cs.primary, CircleShape))
        due -> Box(m.border(1.dp, cs.outline, CircleShape))
        else -> Box(Modifier.size(4.dp).clip(CircleShape).background(cs.outlineVariant))
    }
}

@Composable
private fun MissionList(ms: List<Mission>) {
    PaperCard {
        if (ms.isEmpty()) Muted("Keine Missionen.")
        ms.forEach { m -> MissionRow(m, onEdit = {}, onUpdate = {}, readOnly = true) }
    }
}

@Composable
private fun MonthGrid(
    ym: YearMonth, today: LocalDate, c: RitualCalendar,
    entryDays: Map<LocalDate, List<JournalEntry>>, onDay: (LocalDate) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val first = ym.atDay(1)
    val lead = first.dayOfWeek.value % 7 // Sonntag = 0
    val cells = lead + ym.lengthOfMonth()
    val rows = (cells + 6) / 7
    Row {
        Dates.WEEK_ORDER.forEach {
            Text(it.shortDe().take(2), Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
    for (row in 0 until rows) {
        Row {
            for (col in 0 until 7) {
                val idx = row * 7 + col - lead
                Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                    if (idx in 0 until ym.lengthOfMonth()) {
                        val d = first.plusDays(idx.toLong())
                        val q = c.quote(d, d)
                        val hasEntry = entryDays.containsKey(d)
                        val bg = when {
                            d > today || q.due == 0 -> Color.Transparent
                            q.done == q.due -> cs.secondaryContainer
                            q.done > 0 -> cs.tertiaryContainer
                            else -> Color.Transparent
                        }
                        Column(
                            Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(bg)
                                .then(if (d == today) Modifier.border(1.5.dp, cs.primary, RoundedCornerShape(10.dp)) else Modifier)
                                .clickable { onDay(d) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("${d.dayOfMonth}", style = MaterialTheme.typography.bodyMedium,
                                color = if (d > today) cs.onSurfaceVariant else cs.onSurface)
                            Box(Modifier.size(5.dp).clip(CircleShape).background(if (hasEntry) cs.primary else Color.Transparent))
                        }
                    }
                }
            }
        }
    }
}
