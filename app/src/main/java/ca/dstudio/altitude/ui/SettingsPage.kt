package ca.dstudio.altitude.ui

import ca.dstudio.altitude.R
import ca.dstudio.altitude.mine.Folder
import ca.dstudio.altitude.playlist.Scene
import ca.dstudio.altitude.settings.Quality
import ca.dstudio.altitude.ui.panel.Item
import ca.dstudio.altitude.ui.panel.Page

/**
 * The page (A1): every setting one press away. Sources (Apple's switch, My videos), Scenes (a box
 * each), Playback (Quality) and About. The header counts the clips the choices leave. The last source
 * and the last scene can't be turned off: OK on them does nothing.
 */
internal class SettingsPage(private val screen: SettingsActivity) : Page() {

    override val icon = R.drawable.ic_page_altitude

    override fun title(): String = screen.getString(R.string.app_name)

    override fun subtitle(): String? {
        if (!screen.counted) return null
        val state = screen.state()
        return if (state.nothingToPlay) {
            screen.getString(R.string.nothing_to_play)
        } else {
            screen.resources.getQuantityString(R.plurals.clips_match, state.matching, state.matching)
        }
    }

    override fun subtitleWarns(): Boolean = screen.counted && screen.state().nothingToPlay

    override fun items(): List<Item> {
        val state = screen.state()
        val settings = state.settings
        val counted = screen.counted
        return listOf(
            Item.Header("h-sources", screen.getString(R.string.section_sources)),
            Item.Toggle(
                "apple",
                screen.getString(R.string.row_apple),
                screen.resources.getQuantityString(R.plurals.apple_clips, state.appleClips, state.appleClips).takeIf { counted },
                on = settings.apple,
                switch = true,
            ) {
                val now = screen.state()
                if (now.appleSwitchable) screen.write(now.settings.copy(apple = !now.settings.apple))
            },
            myVideos(state, counted),
            Item.Header("h-scenes", screen.getString(R.string.section_scenes)),
        ) + Scene.entries.map { scene ->
            Item.Toggle("scene-${scene.name}", screen.getString(nameOf(scene)), on = scene in settings.scenes) {
                val now = screen.state()
                val scenes = now.settings.scenes
                if (now.sceneSwitchable(scene)) screen.write(now.settings.copy(scenes = if (scene in scenes) scenes - scene else scenes + scene))
            }
        } + listOf(
            Item.Header("h-playback", screen.getString(R.string.section_playback)),
            quality(settings.quality),
            Item.Link("about", screen.getString(R.string.row_about)) { screen.panel.push(AboutPage(screen)) },
        )
    }

    /** My videos' row: the folder and its count (C1), Not connected (C4), or how to allow access (C3). */
    private fun myVideos(state: PageState, counted: Boolean): Item {
        val place = Folder.parse(state.settings.folder)?.let(::place).orEmpty()
        val line = when (state.folderLine) {
            FolderLine.CHOOSE -> screen.getString(R.string.choose_a_folder)
            FolderLine.ALLOW -> screen.getString(R.string.allow_video_access)
            FolderLine.ALLOW_IN_APP_INFO -> screen.getString(R.string.allow_video_access_app_info)
            FolderLine.FOLDER -> screen.resources.getQuantityString(R.plurals.folder_videos, state.folderVideos, place, state.folderVideos)
            FolderLine.NOT_CONNECTED -> if (counted) screen.getString(R.string.folder_not_connected, place) else place
        }
        val granted = state.access == Access.GRANTED
        return Item.Link(
            "my-videos",
            screen.getString(R.string.row_my_videos),
            line,
            warn = counted && state.folderLine == FolderLine.NOT_CONNECTED,
            hint = screen.getString(if (granted) R.string.choose_a_folder else R.string.hint_allow),
        ) { screen.openMyVideos() }
    }

    /** "USB drive › Videos › Drone": the drive's name, then each folder. */
    private fun place(folder: Folder): String =
        (listOf(screen.volumeName(folder.volume)) + folder.path.split('/').filter { it.isNotEmpty() })
            .joinToString(screen.getString(R.string.place_separator))

    /** Quality's dropdown: Auto says what it plays on this device, once that is known. */
    private fun quality(quality: Quality): Item {
        val auto = screen.facts?.let { screen.getString(if (it.plays4k) R.string.quality_4k else R.string.quality_1080) }
        return Item.Choice(
            "quality",
            screen.getString(R.string.row_quality),
            options = listOf(screen.getString(R.string.quality_auto), screen.getString(R.string.quality_1080)),
            lines = listOf(auto?.let { screen.getString(R.string.quality_auto_line, it) }, screen.getString(R.string.quality_1080_line)),
            selected = if (quality == Quality.AUTO) 0 else 1,
            value = auto?.takeIf { quality == Quality.AUTO }?.let { screen.getString(R.string.quality_auto_value, it) },
        ) { chosen -> screen.write(screen.store.read().copy(quality = if (chosen == 0) Quality.AUTO else Quality.P1080)) }
    }

    private fun nameOf(scene: Scene): Int = when (scene) {
        Scene.LANDSCAPES -> R.string.scene_landscapes
        Scene.CITIES -> R.string.scene_cities
        Scene.UNDERWATER -> R.string.scene_underwater
        Scene.SPACE -> R.string.scene_space
    }
}
