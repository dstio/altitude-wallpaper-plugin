package ca.dstudio.altitude.mine

/**
 * A folder of the device's own videos: the media index's name for its volume (`external_primary`, or
 * a drive's own id such as `1a2b-3c4d`, which stays the same when the drive comes back) and its path
 * within that volume, `/`-separated with no leading slash; `""` is the volume's top. Stored as
 * `volume|path`. Volume names compare ignoring case.
 */
data class Folder(val volume: String, val path: String) {

    override fun toString() = "$volume$SEPARATOR$path"

    companion object {
        /** The media index's name for the device's own storage. */
        const val PRIMARY = "external_primary"

        private const val SEPARATOR = '|'

        /** A stored folder; null for none (`""`) or text that names no volume. */
        fun parse(text: String): Folder? {
            val volume = text.substringBefore(SEPARATOR, "")
            return if (volume.isBlank()) null else Folder(volume, text.substringAfter(SEPARATOR).trim('/'))
        }

        /**
         * The folder a video is in, from its full path, for Android 9 and older, whose media index has
         * no volume or relative path: `/storage/emulated/<user>/...` is [PRIMARY], `/storage/<id>/...`
         * a drive's, its id lower-cased as Android 10 and later name it. Null for a path elsewhere.
         */
        fun ofFile(fullPath: String): Folder? {
            val parts = fullPath.removePrefix("/storage/").takeIf { it != fullPath }?.split('/') ?: return null
            val (volume, inside) = if (parts[0] == "emulated") PRIMARY to parts.drop(2) else parts[0].lowercase() to parts.drop(1)
            return if (volume.isEmpty() || inside.isEmpty()) null else Folder(volume, inside.dropLast(1).joinToString("/"))
        }
    }
}
