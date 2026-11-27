package ca.dstudio.altitude.playlist

import ca.dstudio.altitude.settings.Quality
import ca.dstudio.altitude.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistTest {

    private val noHevc = DeviceFacts(decodes4kHevc = false, panel4k = false, decodes1080Hevc = false)
    private val all4k = DeviceFacts(decodes4kHevc = true, panel4k = true, decodes1080Hevc = true)

    private fun clip(
        n: Int,
        scene: Scene = Scene.LANDSCAPES,
        h264: Boolean = true,
        hevc: Boolean = true,
        fourK: Boolean = true,
    ) = Clip(
        id = "c$n",
        title = "Place $n",
        scene = scene,
        link1080H264 = "https://example.invalid/$n-h264.mov".takeIf { h264 },
        link1080Hevc = "https://example.invalid/$n-hevc.mov".takeIf { hevc },
        link4kHevc = "https://example.invalid/$n-4k.mov".takeIf { fourK },
    )

    private fun mine(n: Int, modified: Long = 1_700_000_000) = MineFile(
        volume = "external_primary",
        id = n.toLong(),
        title = "Clip $n",
        dateModified = modified,
        link = "content://example.invalid/external_primary/$n",
    )

    /** Every scene, and two of Landscapes and Cities. */
    private val mixed = listOf(
        clip(1, Scene.LANDSCAPES),
        clip(2, Scene.LANDSCAPES),
        clip(3, Scene.CITIES),
        clip(4, Scene.CITIES),
        clip(5, Scene.UNDERWATER),
        clip(6, Scene.SPACE),
    )

    private fun items(
        settings: Settings,
        clips: List<Clip> = mixed,
        own: List<MineFile> = emptyList(),
        facts: DeviceFacts = noHevc,
        seed: Long = 1,
    ): List<Item> = (Playlist.make(settings, clips, own, facts, seed) as Answer.Batch).items

    private fun clipIds(settings: Settings) = items(settings).map { it.id.split(':')[1] }.toSet()

    /** How many of a list's items came from [source]: `apple` or `mine`. */
    private fun List<Item>.from(source: String) = count { it.id.startsWith("$source:") }

    @Test
    fun eachSceneAlone() {
        assertEquals(setOf("c1", "c2"), clipIds(Settings(scenes = setOf(Scene.LANDSCAPES))))
        assertEquals(setOf("c3", "c4"), clipIds(Settings(scenes = setOf(Scene.CITIES))))
        assertEquals(setOf("c5"), clipIds(Settings(scenes = setOf(Scene.UNDERWATER))))
        assertEquals(setOf("c6"), clipIds(Settings(scenes = setOf(Scene.SPACE))))
    }

    @Test
    fun scenesCombine() {
        assertEquals(setOf("c1", "c2", "c6"), clipIds(Settings(scenes = setOf(Scene.LANDSCAPES, Scene.SPACE))))
        assertEquals(setOf("c1", "c2", "c3", "c4", "c5", "c6"), clipIds(Settings()))
    }

    @Test
    fun appleOffMatchesNothing() {
        assertEquals(Answer.NothingMatches, Playlist.make(Settings(apple = false), mixed, emptyList(), all4k, 1))
    }

    @Test
    fun autoIs4kOnlyWhenTheDeviceDecodesItAndThePanelIs4k() {
        fun link(facts: DeviceFacts) = items(Settings(), listOf(clip(1)), facts = facts).single().link
        assertEquals("https://example.invalid/1-4k.mov", link(all4k))
        assertEquals("https://example.invalid/1-hevc.mov", link(DeviceFacts(decodes4kHevc = true, panel4k = false, decodes1080Hevc = true)))
        assertEquals("https://example.invalid/1-hevc.mov", link(DeviceFacts(decodes4kHevc = false, panel4k = true, decodes1080Hevc = true)))
        assertEquals("https://example.invalid/1-h264.mov", link(DeviceFacts(decodes4kHevc = false, panel4k = true, decodes1080Hevc = false)))
        assertEquals("https://example.invalid/1-h264.mov", link(noHevc))
    }

    @Test
    fun autoTakes1080WhenTheClipHasNo4k() {
        val hevc = items(Settings(), listOf(clip(1, fourK = false)), facts = all4k).single()
        assertEquals("https://example.invalid/1-hevc.mov", hevc.link)
        assertEquals("apple:c1:1080", hevc.id)
        val h264 = items(Settings(), listOf(clip(1, hevc = false, fourK = false)), facts = all4k).single()
        assertEquals("https://example.invalid/1-h264.mov", h264.link)
    }

    @Test
    fun p1080IsHevcWhereTheDeviceDecodesItElseH264() {
        val settings = Settings(quality = Quality.P1080)
        fun link(clip: Clip, facts: DeviceFacts) = items(settings, listOf(clip), facts = facts).single().link
        assertEquals("https://example.invalid/1-hevc.mov", link(clip(1), all4k))
        assertEquals("https://example.invalid/1-h264.mov", link(clip(1), noHevc))
        // Each falls back to the other link when the clip lacks its own.
        assertEquals("https://example.invalid/1-h264.mov", link(clip(1, hevc = false), all4k))
        assertEquals("https://example.invalid/1-hevc.mov", link(clip(1, h264 = false), noHevc))
        // A clip with a 4K link alone has nothing to play at 1080p.
        assertEquals(Answer.NothingMatches, Playlist.make(settings, listOf(clip(1, h264 = false, hevc = false)), emptyList(), all4k, 1))
    }

    @Test
    fun idsDifferBetween4kAnd1080() {
        assertEquals("apple:c1:4k", items(Settings(), listOf(clip(1)), facts = all4k).single().id)
        assertEquals("apple:c1:1080", items(Settings(quality = Quality.P1080), listOf(clip(1)), facts = all4k).single().id)
    }

    @Test
    fun anItemCarriesItsClipsTitle() {
        assertEquals("Place 1", items(Settings(), listOf(clip(1))).single().title)
    }

    @Test
    fun aListHoldsAtMost60() {
        val many = (1..61).map { clip(it) }
        assertEquals(60, items(Settings(), many).size)
        assertEquals(60, items(Settings(), many).map { it.id }.toSet().size)
    }

    @Test
    fun theSeedDecidesTheOrder() {
        val many = (1..20).map { clip(it) }
        fun order(seed: Long) = items(Settings(), many, seed = seed).map { it.id }
        assertEquals(order(7), order(7))
        assertNotEquals(order(7), order(8))
    }

    @Test
    fun countIsTheFilteredSizeBeforeTheCap() {
        val clips = (1..70).map { clip(it, Scene.CITIES) } + (71..75).map { clip(it, Scene.SPACE) }
        assertEquals(70, Playlist.count(Settings(scenes = setOf(Scene.CITIES)), clips, emptyList()))
        assertEquals(75, Playlist.count(Settings(), clips, emptyList()))
        assertEquals(60, items(Settings(), clips).size)
    }

    // My videos.

    @Test
    fun bothFittingTheCapAreTakenWhole() {
        val list = items(Settings(), (1..10).map { clip(it) }, (1..5).map { mine(it) })
        assertEquals(10, list.from("apple"))
        assertEquals(5, list.from("mine"))
    }

    @Test
    fun theCapIsSharedInProportionToWhatEachHas() {
        val list = items(Settings(), (1..120).map { clip(it) }, (1..24).map { mine(it) })
        assertEquals(50, list.from("apple"))
        assertEquals(10, list.from("mine"))
    }

    @Test
    fun aBigSourceLeavesTheOtherAtLeastOne() {
        val bigFolder = items(Settings(), (1..3).map { clip(it) }, (1..1000).map { mine(it) })
        assertEquals(1, bigFolder.from("apple"))
        assertEquals(59, bigFolder.from("mine"))
        val oneVideo = items(Settings(), (1..1000).map { clip(it) }, listOf(mine(1)))
        assertEquals(59, oneVideo.from("apple"))
        assertEquals(1, oneVideo.from("mine"))
    }

    @Test
    fun appleOffPlaysMyVideosAlone() {
        val list = items(Settings(apple = false), mixed, (1..4).map { mine(it) })
        assertEquals(setOf("Clip 1", "Clip 2", "Clip 3", "Clip 4"), list.map { it.title }.toSet())
        assertEquals(60, items(Settings(apple = false), mixed, (1..70).map { mine(it) }).from("mine"))
    }

    @Test
    fun aFolderWithNoVideosLeavesAppleAlone() {
        val list = items(Settings(folder = "external_primary|Movies"), mixed, emptyList())
        assertEquals(6, list.from("apple"))
        assertEquals(6, list.size)
    }

    @Test
    fun neitherSourceWithAVideoMatchesNothing() {
        assertEquals(Answer.NothingMatches, Playlist.make(Settings(apple = false, folder = "external_primary|Movies"), mixed, emptyList(), noHevc, 1))
    }

    @Test
    fun theFiltersNeverDropAVideoOfMine() {
        // Space at 1080p, from a list with no Space clip: none of Apple's clips here.
        val settings = Settings(scenes = setOf(Scene.SPACE), quality = Quality.P1080)
        val noSpace = mixed.filter { it.scene != Scene.SPACE }
        val own = (1..5).map { mine(it) }
        assertEquals(own.map { it.link }.toSet(), items(settings, noSpace, own).map { it.link }.toSet())
        assertEquals(5, Playlist.count(settings, noSpace, own))
    }

    @Test
    fun aVideoOfMineIsNamedByItsVolumeIdAndDate() {
        val item = items(Settings(apple = false), own = listOf(mine(7, modified = 100))).single()
        assertEquals("mine:external_primary:7:100", item.id)
        assertEquals("content://example.invalid/external_primary/7", item.link)
        assertEquals("Clip 7", item.title)
        val replaced = items(Settings(apple = false), own = listOf(mine(7, modified = 200))).single()
        assertNotEquals(item.id, replaced.id)
    }

    @Test
    fun countAddsMyVideos() {
        assertEquals(10, Playlist.count(Settings(), mixed, (1..4).map { mine(it) }))
    }

    @Test
    fun bothSourcesAreShuffledTogether() {
        fun sources(seed: Long) = items(Settings(), (1..10).map { clip(it) }, (1..10).map { mine(it) }, seed = seed)
            .map { it.id.substringBefore(':') }
        assertEquals(sources(7), sources(7))
        assertNotEquals(List(10) { "apple" } + List(10) { "mine" }, sources(7))
    }

    // Behind the settings panel, after a change.

    private fun backdrop(playing: Item, settings: Settings, own: List<MineFile> = emptyList(), facts: DeviceFacts = noHevc) =
        Playlist.backdrop(playing, settings, mixed, own, facts, seed = 1)

    /** Clip [n] as a list with every scene hands it over. */
    private fun apple(n: Int, facts: DeviceFacts = noHevc) = items(Settings(), facts = facts).single { it.id.startsWith("apple:c$n:") }

    private fun ownItem(n: Int) = items(Settings(apple = false), own = listOf(mine(n))).single()

    @Test
    fun aVideoThatStillMatchesPlaysOn() {
        assertEquals(apple(3), backdrop(apple(3), Settings(scenes = setOf(Scene.CITIES, Scene.SPACE))))
        assertEquals(ownItem(2), backdrop(ownItem(2), Settings(scenes = setOf(Scene.SPACE)), own = listOf(mine(1), mine(2))))
    }

    @Test
    fun aClipWhoseSceneIsUntickedGivesWayToOneThatMatches() {
        val settings = Settings(scenes = setOf(Scene.LANDSCAPES))
        val next = backdrop(apple(3), settings)!!
        assertTrue(next.id, next.id in items(settings).map { it.id })
    }

    @Test
    fun appleTurnedOffGivesWayToAVideoOfMine() {
        assertEquals(ownItem(1), backdrop(apple(1), Settings(apple = false, folder = "external_primary|Movies"), own = listOf(mine(1))))
    }

    @Test
    fun aVideoOfMineOutsideTheNewFolderGivesWay() {
        val other = Settings(apple = false, folder = "external_primary|Movies/Other")
        assertEquals(ownItem(2), backdrop(ownItem(1), other, own = listOf(mine(2))))
        // None: one of Apple's clips.
        assertTrue(backdrop(ownItem(1), Settings())!!.id.startsWith("apple:"))
    }

    @Test
    fun aQualityChangePlaysTheSameClipAtItsNewLink() {
        val fourK = apple(5, facts = all4k)
        assertEquals("https://example.invalid/5-4k.mov", fourK.link)
        val next = backdrop(fourK, Settings(quality = Quality.P1080), facts = all4k)
        assertEquals(Item("apple:c5:1080", "https://example.invalid/5-hevc.mov", "Place 5"), next)
    }

    @Test
    fun nothingMatchingGivesThePlainFill() {
        assertNull(backdrop(apple(1), Settings(apple = false, folder = "external_primary|Nowhere")))
    }
}
