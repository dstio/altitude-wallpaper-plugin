package ca.dstudio.altitude.mine

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import ca.dstudio.altitude.playlist.MineFile

/**
 * The device's own videos, as Android's media index lists them on every volume it indexes (the
 * device's storage and each drive plugged in). Read fresh at every call and never kept; off the main
 * thread only. Without video access it lists none.
 */
class VideoLibrary(context: Context) {

    private val app = context.applicationContext

    /** The videos in [folder] and below it; none while its drive is out or the folder is gone. */
    fun files(folder: Folder): List<MineFile> = rows(null, null)
        .filter { FolderTree.contains(folder, it.folder) }
        .map { MineFile(it.folder.volume, it.id, it.name.substringBeforeLast('.'), it.modified, link(it.folder.volume, it.id)) }

    /** Every folder with videos in it or below it, with how many. */
    fun tree(): List<FolderTree.Entry> = FolderTree.folders(rows(null, null).map { it.folder })

    /** The folder video [id] is in, or null when the media index has no such video. */
    fun folderOf(id: Long): Folder? = rows("${MediaStore.MediaColumns._ID} = ?", arrayOf(id.toString())).firstOrNull()?.folder

    private class Row(val id: Long, val name: String, val modified: Long, val folder: Folder)

    private fun rows(selection: String?, args: Array<String>?): List<Row> = try {
        app.contentResolver.query(URI, projection(), selection, args, null)?.use { cursor ->
            buildList { while (cursor.moveToNext()) rowOf(cursor)?.let(::add) }
        }.orEmpty()
    } catch (e: Exception) {
        // Below Android 10 a read without access throws; from 10 it lists none.
        Log.w(TAG, "my videos: the media index is not readable (${e.javaClass.simpleName})")
        emptyList()
    }

    private fun projection(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.VOLUME_NAME, MediaStore.MediaColumns.RELATIVE_PATH)
    } else {
        @Suppress("DEPRECATION")
        arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATA)
    }

    /** A row of [projection]'s columns; null when the index does not say where the video is. */
    private fun rowOf(cursor: Cursor): Row? {
        val folder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // A video at a volume's top has the relative path "/".
            Folder(cursor.getString(3) ?: return null, (cursor.getString(4) ?: return null).trim('/'))
        } else {
            Folder.ofFile(cursor.getString(3) ?: return null) ?: return null
        }
        return Row(cursor.getLong(0), cursor.getString(1).orEmpty(), cursor.getLong(2), folder)
    }

    /** `content://<authority>/<volume>/<id>`, which [ca.dstudio.altitude.provider.VideoProvider] opens. */
    private fun link(volume: String, id: Long): String = Uri.Builder().scheme("content").authority("${app.packageName}.videos")
        .appendPath(volume).appendPath(id.toString()).build().toString()

    companion object {
        /** Every external volume's videos: below Android 10 the only one, from 10 a view of them all. */
        val URI: Uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        private const val TAG = "Altitude"
    }
}
