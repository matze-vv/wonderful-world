package de.wonderfulworld.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import android.net.Uri
import de.wonderfulworld.app.WonderfulWorldApp
import de.wonderfulworld.app.domain.formatMinutes
import de.wonderfulworld.app.ui.appViewModel
import de.wonderfulworld.app.ui.components.Muted
import de.wonderfulworld.app.ui.components.PaperCard
import de.wonderfulworld.app.ui.components.SectionTitle
import de.wonderfulworld.app.ui.components.TimePickerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class SettingsViewModel(val app: WonderfulWorldApp) : ViewModel() {
    val settings = app.settings.settings
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    fun setSundayReminder(minutes: Int) {
        app.settings.update(settings.value.copy(sundayReminderMinutes = minutes))
        app.scheduler.scheduleSundayReminder()
    }

    fun export(uri: Uri) = run("Backup gespeichert.") { app.backup.export(uri) }

    fun import(uri: Uri) = run("Backup wiederhergestellt.") {
        app.backup.import(uri)
        app.scheduler.rescheduleAll()
    }

    private fun run(success: String, block: suspend () -> Unit) = viewModelScope.launch {
        busy = true
        message = try {
            withContext(Dispatchers.IO) { block() }
            success
        } catch (e: Exception) {
            "Fehler: ${e.message ?: e.javaClass.simpleName}"
        }
        busy = false
    }
}

@Composable
fun SettingsScreen(onEditTheme: () -> Unit = {}) {
    val vm = appViewModel { SettingsViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    var pickTime by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(vm::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImport = uri
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("Farbschema")
        ThemePicker(onEditCustom = onEditTheme)

        SectionTitle("Sonntagsplanung")
        PaperCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Erinnerung sonntags um", style = MaterialTheme.typography.bodyLarge)
                    Muted("Nur, wenn die Woche noch nicht geplant ist.")
                }
                TextButton(onClick = { pickTime = true }) {
                    Text(formatMinutes(settings.sundayReminderMinutes), style = MaterialTheme.typography.titleLarge)
                }
            }
        }

        SectionTitle("Backup")
        PaperCard {
            Muted("Alle Daten bleiben auf dem Handy. Ein Backup ist eine ZIP-Datei mit allen Erlebnissen, Ritualen, Missionen, Weckzeiten und Fotos.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(enabled = !vm.busy, onClick = {
                    exportLauncher.launch("wonderful-world-backup-${LocalDate.now()}.zip")
                }) { Text("Exportieren") }
                OutlinedButton(enabled = !vm.busy, onClick = {
                    importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                }) { Text("Importieren") }
                if (vm.busy) CircularProgressIndicator(Modifier.padding(start = 8.dp))
            }
            vm.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }

        SectionTitle("Über")
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val version = remember { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
        Muted("Wonderful World $version – dein Anti-Rot-Journal.")
    }

    if (pickTime) {
        TimePickerDialog(settings.sundayReminderMinutes, "Sonntags-Erinnerung", onDismiss = { pickTime = false }) {
            vm.setSundayReminder(it); pickTime = false
        }
    }
    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Backup importieren?") },
            text = { Text("Alle aktuellen Daten in der App werden durch den Inhalt des Backups ersetzt.") },
            confirmButton = { TextButton(onClick = { vm.import(uri); pendingImport = null }) { Text("Ersetzen") } },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Abbrechen") } },
        )
    }
}
