package ca.dstudio.altitude.provider

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import ca.dstudio.altitude.mine.Folder
import ca.dstudio.altitude.mine.FolderTree
import ca.dstudio.altitude.mine.VideoLibrary
import ca.dstudio.altitude.settings.AltitudeSettings
import ca.dstudio.atvlauncher.wallpaper.api.WallpaperContract
import java.io.FileNotFoundException

/**
 * Serves the videos of the chosen folder to ATV Launcher, read-only. A link is
 * `content://<authority>/<volume>/<media id>` ([VideoLibrary] makes them); it opens a video only when
 * the media index has it on that volume inside the chosen folder, and hands over the media index's
 * own descriptor, which the launcher's player can seek. Only a launcher holding
 * [WallpaperContract.PERMISSION_BIND_WALLPAPER] may read, and no link is granted to anyone.
 */
class VideoProvider : ContentProvider() {

    /** The entry is checked here, so a build that would serve the videos wrongly fails at its start. */
    override fun attachInfo(context: Context, info: ProviderInfo) {
        if (!info.exported || info.readPermission != WallpaperContract.PERMISSION_BIND_WALLPAPER || info.grantUriPermissions) {
            throw SecurityException(
                "${info.name} must be exported, with android:readPermission=\"${WallpaperContract.PERMISSION_BIND_WALLPAPER}\" " +
                    "and no grantUriPermissions",
            )
        }
        super.attachInfo(context, info)
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException(READ_ONLY)
        val context = context ?: throw FileNotFoundException("not attached")
        val id = videoId(context, uri) ?: run {
            Log.i(TAG, "videos: refused $uri: the media index lists no such video in the chosen folder")
            throw FileNotFoundException("$uri: not a video of the chosen folder")
        }
        return context.contentResolver.openFileDescriptor(ContentUris.withAppendedId(VideoLibrary.URI, id), "r")
            ?: throw FileNotFoundException("$uri: the media index gave no descriptor")
    }

    /** The media id [uri] names, when the media index has that video on the link's volume, inside the chosen folder. */
    private fun videoId(context: Context, uri: Uri): Long? {
        val (volume, text) = uri.pathSegments.takeIf { it.size == 2 } ?: return null
        val id = text.toLongOrNull() ?: return null
        val folder = Folder.parse(AltitudeSettings(context).read().folder) ?: return null
        val file = VideoLibrary(context).folderOf(id) ?: return null
        return id.takeIf { file.volume.equals(volume, ignoreCase = true) && FolderTree.contains(folder, file) }
    }

    /** A constant: Android does not guard this call with the read permission, so a lookup would tell any app which ids exist. */
    override fun getType(uri: Uri): String = "video/*"

    // The entry has no write permission, so Android lets any app make these calls: each is refused here.

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
        throw SecurityException(READ_ONLY)

    override fun insert(uri: Uri, values: ContentValues?): Uri = throw SecurityException(READ_ONLY)

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw SecurityException(READ_ONLY)

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw SecurityException(READ_ONLY)

    private companion object {
        const val TAG = "Altitude"
        const val READ_ONLY = "Altitude's videos are read-only"
    }
}
