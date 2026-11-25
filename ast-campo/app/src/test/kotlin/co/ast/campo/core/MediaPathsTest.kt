package co.ast.campo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MediaPathsTest {
    private val zone = ZoneId.of("America/Bogota")
    private fun millis(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int) =
        ZonedDateTime.of(y, mo, d, h, mi, s, 0, zone).toInstant().toEpochMilli()

    @Test fun relativePathEndsWithSlash() {
        assertEquals(
            "Pictures/AST Campo/Granja Solar X/Pruebas eléctricas/CT 5/",
            MediaPaths.relativePath(listOf("Granja Solar X", "Pruebas eléctricas", "CT 5")),
        )
        assertEquals("Pictures/AST Campo/Proyecto Y/", MediaPaths.relativePath(listOf("Proyecto Y")))
    }

    @Test fun originalPathIsRelativeJoin() {
        assertEquals("Proyecto Y/Subestación 1", MediaPaths.originalRelativePath(listOf("Proyecto Y", "Subestación 1")))
    }

    @Test fun fileNameUsesPathTimestampAndSequence() {
        val name = MediaPaths.fileName(
            listOf("Proyecto Y", "Subestación 1", "Transformadores"),
            millis(2026, 11, 25, 14, 5, 9), zone, 7,
        )
        assertEquals("Proyecto Y_Subestación 1_Transformadores_20261125_140509_007.jpg", name)
    }

    @Test fun fileNameConvertsToLocalTimeZone() {
        // 2026-11-26 03:30 UTC = 25/11 22:30 en Bogotá (UTC-5)
        val utc = ZonedDateTime.of(2026, 11, 26, 3, 30, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals("P_20261125_223000_001.jpg", MediaPaths.fileName(listOf("P"), utc, zone, 1))
    }

    @Test fun longNamesAreCutInTheMiddleAndKeepSuffix() {
        val segs = listOf("Proyecto " + "Largo".repeat(10), "Subestación 12", "Banco de condensadores", "Celda " + "X".repeat(30))
        val name = MediaPaths.fileName(segs, millis(2026, 1, 2, 3, 4, 5), zone, 12)
        assertTrue("longitud ${name.length}", name.length <= 120)
        assertTrue(name, name.endsWith("_20260102_030405_012.jpg"))
        assertTrue(name, name.startsWith("Proyecto "))
        assertTrue(name, name.contains("..."))
        assertTrue(name, name.substringBefore("_2026").endsWith("X"))
    }

    @Test fun shortNamesAreNotCut() {
        val name = MediaPaths.fileName(listOf("A", "B"), 0L, zone, 0)
        assertTrue(!name.contains("..."))
        assertTrue(name.startsWith("A_B_"))
    }

    @Test fun sequenceIsAtLeastThreeDigitsAndNeverNegative() {
        assertTrue(MediaPaths.fileName(listOf("A"), 0L, zone, 1234).endsWith("_1234.jpg"))
        assertTrue(MediaPaths.fileName(listOf("A"), 0L, zone, -5).endsWith("_000.jpg"))
    }

    @Test fun middleCutKeepsHeadAndTail() {
        val cut = MediaPaths.middleCut("0123456789abcdefghij", 11)
        assertEquals(11, cut.length)
        assertTrue(cut.startsWith("0123"))
        assertTrue(cut.endsWith("ghij"))
        assertTrue(cut.contains("..."))
    }
}
