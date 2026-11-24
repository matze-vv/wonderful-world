package de.wonderfulworld.app.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import de.wonderfulworld.app.WonderfulWorldApp
import de.wonderfulworld.app.data.JournalEntry
import de.wonderfulworld.app.ui.appViewModel
import de.wonderfulworld.app.ui.components.Muted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.Locale

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
        val name = withContext(Dispatchers.IO) { runCatching { repo.photos.importImage(uri) }.getOrNull() }
        if (name != null) {
            addedPhotos += name
            photo = name
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

private val headerFmt = DateTimeFormatter.ofPattern("EEEE, d. MMMM · HH:mm", Locale.GERMANY)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(id: Long, onDone: () -> Unit) {
    val vm = appViewModel { EntryViewModel(it) }
    LaunchedEffect(id) { vm.load(id) }
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(vm::importPhoto)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::importPhoto)
    }

    val canSave = (vm.text.isNotBlank() || vm.photo != null) && !vm.busy
    BackHandler { vm.discard(onDone) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (id == 0L) "Neuer Eintrag" else "Eintrag") },
                navigationIcon = { IconButton(onClick = { vm.discard(onDone) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück") } },
                actions = {
                    if (vm.original != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Löschen") }
                    TextButton(onClick = { vm.save(onDone) }, enabled = canSave) { Text("Speichern") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0),
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Muted(java.time.Instant.ofEpochMilli(vm.timestamp).atZone(java.time.ZoneId.systemDefault()).format(headerFmt))
            OutlinedTextField(
                value = vm.text, onValueChange = { vm.text = it },
                placeholder = { Text("Was war schön? Was hast du erlebt?") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            )

            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                val p = vm.photo
                if (p != null) {
                    AsyncImage(
                        model = vm.app.repo.photos.file(p), contentDescription = "Foto",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
                    )
                }
                if (vm.busy) CircularProgressIndicator()
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val f = vm.app.repo.photos.newCameraFile()
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
                    cameraUri = uri
                    camera.launch(uri)
                }) {
                    Icon(Icons.Outlined.PhotoCamera, null); Text("  Kamera")
                }
                OutlinedButton(onClick = {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Outlined.Image, null); Text("  Galerie")
                }
                if (vm.photo != null) TextButton(onClick = vm::removePhoto) { Text("Foto entfernen") }
            }
            Muted("Pro Eintrag ist ein Foto möglich – ein neues ersetzt das alte.")
        }
    }

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
