package co.ast.campo.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Construcción de rutas físicas (MediaStore y almacenamiento privado) y nombres de archivo. */
object MediaPaths {
    const val GALLERY_ROOT = "Pictures/AST Campo"
    const val MAX_FILE_NAME = 120
    private const val ELLIPSIS = "..."
    private val stampFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    /** RELATIVE_PATH de MediaStore: termina siempre en "/". [segments] = Proyecto, Sub, Sub... */
    fun relativePath(segments: List<String>): String =
        if (segments.isEmpty()) "$GALLERY_ROOT/" else "$GALLERY_ROOT/${segments.joinToString("/")}/"

    /** Ruta relativa dentro del almacenamiento privado de la app (copias originales). */
    fun originalRelativePath(segments: List<String>): String = segments.joinToString("/")

    /**
     * `<Proyecto>_<Sub>_..._yyyyMMdd_HHmmss_NNN.jpg`, de máximo [maxLength] caracteres.
     * Si la parte de la ruta no cabe, se recorta por el medio con "...".
     */
    fun fileName(
        segments: List<String>,
        takenAtMillis: Long,
        zone: ZoneId,
        sequence: Int,
        maxLength: Int = MAX_FILE_NAME,
    ): String {
        val stamp = stampFormat.format(Instant.ofEpochMilli(takenAtMillis).atZone(zone))
        val suffix = "_${stamp}_${sequence.coerceAtLeast(0).toString().padStart(3, '0')}.jpg"
        val prefix = segments.joinToString("_")
        val available = (maxLength - suffix.length).coerceAtLeast(ELLIPSIS.length + 2)
        val shortPrefix = if (prefix.length <= available) prefix else middleCut(prefix, available)
        return (shortPrefix.ifEmpty { "Foto" }) + suffix
    }

    /** Recorta por el medio dejando "..." y conservando inicio y final. */
    fun middleCut(text: String, maxLength: Int): String {
        if (text.length <= maxLength) return text
        val keep = (maxLength - ELLIPSIS.length).coerceAtLeast(2)
        val head = (keep + 1) / 2
        val tail = keep - head
        return text.substring(0, head).trimEnd() + ELLIPSIS + text.substring(text.length - tail).trimStart()
    }
}
