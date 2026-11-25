package co.ast.campo.media

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import co.ast.campo.data.PhotoEntity
import co.ast.campo.weather.TempSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface CaptureEvent {
    data class Saved(
        val photo: PhotoEntity,
        val hasLocation: Boolean,
        val accuracyM: Float?,
        val temperatureSource: TempSource,
        val watermarkFailed: Boolean,
        val downscaled: Boolean,
    ) : CaptureEvent

    data class Failed(val message: String) : CaptureEvent
}

/**
 * Orquesta la captura: prepara el archivo/URI para la cámara, recuerda a qué carpeta iba la foto
 * (en SharedPreferences, por si Android mata la app mientras la cámara está abierta) y, al volver,
 * procesa la foto en segundo plano para que se pueda tomar la siguiente de inmediato.
 */
class CaptureManager(
    private val context: Context,
    private val processor: PhotoProcessor,
    private val tracker: LocationSource,
    private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("ast_pending_capture", Context.MODE_PRIVATE)
    private val _events = MutableSharedFlow<CaptureEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<CaptureEvent> = _events.asSharedFlow()
    private val _processing = MutableStateFlow(0)

    /** Fotos que se están marcando/guardando ahora mismo. */
    val processing: StateFlow<Int> = _processing.asStateFlow()

    /**
     * Crea el archivo temporal que llenará la cámara y devuelve su URI (FileProvider). El estado pendiente se
     * guarda con commit() (síncrono) a propósito: Android puede matar la app mientras la cámara está abierta.
     */
    @SuppressLint("ApplySharedPref")
    @Synchronized
    fun prepare(folderId: Long, manualTemperature: Double?): Uri {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "cap_${System.currentTimeMillis()}.jpg")
        val fix = tracker.fix.value
        prefs.edit()
            .putString("file", file.absolutePath)
            .putLong("folder", folderId)
            .putLong("launched", System.currentTimeMillis())
            .putBoolean("has_fix", fix != null)
            .putLong("lat", (fix?.lat ?: 0.0).toBits())
            .putLong("lon", (fix?.lon ?: 0.0).toBits())
            .putFloat("acc", fix?.accuracyM ?: 0f)
            .putLong("fix_elapsed", fix?.elapsedRealtimeMs ?: 0L)
            .putBoolean("has_manual", manualTemperature != null)
            .putFloat("manual", (manualTemperature ?: 0.0).toFloat())
            .commit()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Resultado de la cámara (se entrega aunque la app se haya recreado mientras la cámara estaba abierta). */
    @SuppressLint("ApplySharedPref")
    @Synchronized
    fun onCameraResult(success: Boolean) {
        val pending = readPending() ?: return
        prefs.edit().clear().commit()
        if (!success) {
            File(pending.filePath).delete()
            return
        }
        _processing.value += 1
        scope.launch {
            try {
                val r = processor.process(pending)
                _events.emit(
                    CaptureEvent.Saved(r.photo, r.hasLocation, r.accuracyM, r.temperature.source, r.watermarkFailed, r.downscaled),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _events.emit(CaptureEvent.Failed(e.message ?: "No se pudo guardar la foto"))
            } finally {
                _processing.value -= 1
            }
        }
    }

    private fun readPending(): PendingCapture? {
        val path = prefs.getString("file", null) ?: return null
        val hasFix = prefs.getBoolean("has_fix", false)
        return PendingCapture(
            filePath = path,
            folderId = prefs.getLong("folder", -1L),
            launchedAtMillis = prefs.getLong("launched", System.currentTimeMillis()),
            snapshotLat = if (hasFix) Double.fromBits(prefs.getLong("lat", 0)) else null,
            snapshotLon = if (hasFix) Double.fromBits(prefs.getLong("lon", 0)) else null,
            snapshotAccuracyM = if (hasFix) prefs.getFloat("acc", 0f) else null,
            snapshotElapsedMs = if (hasFix) prefs.getLong("fix_elapsed", 0L) else null,
            manualTemperature = if (prefs.getBoolean("has_manual", false)) prefs.getFloat("manual", 0f).toDouble() else null,
        )
    }

    /** Borra capturas temporales viejas que quedaron sin procesar (más de 3 días). */
    fun cleanOldTemporaries() {
        val limit = System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000
        File(context.cacheDir, "captures").listFiles()?.forEach { if (it.lastModified() < limit) it.delete() }
    }
}
