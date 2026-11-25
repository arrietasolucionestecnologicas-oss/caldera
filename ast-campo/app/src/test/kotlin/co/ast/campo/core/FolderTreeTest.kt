package co.ast.campo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderTreeTest {
    // 1 Granja Solar X > 2 Pruebas eléctricas > 3 CT 5
    // 4 Proyecto Y > 5 Subestación 1 > (6 Transformadores, 7 Tableros) ; 8 Subestación 2
    private val tree = FolderTree(
        listOf(
            FolderNode(1, null, "Granja Solar X"), FolderNode(2, 1, "Pruebas eléctricas"), FolderNode(3, 2, "CT 5"),
            FolderNode(4, null, "Proyecto Y"), FolderNode(5, 4, "Subestación 1"),
            FolderNode(6, 5, "Transformadores"), FolderNode(7, 5, "Tableros"), FolderNode(8, 4, "Subestación 2"),
        ),
    )

    @Test fun segmentsFromProjectToFolder() {
        assertEquals(listOf("Granja Solar X", "Pruebas eléctricas", "CT 5"), tree.segments(3))
        assertEquals(listOf("Proyecto Y", "Subestación 1", "Transformadores"), tree.segments(6))
        assertEquals(emptyList<String>(), tree.segments(99))
    }

    @Test fun projectsAreRootsAndSortedIgnoringCase() {
        assertEquals(listOf("Granja Solar X", "Proyecto Y"), tree.projects.map { it.name })
        assertEquals(listOf("Tableros", "Transformadores"), tree.childrenOf(5).map { it.name })
    }

    @Test fun subtreeIncludesSelfAndAllDescendants() {
        assertEquals(setOf(4L, 5L, 6L, 7L, 8L), tree.subtreeIds(4).toSet())
        assertEquals(listOf(3L), tree.subtreeIds(3))
        assertTrue(tree.subtreeIds(99).isEmpty())
    }

    @Test fun cannotMoveFolderIntoItselfOrDescendant() {
        assertTrue(tree.isSelfOrDescendant(6, 5))
        assertTrue(tree.isSelfOrDescendant(5, 5))
        assertFalse(tree.isSelfOrDescendant(8, 5))
    }

    @Test fun projectOf() {
        assertEquals(4L, tree.projectOf(7)?.id)
        assertNull(tree.projectOf(99))
    }

    @Test fun aggregateSumsDescendantsAndKeepsLatestActivity() {
        val direct = mapOf(
            3L to FolderStats(10, 500), 2L to FolderStats(1, 700),
            6L to FolderStats(42, 900), 7L to FolderStats(18, 100), 8L to FolderStats(5, 800),
        )
        val agg = tree.aggregate(direct)
        assertEquals(FolderStats(11, 700), agg[1])
        assertEquals(FolderStats(60, 900), agg[5])
        assertEquals(FolderStats(65, 900), agg[4])
        assertEquals(FolderStats(0, 0), agg[2]?.let { FolderStats(0, 0) })
    }

    @Test fun cyclesDoNotHangPathLookup() {
        val broken = FolderTree(listOf(FolderNode(1, 2, "A"), FolderNode(2, 1, "B")))
        assertEquals(2, broken.pathOf(1).size)
    }
}
