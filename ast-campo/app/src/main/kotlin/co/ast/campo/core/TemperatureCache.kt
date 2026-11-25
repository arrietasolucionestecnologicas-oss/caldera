package co.ast.campo.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Última temperatura obtenida de internet, con dónde y cuándo se tomó. */
data class TempReading(val celsius: Double, val lat: Double, val lon: Double, val atMillis: Long)

/** Regla: se reutiliza la última lectura si tiene menos de 60 min y se tomó a menos de 10 km. */
object TemperatureCachePolicy {
    const val MAX_AGE_MILLIS = 60 * 60 * 1000L
    const val MAX_DISTANCE_METERS = 10_000.0

    /** Una lectura tan reciente y cercana no necesita volver a consultarse (ráfagas de fotos). */
    const val FRESH_AGE_MILLIS = 10 * 60 * 1000L
    const val FRESH_DISTANCE_METERS = 1_000.0

    fun usable(cached: TempReading?, lat: Double?, lon: Double?, nowMillis: Long): TempReading? {
        if (cached == null || lat == null || lon == null) return null
        val age = nowMillis - cached.atMillis
        if (age < 0 || age >= MAX_AGE_MILLIS) return null
        return if (distanceMeters(cached.lat, cached.lon, lat, lon) < MAX_DISTANCE_METERS) cached else null
    }

    fun fresh(cached: TempReading?, lat: Double?, lon: Double?, nowMillis: Long): TempReading? {
        if (cached == null || lat == null || lon == null) return null
        val age = nowMillis - cached.atMillis
        if (age < 0 || age >= FRESH_AGE_MILLIS) return null
        return if (distanceMeters(cached.lat, cached.lon, lat, lon) < FRESH_DISTANCE_METERS) cached else null
    }

    /** Distancia de círculo máximo (haversine) en metros. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}
