package co.ast.campo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ExifDatesTest {
    private val bogota = ZoneId.of("America/Bogota")

    @Test fun parsesWithExplicitOffsetIgnoringDeviceZone() {
        val expected = ZonedDateTime.of(2026, 11, 25, 19, 5, 9, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals(expected, ExifDates.parse("2026:11:25 14:05:09", "-05:00", ZoneId.of("Asia/Tokyo")))
    }

    @Test fun usesDeviceZoneWhenThereIsNoOffset() {
        val expected = ZonedDateTime.of(2026, 11, 25, 14, 5, 9, 0, bogota).toInstant().toEpochMilli()
        assertEquals(expected, ExifDates.parse("2026:11:25 14:05:09", null, bogota))
        assertEquals(expected, ExifDates.parse(" 2026:11:25 14:05:09 ", "", bogota))
    }

    @Test fun invalidOrMissingReturnsNull() {
        assertNull(ExifDates.parse(null, null, bogota))
        assertNull(ExifDates.parse("", null, bogota))
        assertNull(ExifDates.parse("0000:00:00 00:00:00", null, bogota))
        assertNull(ExifDates.parse("hola", null, bogota))
    }

    @Test fun invalidOffsetFallsBackToDeviceZone() {
        val expected = ZonedDateTime.of(2026, 1, 2, 3, 4, 5, 0, bogota).toInstant().toEpochMilli()
        assertEquals(expected, ExifDates.parse("2026:01:02 03:04:05", "basura", bogota))
    }

    @Test fun formatAndOffsetRoundTrip() {
        val t = ZonedDateTime.of(2026, 3, 7, 9, 5, 59, 0, bogota).toInstant().toEpochMilli()
        assertEquals("2026:03:07 09:05:59", ExifDates.format(t, bogota))
        assertEquals("-05:00", ExifDates.offset(t, bogota))
        assertEquals(t, ExifDates.parse(ExifDates.format(t, bogota), ExifDates.offset(t, bogota), bogota))
    }

    @Test fun utcOffsetIsWrittenAsPlusZero() {
        assertEquals("+00:00", ExifDates.offset(0L, ZoneId.of("UTC")))
    }
}
