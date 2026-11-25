package co.ast.campo.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class PendingStoreTest {
    private fun tmp() = File.createTempFile("cap_", ".jpg").also { it.deleteOnExit() }

    @Test fun roundTripWithAllFields() {
        val photo = tmp()
        val p = PendingCapture(photo.path, 7, 1_700_000_000_000, 10.96854, -74.78132, 8.5f, 123_456L, 31.5, 99_999L)
        PendingStore.write(p, 1)
        val (read, attempts) = PendingStore.read(PendingStore.sidecar(photo))!!
        assertEquals(p, read)
        assertEquals(1, attempts)
        PendingStore.sidecar(photo).delete()
    }

    @Test fun roundTripWithoutOptionalFields() {
        val photo = tmp()
        val p = PendingCapture(photo.path, 3, 5L, null, null, null, null, null, 10L)
        PendingStore.write(p, 0)
        val (read, attempts) = PendingStore.read(PendingStore.sidecar(photo))!!
        assertNull(read.snapshotLat)
        assertNull(read.manualTemperature)
        assertEquals(p, read)
        assertEquals(0, attempts)
        PendingStore.sidecar(photo).delete()
    }

    @Test fun corruptSidecarReadsAsNull() {
        val json = File.createTempFile("cap_", ".json").also { it.writeText("{no es json"); it.deleteOnExit() }
        assertNull(PendingStore.read(json))
    }

    @Test fun sidecarSitsNextToPhoto() {
        assertEquals("/x/cap_1.json", PendingStore.sidecar(File("/x/cap_1.jpg")).path)
    }
}
