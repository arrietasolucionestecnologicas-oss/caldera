package co.ast.campo.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import co.ast.campo.data.Corner
import co.ast.campo.data.WatermarkSettings
import co.ast.campo.data.WatermarkSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.min

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WatermarkRendererTest {
    private val content = WatermarkContent(
        listOf("Granja Solar X", "Pruebas eléctricas", "CT 5"), "25/11/2026 14:05", "10.968540, -74.781320   31 °C", "A.S.T.",
    )
    private val style = WatermarkSettings(company = "A.S.T.")

    private fun render(w: Int, h: Int, orientation: Int, s: WatermarkSettings = style, c: WatermarkContent = content): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(b)
        canvas.drawColor(Color.WHITE)
        WatermarkRenderer.draw(canvas, w, h, orientation, c, s)
        return b
    }

    /** Transformación raw -> imagen vista, según la definición canónica de EXIF (la misma que usan Glide y Coil). */
    private fun toDisplay(raw: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            2 -> m.postScale(-1f, 1f)
            3 -> m.postRotate(180f)
            4 -> { m.postRotate(180f); m.postScale(-1f, 1f) }
            5 -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            6 -> m.postRotate(90f)
            7 -> { m.postRotate(-90f); m.postScale(-1f, 1f) }
            8 -> m.postRotate(-90f)
        }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    }

    private data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /** Rectángulo que contiene todo lo que no es blanco. */
    private fun inkBox(b: Bitmap): Box? {
        var l = Int.MAX_VALUE; var t = Int.MAX_VALUE; var r = -1; var bt = -1
        val row = IntArray(b.width)
        for (y in 0 until b.height) {
            b.getPixels(row, 0, b.width, 0, y, b.width, 1)
            for (x in 0 until b.width) {
                if (row[x] != Color.WHITE) {
                    if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > bt) bt = y
                }
            }
        }
        return if (r < 0) null else Box(l, t, r, bt)
    }

    @Test fun landscapeBlockSitsAtBottomLeftWithTwoPercentMargin() {
        val b = render(2000, 1500, 1)
        val box = inkBox(b)!!
        val margin = 1500 * 0.02f
        assertEquals(margin, box.left.toFloat(), 2f)
        assertEquals(1500 - margin, box.bottom.toFloat(), 2f)
        // Pequeño: el bloque debe ocupar poco de la foto
        assertTrue("ancho ${box.right - box.left}", box.right - box.left < 2000 * 0.6)
        assertTrue("alto ${box.bottom - box.top}", box.bottom - box.top < 1500 * 0.2)
    }

    @Test fun everyExifOrientationEndsAtBottomLeftOfTheOrientedImage() {
        for (o in 1..8) {
            val swap = o in 5..8
            val rawW = 2000; val rawH = 1500
            val dispW = if (swap) rawH else rawW
            val dispH = if (swap) rawW else rawH
            val viaOrientation = toDisplay(render(rawW, rawH, o), o)
            val direct = render(dispW, dispH, 1)
            assertEquals("o=$o ancho", dispW, viaOrientation.width)
            assertEquals("o=$o alto", dispH, viaOrientation.height)
            val a = inkBox(viaOrientation)!!
            val d = inkBox(direct)!!
            assertEquals("o=$o left", d.left.toFloat(), a.left.toFloat(), 2f)
            assertEquals("o=$o top", d.top.toFloat(), a.top.toFloat(), 2f)
            assertEquals("o=$o right", d.right.toFloat(), a.right.toFloat(), 2f)
            assertEquals("o=$o bottom", d.bottom.toFloat(), a.bottom.toFloat(), 2f)
            // El texto no debe salir espejado ni girado: comparación de píxeles en el bloque.
            var diff = 0L; var n = 0L
            for (y in d.top..d.bottom step 3) for (x in d.left..d.right step 3) {
                val p = viaOrientation.getPixel(x, y); val q = direct.getPixel(x, y)
                diff += abs(Color.red(p) - Color.red(q)) + abs(Color.green(p) - Color.green(q)) + abs(Color.blue(p) - Color.blue(q)); n++
            }
            assertTrue("o=$o diferencia media ${diff / (3.0 * n)}", diff / (3.0 * n) < 6.0)
        }
    }

    @Test fun otherCornersAndSizes() {
        val w = 2000; val h = 1500; val margin = 1500 * 0.02f
        val br = inkBox(render(w, h, 1, style.copy(corner = Corner.BOTTOM_RIGHT)))!!
        assertEquals(w - margin, br.right.toFloat(), 2f)
        assertEquals(h - margin, br.bottom.toFloat(), 2f)
        val tl = inkBox(render(w, h, 1, style.copy(corner = Corner.TOP_LEFT)))!!
        assertEquals(margin, tl.left.toFloat(), 2f)
        assertEquals(margin, tl.top.toFloat(), 2f)
        val tr = inkBox(render(w, h, 1, style.copy(corner = Corner.TOP_RIGHT)))!!
        assertEquals(w - margin, tr.right.toFloat(), 2f)
        val small = inkBox(render(w, h, 1, style.copy(size = WatermarkSize.SMALL)))!!
        val medium = inkBox(render(w, h, 1, style.copy(size = WatermarkSize.MEDIUM)))!!
        assertTrue(medium.right - medium.left > (small.right - small.left) * 1.25)
    }

    @Test fun longRouteIsAbbreviatedToFitNarrowPortraitPhoto() {
        val long = content.copy(routeSegments = listOf("Parque Solar Fotovoltaico Los Cocos", "Subestación Elevadora 2", "Banco de condensadores", "Celda 14 lado norte"))
        val b = render(1125, 1500, 1, c = long)
        val box = inkBox(b)!!
        assertTrue("derecha ${box.right}", box.right <= 1125 - 1125 * 0.02f + 2)
        assertTrue(box.left >= 1125 * 0.02f - 2)
    }

    @Test fun opacityChangesBackgroundStrength() {
        val light = render(2000, 1500, 1, style.copy(opacityPct = 30))
        val dark = render(2000, 1500, 1, style.copy(opacityPct = 90))
        val box = inkBox(dark)!!
        // Punto interior del bloque, a la derecha del texto (fondo puro, sin letras).
        val x = box.right - 4; val y = box.top + 6
        val lum = { c: Int -> Color.red(c) + Color.green(c) + Color.blue(c) }
        assertTrue(lum(dark.getPixel(x, y)) < lum(light.getPixel(x, y)) - 100)
    }

    @Test fun emptyContentDrawsNothing() {
        val b = render(800, 600, 1, c = WatermarkContent(emptyList(), null, null, null))
        assertEquals(null, inkBox(b))
    }

    @Test fun writesReviewImages() {
        val dir = File(System.getProperty("user.dir"), "build/test-artifacts").apply { mkdirs() }
        val sample = SamplePhoto.render(style)
        assertNotNull(sample)
        File(dir, "wm_sample_landscape.png").outputStream().use { sample.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Retrato típico de Samsung: píxeles 4:3 horizontales + EXIF 6, ya vistos orientados
        val raw = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        Canvas(raw).drawBitmap(sample, 0f, 0f, null)
        WatermarkRenderer.draw(Canvas(raw), 1600, 1200, 6, content, style)
        File(dir, "wm_sample_orientation6_displayed.png").outputStream().use { toDisplay(raw, 6).compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(min(sample.width, sample.height) > 0)
    }

    @Test fun placeLineIsDrawnAndLongPlacesNeverOverflow() {
        val withPlace = content.copy(place = "Estadio Metropolitano Roberto Meléndez, Barranquillita, Barranquilla, Atlántico, Colombia")
        val box = inkBox(render(1125, 1500, 1, c = withPlace))!!
        val plain = inkBox(render(1125, 1500, 1))!!
        assertTrue("con lugar es más alto", box.bottom - box.top > plain.bottom - plain.top)
        assertTrue("derecha ${box.right}", box.right <= 1125 - 1125 * 0.02f + 2)
    }
}
