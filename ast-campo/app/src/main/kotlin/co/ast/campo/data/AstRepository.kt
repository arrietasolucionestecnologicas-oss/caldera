package co.ast.campo.data

import android.net.Uri
import co.ast.campo.core.FolderStats
import co.ast.campo.core.FolderTemplates
import co.ast.campo.core.FolderTree
import co.ast.campo.core.MediaPaths
import co.ast.campo.core.NameSanitizer
import co.ast.campo.media.Gallery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId

/** Error pensado para mostrarse tal cual al usuario. */
class UserError(message: String) : Exception(message)

data class MoveReport(val moved: Int, val failed: Int)

/**
 * Operaciones sobre el árbol de carpetas y las fotos. La carpeta lógica es la carpeta física:
 * cada cambio de nombre o de lugar se refleja en MediaStore (RELATIVE_PATH) y en la copia original privada.
 */
class AstRepository(
    private val db: AstDatabase,
    private val gallery: Gallery,
    private val settings: SettingsRepository,
) {
    private val folderDao = db.folders()
    private val photoDao = db.photos()

    val tree: Flow<FolderTree> = folderDao.observeAll().map { list -> FolderTree(list.map { it.toNode() }) }

    /** Estadísticas acumuladas por carpeta (fotos propias + de todas sus subcarpetas). */
    fun statsFlow(treeFlow: Flow<FolderTree>): Flow<Pair<FolderTree, Map<Long, FolderStats>>> =
        kotlinx.coroutines.flow.combine(treeFlow, photoDao.observeStats()) { t, stats ->
            t to t.aggregate(stats.associate { it.folderId to FolderStats(it.photoCount, it.lastAt) })
        }

    fun photosOf(folderId: Long): Flow<List<PhotoEntity>> = photoDao.observeByFolder(folderId)

    suspend fun photo(id: Long): PhotoEntity? = photoDao.get(id)

    private suspend fun currentTree() = FolderTree(folderDao.getAll().map { it.toNode() })

    // ---------------------------------------------------------------- carpetas

    /** Crea una carpeta (proyecto si [parentId] es null). Con [withTemplate] agrega las subcarpetas de la plantilla. */
    suspend fun createFolder(parentId: Long?, rawName: String, withTemplate: Boolean = false): Long {
        val name = validName(rawName, siblingsOf(parentId, excluding = null))
        val id = folderDao.insert(FolderEntity(parentId = parentId, name = name, createdAt = System.currentTimeMillis()))
        if (withTemplate) {
            FolderTemplates.cleanTemplate(settings.settings.value.substationTemplate).forEach { child ->
                folderDao.insert(FolderEntity(parentId = id, name = child, createdAt = System.currentTimeMillis()))
            }
        }
        return id
    }

    suspend fun renameFolder(id: Long, rawName: String): MoveReport {
        val node = currentTree()[id] ?: throw UserError("La carpeta ya no existe")
        val name = validName(rawName, siblingsOf(node.parentId, excluding = id))
        if (name == node.name) return MoveReport(0, 0)
        folderDao.rename(id, name)
        return relocateSubtree(id)
    }

    suspend fun moveFolder(id: Long, newParentId: Long?): MoveReport {
        val tree = currentTree()
        val node = tree[id] ?: throw UserError("La carpeta ya no existe")
        if (node.parentId == newParentId) return MoveReport(0, 0)
        if (newParentId != null && tree.isSelfOrDescendant(newParentId, id)) {
            throw UserError("No se puede mover una carpeta dentro de sí misma")
        }
        if (NameSanitizer.isDuplicate(node.name, siblingsOf(newParentId, excluding = id))) {
            throw UserError("Ya existe \"${node.name}\" en el destino")
        }
        folderDao.setParent(id, newParentId)
        return relocateSubtree(id)
    }

    /**
     * Elimina la carpeta y sus subcarpetas. Con [deletePhotos] se borran también las fotos (Galería, copia
     * original y registro). Sin él, las fotos pasan a la carpeta superior; si era un proyecto, las fotos
     * se quedan en la Galería pero dejan de aparecer en la app.
     */
    suspend fun deleteFolder(id: Long, deletePhotos: Boolean): MoveReport = withContext(Dispatchers.IO) {
        val tree = currentTree()
        val node = tree[id] ?: return@withContext MoveReport(0, 0)
        val ids = tree.subtreeIds(id)
        val photos = photoDao.inFolders(ids)
        var ok = 0
        var failed = 0
        when {
            deletePhotos -> photos.forEach { if (removePhoto(it)) ok++ else failed++ }
            node.parentId != null -> {
                val destFolder = node.parentId
                val destSegments = tree.segments(destFolder)
                photos.forEach { p -> if (relocate(p, destFolder, destSegments)) ok++ else failed++ }
            }
            else -> photoDao.deleteAll(photos.map { it.id })
        }
        // Si alguna foto no se pudo quitar/mover, se conserva la carpeta para no dejar filas de foto huérfanas.
        if (failed == 0) folderDao.deleteAll(ids)
        MoveReport(ok, failed)
    }

    private suspend fun siblingsOf(parentId: Long?, excluding: Long?): List<String> =
        folderDao.getAll().filter { it.parentId == parentId && it.id != excluding }.map { it.name }

    private fun validName(raw: String, siblings: List<String>): String {
        val name = NameSanitizer.sanitize(raw)
        if (name.isEmpty()) throw UserError("Escribe un nombre válido")
        if (NameSanitizer.isDuplicate(name, siblings)) throw UserError("Ya existe una carpeta llamada \"$name\" en este nivel")
        return name
    }

    // ------------------------------------------------------------------ fotos

    suspend fun movePhoto(photoId: Long, destFolderId: Long) = withContext(Dispatchers.IO) {
        val p = photoDao.get(photoId) ?: throw UserError("La foto ya no existe")
        if (p.folderId == destFolderId) return@withContext
        val tree = currentTree()
        val segments = tree.segments(destFolderId)
        if (segments.isEmpty()) throw UserError("La carpeta de destino ya no existe")
        if (!relocate(p, destFolderId, segments)) {
            throw UserError("No se pudo mover la foto. Si la app se reinstaló con otra firma, Android ya no le permite modificarla.")
        }
    }

    suspend fun deletePhoto(photoId: Long) = withContext(Dispatchers.IO) {
        val p = photoDao.get(photoId) ?: return@withContext
        if (!removePhoto(p)) throw UserError("No se pudo eliminar la foto de la Galería")
    }

    /** Quita de la app las fotos que ya no existen en la Galería (borradas desde otra app). */
    suspend fun pruneMissing(folderId: Long) = withContext(Dispatchers.IO) {
        val photos = photoDao.inFolders(listOf(folderId))
        if (photos.isEmpty()) return@withContext
        val alive = gallery.existing(photos.map { Uri.parse(it.mediaUri) })
        val gone = photos.filter { Uri.parse(it.mediaUri) !in alive }
        if (gone.isNotEmpty()) photoDao.deleteAll(gone.map { it.id })
    }

    private suspend fun removePhoto(p: PhotoEntity): Boolean {
        if (!gallery.delete(Uri.parse(p.mediaUri))) return false
        p.originalPath?.let { File(it).delete() }
        photoDao.deleteAll(listOf(p.id))
        return true
    }

    /** Recalcula la ruta de todas las fotos de [folderId] y sus descendientes tras renombrar/mover. */
    private suspend fun relocateSubtree(folderId: Long): MoveReport = withContext(Dispatchers.IO) {
        val tree = currentTree()
        val photos = photoDao.inFolders(tree.subtreeIds(folderId))
        var ok = 0
        var failed = 0
        photos.forEach { p ->
            val moved = try {
                relocate(p, p.folderId, tree.segments(p.folderId))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (moved) ok++ else failed++
        }
        MoveReport(ok, failed)
    }

    /** Lleva una foto a [folderId] (ruta [segments]): MediaStore, copia original y registro. */
    private suspend fun relocate(p: PhotoEntity, folderId: Long, segments: List<String>): Boolean {
        val seq = SEQ.find(p.displayName)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val newName = MediaPaths.fileName(segments, p.takenAt, ZoneId.systemDefault(), seq)
        val finalName = gallery.relocate(Uri.parse(p.mediaUri), MediaPaths.relativePath(segments), newName) ?: return false
        var newOriginal = p.originalPath
        p.originalPath?.let { old ->
            val src = File(old)
            if (src.exists()) {
                val root = originalsRootOf(src)
                val dest = File(File(root, MediaPaths.originalRelativePath(segments)).apply { mkdirs() }, finalName)
                newOriginal = if (src.renameTo(dest) || runCatching { src.copyTo(dest, true); src.delete() }.isSuccess) dest.absolutePath else old
            }
        }
        photoDao.update(p.copy(folderId = folderId, displayName = finalName, originalPath = newOriginal))
        return true
    }

    /** Raíz "originales" de la que cuelga la copia original (sube desde el archivo hasta la carpeta con ese nombre). */
    private fun originalsRootOf(file: File): File {
        var cur: File? = file.parentFile
        while (cur != null && cur.name != "originales") cur = cur.parentFile
        return cur ?: file.parentFile ?: File(".")
    }

    private companion object {
        val SEQ = Regex("_(\\d{3,})\\.jpg$")
    }
}
