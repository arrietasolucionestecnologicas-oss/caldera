package co.ast.campo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TemperatureCacheTest {
    private val now = 1_000_000_000_000L
    private val minute = 60_000L
    private val reading = TempReading(31.0, 10.96854, -74.78132, now - 30 * minute)

    @Test fun usableWhenRecentAndNear() {
        assertNotNull(TemperatureCachePolicy.usable(reading, 10.97, -74.78, now))
    }

    @Test fun notUsableAtSixtyMinutesOrMore() {
        val old = reading.copy(atMillis = now - 60 * minute)
        assertNull(TemperatureCachePolicy.usable(old, 10.96854, -74.78132, now))
        val almost = reading.copy(atMillis = now - 59 * minute)
        assertNotNull(TemperatureCachePolicy.usable(almost, 10.96854, -74.78132, now))
    }

    @Test fun notUsableBeyondTenKilometers() {
        // ~0.1 grado de latitud = ~11.1 km
        assertNull(TemperatureCachePolicy.usable(reading, 11.07, -74.78132, now))
        // ~0.05 grado = ~5.5 km
        assertNotNull(TemperatureCachePolicy.usable(reading, 11.02, -74.78132, now))
    }

    @Test fun notUsableWithoutCurrentLocationOrCache() {
        assertNull(TemperatureCachePolicy.usable(reading, null, null, now))
        assertNull(TemperatureCachePolicy.usable(null, 10.0, -74.0, now))
    }

    @Test fun futureTimestampIsRejected() {
        assertNull(TemperatureCachePolicy.usable(reading.copy(atMillis = now + minute), 10.97, -74.78, now))
    }

    @Test fun haversineKnownDistance() {
        // Barranquilla a Cartagena (aprox. 100 km en línea recta)
        val d = TemperatureCachePolicy.distanceMeters(10.9639, -74.7964, 10.3910, -75.4794)
        assertEquals(100_000.0, d, 6_000.0)
        assertEquals(0.0, TemperatureCachePolicy.distanceMeters(1.0, 2.0, 1.0, 2.0), 0.001)
    }

    @Test fun freshRequiresTenMinutesAndOneKilometer() {
        val recent = reading.copy(atMillis = now - 5 * minute)
        assertNotNull(TemperatureCachePolicy.fresh(recent, 10.96854, -74.78132, now))
        assertNull(TemperatureCachePolicy.fresh(reading, 10.96854, -74.78132, now)) // 30 min
        assertNull(TemperatureCachePolicy.fresh(recent, 10.99, -74.78132, now)) // ~2.3 km
    }
}
