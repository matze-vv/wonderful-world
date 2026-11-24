package de.wonderfulworld.app.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import de.wonderfulworld.app.ui.components.MasonryState
import de.wonderfulworld.app.ui.components.TileMeasure
import de.wonderfulworld.app.ui.components.computeSlots
import de.wonderfulworld.app.ui.components.mergeOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReorderTest {
    private val W = 1000
    private val G = 20

    // Fall aus der Simulation: zwei sehr hohe Kacheln (k2, k3), die kleine Kachel k1 wird über k3 gezogen.
    private val bug = mapOf(
        "k0" to TileMeasure(false, 150), "k1" to TileMeasure(false, 150),
        "k2" to TileMeasure(false, 900), "k3" to TileMeasure(false, 900), "k4" to TileMeasure(false, 300),
    )
    private val bugOrder = listOf("k0", "k1", "k2", "k3", "k4")

    @Test fun mergeKeepsSavedOrderAndInsertsNewTiles() {
        // gespeichert: b vor a; neu: "neu" an Standardposition 1; "alt" existiert nicht mehr
        assertEquals(listOf("b", "neu", "a", "c"), mergeOrder(listOf("b", "alt", "a"), listOf("a", "neu", "b", "c")))
        assertEquals(listOf("a", "b"), mergeOrder(emptyList(), listOf("a", "b")))
    }

    @Test fun slotsDoNotOverlap() {
        for (order in listOf(bugOrder, listOf("k2", "k3", "k0", "k1", "k4"), listOf("k0", "k2", "k3", "k1", "k4"))) {
            val slots = computeSlots(order, bug, W, G).values.toList()
            for (i in slots.indices) for (j in i + 1 until slots.size) {
                assertTrue("Überschneidung ${slots[i]} / ${slots[j]}", !slots[i].overlaps(slots[j]))
            }
        }
    }

    /** Langsam zur Mitte von k3 ziehen, dort etwas wackeln. */
    private fun bugPath(): List<Offset> {
        val s = computeSlots(bugOrder, bug, W, G)
        val d = s.getValue("k1"); val r = s.getValue("k3")
        val tx = r.center.x - d.width / 2 - d.left
        val ty = r.center.y - d.height / 2 - d.top
        return List(150) { Offset(tx / 150, ty / 150) } + List(30) { Offset(0f, 1f) } + List(30) { Offset(0f, -1f) }
    }

    /** Hin-und-her-Wechsel zählen: Reihenfolge gleich der vorletzten. */
    private fun flips(h: List<List<String>>) = (2 until h.size).count { h[it] == h[it - 2] }

    /** Alter Algorithmus (Mitte der gezogenen Kachel, Tausch mit der Kachel darunter) – zum Nachweis der Ursache. */
    private fun oldHistory(path: List<Offset>, drag: String): List<List<String>> {
        var order = bugOrder
        var slots = computeSlots(order, bug, W, G)
        var pos = slots.getValue(drag).topLeft
        var last: String? = null
        val hist = mutableListOf(order)
        for (d in path) {
            pos += d
            val r = slots.getValue(drag)
            val c = pos + Offset(r.width / 2, r.height / 2)
            val target = order.firstOrNull { it != drag && slots.getValue(it).contains(c) }
            if (target == null) { last = null; continue }
            if (target == last) continue
            last = target
            val from = order.indexOf(drag); val to = order.indexOf(target)
            order = order.toMutableList().apply { removeAt(from); add(to, drag) }
            slots = computeSlots(order, bug, W, G)
            hist += order
        }
        return hist
    }

    @Test fun oldAlgorithmFlipsBackAndForth() {
        // Beleg für die Ursache: die großen Kacheln wechseln bei jedem Tausch die Spalte
        assertTrue(flips(oldHistory(bugPath(), "k1")) > 50)
    }

    @Test fun newAlgorithmIsStable() {
        val s = MasonryState()
        s.setLayout(bugOrder, bug, W, G)
        val grab = Offset(50f, 50f)
        s.start("k1", bugOrder, grab)
        val hist = mutableListOf(s.order)
        for (d in bugPath()) {
            s.dragBy(d)
            if (s.order != hist.last()) hist += s.order
            s.setLayout(s.order, bug, W, G) // wie das echte Layout
        }
        assertEquals(0, flips(hist))
        assertTrue(hist.size >= 2) // die Kachel wurde tatsächlich verschoben
        // am Ende sitzt k1 in der Spalte, über der der Finger ist (rechts, bei k3)
        val finger = s.dragPos + grab
        val slot = computeSlots(s.order, bug, W, G).getValue("k1")
        assertTrue(finger.x >= slot.left && finger.x < slot.right)
    }

    @Test fun draggingOverATileMovesItThere() {
        val m = mapOf(
            "a" to TileMeasure(false, 100), "b" to TileMeasure(false, 100),
            "c" to TileMeasure(false, 100), "d" to TileMeasure(false, 100),
        )
        val s = MasonryState()
        s.setLayout(listOf("a", "b", "c", "d"), m, 210, 10)
        s.start("a", listOf("a", "b", "c", "d"), Offset(50f, 50f))
        s.dragBy(Offset(110f, 110f)) // Finger jetzt über d (unten rechts)
        assertEquals(listOf("b", "c", "d", "a"), s.order)
        assertEquals(listOf("b", "c", "d", "a"), s.end())
    }

    private fun Rect.overlaps(o: Rect) = left < o.right && o.left < right && top < o.bottom && o.top < bottom
}
