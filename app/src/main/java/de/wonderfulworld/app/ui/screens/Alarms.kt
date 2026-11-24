package de.wonderfulworld.app.ui.screens

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import de.wonderfulworld.app.WonderfulWorldApp
import de.wonderfulworld.app.alarm.AlarmService
import de.wonderfulworld.app.data.AlarmSetting
import de.wonderfulworld.app.domain.Dates
import de.wonderfulworld.app.domain.GERMAN
import de.wonderfulworld.app.domain.formatMinutes
import de.wonderfulworld.app.domain.longDe
import de.wonderfulworld.app.ui.appViewModel
import de.wonderfulworld.app.ui.components.Muted
import de.wonderfulworld.app.ui.components.PaperCard
import de.wonderfulworld.app.ui.components.SectionTitle
import de.wonderfulworld.app.ui.components.TimePickerDialog
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class AlarmsViewModel(val app: WonderfulWorldApp) : ViewModel() {
    val alarms = app.repo.alarms
    var next by mutableStateOf<LocalDateTime?>(null)
        private set

    fun refreshNext() = viewModelScope.launch { next = app.scheduler.nextAlarmTime() }

    fun save(a: AlarmSetting) = viewModelScope.launch {
        app.repo.saveAlarm(a)
        app.scheduler.rescheduleAll()
        next = app.scheduler.nextAlarmTime()
    }

    fun test() = app.scheduler.scheduleTestAlarm()
}

private val nextFmt = DateTimeFormatter.ofPattern("EEEE, HH:mm 'Uhr'", GERMAN)

@Composable
fun AlarmsScreen() {
    val vm = appViewModel { AlarmsViewModel(it) }
    val context = LocalContext.current
    val alarms by vm.alarms.collectAsStateWithLifecycle(emptyList())
    var editDay by remember { mutableStateOf<AlarmSetting?>(null) }
    var notificationsOk by remember { mutableStateOf(true) }
    var fullScreenOk by remember { mutableStateOf(true) }
    var testInfo by remember { mutableIntStateOf(0) }

    LaunchedEffect(alarms) { vm.refreshNext() }
    LifecycleResumeEffect(Unit) {
        notificationsOk = NotificationManagerCompat.from(context).areNotificationsEnabled()
        fullScreenOk = Build.VERSION.SDK_INT < 34 ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        onPauseOrDispose { }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Wecker", style = MaterialTheme.typography.headlineMedium)
        Muted(vm.next?.let { "Nächster Wecker: " + it.format(nextFmt) } ?: "Kein Wecker aktiv.")

        if (!notificationsOk) Warning(
            "Benachrichtigungen sind aus. Ohne sie kann der Wecker nicht zuverlässig klingeln.",
            "Einstellungen öffnen",
        ) {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
        if (!fullScreenOk && Build.VERSION.SDK_INT >= 34) Warning(
            "Für die Anzeige über dem Sperrbildschirm braucht die App die Berechtigung „Vollbild-Benachrichtigungen“.",
            "Erlauben",
        ) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + context.packageName))
            )
        }

        PaperCard {
            val byDay = alarms.associateBy { it.dayOfWeek }
            Dates.WEEK_ORDER.forEachIndexed { i, dow ->
                val a = byDay[dow.value] ?: AlarmSetting(dow.value, false, 7, 0)
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AlarmRow(dow, a, onTime = { editDay = a }, onToggle = { vm.save(a.copy(enabled = it)) })
            }
        }
        Muted("Standard-Weckton des Systems · Schlummern ${AlarmService.SNOOZE_MINUTES} Min., höchstens ${AlarmService.MAX_SNOOZES}×. Nach dem Ausschalten öffnet sich das Dashboard und der Morgen-Impuls erscheint.")

        SectionTitle("Ausprobieren")
        OutlinedButton(onClick = { vm.test(); testInfo++ }) { Text("Test-Wecker in 10 Sekunden") }
        if (testInfo > 0) Muted("Sperr ruhig den Bildschirm – gleich klingelt es.")
    }

    editDay?.let { a ->
        TimePickerDialog(a.hour * 60 + a.minute, DayOfWeek.of(a.dayOfWeek).longDe(), onDismiss = { editDay = null }) { m ->
            vm.save(a.copy(hour = m / 60, minute = m % 60, enabled = true))
            editDay = null
        }
    }
}

@Composable
private fun AlarmRow(dow: DayOfWeek, a: AlarmSetting, onTime: () -> Unit, onToggle: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(dow.longDe(), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onTime) {
            Text(
                formatMinutes(a.hour * 60 + a.minute),
                style = MaterialTheme.typography.titleLarge,
                color = if (a.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = a.enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun Warning(text: String, action: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(text, color = MaterialTheme.colorScheme.onPrimaryContainer)
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}
