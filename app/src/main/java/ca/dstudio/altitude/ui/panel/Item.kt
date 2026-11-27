package ca.dstudio.altitude.ui.panel

/**
 * One row of a settings page. [key] names the row across rebuilds, so the cursor stays on it when
 * the page is redrawn; [signature] is what the row shows, so a rebuild only rebinds rows that changed.
 */
sealed class Item(val key: String) {

    abstract val signature: String

    /** A section's name: not focusable. */
    class Header(key: String, val text: String) : Item(key) {
        override val signature get() = "h|$text"
    }

    /**
     * Opens another page (or asks for something); shows a chevron. [warn] draws the description in
     * amber; [hint] names what OK does in the footer, Open when null.
     */
    class Link(
        key: String,
        val title: String,
        val description: String? = null,
        val value: String? = null,
        val warn: Boolean = false,
        val hint: String? = null,
        val onOpen: () -> Unit,
    ) : Item(key) {
        override val signature get() = "l|$title|$description|$value|$warn"
    }

    /**
     * One of [options], chosen from a dropdown list; [lines] puts a line under each option. [value]
     * overrides what the row shows.
     */
    class Choice(
        key: String,
        val title: String,
        val description: String? = null,
        val options: List<String>,
        val lines: List<String?> = emptyList(),
        val selected: Int,
        val value: String? = null,
        val onChoose: (Int) -> Unit,
    ) : Item(key) {
        val shown: String? get() = value ?: options.getOrNull(selected)
        override val signature get() = "c|$title|$description|$shown"
    }

    /** A switch ([switch]) or a checkbox; OK flips it. */
    class Toggle(
        key: String,
        val title: String,
        val description: String? = null,
        val on: Boolean,
        val switch: Boolean = false,
        val onToggle: (Boolean) -> Unit,
    ) : Item(key) {
        override val signature get() = "t|$title|$description|$on|$switch"
    }

    /** One of a list of rows, the [chosen] one ticked; OK chooses it. */
    class Radio(
        key: String,
        val title: String,
        val description: String? = null,
        val value: String? = null,
        val chosen: Boolean,
        val onChoose: () -> Unit,
    ) : Item(key) {
        override val signature get() = "r|$title|$description|$value|$chosen"
    }

    /** Text to read; focusable so a long page can be scrolled, but OK does nothing. */
    class Info(
        key: String,
        val title: String,
        val description: String? = null,
    ) : Item(key) {
        override val signature get() = "i|$title|$description"
    }
}
