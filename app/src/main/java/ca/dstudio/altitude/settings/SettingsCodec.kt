package ca.dstudio.altitude.settings

import android.os.Bundle
import ca.dstudio.altitude.mine.Folder
import ca.dstudio.atvlauncher.wallpaper.api.WallpaperSettings

/**
 * Altitude's settings as they travel in ATV Launcher's backups and shared setups: version 1, every
 * value public (none of them is private), plain scalars only. Read leniently, whatever version wrote
 * them: a missing or unknown value takes its default, and a key Altitude does not know is ignored.
 */
object SettingsCodec {

    /** The shape [values] writes. A newer one is read the same way. */
    const val VERSION = 1

    private const val APPLE = "apple"
    private const val SCENES = "scenes"
    private const val QUALITY = "quality"
    private const val FOLDER = "folder"

    fun encode(settings: Settings) = WallpaperSettings(VERSION, Bundle().apply {
        for ((key, value) in values(settings)) {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is String -> putString(key, value)
            }
        }
    })

    @Suppress("DEPRECATION") // Bundle.get(String) is deprecated in 33 for the typed getters; a value of any type is read here.
    fun decode(settings: WallpaperSettings): Settings = settings.public.let { public -> read(public.keySet().associateWith { public.get(it) }) }

    /** Apple's switch, the scenes in a fixed order, the quality, and the folder as `volume|path` or `""`. */
    fun values(settings: Settings): Map<String, Any> = mapOf(
        APPLE to settings.apple,
        SCENES to AltitudeSettings.scenesText(settings.scenes),
        QUALITY to settings.quality.key,
        FOLDER to settings.folder,
    )

    /** Settings from [values]: an unknown scene is left out, and no scene left means every one. */
    fun read(values: Map<String, Any?>): Settings {
        val defaults = Settings()
        return Settings(
            apple = values[APPLE] as? Boolean ?: defaults.apple,
            scenes = AltitudeSettings.scenesOf(values[SCENES] as? String) ?: defaults.scenes,
            quality = AltitudeSettings.qualityOf(values[QUALITY] as? String) ?: defaults.quality,
            folder = (values[FOLDER] as? String)?.let(Folder::parse)?.toString() ?: defaults.folder,
        )
    }
}
