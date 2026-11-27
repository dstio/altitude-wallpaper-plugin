package ca.dstudio.altitude.ui

import ca.dstudio.altitude.BuildConfig
import ca.dstudio.altitude.R
import ca.dstudio.altitude.ui.panel.Item
import ca.dstudio.altitude.ui.panel.Page

/** Altitude and its version, the launcher it is made for, who made the videos, the wallpaper API's notice and the licence. */
internal class AboutPage(private val screen: SettingsActivity) : Page() {

    override val icon = R.drawable.ic_page_about

    override fun title(): String = screen.getString(R.string.row_about)

    override fun subtitle(): String = screen.getString(R.string.wallpaper_description)

    override fun items(): List<Item> = listOf(
        Item.Info("altitude", screen.getString(R.string.app_name), screen.getString(R.string.about_version, BuildConfig.VERSION_NAME)),
        Item.Info("made-for", screen.getString(R.string.about_made_for)),
        Item.Info("videos", screen.getString(R.string.about_videos_title), screen.getString(R.string.about_videos)),
        // The wallpaper API's licence asks for this notice.
        Item.Info("api", screen.getString(R.string.about_api_title), screen.getString(R.string.about_api)),
        Item.Info("licence", screen.getString(R.string.about_licence_title), screen.getString(R.string.about_licence)),
    )
}
