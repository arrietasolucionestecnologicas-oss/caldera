package co.ast.campo.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object WatermarkFormatter {
    const val PATH_SEPARATOR = " › "
    private const val ELLIPSIS = "…"
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    fun dateTime(epochMillis: Long, zone: ZoneId): String =
        dateFormat.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    fun coordinates(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.6f, %.6f", lat, lon)

    fun temperature(celsius: Double?): String =
        if (celsius == null) "--" else "${celsius.roundToInt()} °C"

    /** Línea 3. Sin coordenadas se indica "Sin ubicación" para que quede claro en el informe. */
    fun dataLine(lat: Double?, lon: Double?, celsius: Double?, showCoords: Boolean, showTemp: Boolean): String? {
        val parts = buildList {
            if (showCoords) add(if (lat != null && lon != null) coordinates(lat, lon) else "Sin ubicación")
            if (showTemp) add(temperature(celsius))
        }
        return if (parts.isEmpty()) null else parts.joinToString("   ")
    }

    /**
     * Ruta de destino en una línea. Si no cabe según [fits], se abrevia con "…" conservando el
     * proyecto (primer nivel) y la mayor cantidad posible de los últimos niveles.
     */
    fun route(segments: List<String>, fits: (String) -> Boolean): String {
        if (segments.isEmpty()) return ""
        val full = segments.joinToString(PATH_SEPARATOR)
        if (fits(full)) return full
        val project = segments.first()
        if (segments.size == 1) return truncateToFit(project, "", fits)
        // Proyecto › … › últimos k niveles
        for (k in segments.size - 2 downTo 1) {
            val tail = segments.takeLast(k).joinToString(PATH_SEPARATOR)
            val candidate = "$project$PATH_SEPARATOR$ELLIPSIS$PATH_SEPARATOR$tail"
            if (fits(candidate)) return candidate
        }
        // Ni siquiera cabe el último nivel completo: se recorta ese nivel.
        val last = segments.last()
        val middle = if (segments.size > 2) "$ELLIPSIS$PATH_SEPARATOR" else ""
        val head = "$project$PATH_SEPARATOR$middle"
        return truncateToFit(last, head, fits)
    }

    private fun truncateToFit(text: String, head: String, fits: (String) -> Boolean): String {
        for (m in text.length downTo 1) {
            val cut = if (m == text.length) text else text.substring(0, m).trimEnd() + ELLIPSIS
            val candidate = head + cut
            if (fits(candidate)) return candidate
        }
        return head + text.take(1) + ELLIPSIS
    }
}
