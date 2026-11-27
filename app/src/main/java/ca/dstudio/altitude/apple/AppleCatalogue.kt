package ca.dstudio.altitude.apple

import android.content.Context
import android.util.Log
import ca.dstudio.altitude.playlist.AltitudeJson
import ca.dstudio.altitude.playlist.Clip
import ca.dstudio.altitude.playlist.writeAtomically
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileNotFoundException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Apple's clips: the list last fetched from Apple's feed, else the one Altitude ships with. Reading
 * never waits on the network; the feed is fetched in the background, at most once a day whatever
 * the outcome, and the next list uses what it brought.
 */
class AppleCatalogue(context: Context) {

    private val app = context.applicationContext
    private val cache = File(app.filesDir, "apple/clips.json")
    private val prefs = app.getSharedPreferences("apple", Context.MODE_PRIVATE)

    /** The cached list, of any age, else the bundled snapshot. */
    fun clips(): List<Clip> =
        read("cache") { cache.readText() }
            ?: read("snapshot") { app.assets.open(SNAPSHOT).use { it.readBytes().decodeToString() } }
            ?: emptyList()

    private fun read(source: String, text: () -> String): List<Clip>? = try {
        AltitudeJson.decodeFromString<List<Clip>>(text()).ifEmpty { null }
            ?.also { Log.i(TAG, "apple: ${it.size} clips from the $source") }
    } catch (e: FileNotFoundException) {
        null
    } catch (e: Exception) {
        Log.w(TAG, "apple: the $source is unreadable (${e.javaClass.simpleName})")
        null
    }

    /** Starts one fetch of the feed in the background when the last attempt is a day old. */
    fun refreshIfDue() {
        val now = System.currentTimeMillis()
        // A clock set back makes the last attempt look ahead of now: that counts as due.
        if (now - prefs.getLong(LAST_ATTEMPT, 0) in 0 until DAY_MS) return
        prefs.edit().putLong(LAST_ATTEMPT, now).commit()
        thread(name = "altitude-feed") { fetch() }
    }

    private fun fetch() {
        try {
            val request = Request.Builder().url(FEED_URL).apply {
                if (cache.isFile) {
                    prefs.getString(ETAG, null)?.let { header("If-None-Match", it) }
                    prefs.getString(LAST_MODIFIED, null)?.let { header("If-Modified-Since", it) }
                }
            }.build()
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .callTimeout(3, TimeUnit.MINUTES)
                .followSslRedirects(false)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 304) {
                    Log.i(TAG, "apple: the feed has not changed")
                    return
                }
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("no body")
                if (body.contentLength() > MAX_FEED_BYTES) throw IOException("the feed is over $MAX_FEED_BYTES bytes")
                val entries = Capped(body.byteStream(), MAX_FEED_BYTES).use { Tar.entry(it, ENTRIES) }
                    ?: throw IOException("no $ENTRIES in the feed")
                val clips = AppleFeed.parse(entries)
                if (clips.isEmpty()) throw IOException("no usable clip in the feed")
                cache.writeAtomically(AltitudeJson.encodeToString(clips))
                prefs.edit()
                    .putString(ETAG, response.header("ETag"))
                    .putString(LAST_MODIFIED, response.header("Last-Modified"))
                    .apply()
                Log.i(TAG, "apple: fetched ${clips.size} clips; the next list uses them")
            }
        } catch (e: Exception) {
            Log.w(TAG, "apple: fetch failed (${e.javaClass.simpleName}: ${e.message}); the last list stays")
        }
    }

    /** Throws once more than [cap] bytes have come through. */
    private class Capped(input: InputStream, private val cap: Long) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int = super.read().also { if (it >= 0) add(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) add(it.toLong()) }

        override fun skip(n: Long): Long = super.skip(n).also { add(it) }

        private fun add(n: Long) {
            count += n
            if (count > cap) throw IOException("the feed is over $cap bytes")
        }
    }

    private companion object {
        const val TAG = "Altitude"
        const val FEED_URL = "https://sylvan.apple.com/Aerials/resources-16.tar"
        const val ENTRIES = "entries.json"
        const val SNAPSHOT = "apple-snapshot.json"
        const val MAX_FEED_BYTES = 64L * 1024 * 1024
        const val DAY_MS = 24 * 3_600_000L
        const val LAST_ATTEMPT = "last_attempt"
        const val ETAG = "etag"
        const val LAST_MODIFIED = "last_modified"
    }
}
