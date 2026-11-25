package co.ast.campo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPathsTest {
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

    @Test fun fileNameIsPathPlusFolderSequence() {
        assertEquals(
            "Proyecto Y_Subestación 1_Transformadores_007.jpg",
            MediaPaths.fileName(listOf("Proyecto Y", "Subestación 1", "Transformadores"), 7),
        )
        assertEquals("Granja Solar X_Pruebas eléctricas_CT 5_001.jpg", MediaPaths.fileName(listOf("Granja Solar X", "Pruebas eléctricas", "CT 5"), 1))
    }

    @Test fun sequenceHasAtLeastThreeDigitsSoItSortsOnTheComputer() {
        assertTrue(MediaPaths.fileName(listOf("A"), 1234).endsWith("_1234.jpg"))
        assertTrue(MediaPaths.fileName(listOf("A"), 99).endsWith("_099.jpg"))
        assertTrue(MediaPaths.fileName(listOf("A"), 0).endsWith("_001.jpg"))
        assertTrue(MediaPaths.fileName(listOf("A"), -5).endsWith("_001.jpg"))
    }

    @Test fun longNamesAreCutInTheMiddleAndKeepSequence() {
        val segs = listOf("Proyecto " + "Largo".repeat(10), "Subestación 12", "Banco de condensadores", "Celda " + "X".repeat(30))
        val name = MediaPaths.fileName(segs, 12)
        assertTrue("longitud ${name.length}", name.length <= 120)
        assertTrue(name, name.endsWith("_012.jpg"))
        assertTrue(name, name.startsWith("Proyecto "))
        assertTrue(name, name.contains("..."))
        assertTrue(name, name.substringBefore("_012").endsWith("X"))
    }

    @Test fun shortNamesAreNotCut() {
        val name = MediaPaths.fileName(listOf("A", "B"), 3)
        assertEquals("A_B_003.jpg", name)
    }

    @Test fun sequenceIsReadFromNewAndOldStyleNames() {
        assertEquals(7, MediaPaths.sequenceOf("Proyecto Y_Sub_007.jpg"))
        assertEquals(12, MediaPaths.sequenceOf("Proyecto Y_Sub_1234_012.jpg"))
        assertEquals(7, MediaPaths.sequenceOf("Proyecto Y_Sub_20261125_140509_007.jpg"))
        assertEquals(null, MediaPaths.sequenceOf("IMG_20261125.png"))
        assertEquals(null, MediaPaths.sequenceOf("foto.jpg"))
    }

    @Test fun nextSequenceIsMaxPlusOneAndNeverReusesDeletedNumbers() {
        assertEquals(1, MediaPaths.nextSequence(emptyList()))
        assertEquals(4, MediaPaths.nextSequence(listOf("P_001.jpg", "P_002.jpg", "P_003.jpg")))
        // se borró la 2 y la 3: el siguiente sigue siendo 4 (no repite 2)
        assertEquals(4, MediaPaths.nextSequence(listOf("P_001.jpg", "P_003.jpg")))
        assertEquals(1001, MediaPaths.nextSequence(listOf("P_1000.jpg")))
    }

    @Test fun freeSequenceKeepsPreferredOnlyWhenFree() {
        val names = listOf("P_001.jpg", "P_002.jpg")
        assertEquals(5, MediaPaths.freeSequence(names, 5))
        assertEquals(3, MediaPaths.freeSequence(names, 2)) // ocupado: siguiente
        assertEquals(3, MediaPaths.freeSequence(names, 0))
    }

    @Test fun middleCutKeepsHeadAndTail() {
        val cut = MediaPaths.middleCut("0123456789abcdefghij", 11)
        assertEquals(11, cut.length)
        assertTrue(cut.startsWith("0123"))
        assertTrue(cut.endsWith("ghij"))
        assertTrue(cut.contains("..."))
    }
}
