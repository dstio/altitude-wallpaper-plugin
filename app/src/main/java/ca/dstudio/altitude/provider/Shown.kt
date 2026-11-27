package ca.dstudio.altitude.provider

import android.content.Context

/** The id of the item the launcher last reported on screen, so the settings can play that clip behind their panel. */
object Shown {

    private const val FILE = "shown"
    private const val ID = "id"

    fun write(context: Context, id: String) = prefs(context).edit().putString(ID, id).apply()

    fun read(context: Context): String? = prefs(context).getString(ID, null)

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
