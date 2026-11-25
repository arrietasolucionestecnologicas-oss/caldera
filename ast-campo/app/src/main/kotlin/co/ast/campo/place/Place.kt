package co.ast.campo.place

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import co.ast.campo.core.TemperatureCachePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Nombre del sitio donde se tomó la foto, a partir de las coordenadas. */
interface PlaceProvider {
    /** Texto corto ("Calle 72 #45-20, Prado, Barranquilla") o null si no hay dato. Lanza excepción si falla la consulta. */
    suspend fun nameFor(lat: Double, lon: Double): String?
}

/** Partes de una dirección (las que entrega android.location.Address). */
data class PlaceParts(
    val feature: String?,
    val street: String?,
    val streetNumber: String?,
    val subLocality: String?,
    val locality: String?,
    val addressLine: String?,
)

object PlaceFormatter {
    private fun clean(s: String?) = s?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * "Sitio o calle, barrio, ciudad". El nombre de sitio (featureName) solo se usa si es un nombre de verdad, no un
     * número de portal ni la misma calle. Importante: el geocodificador de Android da direcciones y algunos sitios
     * conocidos; NO da la lista de lugares cercanos que muestra WhatsApp o Google Maps.
     */
    fun format(p: PlaceParts): String? {
        val streetName = clean(p.street)
        val street = streetName?.let { s -> clean(p.streetNumber)?.let { "$s #$it" } ?: s }
        val feature = clean(p.feature)?.takeIf { f ->
            f.any { it.isLetter() } && !f.equals(streetName, ignoreCase = true) && street?.contains(f, ignoreCase = true) != true
        }
        val parts = listOfNotNull(feature ?: street, clean(p.subLocality), clean(p.locality))
        val distinct = ArrayList<String>()
        parts.forEach { x -> if (distinct.none { it.equals(x, ignoreCase = true) }) distinct.add(x) }
        val line = distinct.joinToString(", ")
        return line.ifEmpty { clean(p.addressLine)?.take(70) }
    }
}

data class PlaceReading(val name: String, val lat: Double, val lon: Double, val atMillis: Long)

interface PlaceCacheStore {
    fun loadPlace(): PlaceReading?
    fun savePlace(reading: PlaceReading)
}

/** El nombre guardado vale si es de hace menos de 24 h y de menos de 500 m (así una ráfaga en el mismo sitio no repite la consulta). */
object PlaceCachePolicy {
    const val MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L
    const val MAX_DISTANCE_METERS = 500.0

    fun usable(cached: PlaceReading?, lat: Double, lon: Double, nowMillis: Long): PlaceReading? {
        if (cached == null) return null
        val age = nowMillis - cached.atMillis
        if (age < 0 || age >= MAX_AGE_MILLIS) return null
        return if (TemperatureCachePolicy.distanceMeters(cached.lat, cached.lon, lat, lon) < MAX_DISTANCE_METERS) cached else null
    }
}

class PlaceResolver(
    private val provider: () -> PlaceProvider?,
    private val store: PlaceCacheStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun resolve(lat: Double?, lon: Double?): String? {
        if (lat == null || lon == null) return null
        PlaceCachePolicy.usable(store.loadPlace(), lat, lon, now())?.let { return it.name }
        val p = provider() ?: return null
        return try {
            p.nameFor(lat, lon)?.also { store.savePlace(PlaceReading(it, lat, lon, now())) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null // sin internet o sin servicio: la foto sale sin línea de lugar
        }
    }
}

/**
 * Geocodificador de Android. En los Samsung lo atiende el servicio de ubicación de Google, así que las
 * coordenadas se envían a Google para esta consulta (se avisa en Ajustes).
 */
class AndroidGeocoderProvider(context: Context) : PlaceProvider {
    private val appContext = context.applicationContext
    private val locale = Locale.Builder().setLanguage("es").setRegion("CO").build()

    override suspend fun nameFor(lat: Double, lon: Double): String? = withTimeout(TIMEOUT_MS) {
        if (!Geocoder.isPresent()) throw IOException("Sin servicio de geocodificación")
        val geocoder = Geocoder(appContext, locale)
        val addresses: List<Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses)
                    override fun onError(errorMessage: String?) = cont.resumeWithException(IOException(errorMessage ?: "Error de geocodificación"))
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lon, 1).orEmpty()
            }
        }
        addresses.firstOrNull()?.let {
            PlaceFormatter.format(
                PlaceParts(it.featureName, it.thoroughfare, it.subThoroughfare, it.subLocality, it.locality, it.getAddressLine(0)),
            )
        }
    }

    private companion object {
        const val TIMEOUT_MS = 6_000L
    }
}
