package co.ast.campo

import android.net.Uri
import co.ast.campo.media.Gallery
import co.ast.campo.media.LocationFix
import co.ast.campo.media.LocationSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Galería simulada: guarda los archivos en una carpeta temporal respetando RELATIVE_PATH. */
class FakeGallery(private val root: File) : Gallery {
    data class Item(val id: Long, var relativePath: String, var name: String, var file: File)

    val items = LinkedHashMap<Long, Item>()
    private var nextId = 1L
    var failRelocate = false

    private fun idOf(uri: Uri) = uri.lastPathSegment!!.toLong()
    fun at(relativePath: String) = items.values.filter { it.relativePath == relativePath }

    override fun insert(displayName: String, relativePath: String, takenAtMillis: Long, source: File): Uri {
        val id = nextId++
        val dest = File(File(root, relativePath).apply { mkdirs() }, displayName)
        source.copyTo(dest, overwrite = true)
        items[id] = Item(id, relativePath, displayName, dest)
        return Uri.parse("content://fake/media/$id")
    }

    override fun relocate(uri: Uri, relativePath: String, displayName: String): String? {
        if (failRelocate) return null
        val item = items[idOf(uri)] ?: return null
        val dest = File(File(root, relativePath).apply { mkdirs() }, displayName)
        item.file.copyTo(dest, overwrite = true)
        if (item.file != dest) item.file.delete()
        item.relativePath = relativePath
        item.name = displayName
        item.file = dest
        return displayName
    }

    override fun delete(uri: Uri): Boolean {
        val item = items.remove(idOf(uri)) ?: return true
        item.file.delete()
        return true
    }

    override fun exists(uri: Uri) = idOf(uri) in items
    override fun existing(uris: List<Uri>) = uris.filter { exists(it) }.toSet()
}

/** Ubicación simulada. */
class FakeLocation(
    initial: LocationFix? = null,
    private val permission: Boolean = true,
    private val once: LocationFix? = null,
) : LocationSource {
    private val flow = MutableStateFlow(initial)
    override val fix: StateFlow<LocationFix?> = flow
    override fun hasPermission() = permission
    override fun isGpsEnabled() = true
    override fun start() = Unit
    override fun stop() = Unit
    override suspend fun currentOnce(timeoutMs: Long): LocationFix? = once
}
