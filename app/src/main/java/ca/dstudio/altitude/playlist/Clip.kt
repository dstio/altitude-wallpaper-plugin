package ca.dstudio.altitude.playlist

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/** One of Apple's aerial clips, in Altitude's own shape. A clip has at least one link. */
@Serializable
data class Clip(
    /** Apple's id for the clip. */
    val id: String,
    /** The place, in English ("Hong Kong"). */
    val title: String,
    val scene: Scene,
    val link1080H264: String? = null,
    val link1080Hevc: String? = null,
    val link4kHevc: String? = null,
)

enum class Scene { LANDSCAPES, CITIES, UNDERWATER, SPACE }

/** Altitude's own JSON: the clip list (the bundled snapshot and the cache) and the kept answer. */
val AltitudeJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/** Writes [text] beside this file and renames it into place, so a reader never meets half a file. */
@Throws(IOException::class)
fun File.writeAtomically(text: String) {
    parentFile?.mkdirs()
    val tmp = File("$path.tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(this)) {
        tmp.delete()
        throw IOException("could not move $name into place")
    }
}
