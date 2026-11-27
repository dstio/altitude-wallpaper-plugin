package ca.dstudio.altitude.playlist

import ca.dstudio.altitude.settings.Quality
import ca.dstudio.altitude.settings.Settings
import ca.dstudio.atvlauncher.wallpaper.api.WallpaperContract
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** One video of a list: what the launcher is handed as a video item. */
@Serializable
data class Item(
    /**
     * Names the video's content: `apple:<clip id>:<4k|1080>`, so a quality change is new content, or
     * `mine:<volume>:<media id>:<date modified>`, so a file replaced under the same name is too.
     */
    val id: String,
    val link: String,
    val title: String,
)

/** What a list comes to. */
sealed interface Answer {
    data class Batch(val items: List<Item>) : Answer

    /** Nothing passes the settings: the launcher keeps what it shows. */
    data object NothingMatches : Answer
}

/** Every rule that turns the settings, Apple's clips and the user's own videos into a list, with no Android in it. */
object Playlist {

    private const val CAP = WallpaperContract.MAX_ITEMS

    /**
     * Apple's clips that pass the scenes, each at its quality, and the videos of the chosen folder
     * ([mine], which no filter touches), in an order [seed] decides, at most the wallpaper API's cap
     * ([WallpaperContract.MAX_ITEMS]) shared between the two ([shares]). Auto is 4K HEVC where [facts]
     * say this device plays it and the clip has it; otherwise, as for 1080p, the clip's 1080p HEVC where
     * this device decodes it (a third less data), else its 1080p H.264, each falling back to the other.
     */
    fun make(settings: Settings, apple: List<Clip>, mine: List<MineFile>, facts: DeviceFacts, seed: Long): Answer {
        val appleItems = appleItems(settings, apple, facts)
        val mineItems = mineItems(mine)
        val random = Random(seed)
        val (appleShare, mineShare) = shares(appleItems.size, mineItems.size)
        val items = (appleItems.shuffled(random).take(appleShare) + mineItems.shuffled(random).take(mineShare)).shuffled(random)
        return if (items.isEmpty()) Answer.NothingMatches else Answer.Batch(items)
    }

    /**
     * What plays behind the settings after a change: [playing] while it still matches the settings, at
     * its link for them (new only when the quality moved it), else the first of a fresh list ([make]
     * with [seed]); null when nothing matches.
     */
    fun backdrop(playing: Item?, settings: Settings, apple: List<Clip>, mine: List<MineFile>, facts: DeviceFacts, seed: Long): Item? {
        val video = playing?.let { videoOf(it.id) }
        return (appleItems(settings, apple, facts) + mineItems(mine)).firstOrNull { videoOf(it.id) == video }
            ?: (make(settings, apple, mine, facts, seed) as? Answer.Batch)?.items?.first()
    }

    /** An id without its last part (the quality, or the file's date): the same video whatever its link. */
    private fun videoOf(id: String) = id.substringBeforeLast(':')

    private fun appleItems(settings: Settings, apple: List<Clip>, facts: DeviceFacts): List<Item> {
        val fourK = settings.quality == Quality.AUTO && facts.plays4k
        return matching(settings, apple).mapNotNull { clip ->
            val link4k = clip.link4kHevc?.takeIf { fourK }
            val link1080 = if (facts.decodes1080Hevc) clip.link1080Hevc ?: clip.link1080H264 else clip.link1080H264 ?: clip.link1080Hevc
            val link = link4k ?: link1080 ?: return@mapNotNull null
            Item("apple:${clip.id}:${if (link4k != null) "4k" else "1080"}", link, clip.title)
        }
    }

    private fun mineItems(mine: List<MineFile>) = mine.map { Item("mine:${it.volume}:${it.id}:${it.dateModified}", it.link, it.title) }

    /**
     * How many of [apple] and of [mine] a list takes: all of both while they fit the cap, else the cap
     * shared in proportion to what each has, at least one each, so neither pushes the other out.
     */
    private fun shares(apple: Int, mine: Int): Pair<Int, Int> {
        val total = apple + mine
        if (total <= CAP) return apple to mine
        val mineShare = ((CAP * mine + total / 2) / total).coerceIn(minOf(mine, 1), CAP - minOf(apple, 1))
        return CAP - mineShare to mineShare
    }

    /** How many videos the settings let through, before the cap: Apple's clips that match, and every one of [mine]. */
    fun count(settings: Settings, apple: List<Clip>, mine: List<MineFile>): Int = matching(settings, apple).size + mine.size

    private fun matching(settings: Settings, apple: List<Clip>): List<Clip> =
        if (!settings.apple) {
            emptyList()
        } else {
            apple.filter { it.scene in settings.scenes }
        }
}
