package ca.dstudio.altitude.settings

import android.content.Context
import ca.dstudio.altitude.playlist.Scene
import kotlinx.serialization.Serializable

/** Every setting at one moment; what a list is made from. */
@Serializable
data class Settings(
    val apple: Boolean = true,
    val scenes: Set<Scene> = Scene.entries.toSet(),
    val quality: Quality = Quality.AUTO,
    /** The folder of the user's own videos; empty for none. */
    val folder: String = "",
)

enum class Quality(val key: String) { AUTO("auto"), P1080("1080") }

/** Altitude's settings, in one SharedPreferences file. A value that cannot be read takes its default. */
class AltitudeSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(): Settings {
        val defaults = Settings()
        return Settings(
            apple = prefs.getBoolean(APPLE, defaults.apple),
            scenes = scenesOf(prefs.getString(SCENES, null)) ?: defaults.scenes,
            quality = qualityOf(prefs.getString(QUALITY, null)) ?: defaults.quality,
            folder = prefs.getString(FOLDER, null) ?: defaults.folder,
        )
    }

    fun write(settings: Settings) {
        prefs.edit()
            .putBoolean(APPLE, settings.apple)
            .putString(SCENES, scenesText(settings.scenes))
            .putString(QUALITY, settings.quality.key)
            .putString(FOLDER, settings.folder)
            .commit()
    }

    companion object {
        private const val FILE = "settings"
        private const val APPLE = "apple"
        private const val SCENES = "scenes"
        private const val QUALITY = "quality"
        private const val FOLDER = "folder"

        /** Scenes as comma-separated names, in the scenes' own order. */
        fun scenesText(scenes: Set<Scene>): String = scenes.sorted().joinToString(",") { it.name.lowercase() }

        /** Comma-separated scene names to scenes, unknown names left out; null when none is known. */
        fun scenesOf(text: String?): Set<Scene>? = text?.split(',')
            ?.mapNotNull { key -> Scene.entries.firstOrNull { it.name.equals(key.trim(), ignoreCase = true) } }
            ?.toSet()?.ifEmpty { null }

        fun qualityOf(text: String?): Quality? = Quality.entries.firstOrNull { it.key == text }
    }
}
