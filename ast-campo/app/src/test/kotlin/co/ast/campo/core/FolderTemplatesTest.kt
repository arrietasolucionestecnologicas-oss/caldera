package co.ast.campo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderTemplatesTest {
    @Test fun detectsSubstationIgnoringAccentsAndCase() {
        assertTrue(FolderTemplates.isSubstationName("Subestación 1"))
        assertTrue(FolderTemplates.isSubstationName("  subestacion Norte"))
        assertTrue(FolderTemplates.isSubstationName("SUBESTACIÓN"))
        assertFalse(FolderTemplates.isSubstationName("Pruebas eléctricas"))
        assertFalse(FolderTemplates.isSubstationName("Estación Subestación"))
    }

    @Test fun cleanTemplateSanitizesAndDedupes() {
        assertEquals(
            listOf("Tableros", "Banco de condensadores"),
            FolderTemplates.cleanTemplate(listOf(" Tableros ", "tableros", "", "Banco/ de condensadores", "...")),
        )
    }

    @Test fun nextNumberedContinuesTheSequence() {
        assertEquals("Subestación 4", FolderTemplates.nextNumbered(listOf("Subestación 1", "Subestación 3", "Tableros")))
        assertEquals("CT 6", FolderTemplates.nextNumbered(listOf("CT 5")))
        assertNull(FolderTemplates.nextNumbered(listOf("Tableros", "Generadores")))
        assertNull(FolderTemplates.nextNumbered(emptyList()))
    }

    @Test fun nextNumberedPrefersMostUsedPrefix() {
        assertEquals("CT 4", FolderTemplates.nextNumbered(listOf("CT 1", "CT 3", "Celda 9")))
    }
}
