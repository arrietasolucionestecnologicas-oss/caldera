package co.ast.campo.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Fechas en formato EXIF ("yyyy:MM:dd HH:mm:ss" + desfase "+HH:mm" opcional). */
object ExifDates {
    private val format = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    /** Epoch en ms, o null si el texto falta o no es una fecha válida (p. ej. "0000:00:00 00:00:00"). */
    fun parse(dateTime: String?, offset: String?, zone: ZoneId): Long? {
        val text = dateTime?.trim().orEmpty()
        if (text.isEmpty()) return null
        val local = try {
            LocalDateTime.parse(text, format)
        } catch (_: DateTimeParseException) {
            return null
        }
        val fixed = offset?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { ZoneOffset.of(it) }.getOrNull() }
        return local.atZone(fixed ?: zone).toInstant().toEpochMilli()
    }

    /** Fecha EXIF en hora local de [zone]. */
    fun format(epochMillis: Long, zone: ZoneId): String =
        format.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    /** Desfase EXIF ("-05:00", "+00:00") vigente en [epochMillis]. */
    fun offset(epochMillis: Long, zone: ZoneId): String {
        val id = zone.rules.getOffset(Instant.ofEpochMilli(epochMillis)).id
        return if (id == "Z") "+00:00" else id
    }
}
