package ca.dstudio.altitude.mine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderTest {

    @Test
    fun aFolderReadsBackFromItsString() {
        for (folder in listOf(
            Folder(Folder.PRIMARY, "Movies/Trips 2025"),
            Folder("1a2b-3c4d", ""),
            Folder(Folder.PRIMARY, "Odd|name"),
        )) {
            assertEquals(folder, Folder.parse(folder.toString()))
        }
        assertEquals("external_primary|Movies/Trips 2025", Folder(Folder.PRIMARY, "Movies/Trips 2025").toString())
    }

    @Test
    fun noneAndTextWithNoVolumeAreNoFolder() {
        assertNull(Folder.parse(""))
        assertNull(Folder.parse("Movies"))
        assertNull(Folder.parse("|Movies"))
    }

    @Test
    fun slashesAroundAPathAreDropped() {
        assertEquals(Folder(Folder.PRIMARY, "Movies/Trips"), Folder.parse("external_primary|/Movies/Trips/"))
    }

    @Test
    fun belowAndroid10TheVolumeAndPathComeFromTheFullPath() {
        assertEquals(Folder(Folder.PRIMARY, "Movies/Trips"), Folder.ofFile("/storage/emulated/0/Movies/Trips/Coast.mp4"))
        assertEquals(Folder(Folder.PRIMARY, ""), Folder.ofFile("/storage/emulated/0/clip.mp4"))
        assertEquals(Folder("1a2b-3c4d", "Videos/Drone"), Folder.ofFile("/storage/1A2B-3C4D/Videos/Drone/clip.mp4"))
        assertEquals(Folder("1a2b-3c4d", ""), Folder.ofFile("/storage/1A2B-3C4D/clip.mp4"))
        assertNull(Folder.ofFile("/data/media/clip.mp4"))
        assertNull(Folder.ofFile("/storage/emulated/0"))
    }
}
