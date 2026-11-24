package de.wonderfulworld.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import de.wonderfulworld.app.WonderfulWorldApp
import de.wonderfulworld.app.data.Ritual
import de.wonderfulworld.app.data.Tag
import de.wonderfulworld.app.domain.Dates
import de.wonderfulworld.app.domain.shortDe
import de.wonderfulworld.app.ui.appViewModel
import de.wonderfulworld.app.ui.components.DayChips
import de.wonderfulworld.app.ui.components.TAG_PALETTE
import de.wonderfulworld.app.ui.components.TAG_PALETTE_NAMES
import de.wonderfulworld.app.ui.components.nextTagColor
import de.wonderfulworld.app.ui.theme.Ww
import kotlinx.coroutines.launch
import java.time.LocalDate

class ManageViewModel(val app: WonderfulWorldApp) : ViewModel() {
    val repo = app.repo
    fun launch(block: suspend () -> Unit) = viewModelScope.launch { block(); app.scheduler.scheduleNextImpulse() }
}

/** Ritual-Bearbeitung (id 0 = neu). */
private data class RitualDraft(val ritual: Ritual?)
private data class TagDraft(val tag: Tag?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen(onOpenSettings: () -> Unit) {
    val vm = appViewModel { ManageViewModel(it) }
    val rituals by vm.repo.rituals.collectAsStateWithLifecycle(emptyList())
    val tags by vm.repo.tags.collectAsStateWithLifecycle(emptyList())
    val missions by vm.repo.missions.collectAsStateWithLifecycle(emptyList())
    var tab by rememberSaveable { mutableStateOf(0) }
    var ritualDraft by remember { mutableStateOf<RitualDraft?>(null) }
    var tagDraft by remember { mutableStateOf<TagDraft?>(null) }
    val cs = MaterialTheme.colorScheme

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Mehr", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 4.dp)) }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf("Rituale", "Tags").forEachIndexed { i, label ->
                    val sel = tab == i
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (sel) cs.surfaceContainerLowest else Color.Transparent)
                            .clickable { tab = i }.semantics { selected = sel },
                        contentAlignment = Alignment.Center,
                    ) { Text(label, fontWeight = FontWeight.ExtraBold, color = if (sel) cs.onSurface else cs.onSurfaceVariant) }
                }
            }
        }

        if (tab == 0) {
            item {
                Text(
                    "Deine gespeicherten Rituale. Aktive Rituale stehen in der Sonntagsplanung schon mit ihren Standard-Tagen bereit – du passt nur noch an.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            items(rituals.filter { it.archivedOn == null }, key = { "r${it.id}" }) { r -> RitualCard(r, onEdit = { ritualDraft = RitualDraft(r) }) {
                vm.launch { vm.repo.setPaused(r, !it, LocalDate.now()) }
            } }
            item {
                DashedButton("+ Neues Ritual", Ww.colors.ritual.accent) { ritualDraft = RitualDraft(null) }
            }
        } else {
            item {
                Text(
                    "Jeder Tag bekommt im Dashboard eine eigene Missions-Kachel, umrandet in seiner Farbe.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            items(tags, key = { "t${it.id}" }) { t ->
                val count = missions.count { it.tagId == t.id && it.resolution == null }
                val c = Ww.colors.mission
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp).areaCard(c.container, border = Color(t.color), radius = 18.dp)
                        .clickable { tagDraft = TagDraft(t) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(Color(t.color)))
                    Text(t.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = c.content)
                    Text(if (count == 1) "1 Mission" else "$count Missionen", style = MaterialTheme.typography.labelLarge, color = c.muted)
                }
            }
            item { DashedButton("+ Neuer Tag", Ww.colors.mission.accent) { tagDraft = TagDraft(null) } }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainer)
                    .clickable(onClick = onOpenSettings).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.Settings, null, tint = cs.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text("Einstellungen & Backup", fontWeight = FontWeight.Bold)
                    Text("Sonntags-Erinnerung, Export und Import", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = cs.onSurfaceVariant)
            }
        }
    }

    ritualDraft?.let { d ->
        RitualSheet(
            d.ritual,
            onDismiss = { ritualDraft = null },
            onSave = { name, detail, mask ->
                vm.launch {
                    if (d.ritual == null) vm.repo.addRitual(name, LocalDate.now(), detail, mask)
                    else vm.repo.updateRitual(d.ritual.copy(name = name, detail = detail, defaultMask = mask))
                }
                ritualDraft = null
            },
            onArchive = d.ritual?.let { r -> { vm.launch { vm.repo.archiveRitual(r, LocalDate.now()) }; ritualDraft = null } },
        )
    }
    tagDraft?.let { d ->
        TagSheet(
            d.tag, defaultColor = nextTagColor(tags.map { it.color }),
            onDismiss = { tagDraft = null },
            onSave = { name, color ->
                vm.launch {
                    if (d.tag == null) vm.repo.addTag(name, color) else vm.repo.updateTag(d.tag.copy(name = name, color = color))
                }
                tagDraft = null
            },
            onDelete = d.tag?.let { t -> { vm.launch { vm.repo.deleteTag(t) }; tagDraft = null } },
        )
    }
}

@Composable
private fun RitualCard(r: Ritual, onEdit: () -> Unit, onActive: (Boolean) -> Unit) {
    val c = Ww.colors.ritual
    val active = r.pausedOn == null
    Row(
        Modifier.fillMaxWidth().areaCard(if (active) c.container else MaterialTheme.colorScheme.surfaceContainerHigh, radius = 18.dp)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(onClick = onEdit),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(r.name, style = MaterialTheme.typography.titleMedium, color = c.content)
            if (r.detail.isNotBlank()) Text(r.detail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = c.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Dates.WEEK_ORDER.forEach { dow ->
                    val on = r.defaultMask and Dates.bit(dow) != 0
                    Text(
                        dow.shortDe().take(2),
                        Modifier.size(width = 26.dp, height = 22.dp).clip(RoundedCornerShape(6.dp))
                            .background(if (on) c.accent else c.track.copy(alpha = .5f))
                            .padding(top = 3.dp),
                        color = if (on) MaterialTheme.colorScheme.surface else c.muted,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        Switch(
            checked = active, onCheckedChange = onActive,
            colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.semantics { contentDescription = r.name + if (active) " ist aktiv" else " ist pausiert" },
        )
    }
}

@Composable
private fun DashedButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(18.dp))
            .border(2.dp, color, RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = color, fontWeight = FontWeight.ExtraBold) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RitualSheet(
    r: Ritual?, onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit, onArchive: (() -> Unit)?,
) {
    val c = Ww.colors.ritual
    var name by remember { mutableStateOf(r?.name ?: "") }
    var detail by remember { mutableStateOf(r?.detail ?: "") }
    var mask by remember { mutableStateOf(r?.defaultMask ?: 0b1111111) }
    var confirmArchive by remember { mutableStateOf(false) }
    val colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.track, focusedLabelColor = c.accent, cursorColor = c.accent)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.background) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (r == null) "Neues Ritual" else "Ritual bearbeiten", style = MaterialTheme.typography.titleLarge, color = c.content)
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, colors = colors, shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(detail, { detail = it }, label = { Text("Details (optional)") }, minLines = 3, colors = colors, shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth())
            Text("Standard-Tage", style = MaterialTheme.typography.labelLarge, color = c.muted)
            DayChips(mask) { mask = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onArchive != null) TextButton(onClick = { confirmArchive = true }) {
                    Text("Archivieren", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.ExtraBold)
                }
                Button(
                    onClick = { onSave(name, detail, mask) }, enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = MaterialTheme.colorScheme.surface),
                ) { Text("Speichern", fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
    if (confirmArchive && onArchive != null) AlertDialog(
        onDismissRequest = { confirmArchive = false },
        title = { Text("„${r?.name}“ archivieren?") },
        text = { Text("Das Ritual wird ab heute nicht mehr geplant. Vergangene Häkchen bleiben im Archiv erhalten.") },
        confirmButton = { TextButton(onClick = { confirmArchive = false; onArchive() }) { Text("Archivieren") } },
        dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Abbrechen") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagSheet(
    t: Tag?, defaultColor: Int, onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit, onDelete: (() -> Unit)?,
) {
    val c = Ww.colors.mission
    var name by remember { mutableStateOf(t?.name ?: "") }
    var color by remember { mutableStateOf(t?.color ?: defaultColor) }
    var confirmDelete by remember { mutableStateOf(false) }
    val colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(color), unfocusedBorderColor = Color(color), focusedLabelColor = c.content, cursorColor = c.accent)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.background) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (t == null) "Neuer Tag" else "Tag bearbeiten", style = MaterialTheme.typography.titleLarge, color = c.content)
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, colors = colors, shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modifier = Modifier.fillMaxWidth())
            Text("Farbe", style = MaterialTheme.typography.labelLarge, color = c.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TAG_PALETTE.forEachIndexed { i, col ->
                    val sel = col == color
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                            .border(3.dp, if (sel) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                            .padding(4.dp).clip(CircleShape).background(Color(col))
                            .clickable { color = col }
                            .semantics { contentDescription = "Farbe " + TAG_PALETTE_NAMES[i]; selected = sel },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.ExtraBold)
                }
                Button(
                    onClick = { onSave(name, color) }, enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(color), contentColor = Color.White),
                ) { Text("Speichern", fontWeight = FontWeight.ExtraBold) }
            }
        }
    }
    if (confirmDelete && onDelete != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Tag „${t?.name}“ löschen?") },
        text = { Text("Die Missionen bleiben erhalten und landen in „Allgemein“.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Löschen") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
    )
}
