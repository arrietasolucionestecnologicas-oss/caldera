package co.ast.campo.weather

import co.ast.campo.core.TempReading
import co.ast.campo.core.TemperatureCachePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Origen de la temperatura de referencia. Cambiar de proveedor = otra implementación de esta interfaz. */
interface WeatherProvider {
    val id: String
    val displayName: String

    /** Temperatura del aire a 2 m en °C para las coordenadas. Lanza excepción si no se pudo obtener. */
    suspend fun currentTemperature(lat: Double, lon: Double): Double
}

/** Descarga de texto por GET; se abstrae para poder probar el proveedor sin red. */
fun interface HttpGet {
    suspend fun get(url: String): String
}

class UrlConnectionGet(private val timeoutMs: Int = 4_000) : HttpGet {
    override suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "AST-Campo")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HTTP $code")
            body
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * Open-Meteo (current=temperature_2m). Sin clave usa el acceso gratuito (SOLO uso no comercial).
 * Con clave usa customer-api.open-meteo.com (plan comercial); la sintaxis es idéntica.
 * Las coordenadas se envían con 2 decimales (~1 km): suficiente para la malla del modelo y más privado.
 */
class OpenMeteoProvider(
    private val apiKey: String? = null,
    private val http: HttpGet = UrlConnectionGet(),
) : WeatherProvider {
    override val id: String = "open-meteo"
    override val displayName: String =
        if (apiKey.isNullOrBlank()) "Open-Meteo (gratuito, uso no comercial)" else "Open-Meteo (plan comercial)"

    override suspend fun currentTemperature(lat: Double, lon: Double): Double =
        parseTemperature(http.get(buildUrl(lat, lon)))

    fun buildUrl(lat: Double, lon: Double): String {
        val base = if (apiKey.isNullOrBlank()) FREE_URL else COMMERCIAL_URL
        val coords = String.format(Locale.US, "latitude=%.2f&longitude=%.2f", lat, lon)
        val key = if (apiKey.isNullOrBlank()) "" else "&apikey=" + java.net.URLEncoder.encode(apiKey.trim(), "UTF-8")
        return "$base?$coords&current=temperature_2m$key"
    }

    companion object {
        const val FREE_URL = "https://api.open-meteo.com/v1/forecast"
        const val COMMERCIAL_URL = "https://customer-api.open-meteo.com/v1/forecast"

        fun parseTemperature(body: String): Double {
            val json = JSONObject(body)
            if (json.optBoolean("error", false)) error(json.optString("reason", "Error del servicio"))
            val current = json.getJSONObject("current")
            if (current.isNull("temperature_2m")) error("Sin dato de temperatura")
            return current.getDouble("temperature_2m")
        }
    }
}

enum class TempSource { MANUAL, NETWORK, CACHE, NONE }

data class TempResult(val celsius: Double?, val source: TempSource)

interface TempCacheStore {
    fun load(): TempReading?
    fun save(reading: TempReading)
}

/**
 * Prioridad: manual > lectura reciente cercana (evita llamadas repetidas en ráfagas) > internet >
 * última lectura guardada (< 60 min y < 10 km) > "--".
 */
class TemperatureResolver(
    private val provider: () -> WeatherProvider?,
    private val store: TempCacheStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun resolve(lat: Double?, lon: Double?, manual: Double?): TempResult {
        if (manual != null) return TempResult(manual, TempSource.MANUAL)
        if (lat == null || lon == null) return TempResult(null, TempSource.NONE)

        val cached = store.load()
        TemperatureCachePolicy.fresh(cached, lat, lon, now())?.let { return TempResult(it.celsius, TempSource.CACHE) }

        val p = provider()
        if (p != null) {
            try {
                val t = p.currentTemperature(lat, lon)
                store.save(TempReading(t, lat, lon, now()))
                return TempResult(t, TempSource.NETWORK)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Sin internet o error del servicio: se intenta con la última lectura guardada.
            }
        }
        val usable = TemperatureCachePolicy.usable(cached, lat, lon, now())
        return if (usable != null) TempResult(usable.celsius, TempSource.CACHE) else TempResult(null, TempSource.NONE)
    }
}
