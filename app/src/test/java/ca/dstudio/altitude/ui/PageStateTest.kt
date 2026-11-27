package ca.dstudio.altitude.ui

import ca.dstudio.altitude.playlist.Scene
import ca.dstudio.altitude.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageStateTest {

    private val folder = "external_primary|Movies/Drone"

    private fun state(settings: Settings, access: Access = Access.GRANTED, apple: Int = 114, mine: Int = 24) =
        PageState(settings, access, apple, mine)

    @Test
    fun appleCanBeSwitchedOffOnlyWithAFolderChosen() {
        assertFalse(state(Settings(apple = true)).appleSwitchable)
        assertTrue(state(Settings(apple = true, folder = folder)).appleSwitchable)
        // A folder that is not connected is still a source.
        assertTrue(state(Settings(apple = true, folder = folder), mine = 0).appleSwitchable)
        // Switching it back on is always allowed.
        assertTrue(state(Settings(apple = false, folder = folder)).appleSwitchable)
    }

    @Test
    fun noneCanBeChosenOnlyWhileAppleIsOn() {
        assertTrue(state(Settings(apple = true, folder = folder)).noneChoosable)
        assertFalse(state(Settings(apple = false, folder = folder)).noneChoosable)
    }

    @Test
    fun theLastTickedSceneStays() {
        val one = state(Settings(scenes = setOf(Scene.SPACE)))
        assertFalse(one.sceneSwitchable(Scene.SPACE))
        assertTrue(one.sceneSwitchable(Scene.CITIES))
        assertTrue(state(Settings(scenes = setOf(Scene.SPACE, Scene.CITIES))).sceneSwitchable(Scene.SPACE))
    }

    @Test
    fun theHeaderCountsBothSources() {
        assertEquals(114, state(Settings()).matching)
        assertEquals(138, state(Settings(folder = folder)).matching)
        assertEquals(24, state(Settings(apple = false, folder = folder)).matching)
        // Unreadable videos are not counted.
        assertEquals(114, state(Settings(folder = folder), Access.ASK).matching)
    }

    @Test
    fun withAppleOffAndTheFolderGoneNothingPlays() {
        assertTrue(state(Settings(apple = false, folder = folder), mine = 0).nothingToPlay)
        assertTrue(state(Settings(apple = false, folder = folder), Access.ASK).nothingToPlay)
        assertFalse(state(Settings(apple = true, folder = folder), mine = 0).nothingToPlay)
        assertFalse(state(Settings(apple = false, folder = folder)).nothingToPlay)
    }

    @Test
    fun myVideosLine() {
        assertEquals(FolderLine.CHOOSE, state(Settings()).folderLine)
        assertEquals(FolderLine.FOLDER, state(Settings(folder = folder)).folderLine)
        assertEquals(FolderLine.NOT_CONNECTED, state(Settings(folder = folder), mine = 0).folderLine)
        // Without access the line asks for it, folder or not.
        assertEquals(FolderLine.ALLOW, state(Settings(folder = folder), Access.ASK).folderLine)
        assertEquals(FolderLine.ALLOW_IN_APP_INFO, state(Settings(), Access.APP_INFO).folderLine)
    }

    @Test
    fun accessAsksUntilAndroidStopsShowingItsPrompt() {
        assertEquals(Access.GRANTED, Access.of(granted = true, partial = false, askedOnce = true, rationale = false))
        // Before the first ask Android gives no rationale either.
        assertEquals(Access.ASK, Access.of(granted = false, partial = false, askedOnce = false, rationale = false))
        assertEquals(Access.ASK, Access.of(granted = false, partial = false, askedOnce = true, rationale = true))
        assertEquals(Access.APP_INFO, Access.of(granted = false, partial = false, askedOnce = true, rationale = false))
        // Some videos chosen: Android asks again.
        assertEquals(Access.ASK, Access.of(granted = false, partial = true, askedOnce = true, rationale = false))
    }
}
