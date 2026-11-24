package de.wonderfulworld.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import de.wonderfulworld.app.WonderfulWorldApp
import de.wonderfulworld.app.data.Mission
import de.wonderfulworld.app.data.Tag
import de.wonderfulworld.app.domain.Dates
import de.wonderfulworld.app.domain.dmDe
import de.wonderfulworld.app.domain.fullDe
import de.wonderfulworld.app.domain.monthNameDe
import de.wonderfulworld.app.ui.appViewModel
import de.wonderfulworld.app.ui.components.CheckCircle
import de.wonderfulworld.app.ui.components.ProgressLine
import de.wonderfulworld.app.ui.components.ProgressRing
import de.wonderfulworld.app.ui.components.Streak
import de.wonderfulworld.app.ui.components.TagChip
import de.wonderfulworld.app.ui.rememberToday
import de.wonderfulworld.app.ui.theme.Ww
import kotlinx.coroutines.launch
import java.time.LocalDate

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

    /** Schnell-Knopf in der Kachel: einfache Mission umschalten, Zähler +1. */
    fun quick(m: Mission) = launch {
        val p = when {
            !m.counter -> if (m.isDone) 0 else 1
            m.isDone -> m.progress
            else -> m.progress + 1
        }
        repo.updateMission(m.copy(progress = p))
    }
}

/** Eine Missions-Kachel: ein Tag (oder „Allgemein“) mit seinen Missionen. */
data class TagGroup(val tag: Tag?, val missions: List<Mission>) {
    val id: Long get() = tag?.id ?: 0L
    val name: String get() = tag?.name ?: "Allgemein"
}

fun groupByTag(missions: List<Mission>, tags: List<Tag>): List<TagGroup> {
    val byTag = missions.groupBy { it.tagId }
    val known = tags.map { it.id }.toSet()
    val groups = tags.mapNotNull { t -> byTag[t.id]?.let { TagGroup(t, it) } }
    val untagged = missions.filter { it.tagId == null || it.tagId !in known }
    return if (untagged.isEmpty()) groups else groups + TagGroup(null, untagged)
}

@Composable
fun DashboardScreen(
    onPlan: () -> Unit,
    onNewEntry: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onOpenRituals: () -> Unit,
    onOpenTag: (Long) -> Unit,
    onNewMission: () -> Unit,
) {
    val vm = appViewModel { DashboardViewModel(it) }
    val today = rememberToday()
    val cal by vm.repo.calendar.collectAsStateWithLifecycle(null)
    val missions by vm.repo.missions.collectAsStateWithLifecycle(emptyList())
    val tags by vm.repo.tags.collectAsStateWithLifecycle(emptyList())
    val monthPlans by vm.repo.monthPlans.collectAsStateWithLifecycle(null)
    val journal by vm.repo.journal.collectAsStateWithLifecycle(emptyList())

    val week = Dates.weekStart(today)
    val month = Dates.missionMonth(today)
    val monthMissions = missions.filter { it.month == month && it.resolution == null }
    val groups = groupByTag(monthMissions, tags)
    val weekEntries = journal.filter { it.dateTime().toLocalDate().let { d -> d >= week && d <= week.plusDays(6) } }

    val c = cal
    val mp = monthPlans
    val needsWeek = c != null && !c.isWeekPlanned(week)
    val needsMonth = mp != null && mp.none { it.month == month } && (Dates.isMonthPlanningWeek(week) || mp.isEmpty())
    val weekQuote = c?.quote(week, week.plusDays(6))

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewEntry,
                icon = { Icon(Icons.Rounded.EditNote, null) },
                text = { Text("Eintrag", fontWeight = FontWeight.Bold) },
                containerColor = Ww.colors.journal.accent,
                contentColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    Text(
                        "WOCHE ${week.dmDe()}–${week.plusDays(6).dmDe()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(today.fullDe().substringBeforeLast(" "), style = MaterialTheme.typography.headlineMedium)
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("${weekQuote?.done ?: 0}/${weekQuote?.due ?: 0}", "Rituale", Ww.colors.ritual.container, Ww.colors.ritual.content, Modifier.weight(1f))
                    Stat("${monthMissions.count { it.isDone }}/${monthMissions.size}", "Missionen", Ww.colors.mission.container, Ww.colors.mission.content, Modifier.weight(1f))
                    Stat("${weekEntries.size}", "Einträge", Ww.colors.journal.container, Ww.colors.journal.content, Modifier.weight(1f))
                }
            }

            if (needsWeek || needsMonth) item {
                PlanningBanner(
                    needsMonth = needsMonth, monthName = month.monthNameDe(), onPlan = onPlan,
                    onKeep = if (!needsMonth && c?.effectivePlan(week) != null) ({ vm.keepLastWeek(week) }) else null,
                )
            }

            // ---- Rituale ----
            item {
                val due = c?.dueRituals(today).orEmpty()
                val done = due.count { c!!.isChecked(it.id, today) }
                RitualsTile(
                    total = due.size, done = done, onOpen = onOpenRituals,
                    hasPlan = c?.effectivePlan(week) != null,
                ) {
                    due.forEach { r ->
                        val checked = c!!.isChecked(r.id, today)
                        RitualLine(
                            name = r.name, detail = r.detail, checked = checked, streak = c.streak(r.id, today),
                            onToggle = { vm.launch { vm.repo.setChecked(r.id, today, it) } },
                            onOpen = onOpenRituals,
                        )
                    }
                }
            }

            // ---- Missionen: eine Kachel pro Tag ----
            val missionTiles: List<TagGroup?> = groups + listOf(null) // null = „+ Mission“
            missionTiles.chunked(2).forEach { pair ->
                item {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { g ->
                            val mod = Modifier.weight(1f).fillMaxHeight()
                            if (g == null) AddMissionTile(onNewMission, mod)
                            else MissionTile(g, onOpen = { onOpenTag(g.id) }, onQuick = vm::quick, modifier = mod)
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            // ---- Tagebuch: eine Kachel pro Eintrag ----
            if (weekEntries.isEmpty()) item {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .border(BorderStroke(2.dp, Ww.colors.journal.track), RoundedCornerShape(22.dp))
                        .clickable(onClick = onNewEntry).padding(20.dp),
                ) {
                    Text(
                        "Noch keine Einträge diese Woche. Was hat dich heute bewegt?",
                        color = Ww.colors.journal.muted, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                val first = weekEntries.first()
                item(key = "e${first.id}") { JournalTile(first, vm.app, today, onClick = { onOpenEntry(first.id) }, height = 220.dp) }
                weekEntries.drop(1).chunked(2).forEach { pair ->
                    item(key = "e${pair.first().id}") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { e ->
                                JournalTile(e, vm.app, today, onClick = { onOpenEntry(e.id) }, modifier = Modifier.weight(1f))
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, bg: Color, fg: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).areaCard(bg, radius = 14.dp).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = fg)
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
private fun PlanningBanner(needsMonth: Boolean, monthName: String, onPlan: () -> Unit, onKeep: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().areaCard(cs.tertiaryContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (needsMonth) "Neuer Monat, neue Woche" else "Zeit für deine Sonntagsplanung",
            style = MaterialTheme.typography.titleMedium, color = cs.onTertiaryContainer,
        )
        Text(
            if (needsMonth) "Lege deine Missionen für den $monthName fest und plane deine Rituale."
            else "Schau auf die letzte Woche zurück und plane deine Rituale.",
            style = MaterialTheme.typography.bodyMedium, color = cs.onTertiaryContainer,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onPlan) { Text("Jetzt planen") }
            if (onKeep != null) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onKeep) { Text("Wie letzte Woche") }
            }
        }
    }
}

@Composable
private fun RitualsTile(total: Int, done: Int, hasPlan: Boolean, onOpen: () -> Unit, rows: @Composable () -> Unit) {
    val c = Ww.colors.ritual
    val left = total - done
    Column(Modifier.fillMaxWidth().areaCard(c.container).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProgressRing(if (total == 0) 0f else done.toFloat() / total, c.accent, c.track) {
                Text("$done/$total", style = MaterialTheme.typography.labelLarge, color = c.content, fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.weight(1f)) {
                Text("RITUALE HEUTE", style = MaterialTheme.typography.labelMedium, color = c.accent)
                Text(
                    when {
                        total == 0 -> if (hasPlan) "Heute frei" else "Noch nichts geplant"
                        left == 0 -> "Alles geschafft!"
                        left == 1 -> "Noch 1 Ritual"
                        else -> "Noch $left Rituale"
                    },
                    style = MaterialTheme.typography.titleMedium, color = c.content,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Details", tint = c.accent)
        }
        rows()
    }
}

@Composable
private fun RitualLine(
    name: String, detail: String, checked: Boolean, streak: Int,
    onToggle: (Boolean) -> Unit, onOpen: () -> Unit,
) {
    val c = Ww.colors.ritual
    Row(verticalAlignment = Alignment.CenterVertically) {
        CheckCircle(checked, c.accent, if (checked) "$name nicht erledigt" else "$name abhaken", onToggle, Modifier.offset(x = (-12).dp))
        Column(
            Modifier.weight(1f).offset(x = (-8).dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen).padding(vertical = 4.dp),
        ) {
            Text(
                name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = c.content,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
            )
            if (detail.isNotBlank()) Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        if (streak > 0) Streak(streak, c.accent)
    }
}

@Composable
private fun MissionTile(g: TagGroup, onOpen: () -> Unit, onQuick: (Mission) -> Unit, modifier: Modifier) {
    val c = Ww.colors.mission
    val color = tagColor(g.tag)
    Column(
        modifier.heightIn(min = 170.dp).areaCard(c.container, border = color).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 32.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TagChip(g.name, color, Modifier.weight(1f, fill = false))
            Spacer(Modifier.weight(1f))
            Text("${g.missions.count { it.isDone }}/${g.missions.size}", style = MaterialTheme.typography.labelLarge, color = c.muted)
        }
        g.missions.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        m.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = c.content,
                        textDecoration = if (m.isDone) TextDecoration.LineThrough else null, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    if (m.detail.isNotBlank()) Text(m.detail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = c.muted)
                    ProgressLine(m.progress.toFloat() / m.target, color, c.track)
                }
                QuickButton(m, color) { onQuick(m) }
            }
        }
    }
}

@Composable
fun QuickButton(m: Mission, color: Color, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val label = if (m.counter) "${m.title}: eins mehr" else if (m.isDone) "${m.title} wieder öffnen" else "${m.title} abhaken"
    OutlinedButton(
        onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        modifier = Modifier.size(44.dp).semantics { contentDescription = label },
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(0.dp),
        border = BorderStroke(2.dp, color),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (m.isDone) color else Color.Transparent,
            contentColor = if (m.isDone) Color.White else color,
        ),
    ) {
        when {
            m.isDone -> Icon(Icons.Rounded.Check, null, Modifier.size(20.dp))
            m.counter -> Text("+1", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
            else -> {}
        }
    }
}

@Composable
private fun AddMissionTile(onClick: () -> Unit, modifier: Modifier) {
    val c = Ww.colors.mission
    Column(
        modifier.heightIn(min = 120.dp).clip(RoundedCornerShape(22.dp))
            .border(BorderStroke(2.dp, c.accent.copy(alpha = .6f)), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Add, null, tint = c.accent, modifier = Modifier.size(28.dp))
        Text("Mission", style = MaterialTheme.typography.labelLarge, color = c.muted)
    }
}
