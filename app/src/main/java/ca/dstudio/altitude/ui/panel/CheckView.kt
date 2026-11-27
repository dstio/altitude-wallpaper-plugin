package ca.dstudio.altitude.ui.panel

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/**
 * A row's checkbox, 32 px (design pixels) with an 8 px radius. Off, it is a 3 px ring of half-white;
 * on, it is filled blue and carries a white check. On the cursor's blue row the ring turns white, and
 * a ticked box turns white with a blue check. It follows its row's focus (duplicate parent state), so
 * it is drawn from the row's state alone.
 */
@SuppressLint("ViewConstructor")
internal class CheckView(context: Context) : View(context) {

    var on: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private val check = Path()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(px(SIZE), px(SIZE))
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cursor = drawableState.contains(android.R.attr.state_focused)
        val unit = width / SIZE
        if (!on) {
            // The ring's outer edge is the box's edge, as a CSS border is.
            val half = BORDER * unit / 2f
            box.set(half, half, width - half, height - half)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = BORDER * unit
            paint.color = if (cursor) Color.WHITE else Design.white(0.5f)
            canvas.drawRoundRect(box, (RADIUS - BORDER / 2f) * unit, (RADIUS - BORDER / 2f) * unit, paint)
            return
        }
        box.set(0f, 0f, width.toFloat(), height.toFloat())
        paint.style = Paint.Style.FILL
        paint.color = if (cursor) Color.WHITE else Design.ON
        canvas.drawRoundRect(box, RADIUS * unit, RADIUS * unit, paint)
        // The check: a 24-unit glyph drawn 22 px wide in the middle of the box.
        val g = GLYPH * unit / 24f
        val o = (SIZE - GLYPH) / 2f * unit
        check.reset()
        check.moveTo(o + 5f * g, o + 12f * g)
        check.lineTo(o + 10f * g, o + 17f * g)
        check.lineTo(o + 19f * g, o + 7f * g)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = 3.4f * g
        paint.color = if (cursor) Design.CURSOR else Color.WHITE
        canvas.drawPath(check, paint)
    }

    private companion object {
        const val SIZE = 32f
        const val RADIUS = 8f
        const val BORDER = 3f
        const val GLYPH = 22f
    }
}
