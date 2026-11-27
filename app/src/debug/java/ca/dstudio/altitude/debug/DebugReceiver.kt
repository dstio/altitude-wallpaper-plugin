package ca.dstudio.altitude.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ca.dstudio.altitude.mine.Folder
import ca.dstudio.altitude.playlist.Scene
import ca.dstudio.altitude.settings.AltitudeSettings

/**
 * Sets Altitude's settings, in debug builds:
 *
 * ```
 * adb shell am broadcast -n ca.dstudio.altitude/.debug.DebugReceiver -a ca.dstudio.altitude.debug.SET \
 *     [--es scenes space,cities] [--es quality auto|1080] [--ez apple true|false] \
 *     [--es folder "'external_primary|Movies/My clips'"]
 * ```
 *
 * `folder` is `volume|path` (the device shell reads `|` and spaces, hence the inner quotes), or `"''"`
 * for none. Each extra left out keeps its setting. The broadcast's result data says what was written,
 * or what was wrong (then nothing is written). The next list follows the settings: ask the launcher
 * for one. The receiver requires `android.permission.DUMP`, which only the shell holds.
 */
class DebugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        resultData = if (intent.action == ACTION_SET) set(AltitudeSettings(context), intent) else "use -a $ACTION_SET"
    }

    private fun set(store: AltitudeSettings, intent: Intent): String {
        val old = store.read()
        val scenes = intent.getStringExtra("scenes")?.let { text ->
            AltitudeSettings.scenesOf(text)?.takeIf { text.split(',').all { AltitudeSettings.scenesOf(it) != null } }
                ?: return "scenes: one or more of ${Scene.entries.joinToString(",") { it.name.lowercase() }}"
        } ?: old.scenes
        val quality = intent.getStringExtra("quality")?.let { AltitudeSettings.qualityOf(it) ?: return "quality: auto | 1080" }
            ?: old.quality
        val apple = if (intent.hasExtra("apple")) intent.getBooleanExtra("apple", true) else old.apple
        val folder = when (val text = intent.getStringExtra("folder")) {
            null -> old.folder
            "" -> ""
            else -> Folder.parse(text)?.toString() ?: return "folder: volume|path (external_primary|Movies), or '' for none"
        }
        val settings = old.copy(apple = apple, scenes = scenes, quality = quality, folder = folder)
        store.write(settings)
        return "set: $settings"
    }

    private companion object {
        const val ACTION_SET = "ca.dstudio.altitude.debug.SET"
    }
}
