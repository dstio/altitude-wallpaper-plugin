package ca.dstudio.altitude.provider

import ca.dstudio.altitude.playlist.AltitudeJson
import ca.dstudio.altitude.playlist.Item
import ca.dstudio.altitude.playlist.writeAtomically
import ca.dstudio.altitude.settings.Settings
import ca.dstudio.atvlauncher.wallpaper.api.WallpaperContract
import kotlinx.serialization.Serializable
import java.io.File

/** A list as it was handed to the launcher: its items (none for "nothing matches"), when, and from what. */
@Serializable
data class KeptAnswer(val items: List<Item>, val madeAt: Long, val settings: Settings)

/**
 * The last answer, kept in a small file. The launcher binds again and asks with
 * [WallpaperContract.REASON_START] at every return to Home, so a start re-sends the kept list,
 * unchanged, while it is under [LIFE_MS] old and the settings are the same. Every other request
 * makes a new list.
 */
class AnswerKeep(private val file: File) {

    fun read(): KeptAnswer? = try {
        AltitudeJson.decodeFromString<KeptAnswer>(file.readText())
    } catch (e: Exception) {
        null
    }

    fun write(answer: KeptAnswer) = file.writeAtomically(AltitudeJson.encodeToString(answer))

    companion object {
        /** The kept answer's file, in the app's files folder; the settings screen reads it too. */
        const val FILE = "answer.json"

        /** How long a list lasts: the time between the launcher's timed asks. */
        const val LIFE_MS = 6 * 3_600_000L

        /** [kept], when it is the answer to a request for [reason] with [settings] at [now]; else null. */
        fun reusable(kept: KeptAnswer?, reason: Int, settings: Settings, now: Long): KeptAnswer? =
            kept?.takeIf {
                reason == WallpaperContract.REASON_START && it.settings == settings && now - it.madeAt in 0 until LIFE_MS
            }
    }
}
