package co.ast.campo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrientationMatrixTest {
    private fun map(m: FloatArray, x: Float, y: Float) = Pair(m[0] * x + m[1] * y + m[2], m[3] * x + m[4] * y + m[5])

    @Test fun swapsAxesOnlyForFiveToEight() {
        assertFalse(OrientationMatrix.swapsAxes(1))
        assertFalse(OrientationMatrix.swapsAxes(3))
        assertTrue(OrientationMatrix.swapsAxes(6))
        assertTrue(OrientationMatrix.swapsAxes(8))
    }

    @Test fun normalIsIdentity() {
        assertEquals(Pair(10f, 20f), map(OrientationMatrix.displayToRaw(1, 4000f, 3000f), 10f, 20f))
    }

    @Test fun rotate180BottomLeftGoesToTopRight() {
        val m = OrientationMatrix.displayToRaw(3, 4000f, 3000f)
        assertEquals(Pair(4000f, 0f), map(m, 0f, 3000f))
    }

    /**
     * Retrato típico de Samsung: píxeles crudos 4000x3000 (horizontal) con EXIF 6 → imagen vista 3000x4000.
     * La esquina inferior izquierda de lo que ve el usuario es la esquina inferior derecha del bitmap crudo.
     */
    @Test fun orientation6BottomLeftOfDisplayIsBottomRightOfRaw() {
        val m = OrientationMatrix.displayToRaw(6, 3000f, 4000f)
        assertEquals(Pair(4000f, 3000f), map(m, 0f, 4000f))
        // Esquina superior izquierda vista = esquina inferior izquierda cruda
        assertEquals(Pair(0f, 3000f), map(m, 0f, 0f))
        // Esquina superior derecha vista = esquina superior izquierda cruda
        assertEquals(Pair(0f, 0f), map(m, 3000f, 0f))
    }

    @Test fun orientation8BottomLeftOfDisplayIsTopLeftOfRaw() {
        val m = OrientationMatrix.displayToRaw(8, 3000f, 4000f)
        assertEquals(Pair(0f, 0f), map(m, 0f, 4000f))
        assertEquals(Pair(4000f, 0f), map(m, 0f, 0f))
    }

    @Test fun everyOrientationMapsDisplayCornersInsideRawBounds() {
        for (o in 1..8) {
            val swap = OrientationMatrix.swapsAxes(o)
            val rawW = 4000f; val rawH = 3000f
            val w = if (swap) rawH else rawW
            val h = if (swap) rawW else rawH
            val m = OrientationMatrix.displayToRaw(o, w, h)
            for ((x, y) in listOf(0f to 0f, w to 0f, 0f to h, w to h)) {
                val (rx, ry) = map(m, x, y)
                assertTrue("o=$o rx=$rx", rx == 0f || rx == rawW)
                assertTrue("o=$o ry=$ry", ry == 0f || ry == rawH)
            }
        }
    }
}
