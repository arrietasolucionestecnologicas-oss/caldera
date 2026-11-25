package co.ast.campo.core

/** Nodo del árbol de carpetas (Proyecto > subcarpeta > ...). Los proyectos son nodos con parentId == null. */
data class FolderNode(
    val id: Long,
    val parentId: Long?,
    val name: String,
    val createdAt: Long = 0L,
)

/** Conteo de fotos y última actividad de una carpeta, ya sea directo o acumulado con sus descendientes. */
data class FolderStats(val photoCount: Int = 0, val lastActivity: Long = 0L) {
    operator fun plus(o: FolderStats) = FolderStats(photoCount + o.photoCount, maxOf(lastActivity, o.lastActivity))
}

/** Vista de solo lectura del árbol, construida a partir de la lista plana guardada en Room. */
class FolderTree(folders: List<FolderNode>) {
    private val byId: Map<Long, FolderNode> = folders.associateBy { it.id }
    private val children: Map<Long?, List<FolderNode>> =
        folders.groupBy { it.parentId }.mapValues { (_, v) -> v.sortedBy { NameSanitizer.key(it.name) } }

    val all: Collection<FolderNode> get() = byId.values
    val projects: List<FolderNode> get() = children[null].orEmpty()

    operator fun get(id: Long): FolderNode? = byId[id]

    fun childrenOf(parentId: Long?): List<FolderNode> = children[parentId].orEmpty()

    /** Desde el proyecto hasta la carpeta [id] (incluida). Vacío si no existe. Protegido contra ciclos. */
    fun pathOf(id: Long): List<FolderNode> {
        val out = ArrayList<FolderNode>()
        var cur = byId[id]
        val seen = HashSet<Long>()
        while (cur != null && seen.add(cur.id)) {
            out.add(cur)
            cur = cur.parentId?.let { byId[it] }
        }
        out.reverse()
        return out
    }

    fun segments(id: Long): List<String> = pathOf(id).map { it.name }

    fun projectOf(id: Long): FolderNode? = pathOf(id).firstOrNull()

    /** [id] y todos sus descendientes. */
    fun subtreeIds(id: Long): List<Long> {
        if (byId[id] == null) return emptyList()
        val out = ArrayList<Long>()
        val stack = ArrayDeque<Long>().apply { add(id) }
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            out.add(cur)
            childrenOf(cur).forEach { stack.add(it.id) }
        }
        return out
    }

    /** ¿[candidate] es [ancestor] o está dentro de él? (para impedir mover una carpeta dentro de sí misma). */
    fun isSelfOrDescendant(candidate: Long, ancestor: Long): Boolean = candidate in subtreeIds(ancestor)

    /** Suma, para cada carpeta, sus fotos directas más las de todos sus descendientes. */
    fun aggregate(direct: Map<Long, FolderStats>): Map<Long, FolderStats> {
        val result = HashMap<Long, FolderStats>()
        fun visit(id: Long): FolderStats {
            var total = direct[id] ?: FolderStats()
            childrenOf(id).forEach { total += visit(it.id) }
            result[id] = total
            return total
        }
        projects.forEach { visit(it.id) }
        return result
    }
}
