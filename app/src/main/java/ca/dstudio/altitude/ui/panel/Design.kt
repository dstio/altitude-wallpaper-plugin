package ca.dstudio.altitude.ui.panel

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * The settings' measurements and colours: ATV Launcher's drawer look. Sizes are given as pixels on a
 * 1920 x 1080 screen, which is how the design is drawn; on the device they are half that in dp (and text
 * in sp), so the panel keeps its proportions on any TV density.
 */
internal object Design {
    val SURFACE = Color.parseColor("#16181E")
    val BAND = Color.parseColor("#22252D")
    val FRAME = Color.parseColor("#12FFFFFF")
    val CURSOR = Color.parseColor("#3184E3")

    /** A switch that is on, a ticked box, a chosen option's check. */
    val ON = Color.parseColor("#4E9BF0")
    val OPEN_ROW = Color.parseColor("#1AFFFFFF")
    val ICON_TILE = Color.parseColor("#14FFFFFF")
    val FOOTER = Color.parseColor("#2E000000")
    val CHIP = Color.parseColor("#EDFFFFFF")
    val CHIP_TEXT = Color.parseColor("#181B30")

    /** A line that warns: a folder not connected, nothing to play. */
    val AMBER = Color.parseColor("#F0B44E")

    fun white(alpha: Float): Int = Color.argb((alpha * 255).roundToInt(), 255, 255, 255)

    /** A colour for a view whose parent's focus it follows: [focused] on the cursor, else [rest]. */
    fun onCursor(focused: Int, rest: Int): ColorStateList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
        intArrayOf(focused, rest),
    )
}

/** A design size (pixels at 1920 x 1080) in this screen's pixels. */
internal fun Context.px(design: Float): Int = (design / 2f * resources.displayMetrics.density).roundToInt()

internal fun Context.pxf(design: Float): Float = design / 2f * resources.displayMetrics.density

internal fun View.px(design: Float): Int = context.px(design)

internal fun View.pxf(design: Float): Float = context.pxf(design)

/**
 * Sets a text view in the system's Roboto: [size] and [lineHeight] are design pixels, [weight] 300, 400
 * or 500, [spacing] is in em, [alpha] the white's opacity. A [lineHeight] spaces the lines as CSS does,
 * half the extra above the first line and half below the last.
 */
internal fun TextView.roboto(size: Float, weight: Int, alpha: Float = 1f, spacing: Float = 0f, lineHeight: Float = 0f) {
    typeface = Typeface.create(
        when {
            weight <= 300 -> "sans-serif-light"
            weight >= 500 -> "sans-serif-medium"
            else -> "sans-serif"
        },
        Typeface.NORMAL,
    )
    setTextSize(TypedValue.COMPLEX_UNIT_SP, size / 2f)
    setTextColor(Design.white(alpha))
    includeFontPadding = false
    letterSpacing = spacing
    if (lineHeight > 0f) {
        val metrics = paint.fontMetricsInt
        val extra = pxf(lineHeight) - (metrics.descent - metrics.ascent)
        setLineSpacing(extra, 1f)
        if (extra > 0f) setPadding(0, (extra / 2f).roundToInt(), 0, (extra / 2f).roundToInt())
    }
}

/** A rounded rectangle of [color] with a [radius] in design pixels. */
internal fun View.rounded(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = pxf(radius)
    setColor(color)
}

/** An outline-only rounded rectangle: the panels' thin frame. */
internal fun View.frame(radius: Float, width: Float = 2f): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = pxf(radius)
    setColor(Color.TRANSPARENT)
    setStroke(px(width).coerceAtLeast(1), Design.FRAME)
}
