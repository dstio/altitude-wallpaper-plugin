package ca.dstudio.altitude.ui

import ca.dstudio.altitude.mine.Folder
import ca.dstudio.altitude.playlist.Scene
import ca.dstudio.altitude.settings.Settings

/** Whether Altitude may read the device's videos, and if not, how it asks. */
enum class Access {
    GRANTED,

    /** Android's own prompt. */
    ASK,

    /** Android shows no prompt any more: Altitude's app info is the way. */
    APP_INFO,
    ;

    companion object {
        /**
         * Android says no rationale both before the first ask and after a refusal it won't ask about
         * again, so whether Altitude [askedOnce] tells the two apart. Some videos chosen ([partial],
         * Android 14's limited access) counts as no access: Android always asks again for that.
         */
        fun of(granted: Boolean, partial: Boolean, askedOnce: Boolean, rationale: Boolean): Access = when {
            granted -> GRANTED
            partial || !askedOnce || rationale -> ASK
            else -> APP_INFO
        }
    }
}

/** What My videos' row says. */
enum class FolderLine { CHOOSE, FOLDER, NOT_CONNECTED, ALLOW, ALLOW_IN_APP_INFO }

/**
 * The settings page's rules, from the settings and what was counted, with no Android in it: what OK
 * may change - the last source and the last scene can't be turned off - and what the lines say.
 */
data class PageState(
    val settings: Settings,
    val access: Access,
    /** Apple's clips that pass the scenes, whatever Apple's switch says. */
    val appleClips: Int,
    /** The videos Altitude can read in the chosen folder and below it. */
    val folderVideos: Int,
) {
    private val hasFolder get() = Folder.parse(settings.folder) != null

    /** OK flips Apple's switch, unless Apple is the last source on. */
    val appleSwitchable get() = !settings.apple || hasFolder

    /** None turns My videos off, unless it is the last source on. */
    val noneChoosable get() = settings.apple

    /** OK ticks or unticks [scene], unless it is the last one ticked. */
    fun sceneSwitchable(scene: Scene) = scene !in settings.scenes || settings.scenes.size > 1

    /** What the next list draws on, before the cap: the header's count. */
    val matching get() = (if (settings.apple) appleClips else 0) + (if (hasFolder && access == Access.GRANTED) folderVideos else 0)

    /** Nothing can play: the header says so in amber. With a scene always ticked, only Apple off and the folder gone come to it. */
    val nothingToPlay get() = matching == 0

    val folderLine: FolderLine get() = when {
        access == Access.ASK -> FolderLine.ALLOW
        access == Access.APP_INFO -> FolderLine.ALLOW_IN_APP_INFO
        !hasFolder -> FolderLine.CHOOSE
        folderVideos == 0 -> FolderLine.NOT_CONNECTED
        else -> FolderLine.FOLDER
    }
}
