package ca.dstudio.altitude.settings

import ca.dstudio.altitude.playlist.Scene
import org.junit.Assert.assertEquals
import org.junit.Test

/** The settings as they travel, through the plain values a backup keeps (a bundle is Android's, not the JVM's). */
class SettingsCodecTest {

    private val changed = Settings(
        apple = false,
        scenes = setOf(Scene.SPACE, Scene.CITIES),
        quality = Quality.P1080,
        folder = "1a2b-3c4d|Movies/Drone",
    )

    @Test
    fun aRoundTripGivesTheSameSettings() {
        assertEquals(changed, SettingsCodec.read(SettingsCodec.values(changed)))
        assertEquals(Settings(), SettingsCodec.read(SettingsCodec.values(Settings())))
    }

    @Test
    fun theValuesArePlainAndTheScenesInAFixedOrder() {
        assertEquals(
            mapOf("apple" to false, "scenes" to "cities,space", "quality" to "1080", "folder" to "1a2b-3c4d|Movies/Drone"),
            SettingsCodec.values(changed),
        )
    }

    @Test
    fun everyKeyMissingGivesTheDefaults() {
        assertEquals(Settings(), SettingsCodec.read(emptyMap()))
    }

    @Test
    fun aValueOfTheWrongTypeOrUnknownTakesItsDefault() {
        val read = SettingsCodec.read(mapOf("apple" to "false", "scenes" to 3, "quality" to "8k", "folder" to "no volume"))
        assertEquals(Settings(), read)
    }

    @Test
    fun aNewerVersionsExtraKeysAndAnOlderTimeOfDayAreIgnored() {
        val values = SettingsCodec.values(changed) + mapOf("time" to "night", "speed" to 2, "fade" to true)
        assertEquals(changed, SettingsCodec.read(values))
    }

    @Test
    fun anUnknownSceneIsLeftOut() {
        assertEquals(setOf(Scene.CITIES), SettingsCodec.read(mapOf("scenes" to "cities,forests")).scenes)
    }

    @Test
    fun noSceneLeftMeansEveryScene() {
        assertEquals(Scene.entries.toSet(), SettingsCodec.read(mapOf("scenes" to "forests")).scenes)
        assertEquals(Scene.entries.toSet(), SettingsCodec.read(mapOf("scenes" to "")).scenes)
    }
}
