package ca.dstudio.altitude.mine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderTreeTest {

    private fun at(path: String, volume: String = Folder.PRIMARY) = Folder(volume, path)

    private fun entries(vararg files: Folder) = FolderTree.folders(files.toList()).map { it.folder.path to it.count }

    @Test
    fun aCountIncludesTheSubfolders() {
        assertEquals(
            listOf("" to 4, "Movies" to 4, "Movies/Trips" to 4, "Movies/Trips/Drone" to 2),
            entries(at("Movies/Trips"), at("Movies/Trips"), at("Movies/Trips/Drone"), at("Movies/Trips/Drone")),
        )
    }

    @Test
    fun aFolderWithOnlySubfoldersHoldingVideosIsListed() {
        assertEquals(
            listOf("" to 2, "Videos" to 2, "Videos/Drone" to 1, "Videos/Holidays" to 1),
            entries(at("Videos/Drone"), at("Videos/Holidays")),
        )
    }

    @Test
    fun aVideoAtTheTopIsCountedThereAlone() {
        assertEquals(listOf("" to 2, "Movies" to 1), entries(at(""), at("Movies")))
    }

    @Test
    fun volumesAreKeptApart() {
        val tree = FolderTree.folders(listOf(at("Videos", "1a2b-3c4d"), at("Videos"), at("Videos")))
        assertEquals(
            listOf(
                FolderTree.Entry(at("", "1a2b-3c4d"), 1),
                FolderTree.Entry(at("Videos", "1a2b-3c4d"), 1),
                FolderTree.Entry(at(""), 2),
                FolderTree.Entry(at("Videos"), 2),
            ),
            tree,
        )
    }

    @Test
    fun aFoldersSubfoldersComeRightAfterIt() {
        assertEquals(listOf("", "A", "A/B", "A B"), entries(at("A B"), at("A/B"), at("A")).map { it.first })
    }

    @Test
    fun containsIsTrueInTheFolderAndBelowIt() {
        assertTrue(FolderTree.contains(at("Videos/Drone"), at("Videos/Drone")))
        assertTrue(FolderTree.contains(at("Videos"), at("Videos/Drone/2025")))
        assertTrue(FolderTree.contains(at(""), at("Videos/Drone")))
        assertTrue(FolderTree.contains(at("Videos", "1A2B-3C4D"), at("Videos", "1a2b-3c4d")))
    }

    @Test
    fun containsIsFalseOutsideTheFolder() {
        assertFalse(FolderTree.contains(at("Videos/Drone"), at("Videos/Drones")))
        assertFalse(FolderTree.contains(at("Videos/Drone"), at("Videos")))
        assertFalse(FolderTree.contains(at("Videos"), at("Videos", "1a2b-3c4d")))
        assertFalse(FolderTree.contains(at(""), at("Videos", "1a2b-3c4d")))
    }
}
