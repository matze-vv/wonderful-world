package de.wonderfulworld.app.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import de.wonderfulworld.app.WonderfulWorldApp
import de.wonderfulworld.app.data.JournalEntry
import de.wonderfulworld.app.ui.appViewModel
import de.wonderfulworld.app.ui.components.BackTopBar
import de.wonderfulworld.app.ui.theme.Ww
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class EntryViewModel(val app: WonderfulWorldApp) : ViewModel() {
    private val repo = app.repo
    var original by mutableStateOf<JournalEntry?>(null)
        private set
    var text by mutableStateOf("")
    var photo by mutableStateOf<String?>(null)
        private set
    var timestamp by mutableStateOf(System.currentTimeMillis())
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    private var loadedId: Long? = null
    /** Während der Bearbeitung neu hinzugefügte Fotos (werden beim Verwerfen gelöscht). */
    private val addedPhotos = mutableListOf<String>()

    fun load(id: Long) {
        if (loadedId == id) return
        loadedId = id
        if (id == 0L) return
        viewModelScope.launch {
            repo.entry(id)?.let { e -> original = e; text = e.text; photo = e.photo; timestamp = e.timestamp }
        }
    }

    fun importPhoto(uri: Uri) = viewModelScope.launch {
        busy = true
        val result = withContext(Dispatchers.IO) { runCatching { repo.photos.importImage(uri) } }
        val name = result.getOrNull()
        if (name != null) {
            addedPhotos += name
            photo = name
        } else {
            error = "Das Foto konnte nicht geladen werden."
        }
        busy = false
    }

    fun removePhoto() { photo = null }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val e = (original ?: JournalEntry(timestamp = timestamp, text = "")).copy(text = text.trim(), photo = photo)
        repo.saveEntry(e)
        // Nicht mehr verwendete Fotos aufräumen
        (addedPhotos + listOfNotNull(original?.photo)).filter { it != photo }.forEach(repo.photos::delete)
        addedPhotos.clear()
        onDone()
    }

    fun discard(onDone: () -> Unit) {
        addedPhotos.forEach(repo.photos::delete)
        addedPhotos.clear()
        onDone()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        original?.let { repo.deleteEntry(it) }
        addedPhotos.forEach(repo.photos::delete)
        addedPhotos.clear()
        onDone()
    }
}

private val stampFmt = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EntryScreen(id: Long, onDone: () -> Unit) {
    val vm = appViewModel { EntryViewModel(it) }
    LaunchedEffect(id) { vm.load(id) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val c = Ww.colors.journal
    var confirmDelete by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(vm::importPhoto)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::importPhoto)
    }
    fun takePhoto() {
        val f = vm.app.repo.photos.newCameraFile()
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
        cameraUri = uri
        camera.launch(uri)
    }
    fun pickPhoto() = gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    val canSave = (vm.text.isNotBlank() || vm.photo != null) && !vm.busy
    BackHandler { vm.discard(onDone) }

    val date = Instant.ofEpochMilli(vm.timestamp).atZone(ZoneId.systemDefault())
    val dayText = when (date.toLocalDate()) {
        LocalDate.now() -> "Heute"
        LocalDate.now().minusDays(1) -> "Gestern"
        else -> date.toLocalDate().format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", java.util.Locale.GERMANY))
    } + " · " + date.format(stampFmt)

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = c.background,
        topBar = {
            BackTopBar(if (id == 0L) "Neuer Eintrag" else "Eintrag bearbeiten", { vm.discard(onDone) }, c.background, c.content) {
                if (vm.original != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Eintrag löschen") }
            }
        },
        bottomBar = {
            Button(
                onClick = { vm.save(onDone) }, enabled = canSave,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = MaterialTheme.colorScheme.surface),
            ) { Text("Speichern", fontWeight = FontWeight.ExtraBold) }
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- Foto-Kachel ----
            val p = vm.photo
            if (p == null) {
                Column(
                    Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(22.dp)).background(c.container)
                        .border(2.dp, c.track, RoundedCornerShape(22.dp))
                        .clickable(enabled = !vm.busy, onClickLabel = "Foto hinzufügen") { sheet = true },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (vm.busy) CircularProgressIndicator(color = c.accent)
                    else {
                        Box(Modifier.size(72.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(36.dp))
                        }
                        Text("Foto hinzufügen", Modifier.padding(top = 12.dp), color = c.accent, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 220.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(RoundedCornerShape(22.dp)).background(c.container)
                        .combinedClickable(
                            interactionSource = interaction, indication = null,
                            onClick = { fullscreen = true },
                            onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); sheet = true },
                        )
                        .semantics {
                            contentDescription = "Foto. Tippen für Vollbild, gedrückt halten zum Ändern."
                            onLongClick("Foto ändern") { sheet = true; true }
                        },
                ) {
                    AsyncImage(
                        model = vm.app.repo.photos.file(p), contentDescription = null,
                        contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Tippen: Vollbild · Halten: ändern",
                        Modifier.align(Alignment.BottomStart).padding(12.dp).clip(RoundedCornerShape(50))
                            .background(Color(0x9E14283C)).padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White, style = MaterialTheme.typography.labelMedium,
                    )
                    if (vm.busy) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                }
            }
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            // ---- Text-Kachel ----
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.container).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    dayText,
                    Modifier.clip(RoundedCornerShape(50)).background(c.accent).padding(horizontal = 12.dp, vertical = 5.dp),
                    color = MaterialTheme.colorScheme.surface, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold,
                )
                TextField(
                    value = vm.text, onValueChange = { vm.text = it },
                    placeholder = { Text("Was war schön? Was hast du erlebt?") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize, color = c.content),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = c.accent, focusedPlaceholderColor = c.muted, unfocusedPlaceholderColor = c.muted,
                    ),
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Text des Eintrags" },
                )
            }
        }
    }

    if (sheet) {
        ModalBottomSheet(onDismissRequest = { sheet = false }, containerColor = c.background) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (vm.photo == null) "Foto hinzufügen" else "Foto ändern", style = MaterialTheme.typography.titleMedium, color = c.content,
                    modifier = Modifier.padding(4.dp))
                SheetAction(Icons.Rounded.PhotoCamera, "Foto aufnehmen", c.container, c.content) { sheet = false; takePhoto() }
                SheetAction(Icons.Rounded.Image, "Aus Galerie wählen", c.container, c.content) { sheet = false; pickPhoto() }
                if (vm.photo != null) SheetAction(Icons.Rounded.DeleteOutline, "Foto entfernen", Color.Transparent, MaterialTheme.colorScheme.error) {
                    sheet = false; vm.removePhoto()
                }
            }
        }
    }
    if (fullscreen) vm.photo?.let { PhotoViewer(vm.app.repo.photos.file(it)) { fullscreen = false } }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eintrag löschen?") },
            text = { Text("Text und Foto werden endgültig gelöscht.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onDone) }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = fg)
        Text(label, color = fg, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}
