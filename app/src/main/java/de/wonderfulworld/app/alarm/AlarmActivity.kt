package de.wonderfulworld.app.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.wonderfulworld.app.MainActivity
import de.wonderfulworld.app.domain.fullDe
import de.wonderfulworld.app.ui.theme.WonderfulWorldTheme
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Vollbild-Anzeige des Weckers, auch über dem Sperrbildschirm. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            WonderfulWorldTheme {
                val ringing by AlarmService.ringing.collectAsStateWithLifecycle()
                var seenRinging by remember { mutableStateOf(false) }
                LaunchedEffect(ringing) {
                    if (ringing != null) seenRinging = true
                    else if (seenRinging) finish() // anderswo ausgeschaltet/geschlummert
                }
                BackHandler { /* Zurück schaltet den Wecker nicht aus */ }
                AlarmContent(
                    snoozesLeft = AlarmService.MAX_SNOOZES - (ringing ?: intent.getIntExtra(AlarmService.EXTRA_SNOOZE_COUNT, 0)),
                    onSnooze = {
                        AlarmService.command(this, AlarmService.ACTION_SNOOZE)
                        finish()
                    },
                    onDismiss = ::dismissAndOpenDashboard,
                )
            }
        }
    }

    private fun dismissAndOpenDashboard() {
        AlarmService.command(this, AlarmService.ACTION_DISMISS)
        val main = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_DEST, "dashboard")
        val km = getSystemService(KeyguardManager::class.java)
        if (km.isKeyguardLocked) {
            // Erst entsperren lassen, dann direkt zum Dashboard
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { startActivity(main); finish() }
                override fun onDismissCancelled() { finish() }
                override fun onDismissError() { finish() }
            })
        } else {
            startActivity(main)
            finish()
        }
    }

    companion object {
        fun intent(context: Context, snoozeCount: Int) = Intent(context, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            .putExtra(AlarmService.EXTRA_SNOOZE_COUNT, snoozeCount)
    }
}

@androidx.compose.runtime.Composable
private fun AlarmContent(snoozesLeft: Int, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(1_000) } }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Guten Morgen", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                now.format(DateTimeFormatter.ofPattern("HH:mm")),
                fontSize = 88.sp, fontFamily = FontFamily.Serif,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(LocalDate.now().fullDe(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(64.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text("Ausschalten", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(16.dp))
            if (snoozesLeft > 0) {
                OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Schlummern · ${AlarmService.SNOOZE_MINUTES} Min. (noch ${snoozesLeft}×)")
                }
            } else {
                Text("Schlummern ist aufgebraucht – auf geht's!", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
