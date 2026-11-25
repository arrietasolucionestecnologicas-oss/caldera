package co.ast.campo.place

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

private class MemStore(var value: PlaceReading? = null) : PlaceCacheStore {
    override fun loadPlace() = value
    override fun savePlace(reading: PlaceReading) { value = reading }
}

private class FakePlaces(private val result: Result<String?>) : PlaceProvider {
    var calls = 0
    override suspend fun nameFor(lat: Double, lon: Double): String? { calls++; return result.getOrThrow() }
}

class PlaceTest {
    private val now = 10_000_000_000L
    private val lat = 10.9685
    private val lon = -74.7813

    @Test fun formatsStreetNeighborhoodAndCity() {
        val p = PlaceParts("72", "Calle 72", "45-20", "Prado", "Barranquilla", "Calle 72 #45-20, Barranquilla")
        assertEquals("Calle 72 #45-20, Prado, Barranquilla", PlaceFormatter.format(p))
    }

    @Test fun usesRealFeatureNameInsteadOfStreet() {
        val p = PlaceParts("Estadio Metropolitano", "Calle 45", null, "Barranquillita", "Barranquilla", null)
        assertEquals("Estadio Metropolitano, Barranquillita, Barranquilla", PlaceFormatter.format(p))
    }

    @Test fun ignoresNumericOrRepeatedFeatureNames() {
        assertEquals("Calle 72, Barranquilla", PlaceFormatter.format(PlaceParts("45-20", "Calle 72", null, null, "Barranquilla", null)))
        assertEquals("Calle 72, Barranquilla", PlaceFormatter.format(PlaceParts("Calle 72", "Calle 72", null, null, "Barranquilla", null)))
    }

    @Test fun removesDuplicatesIgnoringCase() {
        assertEquals("Barranquilla", PlaceFormatter.format(PlaceParts(null, null, null, "Barranquilla", "barranquilla", null)))
    }

    @Test fun fallsBackToAddressLineAndThenNull() {
        assertEquals("Atlántico, Colombia", PlaceFormatter.format(PlaceParts(null, null, null, null, null, "  Atlántico, Colombia ")))
        assertNull(PlaceFormatter.format(PlaceParts(null, null, null, null, null, null)))
    }

    @Test fun cachePolicyRequiresUnder24hAnd500m() {
        val c = PlaceReading("X", lat, lon, now - 23 * 3_600_000L)
        assertEquals(c, PlaceCachePolicy.usable(c, lat + 0.002, lon, now)) // ~220 m
        assertNull(PlaceCachePolicy.usable(c, lat + 0.01, lon, now)) // ~1,1 km
        assertNull(PlaceCachePolicy.usable(c.copy(atMillis = now - 25 * 3_600_000L), lat, lon, now))
        assertNull(PlaceCachePolicy.usable(null, lat, lon, now))
    }

    @Test fun resolverQueriesThenReusesCacheForNearbyPhotos() = runTest {
        val p = FakePlaces(Result.success("Calle 72, Barranquilla"))
        val r = PlaceResolver({ p }, MemStore(), { now })
        assertEquals("Calle 72, Barranquilla", r.resolve(lat, lon))
        assertEquals("Calle 72, Barranquilla", r.resolve(lat + 0.001, lon))
        assertEquals(1, p.calls)
    }

    @Test fun resolverReturnsNullOfflineWithoutCache() = runTest {
        val r = PlaceResolver({ FakePlaces(Result.failure(IOException("offline"))) }, MemStore(), { now })
        assertNull(r.resolve(lat, lon))
    }

    @Test fun resolverUsesCacheWhenOfflineNearby() = runTest {
        val store = MemStore(PlaceReading("Prado, Barranquilla", lat, lon, now - 3_600_000L))
        val r = PlaceResolver({ FakePlaces(Result.failure(IOException("offline"))) }, store, { now })
        assertEquals("Prado, Barranquilla", r.resolve(lat, lon))
    }

    @Test fun resolverNeedsCoordinatesAndAProvider() = runTest {
        assertNull(PlaceResolver({ FakePlaces(Result.success("X")) }, MemStore(), { now }).resolve(null, null))
        assertNull(PlaceResolver({ null }, MemStore(), { now }).resolve(lat, lon))
    }
}
