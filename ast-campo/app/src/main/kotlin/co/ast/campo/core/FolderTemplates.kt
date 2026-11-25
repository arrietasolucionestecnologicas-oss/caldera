package co.ast.campo.core

import java.text.Normalizer

object FolderTemplates {
    val DEFAULT_SUBSTATION_CHILDREN = listOf(
        "Transformadores", "Tableros", "Generadores", "Banco de condensadores",
    )

    /** Sugerencias rápidas al crear una carpeta. */
    val QUICK_SUGGESTIONS = listOf(
        "Pruebas eléctricas", "Mantenimiento", "Subestación 1", "Transformadores", "Tableros",
        "Generadores", "Banco de condensadores", "Termografía", "CCTV", "Inversores",
        "Antes", "Después", "Placas de datos",
    )

    private val numbered = Regex("^(.*?)(\\d+)$")

    private fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

    /** ¿El nombre es de tipo "Subestación ..." (sin importar tildes ni mayúsculas)? */
    fun isSubstationName(name: String): Boolean = fold(name.trim()).startsWith("subestacion")

    /** Limpia y descarta duplicados de una lista editable de subcarpetas de plantilla. */
    fun cleanTemplate(items: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        return items.map { NameSanitizer.sanitize(it) }
            .filter { it.isNotEmpty() && seen.add(NameSanitizer.key(it)) }
    }

    /**
     * Si los hermanos siguen un patrón "Texto N" (CT 5, Subestación 2), propone el siguiente número
     * del prefijo más usado. Devuelve null si no hay patrón.
     */
    fun nextNumbered(siblings: List<String>): String? {
        val groups = siblings.mapNotNull { n ->
            numbered.matchEntire(n.trim())?.let { m ->
                val prefix = m.groupValues[1]
                val num = m.groupValues[2].toLongOrNull()
                if (prefix.isBlank() || num == null) null else prefix to num
            }
        }.groupBy({ it.first }, { it.second })
        val best = groups.maxByOrNull { (_, nums) -> nums.size * 1_000_000_000L + nums.max() } ?: return null
        return best.key + (best.value.max() + 1)
    }
}
