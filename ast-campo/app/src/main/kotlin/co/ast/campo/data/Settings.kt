package co.ast.campo.data

import android.content.Context
import android.content.SharedPreferences
import co.ast.campo.core.FolderTemplates
import co.ast.campo.core.TempReading
import co.ast.campo.place.PlaceCacheStore
import co.ast.campo.place.PlaceReading
import co.ast.campo.weather.TempCacheStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Corner { BOTTOM_LEFT, BOTTOM_RIGHT, TOP_LEFT, TOP_RIGHT }
enum class WatermarkSize { SMALL, MEDIUM, LARGE }
enum class ThemeMode { DARK, LIGHT_HC }
enum class WeatherMode { FREE, COMMERCIAL, OFF }

data class WatermarkSettings(
    val showRoute: Boolean = true,
    val showDateTime: Boolean = true,
    val showCoords: Boolean = true,
    val showTemp: Boolean = true,
    val showPlace: Boolean = true,
    /** Panel azul marino con barra naranja detrás del texto. Apagado: solo letras con sombra suave. */
    val background: Boolean = false,
    val corner: Corner = Corner.BOTTOM_LEFT,
    val size: WatermarkSize = WatermarkSize.MEDIUM,
    /** Opacidad del fondo azul marino, 30 a 90 %. */
    val opacityPct: Int = 60,
    val company: String = "",
)

data class AppSettings(
    val watermark: WatermarkSettings = WatermarkSettings(),
    val saveOriginal: Boolean = true,
    val theme: ThemeMode = ThemeMode.DARK,
    val weatherMode: WeatherMode = WeatherMode.FREE,
    val weatherApiKey: String = "",
    val substationTemplate: List<String> = FolderTemplates.DEFAULT_SUBSTATION_CHILDREN,
)

/** Ajustes y pequeños estados persistentes. SharedPreferences basta: son pocos valores y no hay cuentas. */
class SettingsRepository(context: Context) : TempCacheStore, PlaceCacheStore {
    private val prefs: SharedPreferences = context.getSharedPreferences("ast_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private inline fun <reified E : Enum<E>> enumOf(key: String, default: E): E =
        prefs.getString(key, null)?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    private fun read(): AppSettings {
        val d = AppSettings()
        val dw = WatermarkSettings()
        val rawTemplate = prefs.getString(K_TEMPLATE, null)
        val template = when {
            rawTemplate == null -> d.substationTemplate
            rawTemplate.isEmpty() -> emptyList()
            else -> rawTemplate.split('\n')
        }
        return AppSettings(
            watermark = WatermarkSettings(
                showRoute = prefs.getBoolean("wm_route", dw.showRoute),
                showDateTime = prefs.getBoolean("wm_dt", dw.showDateTime),
                showCoords = prefs.getBoolean("wm_coords", dw.showCoords),
                showTemp = prefs.getBoolean("wm_temp", dw.showTemp),
                showPlace = prefs.getBoolean("wm_place", dw.showPlace),
                background = prefs.getBoolean("wm_bg", dw.background),
                corner = enumOf("wm_corner", dw.corner),
                size = enumOf("wm_size", dw.size),
                opacityPct = prefs.getInt("wm_opacity", dw.opacityPct).coerceIn(30, 90),
                company = prefs.getString("wm_company", dw.company).orEmpty(),
            ),
            saveOriginal = prefs.getBoolean("save_original", d.saveOriginal),
            theme = enumOf("theme", d.theme),
            weatherMode = enumOf("weather_mode", d.weatherMode),
            weatherApiKey = prefs.getString("weather_key", "").orEmpty(),
            substationTemplate = template,
        )
    }

    fun update(change: (AppSettings) -> AppSettings) {
        val s = change(_settings.value)
        prefs.edit()
            .putBoolean("wm_route", s.watermark.showRoute)
            .putBoolean("wm_dt", s.watermark.showDateTime)
            .putBoolean("wm_coords", s.watermark.showCoords)
            .putBoolean("wm_temp", s.watermark.showTemp)
            .putBoolean("wm_place", s.watermark.showPlace)
            .putBoolean("wm_bg", s.watermark.background)
            .putString("wm_corner", s.watermark.corner.name)
            .putString("wm_size", s.watermark.size.name)
            .putInt("wm_opacity", s.watermark.opacityPct)
            .putString("wm_company", s.watermark.company)
            .putBoolean("save_original", s.saveOriginal)
            .putString("theme", s.theme.name)
            .putString("weather_mode", s.weatherMode.name)
            .putString("weather_key", s.weatherApiKey)
            .putString(K_TEMPLATE, s.substationTemplate.joinToString("\n"))
            .apply()
        _settings.value = s
    }

    // ---- Último destino por proyecto ----
    fun lastDestination(projectId: Long): Long? =
        prefs.getLong("dest_$projectId", -1L).takeIf { it >= 0 }

    fun setLastDestination(projectId: Long, folderId: Long) {
        prefs.edit().putLong("dest_$projectId", folderId).apply()
    }

    // ---- Temperatura manual (caduca a las 3 h para no quedarse puesta por error) ----
    fun manualTemperature(now: Long = System.currentTimeMillis()): Double? {
        val at = prefs.getLong("manual_temp_at", 0L)
        if (!prefs.contains("manual_temp") || now - at !in 0 until MANUAL_TEMP_TTL_MS) return null
        return prefs.getFloat("manual_temp", 0f).toDouble()
    }

    fun setManualTemperature(celsius: Double?, now: Long = System.currentTimeMillis()) {
        prefs.edit().apply {
            if (celsius == null) remove("manual_temp").remove("manual_temp_at")
            else putFloat("manual_temp", celsius.toFloat()).putLong("manual_temp_at", now)
        }.apply()
    }

    // ---- Caché de la última temperatura obtenida de internet ----
    override fun load(): TempReading? {
        if (!prefs.contains("tc_t")) return null
        return TempReading(
            Double.fromBits(prefs.getLong("tc_t", 0)), Double.fromBits(prefs.getLong("tc_lat", 0)),
            Double.fromBits(prefs.getLong("tc_lon", 0)), prefs.getLong("tc_at", 0),
        )
    }

    override fun save(reading: TempReading) {
        prefs.edit()
            .putLong("tc_t", reading.celsius.toBits()).putLong("tc_lat", reading.lat.toBits())
            .putLong("tc_lon", reading.lon.toBits()).putLong("tc_at", reading.atMillis).apply()
    }

    // ---- Caché del último nombre de lugar ----
    override fun loadPlace(): PlaceReading? {
        val name = prefs.getString("pc_name", null) ?: return null
        return PlaceReading(
            name, Double.fromBits(prefs.getLong("pc_lat", 0)), Double.fromBits(prefs.getLong("pc_lon", 0)), prefs.getLong("pc_at", 0),
        )
    }

    override fun savePlace(reading: PlaceReading) {
        prefs.edit()
            .putString("pc_name", reading.name).putLong("pc_lat", reading.lat.toBits())
            .putLong("pc_lon", reading.lon.toBits()).putLong("pc_at", reading.atMillis).apply()
    }

    private companion object {
        const val K_TEMPLATE = "substation_template"
        const val MANUAL_TEMP_TTL_MS = 3 * 60 * 60 * 1000L
    }
}
