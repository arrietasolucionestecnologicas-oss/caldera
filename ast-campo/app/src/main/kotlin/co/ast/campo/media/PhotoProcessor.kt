package co.ast.campo.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.SystemClock
import androidx.exifinterface.media.ExifInterface
import co.ast.campo.core.ExifDates
import co.ast.campo.core.TemperatureCachePolicy
import co.ast.campo.core.awaitBounded
import co.ast.campo.weather.TempSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import co.ast.campo.core.FolderTree
import co.ast.campo.core.MediaPaths
import co.ast.campo.core.NameSanitizer
import co.ast.campo.core.WatermarkFormatter
import co.ast.campo.data.AstDatabase
import co.ast.campo.data.PhotoEntity
import co.ast.campo.data.SettingsRepository
import co.ast.campo.data.WatermarkSettings
import co.ast.campo.place.PlaceResolver
import co.ast.campo.weather.TempResult
import co.ast.campo.weather.TemperatureResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.ZoneId

/** Foto pendiente de procesar, tal como la dejó la cámara. */
data class PendingCapture(
    val filePath: String,
    val folderId: Long,
    val launchedAtMillis: Long,
    val snapshotLat: Double?,
    val snapshotLon: Double?,
    val snapshotAccuracyM: Float?,
    /** SystemClock.elapsedRealtime() del momento de la ubicación guardada (solo válido en este arranque del equipo). */
    val snapshotElapsedMs: Long?,
    val manualTemperature: Double?,
    /** SystemClock.elapsedRealtime() al abrir la cámara. */
    val launchElapsedMs: Long,
)

data class ProcessedPhoto(
    val photo: PhotoEntity,
    val hasLocation: Boolean,
    val accuracyM: Float?,
    val temperature: TempResult,
    /** true si no se pudo aplicar la marca (se guardó la foto sin marca para no perderla). */
    val watermarkFailed: Boolean,
    /** true si se tuvo que reducir la resolución por falta de memoria. */
    val downscaled: Boolean,
    /** true si no se pudo escribir el EXIF (la foto sí quedó con marca). */
    val exifFailed: Boolean = false,
)

class PhotoProcessor(
    private val context: Context,
    private val db: AstDatabase,
    private val gallery: Gallery,
    private val settings: SettingsRepository,
    private val tracker: LocationSource,
    private val temperature: TemperatureResolver,
    private val places: PlaceResolver,
) {
    // Una foto a la vez: acota la memoria (un bitmap de 50 MP pesa ~200 MB) y hace determinista el consecutivo NNN.
    private val mutex = Mutex()

    // Las consultas de red corren aparte: si tardan, la foto se guarda igual (sin ese dato) en vez de quedarse "cargando".
    private val lookupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun process(p: PendingCapture): ProcessedPhoto {
        // Lo lento y dependiente de la red va FUERA del candado para no bloquear a las demás fotos.
        val fix = resolveLocation(p)
        val wantPlace = settings.settings.value.watermark.showPlace
        val (temp, place) = coroutineScope {
            // Temperatura y lugar se piden a la vez; cada uno espera como máximo 5 s. Casi siempre ya están en caché
            // porque la pantalla de captura los precarga mientras apuntas la cámara.
            val t = async {
                awaitBounded(lookupScope, LOOKUP_TIMEOUT_MS) { temperature.resolve(fix?.lat, fix?.lon, p.manualTemperature) }
                    ?: fallbackTemperature(fix)
            }
            val pl = async {
                if (wantPlace) awaitBounded(lookupScope, LOOKUP_TIMEOUT_MS) { places.resolve(fix?.lat, fix?.lon) } else null
            }
            t.await() to pl.await()
        }
        return mutex.withLock { withContext(Dispatchers.Default) { processLocked(p, fix, temp, place) } }
    }

    private suspend fun processLocked(p: PendingCapture, fix: LocationFix?, temp: TempResult, place: String?): ProcessedPhoto {
        val src = File(p.filePath)
        if (!src.exists() || src.length() == 0L) throw IOException("La cámara no devolvió la foto")

        val exif = ExifInterface(src.absolutePath)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("La foto de la cámara está dañada")

        val zone = ZoneId.systemDefault()
        val takenAt = takenAtMillis(exif, src, p.launchedAtMillis, zone)

        val tree = FolderTree(db.folders().getAll().map { it.toNode() })
        val segments = tree.segments(p.folderId)
        if (segments.isEmpty()) throw IOException("La carpeta de destino ya no existe")
        val s = settings.settings.value

        // Número de foto dentro de la carpeta (1, 2, 3...), consecutivo entre días.
        val fileName = MediaPaths.fileName(segments, MediaPaths.nextSequence(db.photos().names(p.folderId)))
        val content = buildContent(s.watermark, segments, takenAt, zone, fix?.lat, fix?.lon, temp.celsius, place)

        // 1) Marca de agua. Si algo falla (p. ej. memoria) se guarda la foto sin marca antes que perderla.
        val marked = File(context.cacheDir, "captures/out_${SystemClock.elapsedRealtimeNanos()}.jpg")
        var watermarkFailed = false
        var downscaled = false
        var outFile = marked
        var outW = bounds.outWidth
        var outH = bounds.outHeight
        var exifFailed = false
        try {
            val r = renderWatermark(src, marked, orientation, content, s.watermark)
            downscaled = r.sample > 1
            outW = r.width
            outH = r.height
            // Un fallo al escribir el EXIF no debe tirar la marca ya dibujada.
            exifFailed = runCatching { writeExif(exif, marked, orientation, takenAt, zone, fix, segments, outW, outH) }.isFailure
        } catch (e: CancellationException) {
            marked.delete()
            throw e
        } catch (_: Throwable) { // incluye OutOfMemoryError
            marked.delete()
            watermarkFailed = true
            outFile = src
        }

        // 2) Copia original sin marca en el almacenamiento privado (opcional).
        var originalPath: String? = null
        if (s.saveOriginal) {
            originalPath = runCatching {
                val dir = File(originalsRoot(), MediaPaths.originalRelativePath(segments)).apply { mkdirs() }
                val dest = File(dir, fileName)
                src.copyTo(dest, overwrite = true)
                // La copia ya trae el EXIF de la cámara; solo se le agrega fecha y GPS.
                runCatching { writeExif(exif, dest, orientation, takenAt, zone, fix, segments, null, null, copyAll = false) }
                dest.absolutePath
            }.getOrNull()
        }

        // 3) Galería + base de datos.
        val uri = gallery.insert(fileName, MediaPaths.relativePath(segments), takenAt, outFile)
        val entity = PhotoEntity(
            folderId = p.folderId, mediaUri = uri.toString(), displayName = fileName, takenAt = takenAt,
            latitude = fix?.lat, longitude = fix?.lon, accuracyM = fix?.accuracyM?.takeIf { it < Float.MAX_VALUE },
            temperatureC = temp.celsius, temperatureSource = temp.source.name, originalPath = originalPath,
            width = outW, height = outH, sizeBytes = outFile.length(), place = place,
        )
        val id = db.photos().insert(entity)
        PendingStore.sidecar(src).delete() // ya no hay nada que recuperar
        src.delete()
        if (outFile !== src) outFile.delete()
        return ProcessedPhoto(entity.copy(id = id), fix != null, entity.accuracyM, temp, watermarkFailed, downscaled, exifFailed)
    }

    private class Rendered(val width: Int, val height: Int, val sample: Int)

    private fun renderWatermark(
        src: File, dest: File, orientation: Int, content: WatermarkContent, style: WatermarkSettings,
    ): Rendered {
        var sample = 1
        var bmp: Bitmap? = null
        while (bmp == null) {
            val opts = BitmapFactory.Options().apply {
                inMutable = true
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = sample
            }
            try {
                bmp = BitmapFactory.decodeFile(src.absolutePath, opts) ?: throw IOException("No se pudo leer la foto")
            } catch (e: OutOfMemoryError) {
                if (sample >= 4) throw e
                sample *= 2
            }
        }
        try {
            WatermarkRenderer.draw(Canvas(bmp), bmp.width, bmp.height, orientation, content, style)
            dest.parentFile?.mkdirs()
            FileOutputStream(dest).use { out ->
                if (!bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) throw IOException("No se pudo codificar el JPEG")
            }
            return Rendered(bmp.width, bmp.height, sample)
        } finally {
            bmp.recycle()
        }
    }

    /**
     * Ubicación de la foto: entre la guardada al abrir la cámara y la actual, la más precisa que no sea de más de 60 s
     * antes de abrir la cámara (una ubicación de hace minutos sería de otro sitio). Si no hay, se pide una nueva.
     */
    private suspend fun resolveLocation(p: PendingCapture): LocationFix? {
        val now = SystemClock.elapsedRealtime()
        val snapshot = if (p.snapshotLat != null && p.snapshotLon != null && p.snapshotElapsedMs != null) {
            LocationFix(p.snapshotLat, p.snapshotLon, p.snapshotAccuracyM ?: Float.MAX_VALUE, p.snapshotElapsedMs)
        } else null
        val best = LocationPolicy.pick(listOfNotNull(snapshot, tracker.fix.value), p.launchElapsedMs, now)
        return best ?: tracker.currentOnce()
    }

    /** Si la consulta de temperatura no llegó a tiempo: la última guardada si es reciente y cercana, o sin dato. */
    private fun fallbackTemperature(fix: LocationFix?): TempResult {
        val cached = TemperatureCachePolicy.usable(settings.load(), fix?.lat, fix?.lon, System.currentTimeMillis())
        return if (cached != null) TempResult(cached.celsius, TempSource.CACHE) else TempResult(null, TempSource.NONE)
    }

    private fun takenAtMillis(exif: ExifInterface, file: File, launchedAt: Long, zone: ZoneId): Long {
        val fromExif = ExifDates.parse(
            exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL), exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL), zone,
        ) ?: ExifDates.parse(exif.getAttribute(ExifInterface.TAG_DATETIME), exif.getAttribute(ExifInterface.TAG_OFFSET_TIME), zone)
        if (fromExif != null && kotlin.math.abs(fromExif - launchedAt) < 2 * DAY_MS) return fromExif
        val modified = file.lastModified()
        return if (modified > 0 && kotlin.math.abs(modified - launchedAt) < 2 * DAY_MS) modified else System.currentTimeMillis()
    }

    private fun originalsRoot(): File =
        context.getExternalFilesDir("originales") ?: File(context.filesDir, "originales")

    private fun writeExif(
        from: ExifInterface, file: File, orientation: Int, takenAt: Long, zone: ZoneId, fix: LocationFix?,
        segments: List<String>, width: Int?, height: Int?, copyAll: Boolean = true,
    ) {
        val out = ExifInterface(file.absolutePath)
        if (copyAll) {
            COPIED_TAGS.forEach { tag -> from.getAttribute(tag)?.let { out.setAttribute(tag, it) } }
        }
        val stamp = ExifDates.format(takenAt, zone)
        val offset = ExifDates.offset(takenAt, zone)
        out.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        out.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, stamp)
        out.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, stamp)
        out.setAttribute(ExifInterface.TAG_DATETIME, stamp)
        out.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
        out.setAttribute(ExifInterface.TAG_OFFSET_TIME, offset)
        // EXIF solo admite ASCII en este campo: se quitan tildes para no guardar signos de interrogación.
        out.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, NameSanitizer.asciiFold(segments.joinToString(" > ")))
        if (width != null && height != null) {
            out.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, width.toString())
            out.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, height.toString())
        }
        if (fix != null) out.setLatLong(fix.lat, fix.lon)
        out.saveAttributes()
    }

    companion object {
        const val JPEG_QUALITY = 95
        const val LOOKUP_TIMEOUT_MS = 5_000L
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        /** Datos técnicos de la cámara que se conservan al recomprimir. */
        private val COPIED_TAGS = listOf(
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_LENS_MAKE, ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_FLASH,
            ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_EXPOSURE_PROGRAM, ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
            ExifInterface.TAG_EXPOSURE_MODE, ExifInterface.TAG_METERING_MODE, ExifInterface.TAG_SCENE_CAPTURE_TYPE,
            ExifInterface.TAG_BRIGHTNESS_VALUE, ExifInterface.TAG_APERTURE_VALUE, ExifInterface.TAG_SHUTTER_SPEED_VALUE,
            ExifInterface.TAG_MAX_APERTURE_VALUE, ExifInterface.TAG_DIGITAL_ZOOM_RATIO, ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        )

        fun buildContent(
            style: WatermarkSettings, segments: List<String>, takenAt: Long, zone: ZoneId,
            lat: Double?, lon: Double?, celsius: Double?, place: String? = null,
        ) = WatermarkContent(
            routeSegments = if (style.showRoute) segments else emptyList(),
            dateTime = if (style.showDateTime) WatermarkFormatter.dateTime(takenAt, zone) else null,
            data = WatermarkFormatter.dataLine(lat, lon, celsius, style.showCoords, style.showTemp),
            place = if (style.showPlace) place?.trim()?.ifEmpty { null } else null,
        )
    }
}
