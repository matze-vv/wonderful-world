package de.wonderfulworld.app

import android.app.Application
import de.wonderfulworld.app.alarm.Notifications
import de.wonderfulworld.app.alarm.Scheduler
import de.wonderfulworld.app.data.AppDatabase
import de.wonderfulworld.app.data.BackupManager
import de.wonderfulworld.app.data.PhotoStore
import de.wonderfulworld.app.data.Repository
import de.wonderfulworld.app.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WonderfulWorldApp : Application() {
    lateinit var repo: Repository
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var backup: BackupManager
        private set
    lateinit var scheduler: Scheduler
        private set

    /** Für Arbeit, die länger als ein Bildschirm leben soll (z. B. Neuplanung der Wecker). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.create(this)
        val photos = PhotoStore(this)
        repo = Repository(db, photos)
        settings = SettingsStore(this)
        backup = BackupManager(this, db, photos, settings)
        scheduler = Scheduler(this, repo, settings)
        Notifications.createChannels(this)
        appScope.launch {
            repo.ensureDefaults()
            scheduler.rescheduleAll()
        }
    }
}

val android.content.Context.app: WonderfulWorldApp get() = applicationContext as WonderfulWorldApp
