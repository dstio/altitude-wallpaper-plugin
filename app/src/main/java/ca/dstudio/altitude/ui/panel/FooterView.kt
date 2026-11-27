package ca.dstudio.altitude.ui.panel

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** A key the footer names, and what pressing it does here. */
data class Hint(val key: String, val action: String)

/**
 * The panel's footer: a key chip (white, dark text) followed by what the key does, for each key
 * that does something on the row under the cursor. A thin line above it parts it from the list.
 */
@SuppressLint("ViewConstructor")
internal class FooterView(context: Context) : LinearLayout(context) {

    private val line = Paint().apply { color = Design.FRAME }
    private var shown: List<Hint> = emptyList()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Design.FOOTER)
        setPadding(px(30f), px(18f), px(30f), px(18f))
        setWillNotDraw(false)
    }

    fun show(hints: List<Hint>) {
        if (hints == shown) return
        shown = hints
        removeAllViews()
        hints.forEachIndexed { i, hint ->
            addView(chip(hint.key), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                if (i > 0) marginStart = px(28f)
            })
            addView(TextView(context).apply {
                roboto(22f, 400, alpha = 0.7f)
                maxLines = 1
                text = hint.action
            }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = px(14f)
            })
        }
    }

    private fun chip(key: String) = TextView(context).apply {
        roboto(18f, 500)
        setTextColor(Design.CHIP_TEXT)
        background = rounded(Design.CHIP, 8f)
        gravity = Gravity.CENTER
        minWidth = px(54f)
        minHeight = px(36f)
        setPadding(px(10f), 0, px(10f), 0)
        maxLines = 1
        text = key
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), 1f, line)
    }
}
