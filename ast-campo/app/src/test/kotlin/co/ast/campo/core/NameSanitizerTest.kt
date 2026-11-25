package co.ast.campo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameSanitizerTest {
    @Test fun removesForbiddenCharacters() {
        assertEquals("CT 5 Norte", NameSanitizer.sanitize("CT 5: Norte"))
        assertEquals("ab", NameSanitizer.sanitize("a/b\\*?\"<>|"))
        assertEquals("Granja Solar X", NameSanitizer.sanitize("Granja *Solar* X"))
    }

    @Test fun keepsAccentsAndEnye() {
        assertEquals("Subestación Año Pequeño", NameSanitizer.sanitize("Subestación Año Pequeño"))
    }

    @Test fun normalizesDecomposedAccentsToNfc() {
        val decomposed = "Subestación"
        assertEquals("Subestación", NameSanitizer.sanitize(decomposed))
    }

    @Test fun trimsSpacesAndDots() {
        assertEquals("Tableros", NameSanitizer.sanitize("  Tableros . . "))
        assertEquals("Tableros", NameSanitizer.sanitize("...Tableros..."))
        assertEquals("", NameSanitizer.sanitize(".."))
        assertEquals("", NameSanitizer.sanitize("   "))
        assertEquals("", NameSanitizer.sanitize("///"))
    }

    @Test fun collapsesInnerWhitespace() {
        assertEquals("Banco de condensadores", NameSanitizer.sanitize("Banco   de\tcondensadores"))
    }

    @Test fun collapsesSpacesLeftByRemovedSymbols() {
        assertEquals("CT 5 Norte", NameSanitizer.sanitize("CT 5 : Norte"))
    }

    @Test fun limitsLengthAndRetrimsEdges() {
        val long = "a".repeat(100)
        assertEquals(60, NameSanitizer.sanitize(long).length)
        assertEquals("abc", NameSanitizer.sanitize("abc. def", maxLength = 4))
    }

    @Test fun doesNotSplitSurrogatePairs() {
        val s = "ab" + "😀" // emoji tras dos letras
        assertEquals("ab", NameSanitizer.sanitize(s, maxLength = 3))
    }

    @Test fun duplicatesIgnoreCase() {
        assertTrue(NameSanitizer.isDuplicate("transformadores", listOf("Tableros", "Transformadores")))
        assertFalse(NameSanitizer.isDuplicate("Transformador", listOf("Transformadores")))
        // Las tildes sí distinguen: "Subestacion" y "Subestación" son nombres distintos.
        assertFalse(NameSanitizer.isDuplicate("Subestacion 1", listOf("Subestación 1")))
    }

    @Test fun searchKeyIgnoresAccentsAndCase() {
        assertEquals("subestacion norte", NameSanitizer.searchKey("Subestación NORTE"))
        assertEquals("ano", NameSanitizer.searchKey("Año"))
    }
}
