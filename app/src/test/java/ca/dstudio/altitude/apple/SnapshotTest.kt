package ca.dstudio.altitude.apple

import ca.dstudio.altitude.playlist.AltitudeJson
import ca.dstudio.altitude.playlist.Clip
import ca.dstudio.altitude.playlist.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SnapshotTest {

    @Test
    fun theBundledSnapshotHoldsEveryScene() {
        val clips = AltitudeJson.decodeFromString<List<Clip>>(File("src/main/assets/apple-snapshot.json").readText())
        assertTrue("${clips.size} clips", clips.size > 100)
        assertEquals(Scene.entries.toSet(), clips.map { it.scene }.toSet())
        assertEquals(clips.size, clips.map { it.id }.toSet().size)
    }
}
