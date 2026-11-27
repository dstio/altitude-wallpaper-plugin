package ca.dstudio.altitude.ui.panel

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * A row's switch, 60 x 32 px (design pixels): a pill track and a 24 px knob, at the left while off
 * (a dim white track) and at the right while on (a blue track). On the cursor's blue row a switch that
 * is on turns white with a blue knob. It follows its row's focus, as [CheckView] does.
 */
@SuppressLint("ViewConstructor")
internal class SwitchView(context: Context) : View(context) {

    var on: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(px(WIDTH), px(HEIGHT))
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cursor = drawableState.contains(android.R.attr.state_focused)
        val unit = width / WIDTH
        track.set(0f, 0f, width.toFloat(), height.toFloat())
        paint.color = when {
            on && cursor -> Color.WHITE
            on -> Design.ON
            else -> Design.white(0.22f)
        }
        canvas.drawRoundRect(track, height / 2f, height / 2f, paint)
        val left = if (on) KNOB_ON else KNOB_OFF
        paint.color = if (on && cursor) Design.CURSOR else Color.WHITE
        canvas.drawCircle((left + KNOB / 2f) * unit, height / 2f, KNOB / 2f * unit, paint)
    }

    private companion object {
        const val WIDTH = 60f
        const val HEIGHT = 32f
        const val KNOB = 24f
        const val KNOB_OFF = 4f
        const val KNOB_ON = 32f
    }
}
