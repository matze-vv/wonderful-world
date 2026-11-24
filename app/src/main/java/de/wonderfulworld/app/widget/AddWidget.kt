package de.wonderfulworld.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.wonderfulworld.app.MainActivity
import de.wonderfulworld.app.R
import de.wonderfulworld.app.ui.screens.NewChooser
import de.wonderfulworld.app.ui.theme.WonderfulWorldTheme

/** Homescreen-Widget: ein „+“, das wie der Knopf im Dashboard die Auswahl Mission / Erlebnis öffnet. */
class AddWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, ChooserActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val views = RemoteViews(context.packageName, R.layout.widget_add).apply {
            setOnClickPendingIntent(R.id.widget_root, open)
            setOnClickPendingIntent(R.id.widget_button, open)
        }
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}

/** Durchsichtige Activity mit der Auswahl – erscheint direkt über dem Homescreen. */
class ChooserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WonderfulWorldTheme {
                NewChooser(
                    onMission = { open(MainActivity.DEST_NEW_MISSION) },
                    onEntry = { open(MainActivity.DEST_NEW_ENTRY) },
                    onDismiss = { finish() },
                )
            }
        }
    }

    private fun open(dest: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_DEST, dest)
        )
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}
