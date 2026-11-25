package co.ast.campo.core

/** Construcción de rutas físicas (MediaStore y almacenamiento privado) y nombres de archivo. */
object MediaPaths {
    const val GALLERY_ROOT = "Pictures/AST Campo"
    const val MAX_FILE_NAME = 120
    private const val ELLIPSIS = "..."

    /** RELATIVE_PATH de MediaStore: termina siempre en "/". [segments] = Proyecto, Sub, Sub... */
    fun relativePath(segments: List<String>): String =
        if (segments.isEmpty()) "$GALLERY_ROOT/" else "$GALLERY_ROOT/${segments.joinToString("/")}/"

    /** Ruta relativa dentro del almacenamiento privado de la app (copias originales). */
    fun originalRelativePath(segments: List<String>): String = segments.joinToString("/")

    /**
     * `<Proyecto>_<Sub>_..._NNN.jpg` (máximo [maxLength] caracteres): la ruta de carpetas más el número de foto
     * dentro de esa carpeta (001, 002...; 4 o más cifras pasando de 999). Si la ruta no cabe, se recorta por el
     * medio con "..." y el número siempre se conserva. La fecha y hora quedan en el EXIF y en la fecha del archivo.
     */
    fun fileName(segments: List<String>, sequence: Int, maxLength: Int = MAX_FILE_NAME): String {
        val suffix = "_${sequence.coerceAtLeast(1).toString().padStart(3, '0')}.jpg"
        val prefix = segments.joinToString("_")
        val available = (maxLength - suffix.length).coerceAtLeast(ELLIPSIS.length + 2)
        val shortPrefix = if (prefix.length <= available) prefix else middleCut(prefix, available)
        return (shortPrefix.ifEmpty { "Foto" }) + suffix
    }

    private val sequenceRegex = Regex("_(\\d{1,6})\\.jpg$")

    /** Número de foto al final del nombre (también en nombres viejos con fecha: `..._20261125_140509_007.jpg` da 7). */
    fun sequenceOf(fileName: String): Int? = sequenceRegex.find(fileName)?.groupValues?.get(1)?.toIntOrNull()

    /** Siguiente número de una carpeta: el mayor que ya existe más uno (no se reutilizan los de fotos borradas). */
    fun nextSequence(existingNames: Collection<String>): Int = (existingNames.mapNotNull(::sequenceOf).maxOrNull() ?: 0) + 1

    /** [preferred] si está libre en la carpeta; si no (o si es 0), el siguiente. */
    fun freeSequence(existingNames: Collection<String>, preferred: Int): Int {
        val used = existingNames.mapNotNull(::sequenceOf).toSet()
        return if (preferred >= 1 && preferred !in used) preferred else nextSequence(existingNames)
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
