package co.ast.campo.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationPolicyTest {
    private fun fix(accuracy: Float, elapsedSec: Long) = LocationFix(10.0, -74.0, accuracy, elapsedSec * 1000)

    @Test fun picksMostAccurateWithinWindow() {
        val launch = 1_000_000L
        val a = fix(12f, 995)
        val b = fix(5f, 1_001)
        assertEquals(b, LocationPolicy.pick(listOf(a, b), launch, 1_010_000L))
    }

    @Test fun rejectsFixOlderThanSixtySecondsBeforeLaunch() {
        // Escenario del revisor: ubicación de hace 8 min (otra subestación) con muy buena precisión
        val launch = 1_000_000L
        val stale = fix(4f, 520)
        assertNull(LocationPolicy.pick(listOf(stale), launch, 1_010_000L))
        val ok = fix(30f, 990)
        assertEquals(ok, LocationPolicy.pick(listOf(stale, ok), launch, 1_010_000L))
    }

    @Test fun rejectsFutureFixes() {
        assertNull(LocationPolicy.pick(listOf(fix(5f, 2_000)), 1_000_000L, 1_010_000L))
    }

    @Test fun emptyReturnsNull() {
        assertNull(LocationPolicy.pick(emptyList(), 1L, 2L))
    }
}
