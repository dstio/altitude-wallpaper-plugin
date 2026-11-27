package ca.dstudio.altitude.provider

import ca.dstudio.altitude.playlist.Item
import ca.dstudio.altitude.playlist.Scene
import ca.dstudio.altitude.settings.Quality
import ca.dstudio.altitude.settings.Settings
import ca.dstudio.atvlauncher.wallpaper.api.WallpaperContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AnswerKeepTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val hour = 3_600_000L
    private val settings = Settings()
    private val kept = KeptAnswer(
        items = listOf(Item("apple:c1:1080", "https://example.invalid/c1.mov", "Place 1")),
        madeAt = 100 * hour,
        settings = settings,
    )

    @Test
    fun aStartUnderSixHoursWithTheSameSettingsSendsTheKeptList() {
        assertSame(kept, AnswerKeep.reusable(kept, WallpaperContract.REASON_START, settings, kept.madeAt + 5 * hour))
    }

    @Test
    fun everyOtherReasonMakesANewList() {
        for (reason in listOf(
            WallpaperContract.REASON_TIMER,
            WallpaperContract.REASON_SETTINGS_CLOSED,
            WallpaperContract.REASON_MANUAL,
            WallpaperContract.REASON_INVALIDATED,
        )) {
            assertNull("reason $reason", AnswerKeep.reusable(kept, reason, settings, kept.madeAt + hour))
        }
    }

    @Test
    fun aSettingsChangeMakesANewList() {
        assertNull(AnswerKeep.reusable(kept, WallpaperContract.REASON_START, settings.copy(scenes = setOf(Scene.SPACE)), kept.madeAt + hour))
        assertNull(AnswerKeep.reusable(kept, WallpaperContract.REASON_START, settings.copy(quality = Quality.P1080), kept.madeAt + hour))
    }

    @Test
    fun anOldListOrNoneMakesANewList() {
        assertNull(AnswerKeep.reusable(kept, WallpaperContract.REASON_START, settings, kept.madeAt + 6 * hour))
        // A clock set back puts the list in the future.
        assertNull(AnswerKeep.reusable(kept, WallpaperContract.REASON_START, settings, kept.madeAt - 1))
        assertNull(AnswerKeep.reusable(null, WallpaperContract.REASON_START, settings, kept.madeAt))
    }

    @Test
    fun theKeptAnswerReadsBackFromItsFile() {
        val keep = AnswerKeep(File(folder.root, "answer.json"))
        assertNull(keep.read())
        val answer = kept.copy(settings = Settings(scenes = setOf(Scene.SPACE, Scene.CITIES), quality = Quality.P1080))
        keep.write(answer)
        assertEquals(answer, keep.read())
    }
}
