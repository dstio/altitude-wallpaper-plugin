package ca.dstudio.altitude.apple

import ca.dstudio.altitude.playlist.Clip
import ca.dstudio.altitude.playlist.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleFeedTest {

    /** Apple's shape, made up: no id, name or link here is Apple's. */
    private val feed = """
        {
          "version": 1,
          "initialAssetCount": 2,
          "categories": [
            { "id": "CAT-L", "localizedNameKey": "AerialCategoryLandscapes", "preferredOrder": 0 },
            { "id": "CAT-C", "localizedNameKey": "AerialCategoryCities", "preferredOrder": 1 },
            { "id": "CAT-U", "localizedNameKey": "AerialCategoryUnderwater", "preferredOrder": 2 },
            { "id": "CAT-S", "localizedNameKey": "AerialCategorySpace", "preferredOrder": 3 },
            { "id": "CAT-X", "localizedNameKey": "AerialCategoryMoon", "preferredOrder": 4 }
          ],
          "assets": [
            {
              "id": "A1", "accessibilityLabel": "Test Valley", "categories": ["CAT-L"], "shotID": "T1",
              "pointsOfInterest": { "0": "T1_0" },
              "url-1080-H264": "https://example.invalid/a1-h264.mov",
              "url-1080-SDR": "https://example.invalid/a1-hevc.mov",
              "url-4K-SDR": "https://example.invalid/a1-4k.mov",
              "url-1080-HDR": "https://example.invalid/a1-hdr.mov",
              "url-4K-HDR": "https://example.invalid/a1-4k-hdr.mov"
            },
            { "id": "A2", "accessibilityLabel": "Test Harbour Night", "categories": ["CAT-C"], "group": "T",
              "url-1080-H264": "https://example.invalid/a2-h264.mov" },
            { "id": "A3", "accessibilityLabel": "Test Reef", "categories": ["CAT-U"],
              "url-1080-HDR": "https://example.invalid/a3-hdr.mov", "url-4K-HDR": "https://example.invalid/a3-4k-hdr.mov" },
            { "id": "A4", "accessibilityLabel": "Test Crater", "categories": ["CAT-X"],
              "url-1080-H264": "https://example.invalid/a4-h264.mov" },
            { "id": "A5", "accessibilityLabel": "Test Orbit", "categories": ["CAT-S"],
              "url-1080-H264": "http://example.invalid/a5-h264.mov" },
            { "id": "A6", "accessibilityLabel": "Test Coast", "categories": ["CAT-L"] },
            "not an asset"
          ]
        }
    """.trimIndent()

    @Test
    fun readsTheFieldsAndLeavesOutWhatCannotPlay() {
        assertEquals(
            listOf(
                Clip(
                    id = "A1",
                    title = "Test Valley",
                    scene = Scene.LANDSCAPES,
                    link1080H264 = "https://example.invalid/a1-h264.mov",
                    link1080Hevc = "https://example.invalid/a1-hevc.mov",
                    link4kHevc = "https://example.invalid/a1-4k.mov",
                ),
                Clip("A2", "Test Harbour Night", Scene.CITIES, link1080H264 = "https://example.invalid/a2-h264.mov"),
            ),
            // A3 has HDR links alone, A4 an unknown scene, A5 no https link, A6 no link.
            AppleFeed.parse(feed.toByteArray()),
        )
    }

    @Test
    fun anUnreadableFileIsNoClips() {
        for (garbage in listOf("", "not json", "[]", "{}", """{"categories": [], "assets": 5}""", "\u0000\u0001")) {
            assertTrue(garbage, AppleFeed.parse(garbage.toByteArray()).isEmpty())
        }
    }
}
