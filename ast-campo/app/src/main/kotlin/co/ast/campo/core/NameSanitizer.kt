package co.ast.campo.core

import java.text.Normalizer
import java.util.Locale

/** Limpia nombres de carpetas para que sean válidos como carpeta física y como parte de un nombre de archivo. */
object NameSanitizer {
    const val MAX_NAME_LENGTH = 60

    private val forbidden = Regex("[/\\\\:*?\"<>|\\p{Cntrl}]")
    private val whitespace = Regex("\\s+")

    /**
     * Quita `/ \ : * ? " < > |` y caracteres de control, colapsa espacios, recorta espacios y puntos
     * finales (y puntos iniciales, que crearían carpetas ocultas) y limita la longitud.
     * Conserva tildes y ñ (normaliza a NFC). Devuelve cadena vacía si no queda nada utilizable.
     */
    fun sanitize(raw: String, maxLength: Int = MAX_NAME_LENGTH): String {
        var s = Normalizer.normalize(raw, Normalizer.Form.NFC)
        s = whitespace.replace(s, " ") // tabuladores y saltos de línea cuentan como espacio
        s = forbidden.replace(s, "")
        s = whitespace.replace(s, " ") // quitar un símbolo entre espacios puede dejar dobles
        s = trimEdges(s)
        if (s.length > maxLength) {
            s = trimEdges(cutWithoutSplittingSurrogate(s, maxLength))
        }
        return s
    }

    /** Clave para comparar nombres en un mismo nivel sin distinguir mayúsculas/minúsculas. */
    fun key(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFC).lowercase(Locale.ROOT)

    /** Indica si [candidate] ya existe entre [siblings] (sin distinguir mayúsculas). */
    fun isDuplicate(candidate: String, siblings: Collection<String>): Boolean {
        val k = key(candidate)
        return siblings.any { key(it) == k }
    }

    private fun trimEdges(s: String): String {
        var start = 0
        var end = s.length
        while (start < end && (s[start] == '.' || s[start].isWhitespace())) start++
        while (end > start && (s[end - 1] == '.' || s[end - 1].isWhitespace())) end--
        return s.substring(start, end)
    }

    private fun cutWithoutSplittingSurrogate(s: String, max: Int): String {
        var end = max
        if (end in 1 until s.length && Character.isHighSurrogate(s[end - 1])) end--
        return s.substring(0, end)
    }
}
