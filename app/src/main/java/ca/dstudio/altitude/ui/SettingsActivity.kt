package ca.dstudio.altitude.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.storage.StorageManager
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import ca.dstudio.altitude.R
import ca.dstudio.altitude.apple.AppleCatalogue
import ca.dstudio.altitude.mine.Folder
import ca.dstudio.altitude.mine.FolderTree
import ca.dstudio.altitude.mine.VideoLibrary
import ca.dstudio.altitude.playlist.Clip
import ca.dstudio.altitude.playlist.DeviceFacts
import ca.dstudio.altitude.playlist.Item
import ca.dstudio.altitude.playlist.Playlist
import ca.dstudio.altitude.settings.AltitudeSettings
import ca.dstudio.altitude.settings.Settings
import ca.dstudio.altitude.ui.panel.PanelView
import ca.dstudio.altitude.ui.panel.px
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Altitude's settings, as ATV Launcher opens them from its plugin page: full screen, the clip playing
 * now behind a side panel on the right. Every change is written at once and nothing is sent from
 * here: the launcher asks for a new list when it comes back. Nothing in the starting intent is read.
 *
 * What the pages count with (Apple's clips, the chosen folder's videos, the drives' names, what this
 * device plays) is read off the main thread each time the screen comes to the front; until it is in,
 * the lines that need it are left out.
 */
class SettingsActivity : Activity() {

    internal val store by lazy { AltitudeSettings(this) }
    internal lateinit var panel: PanelView

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "altitude-settings") }
    private val main = Handler(Looper.getMainLooper())
    private lateinit var backdrop: Backdrop
    private var started = false

    /** What plays behind the panel, as last decided; the worker thread's alone, so each change follows the one before. */
    private var behind: Item? = null

    private var apple: List<Clip>? = null

    /** The videos in the chosen folder and below it; null until counted. */
    private var folderVideos: Int? = null

    /** Each drive's own name, by the media index's name for its volume (lower case). */
    private var volumes: Map<String, String> = emptyMap()

    /** What this device plays; null until read (the codec list is slow to read). */
    internal var facts: DeviceFacts? = null
        private set

    /** The counts are in: the lines that need them can be shown. */
    internal val counted: Boolean get() = apple != null && folderVideos != null

    private val videoAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        val clip = TextureView(this)
        root.addView(clip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        panel = PanelView(this) { finish() }
        root.addView(panel, FrameLayout.LayoutParams(px(640f), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END).apply {
            topMargin = px(48f)
            bottomMargin = px(48f)
            rightMargin = px(48f)
        })
        setContentView(root)
        backdrop = Backdrop(clip)
        panel.push(SettingsPage(this))
    }

    override fun onStart() {
        super.onStart()
        started = true
        worker.execute {
            val clip = Backdrop.shownClip(this)
            behind = clip
            main.post { if (started) backdrop.play(clip) }
        }
    }

    /** Also back from Altitude's app info, which a TV shows as a panel over this screen: access may have changed there. */
    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onStop() {
        started = false
        backdrop.stop()
        super.onStop()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = panel.handleKey(event) || super.dispatchKeyEvent(event)

    /** Writes [settings] and redraws the page; the launcher reads them when it asks for the next list. */
    internal fun write(settings: Settings) {
        store.write(settings)
        panel.refresh()
        follow(settings)
    }

    /**
     * After a change, the clip behind the panel follows [settings] ([Playlist.backdrop]): it plays on
     * while it still matches, again at its new link when only the quality moved it, and gives way to
     * one that matches, or to the plain fill when none does. Nothing goes to the launcher.
     */
    private fun follow(settings: Settings) = worker.execute {
        val apple = AppleCatalogue(this).clips()
        val mine = Folder.parse(settings.folder)?.let { VideoLibrary(this).files(it) }.orEmpty()
        val next = Playlist.backdrop(behind, settings, apple, mine, DeviceFacts.read(this), Random.nextLong())
        val replay = next?.link != behind?.link
        behind = next
        if (replay) {
            Log.i(TAG, "settings: behind the panel now ${next?.id ?: "the plain fill"}")
            main.post { if (started) backdrop.play(next) }
        }
    }

    /** The page's rules and lines, from the settings as stored now. */
    internal fun state(): PageState {
        val settings = store.read()
        val clips = apple.orEmpty()
        return PageState(settings, access(), Playlist.count(settings.copy(apple = true), clips, emptyList()), folderVideos ?: 0)
    }

    internal fun access(): Access {
        val granted = checkSelfPermission(videoAccess) == PackageManager.PERMISSION_GRANTED
        val partial = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        val asked = getPreferences(MODE_PRIVATE).getBoolean(ASKED, false)
        return Access.of(granted, partial, asked, shouldShowRequestPermissionRationale(videoAccess))
    }

    /** My videos' row: the folder list with access; else Android's prompt, or Altitude's app info once Android shows none. */
    internal fun openMyVideos() {
        when (access()) {
            Access.GRANTED -> panel.push(FoldersPage(this))
            Access.ASK -> {
                getPreferences(MODE_PRIVATE).edit().putBoolean(ASKED, true).apply()
                // From Android 14 asking for the limited access too lets Android offer "all" again over it.
                val asked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    arrayOf(videoAccess, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                } else {
                    arrayOf(videoAccess)
                }
                requestPermissions(asked, ASK_ACCESS)
            }
            Access.APP_INFO -> try {
                startActivity(Intent(ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "settings: this device has no app info screen")
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode != ASK_ACCESS) return
        if (access() == Access.GRANTED) panel.push(FoldersPage(this)) else panel.refresh()
    }

    /** The folder list's choice: written, counted from the list, and back to the page. */
    internal fun chooseFolder(folder: Folder?, videos: Int) {
        val settings = store.read().copy(folder = folder?.toString().orEmpty())
        store.write(settings)
        folderVideos = videos
        panel.back()
        follow(settings)
    }

    /** "This device", or the drive's own name, else "USB drive". */
    internal fun volumeName(volume: String): String = when {
        volume.equals(Folder.PRIMARY, ignoreCase = true) -> getString(R.string.volume_this_device)
        else -> volumes[volume.lowercase()] ?: getString(R.string.volume_usb)
    }

    /** Every folder with videos in it or below it, read once off the main thread, to [done] on it. */
    internal fun readTree(done: (List<FolderTree.Entry>) -> Unit) = worker.execute {
        val tree = VideoLibrary(this).tree()
        main.post { if (!isDestroyed) done(tree) }
    }

    /** Reads what the page counts with, off the main thread, and redraws as each part comes in. */
    private fun reload() {
        val folder = store.read().folder
        worker.execute {
            val clips = AppleCatalogue(this).clips()
            val videos = Folder.parse(folder)?.let { VideoLibrary(this).files(it).size } ?: 0
            val names = volumeNames()
            main.post {
                if (isDestroyed) return@post
                apple = clips
                volumes = names
                // A folder chosen meanwhile was counted by the folder list.
                if (store.read().folder == folder) folderVideos = videos
                panel.refresh()
            }
            val read = DeviceFacts.read(this)
            main.post {
                if (isDestroyed) return@post
                facts = read
                panel.refresh()
            }
        }
    }

    private fun volumeNames(): Map<String, String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return emptyMap()
        return getSystemService(StorageManager::class.java).storageVolumes.mapNotNull { volume ->
            val name = when {
                volume.isPrimary -> Folder.PRIMARY
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> volume.mediaStoreVolumeName
                else -> volume.uuid
            } ?: return@mapNotNull null
            name.lowercase() to volume.getDescription(this)
        }.toMap()
    }

    private companion object {
        const val TAG = "Altitude"
        const val ASK_ACCESS = 1
        const val ASKED = "asked_access"
    }
}
