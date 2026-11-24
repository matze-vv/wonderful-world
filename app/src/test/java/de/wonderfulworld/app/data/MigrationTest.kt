package de.wonderfulworld.app.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Prüft, dass vorhandene Daten das Update von Version 1 auf 2 überstehen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate1To2_keepsData() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO rituals (id, name, createdOn, archivedOn, sortOrder) VALUES (1, 'Laufen', ${LocalDate.of(2026, 11, 1).toEpochDay()}, NULL, 1)")
            execSQL("INSERT INTO missions (id, month, title, counter, target, progress, resolution, createdAt) VALUES (1, '2026-11', 'Wandern', 1, 4, 2, NULL, 0)")
            execSQL("INSERT INTO journal_entries (id, timestamp, text, photo) VALUES (1, 0, 'Hallo', 'a.jpg')")
            close()
        }
        // Validiert zugleich das Schema gegen die Room-Entities von Version 2.
        helper.runMigrationsAndValidate(dbName, 2, true, AppDatabase.MIGRATION_1_2).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2).allowMainThreadQueries().build()
        runBlocking {
            val r = db.ritualDao().rituals().single()
            assertEquals("Laufen", r.name)
            assertEquals("", r.detail)
            assertEquals(0b1111111, r.defaultMask)
            assertNull(r.pausedOn)
            val m = db.missionDao().observeAll().first().single()
            assertEquals(2, m.progress)
            assertEquals("", m.detail)
            assertNull(m.tagId)
            assertEquals("Hallo", db.backupDao().journal().single().text)
            db.tagDao().insert(Tag(name = "Draußen", color = 0xFF4C8A4A.toInt()))
            assertEquals(1, db.tagDao().all().size)
        }
        db.close()
    }
}
