package de.wonderfulworld.app.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class BackupData(
    val version: Int = 2,
    val exportedAt: Long,
    val rituals: List<Ritual>,
    val weekPlans: List<WeekPlan>,
    val schedules: List<RitualSchedule>,
    val checks: List<RitualCheck>,
    val missions: List<Mission>,
    val monthPlans: List<MonthPlan>,
    val journal: List<JournalEntry>,
    val alarms: List<AlarmSetting>,
    val settings: AppSettings,
    val tags: List<Tag> = emptyList(),
)

/** Backup als ZIP: data.json + photos/… */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val photos: PhotoStore,
    private val settings: SettingsStore,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(target: Uri) {
        val dao = db.backupDao()
        val data = BackupData(
            exportedAt = System.currentTimeMillis(),
            rituals = dao.rituals(), weekPlans = dao.plans(), schedules = dao.schedules(),
            checks = dao.checks(), missions = dao.missions(), monthPlans = dao.monthPlans(),
            journal = dao.journal(), alarms = dao.alarms(), settings = settings.settings.value,
            tags = dao.tags(),
        )
        val out = context.contentResolver.openOutputStream(target, "w") ?: error("Datei kann nicht geschrieben werden")
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(json.encodeToString(BackupData.serializer(), data).toByteArray())
            zip.closeEntry()
            data.journal.mapNotNull { it.photo }.distinct().forEach { name ->
                val f = photos.file(name)
                if (f.exists()) {
                    zip.putNextEntry(ZipEntry("photos/$name"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Ersetzt ALLE Daten durch den Inhalt des Backups. */
    suspend fun import(source: Uri) {
        val staging = File(context.cacheDir, "import").apply { deleteRecursively(); mkdirs() }
        var data: BackupData? = null
        val input = context.contentResolver.openInputStream(source) ?: error("Datei kann nicht gelesen werden")
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.name == "data.json" ->
                        data = json.decodeFromString(BackupData.serializer(), zip.readBytes().decodeToString())
                    entry.name.startsWith("photos/") && !entry.isDirectory -> {
                        // Nur den Dateinamen verwenden (keine Pfade aus dem ZIP übernehmen).
                        val name = File(entry.name).name
                        if (name.isNotBlank() && !name.startsWith(".")) {
                            File(staging, name).outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
            }
        }
        val d = data ?: error("Keine data.json im Backup gefunden")

        val dao = db.backupDao()
        db.withTransaction {
            db.clearAllTables()
            dao.insertTags(d.tags)
            dao.insertRituals(d.rituals)
            dao.insertPlans(d.weekPlans)
            dao.insertSchedules(d.schedules)
            dao.insertChecks(d.checks)
            dao.insertMissions(d.missions)
            dao.insertMonthPlans(d.monthPlans)
            dao.insertJournal(d.journal)
            dao.insertAlarms(d.alarms)
        }
        photos.dir.listFiles()?.forEach { it.delete() }
        staging.listFiles()?.forEach { it.copyTo(photos.file(it.name), overwrite = true) }
        staging.deleteRecursively()
        settings.update(d.settings)
    }
}
