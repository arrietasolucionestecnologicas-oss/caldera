package co.ast.campo.weather

import co.ast.campo.core.TempReading
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeStore(var value: TempReading? = null) : TempCacheStore {
    override fun load() = value
    override fun save(reading: TempReading) { value = reading }
}

private class FakeProvider(private val result: Result<Double>) : WeatherProvider {
    var calls = 0
    override val id = "fake"
    override val displayName = "fake"
    override suspend fun currentTemperature(lat: Double, lon: Double): Double { calls++; return result.getOrThrow() }
}

class WeatherTest {
    private val now = 5_000_000_000L
    private val minute = 60_000L
    private val lat = 10.9685
    private val lon = -74.7813

    @Test fun parsesOpenMeteoResponse() {
        val body = """{"latitude":10.96,"current_units":{"temperature_2m":"°C"},"current":{"time":"2026-11-25T14:00","interval":900,"temperature_2m":31.4}}"""
        assertEquals(31.4, OpenMeteoProvider.parseTemperature(body), 0.0001)
    }

    @Test(expected = IllegalStateException::class)
    fun errorResponseThrows() {
        OpenMeteoProvider.parseTemperature("""{"error":true,"reason":"Invalid API key"}""")
    }

    @Test(expected = Exception::class)
    fun missingFieldsThrow() {
        OpenMeteoProvider.parseTemperature("""{"current":{}}""")
    }

    @Test fun urlUsesFreeEndpointWithoutKeyAndTwoDecimals() {
        val url = OpenMeteoProvider(null).buildUrl(10.96854, -74.78132)
        assertEquals("https://api.open-meteo.com/v1/forecast?latitude=10.97&longitude=-74.78&current=temperature_2m", url)
    }

    @Test fun urlUsesCommercialEndpointWithKey() {
        val url = OpenMeteoProvider("abc 123").buildUrl(10.0, -74.0)
        assertTrue(url, url.startsWith("https://customer-api.open-meteo.com/v1/forecast?"))
        assertTrue(url, url.endsWith("&apikey=abc+123"))
    }

    @Test fun manualBeatsEverythingAndSkipsNetwork() = runTest {
        val p = FakeProvider(Result.success(20.0))
        val r = TemperatureResolver({ p }, FakeStore(), { now }).resolve(lat, lon, 35.0)
        assertEquals(TempResult(35.0, TempSource.MANUAL), r)
        assertEquals(0, p.calls)
    }

    @Test fun networkValueIsReturnedAndCached() = runTest {
        val store = FakeStore()
        val r = TemperatureResolver({ FakeProvider(Result.success(29.5)) }, store, { now }).resolve(lat, lon, null)
        assertEquals(TempResult(29.5, TempSource.NETWORK), r)
        assertEquals(29.5, store.value!!.celsius, 0.0)
        assertEquals(now, store.value!!.atMillis)
    }

    @Test fun veryFreshCacheAvoidsNewRequest() = runTest {
        val store = FakeStore(TempReading(28.0, lat, lon, now - 3 * minute))
        val p = FakeProvider(Result.success(40.0))
        val r = TemperatureResolver({ p }, store, { now }).resolve(lat, lon, null)
        assertEquals(TempResult(28.0, TempSource.CACHE), r)
        assertEquals(0, p.calls)
    }

    @Test fun offlineFallsBackToCacheUnderSixtyMinutesAndTenKm() = runTest {
        val store = FakeStore(TempReading(27.0, lat, lon, now - 40 * minute))
        val r = TemperatureResolver({ FakeProvider(Result.failure(java.io.IOException("offline"))) }, store, { now })
            .resolve(lat + 0.03, lon, null)
        assertEquals(TempResult(27.0, TempSource.CACHE), r)
    }

    @Test fun offlineWithOldCacheShowsNothing() = runTest {
        val store = FakeStore(TempReading(27.0, lat, lon, now - 61 * minute))
        val r = TemperatureResolver({ FakeProvider(Result.failure(java.io.IOException("offline"))) }, store, { now })
            .resolve(lat, lon, null)
        assertEquals(TempResult(null, TempSource.NONE), r)
    }

    @Test fun offlineWithFarCacheShowsNothing() = runTest {
        val store = FakeStore(TempReading(27.0, lat, lon, now - 20 * minute))
        val r = TemperatureResolver({ FakeProvider(Result.failure(java.io.IOException("offline"))) }, store, { now })
            .resolve(lat + 0.2, lon, null)
        assertNull(r.celsius)
        assertEquals(TempSource.NONE, r.source)
    }

    @Test fun noProviderConfiguredUsesOnlyCache() = runTest {
        val store = FakeStore(TempReading(26.0, lat, lon, now - 30 * minute))
        val r = TemperatureResolver({ null }, store, { now }).resolve(lat, lon, null)
        assertEquals(TempResult(26.0, TempSource.CACHE), r)
    }

    @Test fun withoutLocationThereIsNoAutomaticTemperature() = runTest {
        val p = FakeProvider(Result.success(30.0))
        val r = TemperatureResolver({ p }, FakeStore(TempReading(26.0, lat, lon, now)), { now }).resolve(null, null, null)
        assertEquals(TempSource.NONE, r.source)
        assertEquals(0, p.calls)
    }
}
