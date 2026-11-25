package co.ast.campo.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import co.ast.campo.data.WatermarkSettings
import java.time.ZoneId
import kotlin.math.pow
import kotlin.math.sin

/** Foto de ejemplo dibujada por código (granja solar) para la vista previa de la marca en Ajustes. */
object SamplePhoto {
    const val WIDTH = 1600
    const val HEIGHT = 1200

    val sampleRoute = listOf("Granja Solar X", "Pruebas eléctricas", "CT 5")

    fun render(style: WatermarkSettings, nowMillis: Long = System.currentTimeMillis()): Bitmap {
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        drawScene(c, WIDTH.toFloat(), HEIGHT.toFloat())
        val content = PhotoProcessor.buildContent(style, sampleRoute, nowMillis, ZoneId.systemDefault(), 10.96854, -74.78132, 31.0, style.company)
        WatermarkRenderer.draw(c, WIDTH, HEIGHT, 1, content, style)
        return bmp
    }

    private fun drawScene(c: Canvas, w: Float, h: Float) {
        val horizon = h * 0.46f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, horizon, 0xFF4682C8.toInt(), 0xFFBEDFFA.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, horizon, p)
        p.shader = LinearGradient(0f, horizon, 0f, h, 0xFF96825F.toInt(), 0xFF5A4B35.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, horizon, w, h, p)
        p.shader = null

        val hills = Path().apply {
            moveTo(0f, horizon)
            var x = 0f
            while (x <= w + 40f) {
                lineTo(x, horizon - 18f - (14f * sin(x / 140f) + 8f * sin(x / 57f)))
                x += 40f
            }
            lineTo(w, horizon)
            close()
        }
        p.color = 0xFF607668.toInt()
        c.drawPath(hills, p)

        val rows = 9
        val vx = w / 2f
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF78A0BE.toInt(); style = Paint.Style.STROKE }
        for (r in 0 until rows) {
            val t0 = (r / rows.toFloat()).pow(1.7f)
            val t1 = ((r + 0.72f) / rows).pow(1.7f)
            val y0 = horizon + 8f + t0 * (h - horizon) * 0.78f
            val y1 = horizon + 8f + t1 * (h - horizon) * 0.78f
            val s0 = (0.18f + 0.95f * t0) * w
            val s1 = (0.18f + 0.95f * t1) * w
            val quad = Path().apply {
                moveTo(vx - s0 / 2, y0); lineTo(vx + s0 / 2, y0); lineTo(vx + s1 / 2, y1); lineTo(vx - s1 / 2, y1); close()
            }
            p.color = android.graphics.Color.rgb(22 + r * 2, 44 + r * 3, 86 + r * 4)
            c.drawPath(quad, p)
            line.strokeWidth = 1f + 2f * t0
            for (i in 0..14) {
                val f = i / 14f
                c.drawLine(vx - s0 / 2 + s0 * f, y0, vx - s1 / 2 + s1 * f, y1, line)
            }
        }
        // Gabinete en primer plano
        val bx = w * 0.70f
        val by = h * 0.60f
        p.color = 0xFF969BA0.toInt()
        c.drawRect(bx, by, bx + w * 0.20f, by + h * 0.30f, p)
        p.color = 0xFF6E727A.toInt()
        c.drawRect(bx + w * 0.10f - 3f, by + 12f, bx + w * 0.10f + 3f, by + h * 0.30f - 12f, p)
        p.color = 0xFFF0BE28.toInt()
        c.drawRect(bx + 14f, by + h * 0.15f, bx + 34f, by + h * 0.15f + 14f, p)
    }
}
