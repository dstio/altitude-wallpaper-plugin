package ca.dstudio.altitude.apple

import ca.dstudio.altitude.playlist.Clip
import ca.dstudio.altitude.playlist.Scene
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Apple's `entries.json` (the aerials feed's list) to clips. An asset's scene is the category it
 * names; its title is its English label. HDR (Dolby Vision) links are not read. An asset with an
 * unknown scene or no https link is left out; a file that cannot be read is no clips.
 */
object AppleFeed {

    /** The launcher streams https alone. */
    private const val HTTPS = "https://"

    private val SCENES = mapOf(
        "AerialCategoryLandscapes" to Scene.LANDSCAPES,
        "AerialCategoryCities" to Scene.CITIES,
        "AerialCategoryUnderwater" to Scene.UNDERWATER,
        "AerialCategorySpace" to Scene.SPACE,
    )

    fun parse(bytes: ByteArray): List<Clip> = try {
        val root = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        val scenes = root.getValue("categories").jsonArray.mapNotNull { element ->
            val category = element as? JsonObject ?: return@mapNotNull null
            val scene = SCENES[category.text("localizedNameKey")] ?: return@mapNotNull null
            category.text("id")?.let { it to scene }
        }.toMap()
        root.getValue("assets").jsonArray
            .mapNotNull { asset -> (asset as? JsonObject)?.let { clipOf(it, scenes) } }
            .distinctBy { it.id }
    } catch (e: Exception) {
        emptyList()
    }

    private fun clipOf(asset: JsonObject, scenes: Map<String, Scene>): Clip? {
        val id = asset.text("id") ?: return null
        val title = asset.text("accessibilityLabel") ?: return null
        val scene = (asset["categories"] as? JsonArray)
            ?.firstNotNullOfOrNull { scenes[(it as? JsonPrimitive)?.contentOrNull] } ?: return null
        fun link(key: String) = asset.text(key)?.takeIf { it.startsWith(HTTPS) }
        val h264 = link("url-1080-H264")
        val hevc = link("url-1080-SDR")
        val fourK = link("url-4K-SDR")
        if (h264 == null && hevc == null && fourK == null) return null
        return Clip(id, title, scene, h264, hevc, fourK)
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
}
