package ca.dstudio.altitude.ui

import ca.dstudio.altitude.R
import ca.dstudio.altitude.mine.Folder
import ca.dstudio.altitude.mine.FolderTree
import ca.dstudio.altitude.ui.panel.Item
import ca.dstudio.altitude.ui.panel.Page

/**
 * The folder list (C2): None, then each drive's folders that hold videos (this device first), with how
 * many each holds, subfolders included, the chosen one ticked. The list is read once, off the main
 * thread, when the page opens - nothing shows for it until then - and never counted again while it is
 * up. OK chooses and goes back to the page; None does nothing while Apple is off.
 */
internal class FoldersPage(private val screen: SettingsActivity) : Page() {

    private var folders: List<FolderTree.Entry>? = null

    init {
        screen.readTree { tree ->
            folders = tree
            // The cursor goes to the chosen folder once it is listed.
            if (screen.panel.page === this) screen.panel.refresh(focus = Folder.parse(screen.store.read().folder)?.let(::keyOf))
        }
    }

    override val icon = R.drawable.ic_page_folder

    override fun title(): String = screen.getString(R.string.row_my_videos)

    override fun subtitle(): String = screen.getString(R.string.choose_a_folder)

    override fun items(): List<Item> {
        val chosen = Folder.parse(screen.store.read().folder)
        val rows = arrayListOf<Item>(
            Item.Radio("none", screen.getString(R.string.folder_none), screen.getString(R.string.folder_none_line), chosen = chosen == null) {
                if (screen.state().noneChoosable) screen.chooseFolder(null, 0)
            },
        )
        // A drive's top is not offered as a folder; the folders on it are.
        val byVolume = folders.orEmpty().filter { it.folder.path.isNotEmpty() }.groupBy { it.folder.volume.lowercase() }
        for ((volume, entries) in byVolume.entries.sortedBy { it.key != Folder.PRIMARY }) {
            rows += Item.Header("h-$volume", screen.volumeName(volume))
            for (entry in entries) {
                val folder = entry.folder
                rows += Item.Radio(
                    keyOf(folder),
                    folder.path.split('/').joinToString(screen.getString(R.string.place_separator)),
                    value = screen.resources.getQuantityString(R.plurals.videos, entry.count, entry.count),
                    chosen = chosen != null && keyOf(chosen) == keyOf(folder),
                ) { screen.chooseFolder(folder, entry.count) }
            }
        }
        return rows
    }

    /** A folder's row key; volume names compare ignoring case. */
    private fun keyOf(folder: Folder) = "f-${folder.volume.lowercase()}|${folder.path}"
}
