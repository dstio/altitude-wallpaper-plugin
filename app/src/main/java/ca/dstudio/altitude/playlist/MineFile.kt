package ca.dstudio.altitude.playlist

/** One of the device's own videos in the chosen folder, as the media index lists it. */
data class MineFile(
    /** The media index's volume it is on. */
    val volume: String,
    /** The media index's id for it. */
    val id: Long,
    /** Its file name without the extension. */
    val title: String,
    /** When the file last changed, in seconds: a file replaced under the same name is new content. */
    val dateModified: Long,
    /** Altitude's own `content://` link to it, which only ATV Launcher can open. */
    val link: String,
)
