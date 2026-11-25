package co.ast.campo.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/** Operaciones de la Galería que usa la app (interfaz para poder probar sin MediaStore real). */
interface Gallery {
    fun insert(displayName: String, relativePath: String, takenAtMillis: Long, source: File): Uri
    fun relocate(uri: Uri, relativePath: String, displayName: String): String?
    fun delete(uri: Uri): Boolean
    fun exists(uri: Uri): Boolean
    fun existing(uris: List<Uri>): Set<Uri>
}

/**
 * Acceso a la Galería mediante MediaStore. Con RELATIVE_PATH + IS_PENDING la foto aparece sola en la
 * Galería y no hace falta ningún permiso de almacenamiento (la app es dueña de lo que ella inserta).
 */
class GalleryStore(context: Context) : Gallery {
    private val resolver = context.applicationContext.contentResolver
    private val collection: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /** Inserta [source] (un JPEG) en [relativePath] con el nombre [displayName]. Devuelve la URI creada. */
    override fun insert(displayName: String, relativePath: String, takenAtMillis: Long, source: File): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.DATE_TAKEN, takenAtMillis)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore no creó el archivo")
        try {
            val out = resolver.openOutputStream(uri, "w") ?: throw IOException("No se pudo abrir el archivo de destino")
            out.use { o -> source.inputStream().use { it.copyTo(o, 256 * 1024) } }
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return uri
    }

    /** Cambia carpeta y nombre de una foto de la app. Devuelve el nombre final o null si no fue posible. */
    override fun relocate(uri: Uri, relativePath: String, displayName: String): String? = try {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        }
        if (resolver.update(uri, values, null, null) > 0) displayNameOf(uri) ?: displayName else null
    } catch (_: SecurityException) {
        null // por ejemplo la foto ya no pertenece a la app (reinstalación con otra firma)
    } catch (_: Exception) {
        null // destino inválido, archivo ausente u otro error de MediaProvider
    }

    /** true si ya no existe en la Galería o si se borró. false si existe pero no se pudo borrar. */
    override fun delete(uri: Uri): Boolean = try {
        resolver.delete(uri, null, null) > 0 || !exists(uri)
    } catch (_: SecurityException) {
        false
    }

    override fun exists(uri: Uri): Boolean = try {
        resolver.query(uri, arrayOf(MediaStore.Images.Media._ID), null, null, null)?.use { it.count > 0 } ?: false
    } catch (_: Exception) {
        false
    }

    fun displayNameOf(uri: Uri): String? = try {
        resolver.query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    /** De las URIs dadas, las que todavía existen en la Galería. */
    override fun existing(uris: List<Uri>): Set<Uri> {
        val ids = uris.mapNotNull { runCatching { ContentUris.parseId(it) }.getOrNull() }
        val found = HashSet<Long>()
        ids.chunked(400).forEach { chunk ->
            val sel = "${MediaStore.Images.Media._ID} IN (${chunk.joinToString(",") { "?" }})"
            resolver.query(collection, arrayOf(MediaStore.Images.Media._ID), sel, chunk.map { it.toString() }.toTypedArray(), null)
                ?.use { c -> while (c.moveToNext()) found.add(c.getLong(0)) }
        }
        return uris.filter { runCatching { ContentUris.parseId(it) }.getOrNull() in found }.toSet()
    }
}
