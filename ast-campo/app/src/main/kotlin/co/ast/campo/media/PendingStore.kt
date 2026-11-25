package co.ast.campo.media

import org.json.JSONObject
import java.io.File

/**
 * Cola persistente de fotos por procesar: junto a cada `cap_*.jpg` de la cámara se guarda un `cap_*.json` con
 * lo necesario para procesarla. Si Android mata la app a mitad de camino, al abrirla de nuevo se reprocesan.
 */
object PendingStore {
    const val MAX_ATTEMPTS = 3

    fun sidecar(photo: File) = File(photo.path.removeSuffix(".jpg") + ".json")

    fun write(p: PendingCapture, attempts: Int) {
        val j = JSONObject()
            .put("file", p.filePath).put("folder", p.folderId).put("launched", p.launchedAtMillis)
            .put("launchElapsed", p.launchElapsedMs).put("attempts", attempts)
        p.snapshotLat?.let { j.put("lat", it) }
        p.snapshotLon?.let { j.put("lon", it) }
        p.snapshotAccuracyM?.let { j.put("acc", it.toDouble()) }
        p.snapshotElapsedMs?.let { j.put("fixElapsed", it) }
        p.manualTemperature?.let { j.put("manual", it) }
        sidecar(File(p.filePath)).writeText(j.toString())
    }

    /** Pendiente guardado en [json] y cuántos intentos de proceso lleva. */
    fun read(json: File): Pair<PendingCapture, Int>? = runCatching {
        val j = JSONObject(json.readText())
        PendingCapture(
            filePath = j.getString("file"), folderId = j.getLong("folder"), launchedAtMillis = j.getLong("launched"),
            snapshotLat = if (j.has("lat")) j.getDouble("lat") else null,
            snapshotLon = if (j.has("lon")) j.getDouble("lon") else null,
            snapshotAccuracyM = if (j.has("acc")) j.getDouble("acc").toFloat() else null,
            snapshotElapsedMs = if (j.has("fixElapsed")) j.getLong("fixElapsed") else null,
            manualTemperature = if (j.has("manual")) j.getDouble("manual") else null,
            launchElapsedMs = j.optLong("launchElapsed", 0L),
        ) to j.optInt("attempts", 0)
    }.getOrNull()
}
