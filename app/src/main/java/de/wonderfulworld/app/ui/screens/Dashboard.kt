package de.wonderfulworld.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.wonderfulworld.app.data.TileLayout
import de.wonderfulworld.app.data.TileSize
import de.wonderfulworld.app.ui.components.Masonry
import de.wonderfulworld.app.ui.components.TileBox
import de.wonderfulworld.app.ui.components.TileSizeSheet
import de.wonderfulworld.app.ui.components.height
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

/** Eine Missions-Kachel: ein Tag mit seinen Missionen. */
data class TagGroup(val tag: Tag, val missions: List<Mission>)

/** Missionen je Tag (nur Tags mit Missionen) und die Missionen ohne Tag. */
fun groupByTag(missions: List<Mission>, tags: List<Tag>): Pair<List<TagGroup>, List<Mission>> {
    val byTag = missions.groupBy { it.tagId }
    val known = tags.map { it.id }.toSet()
    val groups = tags.mapNotNull { t -> byTag[t.id]?.let { TagGroup(t, it) } }
    val untagged = missions.filter { it.tagId == null || it.tagId !in known }
    return groups to untagged
}

/** Eine Kachel im Dashboard: Schlüssel für die gespeicherte Größe, Standardbreite und Inhalt. */
private class TileSpec(
    val key: String,
    val label: String,
    val defaultWide: Boolean,
    val resizable: Boolean = true,
    val content: @Composable (size: TileSize, onMenu: () -> Unit) -> Unit,
)

@Composable
fun DashboardScreen(
    onPlan: () -> Unit,
    onNewEntry: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onOpenRituals: () -> Unit,
    onOpenTag: (Long) -> Unit,
    onOpenMission: (Long) -> Unit,
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
    val (groups, untagged) = groupByTag(monthMissions, tags)
    val settings by vm.app.settings.settings.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<TileSpec?>(null) }
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

            // ---- Alle Kacheln im Mauerwerk-Layout ----
            item {
                val specs = buildList {
                    add(TileSpec("rituals", "Rituale heute", defaultWide = true) { size, onMenu ->
                        val due = c?.dueRituals(today).orEmpty()
                        val done = due.count { c!!.isChecked(it.id, today) }
                        RitualsTile(due.size, done, c?.effectivePlan(week) != null, size, onOpenRituals, onMenu) {
                            due.forEach { r ->
                                val checked = c!!.isChecked(r.id, today)
                                RitualLine(
                                    name = r.name, detail = r.detail, checked = checked, streak = c.streak(r.id, today),
                                    onToggle = { vm.launch { vm.repo.setChecked(r.id, today, it) } },
                                    onOpen = onOpenRituals, onMenu = onMenu,
                                )
                            }
                        }
                    })
                    groups.forEach { g ->
                        add(TileSpec("tag:${g.tag.id}", "Tag „${g.tag.name}“", defaultWide = false) { size, onMenu ->
                            MissionTile(g, size, onOpen = { onOpenTag(g.tag.id) }, onQuick = vm::quick, onMenu = onMenu)
                        })
                    }
                    untagged.forEach { m ->
                        add(TileSpec("mission:${m.id}", "Mission „${m.title}“", defaultWide = false) { size, onMenu ->
                            SingleMissionTile(m, size, onOpen = { onOpenMission(m.id) }, onQuick = vm::quick, onMenu = onMenu)
                        })
                    }
                    add(TileSpec("add", "", defaultWide = false, resizable = false) { _, _ -> AddMissionTile(onNewMission) })
                    if (weekEntries.isEmpty()) {
                        add(TileSpec("noentries", "", defaultWide = true, resizable = false) { _, _ ->
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
                        })
                    }
                    weekEntries.forEachIndexed { i, e ->
                        add(TileSpec("entry:${e.id}", "Tagebucheintrag", defaultWide = i == 0) { size, onMenu ->
                            JournalTile(
                                e, vm.app, today, onClick = { onOpenEntry(e.id) }, onLongClick = onMenu,
                                height = size.height() ?: if (i == 0) 220.dp else 190.dp,
                            )
                        })
                    }
                }
                fun layoutOf(t: TileSpec) = settings.tiles[t.key] ?: TileLayout(t.defaultWide)
                Masonry(wide = specs.map { layoutOf(it).wide }) {
                    specs.forEach { t ->
                        key(t.key) { t.content(layoutOf(t).size) { if (t.resizable) menuFor = t } }
                    }
                }
            }
        }
    }

    menuFor?.let { t ->
        TileSizeSheet(
            title = t.label,
            layout = settings.tiles[t.key] ?: TileLayout(t.defaultWide),
            onChange = { vm.app.settings.setTile(t.key, it) },
            onReset = { vm.app.settings.setTile(t.key, null); menuFor = null },
            onDismiss = { menuFor = null },
        )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RitualsTile(
    total: Int, done: Int, hasPlan: Boolean, size: TileSize,
    onOpen: () -> Unit, onMenu: () -> Unit, rows: @Composable () -> Unit,
) {
    val c = Ww.colors.ritual
    val left = total - done
    Box(Modifier.fillMaxWidth().areaCard(c.container).combinedClickable(onClick = onOpen, onLongClick = onMenu)) {
        TileBox(size, autoMin = 120.dp, fade = c.container) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).combinedClickable(onClick = onOpen, onLongClick = onMenu),
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RitualLine(
    name: String, detail: String, checked: Boolean, streak: Int,
    onToggle: (Boolean) -> Unit, onOpen: () -> Unit, onMenu: () -> Unit,
) {
    val c = Ww.colors.ritual
    Row(verticalAlignment = Alignment.CenterVertically) {
        CheckCircle(checked, c.accent, if (checked) "$name nicht erledigt" else "$name abhaken", onToggle, Modifier.offset(x = (-12).dp))
        Column(
            Modifier.weight(1f).offset(x = (-8).dp).clip(RoundedCornerShape(8.dp))
                .combinedClickable(onClick = onOpen, onLongClick = onMenu).padding(vertical = 4.dp),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MissionTile(g: TagGroup, size: TileSize, onOpen: () -> Unit, onQuick: (Mission) -> Unit, onMenu: () -> Unit) {
    val c = Ww.colors.mission
    val color = tagColor(g.tag)
    Box(Modifier.fillMaxWidth().areaCard(c.container, border = color).combinedClickable(onClick = onOpen, onLongClick = onMenu)) {
        TileBox(size, autoMin = 120.dp, fade = c.container) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 32.dp).clip(RoundedCornerShape(10.dp))
                        .combinedClickable(onClick = onOpen, onLongClick = onMenu),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TagChip(g.tag.name, color, Modifier.weight(1f, fill = false))
                    Spacer(Modifier.weight(1f))
                    Text("${g.missions.count { it.isDone }}/${g.missions.size}", style = MaterialTheme.typography.labelLarge, color = c.muted)
                }
                g.missions.forEach { m -> MissionLine(m, color, onOpen, onQuick, onMenu) }
            }
        }
    }
}

/** Kachel für eine einzelne Mission ohne Tag – ohne Kopfzeile. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SingleMissionTile(m: Mission, size: TileSize, onOpen: () -> Unit, onQuick: (Mission) -> Unit, onMenu: () -> Unit) {
    val c = Ww.colors.mission
    Box(Modifier.fillMaxWidth().areaCard(c.container).combinedClickable(onClick = onOpen, onLongClick = onMenu)) {
        TileBox(size, autoMin = 120.dp, fade = c.container) {
            Column(Modifier.padding(12.dp)) { MissionLine(m, c.accent, onOpen, onQuick, onMenu, maxDetailLines = 3) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MissionLine(
    m: Mission, color: Color, onOpen: () -> Unit, onQuick: (Mission) -> Unit, onMenu: () -> Unit, maxDetailLines: Int = 1,
) {
    val c = Ww.colors.mission
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = onOpen, onLongClick = onMenu),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                m.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = c.content,
                textDecoration = if (m.isDone) TextDecoration.LineThrough else null, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (m.detail.isNotBlank()) Text(m.detail, maxLines = maxDetailLines, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = c.muted)
            if (m.counter) Text("${m.progress}/${m.target}", style = MaterialTheme.typography.labelMedium, color = c.muted)
            ProgressLine(m.progress.toFloat() / m.target, color, c.track)
        }
        QuickButton(m, color) { onQuick(m) }
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
private fun AddMissionTile(onClick: () -> Unit) {
    val c = Ww.colors.mission
    Column(
        Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(22.dp))
            .border(BorderStroke(2.dp, c.accent.copy(alpha = .6f)), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Add, null, tint = c.accent, modifier = Modifier.size(28.dp))
        Text("Mission", style = MaterialTheme.typography.labelLarge, color = c.muted)
    }
}
