package ca.dstudio.altitude.provider

import android.Manifest
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ca.dstudio.altitude.mine.Folder
import ca.dstudio.altitude.mine.FolderTree
import ca.dstudio.altitude.mine.VideoLibrary
import ca.dstudio.altitude.playlist.Scene
import ca.dstudio.altitude.settings.AltitudeSettings
import ca.dstudio.altitude.settings.Quality
import ca.dstudio.altitude.settings.Settings
import ca.dstudio.altitude.settings.SettingsCodec
import ca.dstudio.atvlauncher.wallpaper.api.IWallpaperHost
import ca.dstudio.atvlauncher.wallpaper.api.IWallpaperProvider
import ca.dstudio.atvlauncher.wallpaper.api.WallpaperBatch
import ca.dstudio.atvlauncher.wallpaper.api.WallpaperContract
import ca.dstudio.atvlauncher.wallpaper.api.WallpaperEnvironment
import ca.dstudio.atvlauncher.wallpaper.api.WallpaperSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileNotFoundException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS

/**
 * The service and the video provider as ATV Launcher drives them, from Altitude's own process (the
 * shell cannot bind or read them: it has no BIND_WALLPAPER). Needs four test videos in the media
 * index - Coast 1 and Coast 2 in Movies/Altitude test, Drone 1 and Drone 2 in its Drone subfolder -
 * and Android 9 or later (video access is granted here). The settings are put back after each test.
 */
@RunWith(AndroidJUnit4::class)
class MyVideosTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val resolver = context.contentResolver
    private val store = AltitudeSettings(context)
    private val library = VideoLibrary(context)
    private val saved = store.read()
    private val pushes = LinkedBlockingQueue<WallpaperBatch>()

    /** The settings answers: request id, status, and the settings a request is answered with. */
    private val answers = LinkedBlockingQueue<Triple<Int, Int, WallpaperSettings?>>()
    private val connections = mutableListOf<ServiceConnection>()

    private val host = object : IWallpaperHost.Stub() {
        override fun push(batch: WallpaperBatch) {
            pushes.add(batch)
        }

        override fun invalidate() {}

        override fun surfaceDrawn(surfaceId: Int) {}

        override fun surfaceReleased(surfaceId: Int) {}

        override fun surfaceFailed(surfaceId: Int, code: Int) {}

        override fun wantEvents(flags: Int) {}

        override fun settings(requestId: Int, status: Int, settings: WallpaperSettings?) {
            answers.add(Triple(requestId, status, settings))
        }
    }

    @Before
    fun allowVideoAccess() {
        val access = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, access)
    }

    @After
    fun putBack() {
        connections.forEach(context::unbindService)
        store.write(saved)
    }

    private fun attached(): IWallpaperProvider {
        val bound = LinkedBlockingQueue<IBinder>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                bound.add(binder)
            }

            override fun onServiceDisconnected(name: ComponentName) {}
        }
        assertTrue(context.bindService(Intent(context, AltitudeService::class.java), connection, Context.BIND_AUTO_CREATE))
        connections += connection
        val provider = IWallpaperProvider.Stub.asInterface(bound.poll(10, SECONDS)!!)
        provider.attach(WallpaperEnvironment(1920, 1080, 320, "en-CA", WallpaperContract.API_VERSION), host)
        return provider
    }

    /** The first answer: a fresh process reads the bundled list and the media index first. */
    private fun answer(): WallpaperBatch = pushes.poll(60, SECONDS).also { assertNotNull("a push within 60 s", it) }!!

    private fun open(link: String, mode: String = "r") = resolver.openFileDescriptor(Uri.parse(link), mode)

    @Test
    fun aStartIsAnsweredWithAPush() {
        attached().request(WallpaperContract.REASON_START)
        answer()
    }

    /** The status and settings of the answer to settings call [requestId], which goes to the attached host. */
    private fun settingsAnswer(requestId: Int): Pair<Int, WallpaperSettings?> {
        val (id, status, settings) = answers.poll(10, SECONDS).also { assertNotNull("a settings answer within 10 s", it) }!!
        assertEquals(requestId, id)
        return status to settings
    }

    @Test
    fun aBackupIsAnsweredWithEverySettingAllPublic() {
        store.write(Settings(scenes = setOf(Scene.CITIES), quality = Quality.P1080, folder = TEST.toString()))
        attached().requestSettings(1)
        val (status, settings) = settingsAnswer(1)
        assertEquals(WallpaperContract.STATUS_OK, status)
        assertEquals(SettingsCodec.VERSION, settings!!.settingsVersion)
        val public = settings.public
        assertEquals(setOf("apple", "scenes", "quality", "folder"), public.keySet())
        assertTrue(public.getBoolean("apple"))
        assertEquals("cities", public.getString("scenes"))
        assertEquals("1080", public.getString("quality"))
        assertEquals(TEST.toString(), public.getString("folder"))
        assertTrue(settings.private.isEmpty)
    }

    @Test
    fun aRestoresSettingsAreKeptAndHandedBackAtTheNextBackup() {
        val provider = attached()
        // A newer version's shape: a key Altitude does not know, and an older version's Time of day.
        val restored = WallpaperSettings(2, Bundle().apply {
            putBoolean("apple", false)
            putString("scenes", "space,underwater")
            putString("quality", "1080")
            putString("folder", DRONE.toString())
            putString("time", "night")
            putInt("speed", 2)
        })
        provider.applySettings(2, restored)
        assertEquals(WallpaperContract.STATUS_OK to null, settingsAnswer(2))
        val expected = Settings(apple = false, scenes = setOf(Scene.UNDERWATER, Scene.SPACE), quality = Quality.P1080, folder = DRONE.toString())
        assertEquals(expected, store.read())
        provider.requestSettings(3)
        val (status, again) = settingsAnswer(3)
        assertEquals(WallpaperContract.STATUS_OK, status)
        assertEquals("underwater,space", again!!.public.getString("scenes"))
        assertEquals(expected, SettingsCodec.decode(again))
    }

    @Test
    fun withAppleOffAListIsTheFoldersVideosAlone() {
        store.write(Settings(apple = false, folder = TEST.toString()))
        attached().request(WallpaperContract.REASON_MANUAL)
        val items = answer().items
        assertEquals(setOf("Coast 1", "Coast 2", "Drone 1", "Drone 2"), items.map { it.title }.toSet())
        for (item in items) {
            assertTrue(item.id, item.id.startsWith("mine:${Folder.PRIMARY}:"))
            assertTrue(item.uri, item.uri.startsWith("content://${context.packageName}.videos/${Folder.PRIMARY}/"))
            assertEquals(WallpaperContract.TYPE_VIDEO, item.type)
            assertEquals(WallpaperContract.MODE_DEFAULT, item.mode)
        }
    }

    @Test
    fun theProviderOpensAVideoOfTheFolderAsAFileThePlayerCanSeek() {
        store.write(saved.copy(folder = DRONE.toString()))
        val drone = library.files(DRONE)
        assertEquals(setOf("Drone 1", "Drone 2"), drone.map { it.title }.toSet())
        for (file in drone) {
            open(file.link)!!.use { video ->
                assertTrue(video.statSize > 0)
                assertEquals(video.statSize, Os.lseek(video.fileDescriptor, 0, OsConstants.SEEK_END))
                // An MP4 file begins with its size and "ftyp".
                Os.lseek(video.fileDescriptor, 4, OsConstants.SEEK_SET)
                val box = ByteArray(4)
                assertEquals(4, Os.read(video.fileDescriptor, box, 0, 4))
                assertEquals("ftyp", String(box, Charsets.US_ASCII))
            }
        }
    }

    @Test
    fun theProviderRefusesEveryVideoOutsideTheFolder() {
        store.write(saved.copy(folder = DRONE.toString()))
        val coast = library.files(TEST).filter { it.title.startsWith("Coast") }
        assertEquals(2, coast.size)
        val drone = library.files(DRONE).first()
        val base = "content://${context.packageName}.videos"
        val links = coast.map { it.link } + listOf(
            drone.link.replace("/${Folder.PRIMARY}/", "/1a2b-3c4d/"), // its id, on another volume
            "$base/${Folder.PRIMARY}/999999999", // no such video
            "$base/${Folder.PRIMARY}",
            "$base/${Folder.PRIMARY}/${drone.id}/x",
        )
        for (link in links) {
            assertThrows(link, FileNotFoundException::class.java) { open(link)?.close() }
        }
        store.write(saved.copy(folder = ""))
        assertThrows("no folder chosen", FileNotFoundException::class.java) { open(drone.link)?.close() }
    }

    @Test
    fun theProviderRefusesEveryWrite() {
        store.write(saved.copy(folder = DRONE.toString()))
        val link = Uri.parse(library.files(DRONE).first().link)
        for (mode in listOf("w", "wt", "wa", "rw", "rwt")) {
            assertThrows(mode, SecurityException::class.java) { resolver.openFileDescriptor(link, mode)?.close() }
        }
        assertThrows(SecurityException::class.java) { resolver.insert(link, ContentValues()) }
        assertThrows(SecurityException::class.java) { resolver.update(link, ContentValues(), null, null) }
        assertThrows(SecurityException::class.java) { resolver.delete(link, null, null) }
        assertThrows(SecurityException::class.java) { resolver.query(link, null, null, null, null)?.close() }
    }

    @Test
    fun aTypeIsAnsweredWithoutALookup() {
        assertEquals("video/*", resolver.getType(Uri.parse("content://${context.packageName}.videos/${Folder.PRIMARY}/999999999")))
    }

    @Test
    fun theTreeCountsEachFolderWithItsSubfolders() {
        val tree = library.tree()
        assertTrue(tree.toString(), FolderTree.Entry(TEST, 4) in tree)
        assertTrue(tree.toString(), FolderTree.Entry(DRONE, 2) in tree)
    }

    private companion object {
        val TEST = Folder(Folder.PRIMARY, "Movies/Altitude test")
        val DRONE = Folder(Folder.PRIMARY, "Movies/Altitude test/Drone")
    }
}
