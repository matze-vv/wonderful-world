package de.wonderfulworld.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import de.wonderfulworld.app.ui.components.MasonryItem
import de.wonderfulworld.app.ui.components.ReorderableMasonry
import de.wonderfulworld.app.ui.components.mergeOrder
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.wonderfulworld.app.data.TileLayout
import de.wonderfulworld.app.data.TileSize
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
    /** XS ist nur für Kacheln mit einer einzelnen Mission erlaubt. */
    val allowXs: Boolean = false,
    val content: @Composable (size: TileSize) -> Unit,
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
    var chooser by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var viewport by remember { mutableStateOf<Pair<Float, Float>?>(null) }
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
            FloatingActionButton(
                onClick = { chooser = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
            ) { Icon(Icons.Rounded.Add, "Neu: Mission oder Erlebnis", Modifier.size(30.dp)) }
        },
    ) { inner ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(inner).onGloballyPositioned {
                val top = it.positionInWindow().y
                viewport = top to top + it.size.height
            },
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
                    Stat("${weekEntries.size}", "Erlebnisse", Ww.colors.journal.container, Ww.colors.journal.content, Modifier.weight(1f))
                }
            }

            if (needsWeek || needsMonth) item {
                PlanningBanner(
                    needsMonth = needsMonth, monthName = month.monthNameDe(), onPlan = onPlan,
                    onKeep = if (!needsMonth && c?.effectivePlan(week) != null) ({ vm.keepLastWeek(week) }) else null,
                )
            }

            // ---- Alle Kacheln: verschiebbar (gedrückt halten + ziehen), Größe per Menü ----
            item {
                val specs = buildList {
                    add(TileSpec("rituals", "Rituale heute", defaultWide = true) { size ->
                        val due = c?.dueRituals(today).orEmpty()
                        val done = due.count { c!!.isChecked(it.id, today) }
                        RitualsTile(due.size, done, c?.effectivePlan(week) != null, size, onOpenRituals) {
                            due.forEach { r ->
                                val checked = c!!.isChecked(r.id, today)
                                RitualLine(
                                    name = r.name, detail = r.detail, checked = checked, streak = c.streak(r.id, today),
                                    onToggle = { vm.launch { vm.repo.setChecked(r.id, today, it) } },
                                    onOpen = onOpenRituals,
                                )
                            }
                        }
                    })
                    groups.forEach { g ->
                        add(TileSpec("tag:${g.tag.id}", "Tag „${g.tag.name}“", defaultWide = false) { size ->
                            MissionTile(g, size, onOpen = { onOpenTag(g.tag.id) }, onQuick = vm::quick)
                        })
                    }
                    untagged.forEach { m ->
                        add(TileSpec("mission:${m.id}", "Mission „${m.title}“", defaultWide = false, allowXs = true) { size ->
                            SingleMissionTile(m, size, onOpen = { onOpenMission(m.id) }, onQuick = vm::quick)
                        })
                    }
                    if (weekEntries.isEmpty()) {
                        add(TileSpec("noentries", "", defaultWide = true, resizable = false) { _ ->
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                                    .border(BorderStroke(2.dp, Ww.colors.journal.track), RoundedCornerShape(22.dp))
                                    .clickable(onClick = onNewEntry).padding(20.dp),
                            ) {
                                Text(
                                    "Noch keine Erlebnisse diese Woche. Was hat dich heute bewegt?",
                                    color = Ww.colors.journal.muted, style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        })
                    }
                    weekEntries.forEachIndexed { i, e ->
                        add(TileSpec("entry:${e.id}", "Erlebnis", defaultWide = i == 0) { size ->
                            JournalTile(
                                e, vm.app, today, onClick = { onOpenEntry(e.id) },
                                height = size.height() ?: if (i == 0) 220.dp else 190.dp,
                            )
                        })
                    }
                }
                fun layoutOf(t: TileSpec) = settings.tiles[t.key] ?: TileLayout(t.defaultWide)
                ReorderableMasonry(
                    items = specs.map { t ->
                        val l = layoutOf(t)
                        val size = if (l.size == TileSize.XS && !t.allowXs) TileSize.S else l.size
                        MasonryItem(
                            key = t.key, wide = l.wide, movable = t.resizable,
                            onHold = if (t.resizable) ({ menuFor = t }) else null,
                        ) { t.content(size) }
                    },
                    order = mergeOrder(settings.tileOrder, specs.map { it.key }),
                    onReorder = { vm.app.settings.setTileOrder(it) },
                    listState = listState,
                    viewport = { viewport },
                )
            }
        }
    }

    menuFor?.let { t ->
        TileSizeSheet(
            title = t.label,
            layout = settings.tiles[t.key] ?: TileLayout(t.defaultWide),
            allowXs = t.allowXs,
            onChange = { vm.app.settings.setTile(t.key, it) },
            onReset = { vm.app.settings.setTile(t.key, null); menuFor = null },
            onDismiss = { menuFor = null },
        )
    }
    if (chooser) NewChooser(
        onMission = { chooser = false; onNewMission() },
        onEntry = { chooser = false; onNewEntry() },
        onDismiss = { chooser = false },
    )
}

/** Auswahl nach dem +: Mission oder Erlebnis. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewChooser(onMission: () -> Unit, onEntry: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Was möchtest du anlegen?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChooserTile("Mission", "Ein Ziel für diesen Monat", Icons.Rounded.Flag, Ww.colors.mission, onMission, Modifier.weight(1f))
                ChooserTile("Erlebnis", "Was hast du heute erlebt?", Icons.Rounded.EditNote, Ww.colors.journal, onEntry, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ChooserTile(
    title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    colors: de.wonderfulworld.app.ui.theme.AreaColors, onClick: () -> Unit, modifier: Modifier,
) {
    Column(
        modifier.heightIn(min = 140.dp).areaCard(colors.container).clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.surface)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.content)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.muted)
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
    onOpen: () -> Unit, rows: @Composable () -> Unit,
) {
    val c = Ww.colors.ritual
    val left = total - done
    Box(Modifier.fillMaxWidth().areaCard(c.container).clickable(onClick = onOpen)) {
        TileBox(size, autoMin = 120.dp, fade = c.container) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RitualLine(
    name: String, detail: String, checked: Boolean, streak: Int,
    onToggle: (Boolean) -> Unit, onOpen: () -> Unit,
) {
    val c = Ww.colors.ritual
    Row(verticalAlignment = Alignment.CenterVertically) {
        CheckCircle(checked, c.accent, if (checked) "$name nicht erledigt" else "$name abhaken", onToggle, Modifier.offset(x = (-12).dp))
        Column(
            Modifier.weight(1f).offset(x = (-8).dp).clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpen).padding(vertical = 4.dp),
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
private fun MissionTile(g: TagGroup, size: TileSize, onOpen: () -> Unit, onQuick: (Mission) -> Unit) {
    val c = Ww.colors.mission
    val color = tagColor(g.tag)
    Box(Modifier.fillMaxWidth().areaCard(c.container, border = color).clickable(onClick = onOpen)) {
        TileBox(size, autoMin = 120.dp, fade = c.container) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 32.dp).clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpen),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Tag darf bis kurz vor den Zähler wachsen, danach „…“
                    Box(Modifier.weight(1f)) { TagChip(g.tag.name, color) }
                    Text("${g.missions.count { it.isDone }}/${g.missions.size}", style = MaterialTheme.typography.labelLarge, color = c.muted, maxLines = 1)
                }
                g.missions.forEach { m -> MissionLine(m, color, onOpen, onQuick) }
            }
        }
    }
}

/** Kachel für eine einzelne Mission ohne Tag – ohne Kopfzeile. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SingleMissionTile(m: Mission, size: TileSize, onOpen: () -> Unit, onQuick: (Mission) -> Unit) {
    val c = Ww.colors.mission
    val xs = size == TileSize.XS
    Box(Modifier.fillMaxWidth().areaCard(c.container).clickable(onClick = onOpen)) {
        TileBox(size, autoMin = if (xs) 76.dp else 120.dp, fade = c.container) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = if (xs) 6.dp else 12.dp)) {
                MissionLine(m, c.accent, onOpen, onQuick, maxDetailLines = 3, compact = xs)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MissionLine(
    m: Mission, color: Color, onOpen: () -> Unit, onQuick: (Mission) -> Unit,
    maxDetailLines: Int = 1, compact: Boolean = false,
) {
    val c = Ww.colors.mission
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                m.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = c.content,
                textDecoration = if (m.isDone) TextDecoration.LineThrough else null,
                maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis,
            )
            if (!compact && m.detail.isNotBlank()) Text(m.detail, maxLines = maxDetailLines, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = c.muted)
            if (m.counter && !compact) Text("${m.progress}/${m.target}", style = MaterialTheme.typography.labelMedium, color = c.muted)
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
