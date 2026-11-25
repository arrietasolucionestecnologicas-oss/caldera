package co.ast.campo.media

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import co.ast.campo.core.OrientationMatrix
import co.ast.campo.core.WatermarkFormatter
import co.ast.campo.data.Corner
import co.ast.campo.data.WatermarkSettings
import co.ast.campo.data.WatermarkSize
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Lo que se escribe en la marca. Lo desactivado en Ajustes llega como null / lista vacía. */
data class WatermarkContent(
    val routeSegments: List<String>,
    val dateTime: String?,
    val data: String?,
    val company: String?,
    val place: String? = null,
)

/**
 * Dibuja el bloque de la marca de agua: fondo azul marino translúcido con esquinas apenas redondeadas,
 * barra fina naranja a la izquierda, ruta en semibold y datos en gris claro.
 *
 * Se dibuja sobre los píxeles crudos del bitmap y se transforma con [OrientationMatrix] para que
 * quede en la esquina de la imagen ya orientada (así no hace falta rotar fotos de 50 MP en memoria).
 */
object WatermarkRenderer {
    private const val NAVY = 0xFF0A1F44.toInt()
    private const val ORANGE = 0xFFF58220.toInt()
    private const val GRAY = 0xFFCDD6E4.toInt()

    private fun typeface(weight: Int): Typeface =
        Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), weight, false)

    private fun ellipsize(text: String, paint: Paint, maxW: Float): String {
        var end = text.length
        while (end > 1 && paint.measureText(text, 0, end) + paint.measureText("…") > maxW) end--
        return text.substring(0, end).trimEnd() + "…"
    }

    fun draw(canvas: Canvas, rawW: Int, rawH: Int, orientation: Int, content: WatermarkContent, style: WatermarkSettings) {
        val swap = OrientationMatrix.swapsAxes(orientation)
        val w = (if (swap) rawH else rawW).toFloat()
        val h = (if (swap) rawW else rawH).toFloat()
        val short = min(w, h)
        val t = short * when (style.size) {
            WatermarkSize.SMALL -> 0.022f
            WatermarkSize.MEDIUM -> 0.030f
            WatermarkSize.LARGE -> 0.040f
        }
        val margin = short * 0.02f
        // Con fondo: panel con barra naranja. Sin fondo: solo texto, pegado al margen.
        val boxed = style.background
        val padX = if (boxed) t * 0.75f else 0f
        val padY = if (boxed) t * 0.6f else 0f
        val barW = if (boxed) t * 0.20f else 0f
        val barInset = if (boxed) padX * 0.55f else 0f
        val barGap = if (boxed) t * 0.65f else 0f
        val lineGap = t * 0.14f
        val radius = t * 0.35f

        val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = t; color = Color.WHITE; typeface = typeface(600) }
        // Sin panel no hay contraste de fondo: todo el texto va en blanco puro y un poco más grueso.
        val dataPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = t * 0.92f
            color = if (style.background) GRAY else Color.WHITE
            typeface = typeface(if (style.background) 400 else 500)
        }
        val companyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = t * 0.80f; color = ORANGE; typeface = typeface(600) }

        val maxTextW = w - 2 * margin - (barInset + barW + barGap + padX)
        data class Row(val text: String, val paint: Paint)
        val rows = ArrayList<Row>(3)
        if (content.routeSegments.isNotEmpty()) {
            val route = WatermarkFormatter.route(content.routeSegments) { routePaint.measureText(it) <= maxTextW }
            rows.add(Row(route, routePaint))
        }
        content.dateTime?.let { rows.add(Row(it, dataPaint)) }
        content.data?.let { rows.add(Row(it, dataPaint)) }
        content.place?.let { rows.add(Row(it, dataPaint)) }
        val company = content.company?.takeIf { it.isNotBlank() }
        var companyRow = -1
        if (company != null) {
            if (rows.size >= 2) companyRow = 1 else { rows.add(Row(company, companyPaint)); companyRow = rows.lastIndex }
        }
        if (rows.isEmpty()) return

        // Ninguna línea puede salirse de la foto: las demasiado largas se cortan con "…".
        for (i in rows.indices) {
            val r = rows[i]
            if (r.paint.measureText(r.text) > maxTextW) rows[i] = Row(ellipsize(r.text, r.paint, maxTextW), r.paint)
        }
        val heights = rows.map { r -> r.paint.fontMetrics.let { it.descent - it.ascent } }
        var textW = rows.maxOf { it.paint.measureText(it.text) }
        val companyOnRow = companyRow in 0 until rows.size && rows[companyRow].paint !== companyPaint
        val companyW = if (companyOnRow) companyPaint.measureText(company!!) else 0f
        if (companyOnRow) textW = max(textW, rows[companyRow].paint.measureText(rows[companyRow].text) + t * 1.2f + companyW)
        val blockW = barInset + barW + barGap + textW + padX
        val blockH = padY * 2 + heights.sum() + lineGap * (rows.size - 1)

        val left = when (style.corner) {
            Corner.BOTTOM_LEFT, Corner.TOP_LEFT -> margin
            else -> w - margin - blockW
        }
        val top = when (style.corner) {
            Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT -> h - margin - blockH
            else -> margin
        }

        val m = OrientationMatrix.displayToRaw(orientation, w, h)
        val matrix = Matrix().apply { setValues(floatArrayOf(m[0], m[1], m[2], m[3], m[4], m[5], 0f, 0f, 1f)) }
        canvas.save()
        canvas.concat(matrix)

        val alpha = (style.opacityPct.coerceIn(30, 90) / 100f * 255).roundToInt()
        if (boxed) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY; this.alpha = alpha }
            canvas.drawRoundRect(RectF(left, top, left + blockW, top + blockH), radius, radius, bg)
            val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ORANGE }
            canvas.drawRoundRect(
                RectF(left + barInset, top + padY, left + barInset + barW, top + blockH - padY), barW / 2, barW / 2, bar,
            )
        } else {
            // Sombra suave para que las letras se lean sobre fondos claros y oscuros, sin recuadro.
            listOf(routePaint, dataPaint, companyPaint).forEach {
                it.setShadowLayer(t * 0.20f, 0f, t * 0.06f, 0xF2000000.toInt())
            }
        }
        val textX = left + barInset + barW + barGap
        var y = top + padY
        // Sin panel el texto se dibuja dos veces: la sombra se vuelve más densa y se lee sobre fondos claros.
        val passes = if (boxed) 1 else 2
        rows.forEachIndexed { i, row ->
            repeat(passes) {
                canvas.drawText(row.text, textX, y - row.paint.fontMetrics.ascent, row.paint)
                if (companyOnRow && i == companyRow) {
                    canvas.drawText(
                        company!!, left + blockW - padX - companyW,
                        y - row.paint.fontMetrics.ascent, companyPaint,
                    )
                }
            }
            y += heights[i] + lineGap
        }
        canvas.restore()
    }
}
