package de.wonderfulworld.app.domain

import de.wonderfulworld.app.data.Mission
import de.wonderfulworld.app.data.Tag
import de.wonderfulworld.app.data.coverScale
import de.wonderfulworld.app.ui.screens.groupByTag
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class DashboardLogicTest {
    @Test fun coverScaleFillsFrame() {
        assertEquals(1f, coverScale(400f, 300f, 0f), 1e-4f)
        // Quadrat um 45° gedreht muss um √2 vergrößert werden
        assertEquals(1.41421f, coverScale(100f, 100f, 45f), 1e-3f)
        // Richtung egal
        assertEquals(coverScale(400f, 300f, 10f), coverScale(400f, 300f, -10f), 1e-5f)
    }

    private fun m(id: Long, tag: Long?) = Mission(id, YearMonth.of(2026, 11), "M$id", false, 1, createdAt = 0, tagId = tag)

    @Test fun untaggedMissionsGetOwnTiles() {
        val tags = listOf(Tag(1, "Draußen", 0), Tag(2, "Kreativ", 0), Tag(3, "Leer", 0))
        val (groups, untagged) = groupByTag(listOf(m(1, 1), m(2, null), m(3, 2), m(4, 1), m(5, 99)), tags)
        assertEquals(listOf(1L, 2L), groups.map { it.tag.id })          // Tag ohne Missionen bekommt keine Kachel
        assertEquals(listOf(1L, 4L), groups[0].missions.map { it.id })
        assertEquals(listOf(2L, 5L), untagged.map { it.id })            // auch Missionen mit gelöschtem Tag
    }

    @Test fun weeklyMissionsShowInLaterMonths() {
        val nov = YearMonth.of(2026, 11)
        val weekly = Mission(1, nov, "Sport", false, 1, createdAt = 0, weekly = true)
        val once = Mission(2, nov, "Buch", false, 1, createdAt = 0)
        assert(weekly.showsIn(nov) && weekly.showsIn(nov.plusMonths(3)))
        assert(!weekly.showsIn(nov.minusMonths(1)))
        assert(once.showsIn(nov) && !once.showsIn(nov.plusMonths(1)))
        assert(!weekly.copy(resolution = Mission.RESOLUTION_ARCHIVED).showsIn(nov))
    }
}
