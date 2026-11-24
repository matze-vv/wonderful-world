package de.wonderfulworld.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import de.wonderfulworld.app.alarm.AlarmService
import de.wonderfulworld.app.ui.AppNavigation
import de.wonderfulworld.app.ui.theme.WonderfulWorldTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Ziel, zu dem navigiert werden soll (aus Benachrichtigungen oder nach dem Wecker). */
    private val pendingDest = MutableStateFlow<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            WonderfulWorldTheme {
                AppNavigation(pendingDest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.getBooleanExtra(EXTRA_DISMISS_ALARM, false)) {
            AlarmService.command(this, AlarmService.ACTION_DISMISS)
            pendingDest.value = "dashboard"
        }
        intent.getStringExtra(EXTRA_DEST)?.let { pendingDest.value = it }
        intent.removeExtra(EXTRA_DISMISS_ALARM)
        intent.removeExtra(EXTRA_DEST)
    }

    companion object {
        const val EXTRA_DEST = "dest"
        const val EXTRA_DISMISS_ALARM = "dismissAlarm"
    }
}
