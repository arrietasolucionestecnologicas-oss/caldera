package co.ast.campo.media

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

data class LocationFix(
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    /** SystemClock.elapsedRealtime() del momento en que el sistema obtuvo la ubicación. */
    val elapsedRealtimeMs: Long,
) {
    fun ageMs(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long = nowElapsedMs - elapsedRealtimeMs
}

/** Fuente de ubicación (interfaz para poder probar sin servicios de Google). */
interface LocationSource {
    val fix: StateFlow<LocationFix?>
    fun hasPermission(): Boolean
    fun isGpsEnabled(): Boolean
    fun start()
    fun stop()
    suspend fun currentOnce(timeoutMs: Long = 5_000L): LocationFix?
}

/** Ubicación de alta precisión con FusedLocationProviderClient, viva mientras la pantalla de captura está abierta. */
class LocationTracker(private val context: Context) : LocationSource {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val _fix = MutableStateFlow<LocationFix?>(null)
    override val fix: StateFlow<LocationFix?> = _fix.asStateFlow()
    private var active = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::publish)
        }
    }

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun isGpsEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isLocationEnabled
    }

    @SuppressLint("MissingPermission")
    override fun start() {
        if (active || !hasPermission()) return
        active = true
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .build()
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            client.lastLocation.addOnSuccessListener { it?.let(::publish) }
        } catch (_: SecurityException) {
            active = false
        }
    }

    override fun stop() {
        if (!active) return
        active = false
        client.removeLocationUpdates(callback)
    }

    /** Pide una ubicación nueva (hasta [timeoutMs]). Null si no hay permiso, GPS o señal. */
    @SuppressLint("MissingPermission")
    override suspend fun currentOnce(timeoutMs: Long): LocationFix? {
        if (!hasPermission()) return null
        return try {
            val req = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(timeoutMs)
                .setMaxUpdateAgeMillis(10_000L)
                .build()
            client.getCurrentLocation(req, null).await()?.also(::publish)?.let(::toFix)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun toFix(l: Location) = LocationFix(
        l.latitude, l.longitude, if (l.hasAccuracy()) l.accuracy else Float.MAX_VALUE,
        l.elapsedRealtimeNanos / 1_000_000L,
    )

    private fun publish(l: Location) {
        _fix.value = toFix(l)
    }
}
