package ca.dstudio.altitude.ui

import android.content.ContentUris
import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.Surface
import android.view.TextureView
import ca.dstudio.altitude.mine.VideoLibrary
import ca.dstudio.altitude.playlist.Item
import ca.dstudio.altitude.provider.AnswerKeep
import ca.dstudio.altitude.provider.Shown
import java.io.File

/**
 * Behind the panel: the clip the launcher last reported on screen, or after a change one that fits
 * the new choices, played by this screen's own player, muted and looping, scaled to fill. It stays
 * hidden until its first frame, so the window's plain fill shows while there is no clip, while it
 * loads, and after a player error. [play] makes the player; [stop] lets it go. It never asks for
 * audio focus.
 */
class Backdrop(private val view: TextureView) : TextureView.SurfaceTextureListener {

    private var clip: Uri? = null
    private var player: MediaPlayer? = null
    private var surface: Surface? = null

    init {
        view.surfaceTextureListener = this
        view.alpha = 0f
    }

    /**
     * Plays [item], or nothing when it is null: an Apple link as it is, a video of mine from the media
     * index itself (Altitude reads it with its own access), not through Altitude's provider.
     */
    fun play(item: Item?) {
        stop()
        clip = item?.let { uriOf(Uri.parse(it.link)) }
        start()
    }

    private fun uriOf(link: Uri): Uri? =
        if (link.scheme != "content") link else link.lastPathSegment?.toLongOrNull()?.let { ContentUris.withAppendedId(VideoLibrary.URI, it) }

    fun stop() {
        player?.release()
        player = null
        surface?.release()
        surface = null
        view.alpha = 0f
    }

    /** Starts once there is both a clip and somewhere to draw it. */
    private fun start() {
        val uri = clip ?: return
        val texture = view.surfaceTexture ?: return
        if (player != null) return
        val out = Surface(texture)
        val mp = MediaPlayer()
        surface = out
        player = mp
        try {
            mp.setVolume(0f, 0f)
            mp.isLooping = true
            mp.setSurface(out)
            mp.setOnVideoSizeChangedListener { _, width, height -> fill(width, height) }
            mp.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) view.alpha = 1f
                false
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "backdrop: the player failed ($what, $extra); the plain fill stays")
                stop()
                true
            }
            mp.setOnPreparedListener { it.start() }
            mp.setDataSource(view.context, uri)
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "backdrop: could not play the clip (${e.javaClass.simpleName}); the plain fill stays")
            stop()
        }
    }

    /** Scales a [width] x [height] video to cover the view, cropping what overflows. */
    private fun fill(width: Int, height: Int) {
        if (width <= 0 || height <= 0 || view.width == 0 || view.height == 0) return
        val w = view.width.toFloat()
        val h = view.height.toFloat()
        val scale = maxOf(w / width, h / height)
        view.setTransform(Matrix().apply { setScale(width * scale / w, height * scale / h, w / 2f, h / 2f) })
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) = start()

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        stop()
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    companion object {
        private const val TAG = "Altitude"

        /**
         * The item of the kept list the launcher last reported shown; null when nothing was shown or the
         * list no longer holds it. Off the main thread.
         */
        fun shownClip(context: Context): Item? {
            val id = Shown.read(context) ?: return null
            return AnswerKeep(File(context.filesDir, AnswerKeep.FILE)).read()?.items?.firstOrNull { it.id == id }
        }
    }
}
