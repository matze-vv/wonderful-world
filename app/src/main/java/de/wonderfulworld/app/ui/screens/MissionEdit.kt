package de.wonderfulworld.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import de.wonderfulworld.app.WonderfulWorldApp
import de.wonderfulworld.app.data.Mission
import de.wonderfulworld.app.domain.Dates
import de.wonderfulworld.app.ui.appViewModel
import de.wonderfulworld.app.ui.components.BackTopBar
import de.wonderfulworld.app.ui.components.nextTagColor
import de.wonderfulworld.app.ui.theme.Ww
import kotlinx.coroutines.launch
import java.time.LocalDate

class MissionEditViewModel(val app: WonderfulWorldApp) : ViewModel() {
    val repo = app.repo
    var original by mutableStateOf<Mission?>(null); private set
    var title by mutableStateOf("")
    var detail by mutableStateOf("")
    var tagId by mutableStateOf<Long?>(null)
    var counter by mutableStateOf(false)
    var target by mutableIntStateOf(4)
    private var loaded = false

    fun load(id: Long, presetTag: Long) {
        if (loaded) return
        loaded = true
        if (presetTag > 0) tagId = presetTag
        if (id == 0L) return
        viewModelScope.launch {
            repo.mission(id)?.let { m ->
                original = m; title = m.title; detail = m.detail; tagId = m.tagId
                counter = m.counter; target = if (m.counter) m.target else 4
            }
        }
    }

    fun addTag(name: String, used: List<Int>) = viewModelScope.launch {
        tagId = repo.addTag(name, nextTagColor(used)).id
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val o = original
        if (o == null) {
            repo.addMission(Dates.missionMonth(LocalDate.now()), title, counter, target, detail, tagId)
        } else {
            val t = if (counter) target else 1
            repo.updateMission(
                o.copy(
                    title = title.trim(), detail = detail.trim(), tagId = tagId, counter = counter, target = t,
                    progress = if (counter) o.progress.coerceAtMost(t) else minOf(o.progress, 1),
                )
            )
        }
        onDone()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        original?.let { repo.deleteMission(it) }
        onDone()
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MissionEditScreen(id: Long, presetTag: Long, onDone: () -> Unit) {
    val vm = appViewModel { MissionEditViewModel(it) }
    LaunchedEffect(id) { vm.load(id, presetTag) }
    val tags by vm.repo.tags.collectAsStateWithLifecycle(emptyList())
    val c = Ww.colors.mission
    var adding by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.accent, unfocusedBorderColor = c.track,
        focusedLabelColor = c.accent, cursorColor = c.accent,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    )
    val addTag = {
        if (draft.isNotBlank()) vm.addTag(draft, tags.map { it.color })
        draft = ""; adding = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = c.background,
        topBar = { BackTopBar(if (id == 0L) "Neue Mission" else "Mission bearbeiten", onDone, c.background, c.content) },
        bottomBar = {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (vm.original != null) TextButton(onClick = { confirmDelete = true }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.ExtraBold)
                }
                Button(
                    onClick = { vm.save(onDone) },
                    enabled = vm.title.isNotBlank(),
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = MaterialTheme.colorScheme.surface),
                ) { Text("Speichern", fontWeight = FontWeight.ExtraBold) }
            }
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            OutlinedTextField(
                vm.title, { vm.title = it }, label = { Text("Was möchtest du schaffen?") },
                singleLine = true, colors = fieldColors, shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    vm.detail, { vm.detail = it }, label = { Text("Details (optional)") },
                    minLines = 3, colors = fieldColors, shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Der Anfang ist in der Kachel zu sehen, der ganze Text in der Detailansicht.",
                    style = MaterialTheme.typography.bodySmall, color = c.muted)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tag", style = MaterialTheme.typography.labelLarge, color = c.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { t ->
                        val col = Color(t.color)
                        val sel = vm.tagId == t.id
                        FilterChip(
                            selected = sel,
                            onClick = { vm.tagId = if (sel) null else t.id },
                            label = { Text(t.name, fontWeight = FontWeight.ExtraBold) },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = c.container, labelColor = c.content,
                                selectedContainerColor = col, selectedLabelColor = Color.White,
                            ),
                            border = FilterChipDefaults.filterChipBorder(true, sel, borderColor = col, selectedBorderColor = col, borderWidth = 2.dp, selectedBorderWidth = 2.dp),
                        )
                    }
                    if (adding) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                draft, { draft = it }, placeholder = { Text("z. B. Sport") }, singleLine = true,
                                colors = fieldColors, shape = RoundedCornerShape(50),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { addTag() }),
                                modifier = Modifier.width(160.dp),
                            )
                            FilledIconButton(
                                onClick = addTag,
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.accent),
                            ) { Icon(Icons.Rounded.Check, "Tag hinzufügen", tint = MaterialTheme.colorScheme.surface) }
                        }
                    } else {
                        FilterChip(
                            selected = false, onClick = { adding = true },
                            label = { Text("+ Neuer Tag", fontWeight = FontWeight.ExtraBold) },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(containerColor = Color.Transparent, labelColor = c.content),
                            border = FilterChipDefaults.filterChipBorder(true, false, borderColor = c.accent, borderWidth = 2.dp),
                        )
                    }
                }
                Text("Jeder Tag bekommt im Dashboard eine eigene Kachel.", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Art", style = MaterialTheme.typography.labelLarge, color = c.muted)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(false to "Einfach", true to "Mit Zähler").forEachIndexed { i, (value, label) ->
                        SegmentedButton(
                            selected = vm.counter == value, onClick = { vm.counter = value },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = c.accent, activeContentColor = MaterialTheme.colorScheme.surface,
                                inactiveContainerColor = Color.Transparent, inactiveContentColor = c.content,
                                activeBorderColor = c.accent, inactiveBorderColor = c.track,
                            ),
                            icon = {},
                        ) { Text(label, fontWeight = FontWeight.ExtraBold) }
                    }
                }
                if (vm.counter) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ziel", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = c.content)
                    OutlinedIconButton(onClick = { vm.target = (vm.target - 1).coerceAtLeast(1) }, border = BorderStroke(2.dp, c.accent)) {
                        Icon(Icons.Rounded.Remove, "Weniger", tint = c.accent)
                    }
                    Text("${vm.target}", Modifier.widthIn(min = 40.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge, color = c.content)
                    FilledIconButton(
                        onClick = { vm.target = (vm.target + 1).coerceAtMost(999) },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.accent),
                    ) { Icon(Icons.Rounded.Add, "Mehr", tint = MaterialTheme.colorScheme.surface) }
                }
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Mission löschen?") },
        text = { Text("„${vm.title}“ wird endgültig gelöscht.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onDone) }) { Text("Löschen") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
    )
}
