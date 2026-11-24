package de.wonderfulworld.app.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import de.wonderfulworld.app.ui.components.MasonryState
import de.wonderfulworld.app.ui.components.mergeOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderTest {
    @Test fun mergeKeepsSavedOrderAndInsertsNewTiles() {
        // gespeichert: b vor a; neu: "neu" an Standardposition 1; "alt" existiert nicht mehr
        assertEquals(listOf("b", "neu", "a", "c"), mergeOrder(listOf("b", "alt", "a"), listOf("a", "neu", "b", "c")))
        assertEquals(listOf("a", "b"), mergeOrder(emptyList(), listOf("a", "b")))
    }

    @Test fun draggingOverATileMovesItThere() {
        val s = MasonryState()
        // zwei Spalten: a | b, darunter c | d (je 100×100, Abstand 10)
        s.slots["a"] = Rect(0f, 0f, 100f, 100f)
        s.slots["b"] = Rect(110f, 0f, 210f, 100f)
        s.slots["c"] = Rect(0f, 110f, 100f, 210f)
        s.slots["d"] = Rect(110f, 110f, 210f, 210f)
        s.start("a", listOf("a", "b", "c", "d"), Offset(50f, 50f))
        s.dragBy(Offset(110f, 110f))          // Mitte von a liegt jetzt über d
        assertEquals(listOf("b", "c", "d", "a"), s.order)
        s.dragBy(Offset(1f, 0f))              // gleiche Zielkachel → keine erneute Umsortierung
        assertEquals(listOf("b", "c", "d", "a"), s.order)
        assertEquals(listOf("b", "c", "d", "a"), s.end())
    }
}
