package ca.dstudio.altitude.provider

import android.util.Log
import ca.dstudio.altitude.apple.AppleCatalogue
import ca.dstudio.altitude.mine.Folder
import ca.dstudio.altitude.mine.VideoLibrary
import ca.dstudio.altitude.playlist.Answer
import ca.dstudio.altitude.playlist.DeviceFacts
import ca.dstudio.altitude.playlist.Item
import ca.dstudio.altitude.playlist.Playlist
import ca.dstudio.altitude.settings.AltitudeSettings
import ca.dstudio.altitude.settings.Settings
import ca.dstudio.altitude.settings.SettingsCodec
import ca.dstudio.atvlauncher.wallpaper.api.WallpaperBatch
import ca.dstudio.atvlauncher.wallpaper.api.WallpaperContract
import ca.dstudio.atvlauncher.wallpaper.api.WallpaperEnvironment
import ca.dstudio.atvlauncher.wallpaper.api.WallpaperItem
import ca.dstudio.atvlauncher.wallpaper.api.WallpaperProviderService
import ca.dstudio.atvlauncher.wallpaper.api.WallpaperSettings
import java.io.File
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Altitude's door for ATV Launcher: every request is answered with one list of videos, made on a
 * worker thread. A start re-sends the kept list ([AnswerKeep]); every other request reshuffles.
 * The log holds counts and ids, never a link.
 */
class AltitudeService : WallpaperProviderService() {

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "altitude-answer") }
    private val settings by lazy { AltitudeSettings(this) }
    private val catalogue by lazy { AppleCatalogue(this) }
    private val library by lazy { VideoLibrary(this) }
    private val keep by lazy { AnswerKeep(File(filesDir, AnswerKeep.FILE)) }
    private val facts by lazy {
        DeviceFacts.read(this).also {
            Log.i(TAG, "device: decodes 4K HEVC ${it.decodes4kHevc}, 4K panel ${it.panel4k}, decodes 1080p HEVC ${it.decodes1080Hevc}")
        }
    }

    override fun onAttach(env: WallpaperEnvironment) {
        Log.i(TAG, "attached: ${env.widthPx}x${env.heightPx}, api ${env.apiVersion}")
    }

    override fun onRequest(reason: Int) {
        // A call posted before the unbind can still arrive after onDestroy.
        if (!worker.isShutdown) worker.execute { answer(reason) }
    }

    override fun onShown(itemId: String) {
        Log.i(TAG, "shown $itemId")
        Shown.write(this, itemId)
    }

    override fun onFailed(itemId: String, why: Int) {
        Log.w(TAG, "the launcher could not play $itemId: $why")
    }

    /** Every setting, for a backup or a shared setup. */
    override fun onGetSettings(): WallpaperSettings {
        Log.i(TAG, "settings handed over")
        return SettingsCodec.encode(settings.read())
    }

    /** A restore's settings, from any version of Altitude. The launcher asks for the next list itself. */
    override fun onSetSettings(settings: WallpaperSettings) {
        val restored = SettingsCodec.decode(settings)
        this.settings.write(restored)
        Log.i(
            TAG,
            "settings restored from version ${settings.settingsVersion}: apple ${restored.apple}, ${restored.scenes.size} scenes, " +
                "quality ${restored.quality.key}, ${if (restored.folder.isEmpty()) "no folder" else "a folder"}",
        )
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    /** On the worker: exactly one push, then a feed fetch when one is due. */
    private fun answer(reason: Int) {
        val batch = try {
            batchFor(reason)
        } catch (e: Exception) {
            Log.w(TAG, "request $reason: no list (${e.javaClass.simpleName}); nothing yet")
            WallpaperBatch(emptyList())
        }
        push(batch)
        try {
            catalogue.refreshIfDue()
        } catch (e: Exception) {
            Log.w(TAG, "apple: could not start a fetch (${e.javaClass.simpleName})")
        }
    }

    private fun batchFor(reason: Int): WallpaperBatch {
        val now = System.currentTimeMillis()
        val current = settings.read()
        val kept = AnswerKeep.reusable(keep.read(), reason, current, now)
        val answer = if (kept != null) {
            Log.i(TAG, "request $reason: the kept list of ${kept.items.size} again, made ${(now - kept.madeAt) / 60_000} min ago")
            kept
        } else {
            KeptAnswer(newList(reason, current), now, current).also { keep.write(it) }
        }
        val untilStale = AnswerKeep.LIFE_MS - (now - answer.madeAt)
        return WallpaperBatch(answer.items.map { it.toVideo() }, maxOf(WallpaperContract.MIN_REQUEST_INTERVAL_MS, untilStale))
    }

    private fun newList(reason: Int, settings: Settings): List<Item> {
        val apple = catalogue.clips()
        val mine = Folder.parse(settings.folder)?.let(library::files).orEmpty()
        return when (val made = Playlist.make(settings, apple, mine, facts, Random.nextLong())) {
            is Answer.Batch -> made.items.also { items ->
                val fourK = items.count { it.id.endsWith(":4k") }
                val own = items.count { it.id.startsWith("mine:") }
                Log.i(
                    TAG,
                    "request $reason: a new list of ${items.size} (${Playlist.count(settings, apple, mine)} match, " +
                        "${mine.size} in the folder), $fourK at 4K, $own of my videos",
                )
            }
            Answer.NothingMatches -> emptyList<Item>().also {
                Log.i(TAG, "request $reason: nothing matches; the launcher keeps what it shows")
            }
        }
    }

    private fun Item.toVideo() = WallpaperItem(
        id = id,
        uri = link,
        type = WallpaperContract.TYPE_VIDEO,
        mode = WallpaperContract.MODE_DEFAULT,
        title = title,
    )

    private companion object {
        const val TAG = "Altitude"
    }
}
