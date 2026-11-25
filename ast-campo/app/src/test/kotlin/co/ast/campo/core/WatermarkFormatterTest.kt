package co.ast.campo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class WatermarkFormatterTest {
    private val zone = ZoneId.of("America/Bogota")

    @Test fun dateTimeFormatIsDdMmYyyyHhMm() {
        val t = ZonedDateTime.of(2026, 3, 7, 9, 5, 59, 0, zone).toInstant().toEpochMilli()
        assertEquals("07/03/2026 09:05", WatermarkFormatter.dateTime(t, zone))
    }

    @Test fun coordinatesHaveSixDecimalsWithDotSeparator() {
        assertEquals("10.968540, -74.781320", WatermarkFormatter.coordinates(10.96854, -74.78132))
        assertEquals("0.000001, -0.000000".replace("-0.000000", "0.000000"), WatermarkFormatter.coordinates(0.000001, 0.0))
    }

    @Test fun temperatureRoundsAndShowsDashesWhenMissing() {
        assertEquals("31 °C", WatermarkFormatter.temperature(30.6))
        assertEquals("30 °C", WatermarkFormatter.temperature(30.4))
        assertEquals("--", WatermarkFormatter.temperature(null))
    }

    @Test fun dataLineMatchesSpecExample() {
        assertEquals(
            "10.968540, -74.781320   31 °C",
            WatermarkFormatter.dataLine(10.96854, -74.78132, 31.0, showCoords = true, showTemp = true),
        )
    }

    @Test fun dataLineRespectsToggles() {
        assertEquals("31 °C", WatermarkFormatter.dataLine(1.0, 2.0, 31.0, showCoords = false, showTemp = true))
        assertEquals("1.000000, 2.000000", WatermarkFormatter.dataLine(1.0, 2.0, 31.0, showCoords = true, showTemp = false))
        assertNull(WatermarkFormatter.dataLine(1.0, 2.0, 31.0, showCoords = false, showTemp = false))
    }

    @Test fun dataLineWithoutLocationSaysSo() {
        assertEquals("Sin ubicación   --", WatermarkFormatter.dataLine(null, null, null, true, true))
    }

    @Test fun routeFitsUntouched() {
        val r = WatermarkFormatter.route(listOf("Granja Solar X", "Pruebas eléctricas", "CT 5")) { it.length <= 80 }
        assertEquals("Granja Solar X › Pruebas eléctricas › CT 5", r)
    }

    @Test fun routeAbbreviatesKeepingProjectAndLastLevels() {
        val segs = listOf("Granja Solar X", "Pruebas eléctricas", "Zona norte", "CT 5")
        val r = WatermarkFormatter.route(segs) { it.length <= 38 }
        assertEquals("Granja Solar X › … › Zona norte › CT 5", r.takeIf { it.length <= 38 } ?: r)
        assertTrue(r.startsWith("Granja Solar X"))
        assertTrue(r.endsWith("CT 5"))
        assertTrue(r.contains("…"))
    }

    @Test fun routeDropsMoreLevelsWhenNeeded() {
        val segs = listOf("Granja Solar X", "Pruebas eléctricas", "Zona norte", "CT 5")
        val r = WatermarkFormatter.route(segs) { it.length <= 28 }
        assertEquals("Granja Solar X › … › CT 5", r)
    }

    @Test fun routeTruncatesLastLevelAsLastResort() {
        val segs = listOf("Proyecto", "Banco de condensadores muy largo")
        val r = WatermarkFormatter.route(segs) { it.length <= 20 }
        assertTrue(r, r.length <= 20)
        assertTrue(r, r.startsWith("Proyecto › "))
        assertTrue(r, r.endsWith("…"))
    }

    @Test fun singleSegmentTruncatesWithEllipsis() {
        val r = WatermarkFormatter.route(listOf("Un proyecto con nombre larguísimo")) { it.length <= 12 }
        assertTrue(r, r.length <= 12)
        assertTrue(r, r.endsWith("…"))
    }
}
