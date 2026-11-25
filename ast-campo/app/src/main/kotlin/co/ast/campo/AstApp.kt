package co.ast.campo

import android.app.Application
import androidx.annotation.VisibleForTesting
import co.ast.campo.data.AstDatabase
import co.ast.campo.data.AstRepository
import co.ast.campo.data.SettingsRepository
import co.ast.campo.data.WeatherMode
import co.ast.campo.media.CaptureManager
import co.ast.campo.media.cleanOldCaptureTemporaries
import co.ast.campo.media.GalleryStore
import co.ast.campo.media.LocationSource
import co.ast.campo.media.LocationTracker
import co.ast.campo.media.PhotoProcessor
import co.ast.campo.weather.OpenMeteoProvider
import co.ast.campo.weather.TemperatureResolver
import co.ast.campo.weather.WeatherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Contenedor de dependencias de la app (sin inyección de dependencias externa: son pocas piezas). */
class AstApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { AstDatabase.create(this) }
    val settings by lazy { SettingsRepository(this) }
    val gallery by lazy { GalleryStore(this) }
    private val realTracker by lazy { LocationTracker(this) }

    /** Solo pruebas: permite sustituir la ubicación real (servicios de Google) por una simulada. */
    @VisibleForTesting
    var trackerOverride: LocationSource? = null
    val tracker: LocationSource get() = trackerOverride ?: realTracker
    val repository by lazy { AstRepository(database, gallery, settings) }

    val temperature by lazy {
        TemperatureResolver(provider = ::currentProvider, store = settings)
    }

    val processor by lazy { PhotoProcessor(this, database, gallery, settings, tracker, temperature) }
    val captureManager by lazy { CaptureManager(this, processor, tracker, appScope) }

    /** Proveedor de temperatura según Ajustes. Sin clave en modo comercial no se consulta (no se cae al gratuito en silencio). */
    private fun currentProvider(): WeatherProvider? {
        val s = settings.settings.value
        return when (s.weatherMode) {
            WeatherMode.OFF -> null
            WeatherMode.FREE -> OpenMeteoProvider(null)
            WeatherMode.COMMERCIAL -> s.weatherApiKey.takeIf { it.isNotBlank() }?.let { OpenMeteoProvider(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch(Dispatchers.IO) { cleanOldCaptureTemporaries(this@AstApp) }
    }
}
