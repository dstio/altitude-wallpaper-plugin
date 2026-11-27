package ca.dstudio.altitude.mine

/** The folders that hold the device's own videos, from where each video is; no Android in it. */
object FolderTree {

    /** A folder, and how many videos are in it and below it. */
    data class Entry(val folder: Folder, val count: Int)

    /**
     * Every folder with videos in it or below it, each volume's top included, with its count
     * (subfolders included), by volume, then by path with each folder's subfolders right after it.
     * [files] holds the folder each video is in, one per video.
     */
    fun folders(files: List<Folder>): List<Entry> = files
        .flatMap { file ->
            generateSequence(file.path) { path -> if (path.isEmpty()) null else path.substringBeforeLast('/', "") }
                .map { Folder(file.volume, it) }
        }
        .groupingBy { it }.eachCount()
        .map { (folder, count) -> Entry(folder, count) }
        // '/' made the lowest character, so a folder's subfolders sort before its siblings ("A/B" before "A B").
        .sortedWith(compareBy({ it.folder.volume }, { it.folder.path.replace('/', '\u0000') }))

    /** Whether a video in folder [file] is in [folder] or below it. */
    fun contains(folder: Folder, file: Folder): Boolean =
        folder.volume.equals(file.volume, ignoreCase = true) &&
            (folder.path.isEmpty() || file.path == folder.path || file.path.startsWith("${folder.path}/"))
}
