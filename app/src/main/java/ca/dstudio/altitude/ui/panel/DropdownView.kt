package ca.dstudio.altitude.ui.panel

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ca.dstudio.altitude.R

/**
 * A choice row's list: a raised piece of the panel dropped under its row (above it when there is
 * no room below, and scrolling when there is no room either way), the chosen option checked. It
 * lives in the panel's own window, over the page's list, so focus never leaves the panel.
 *
 * OK chooses; [close] (BACK) leaves the choice as it was. The host blocks the page's rows while it is
 * open and restores the cursor after.
 */
@SuppressLint("ViewConstructor")
internal class DropdownView(context: Context, private val onClosed: (anchorKey: String) -> Unit) : FrameLayout(context) {

    private val list = RecyclerView(context)
    private var item: Item.Choice? = null
    private var anchor: RowView? = null

    val isOpen: Boolean get() = item != null

    init {
        visibility = GONE
        background = rounded(Design.BAND, 22f)
        foreground = frame(22f)
        elevation = pxf(24f)
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        clipToOutline = true
        val pad = px(10f)
        list.setPadding(pad, pad, pad, pad)
        list.clipToPadding = false
        list.layoutManager = LinearLayoutManager(context)
        list.itemAnimator = null
        list.isFocusable = false
        list.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Opens [choice]'s list under [row], placed inside [frame] (this view's parent). */
    fun open(row: RowView, choice: Item.Choice, frame: ViewGroup) {
        item = choice
        anchor = row
        row.setOpen(true)

        val gap = px(14f)
        val inset = px(16f)
        val r = Rect(0, 0, row.width, row.height)
        frame.offsetDescendantRectToMyCoords(row, r)
        val width = r.width() - inset * 2

        // Options with a line are taller than those without: measure each.
        val probe = OptionView(context)
        val content = choice.options.indices.sumOf { i ->
            probe.bind(choice.options[i], choice.lines.getOrNull(i), false)
            probe.measure(
                MeasureSpec.makeMeasureSpec(width - px(20f), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            probe.measuredHeight
        } + px(20f)
        val below = frame.height - gap - (r.bottom + gap)
        val above = r.top - gap - gap
        val (top, height) = when {
            content <= below -> (r.bottom + gap) to content
            content <= above -> (r.top - gap - content) to content
            else -> gap to minOf(content, frame.height - gap * 2)
        }
        layoutParams = LayoutParams(width, height).apply {
            leftMargin = r.left + inset
            topMargin = top
            gravity = Gravity.TOP or Gravity.START
        }
        list.adapter = Options(choice)
        visibility = VISIBLE
        val start = choice.selected.coerceAtLeast(0)
        (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(start, height / 3)
        list.post { list.findViewHolderForAdapterPosition(start)?.itemView?.requestFocus() }
    }

    /**
     * UP (-1) or DOWN (+1): the cursor to the next option, scrolling it into view; it stops at
     * either end rather than leaving the list.
     */
    fun move(delta: Int) {
        val count = list.adapter?.itemCount ?: return
        val focused = list.focusedChild ?: return
        val at = list.getChildAdapterPosition(focused)
        val target = (at + delta).coerceIn(0, count - 1)
        if (target == at) return
        val holder = list.findViewHolderForAdapterPosition(target)
        val lm = list.layoutManager as LinearLayoutManager
        if (holder != null && lm.isViewPartiallyVisible(holder.itemView, true, true)) {
            holder.itemView.requestFocus()
        } else {
            lm.scrollToPosition(target)
            list.post { list.findViewHolderForAdapterPosition(target)?.itemView?.requestFocus() }
        }
    }

    /** Closes the list; the host puts the cursor back on its row. */
    fun close() {
        val key = item?.key ?: return
        item = null
        anchor?.setOpen(false)
        anchor = null
        visibility = GONE
        list.adapter = null
        onClosed(key)
    }

    private fun choose(index: Int) {
        val choice = item ?: return
        close()
        if (index != choice.selected) choice.onChoose(index)
    }

    private inner class Options(val choice: Item.Choice) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = choice.options.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            object : RecyclerView.ViewHolder(OptionView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
            }) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val v = holder.itemView as OptionView
            v.bind(choice.options[position], choice.lines.getOrNull(position), position == choice.selected)
            v.setOnClickListener { holder.bindingAdapterPosition.takeIf { it >= 0 }?.let(::choose) }
        }
    }
}

/** One option: its name, a line under it when it has one, and on the chosen one a check. */
@SuppressLint("ViewConstructor")
private class OptionView(context: Context) : LinearLayout(context) {
    private val label = TextView(context)
    private val line = TextView(context)
    private val check = ImageView(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        setPadding(px(18f), px(14f), px(18f), px(14f))
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), rounded(Design.CURSOR, 12f))
            addState(intArrayOf(), rounded(Color.TRANSPARENT, 12f))
        }
        val text = LinearLayout(context).apply { orientation = VERTICAL }
        label.roboto(27f, 400)
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        text.addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        line.roboto(21f, 400, alpha = 0.58f, lineHeight = 27.3f)
        line.maxLines = 1
        line.ellipsize = TextUtils.TruncateAt.END
        text.addView(line, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = px(3f) })
        addView(text, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        check.setImageResource(R.drawable.ic_check)
        check.isDuplicateParentStateEnabled = true
        check.imageTintList = Design.onCursor(Color.WHITE, Design.ON)
        addView(check, LayoutParams(px(26f), px(26f)).apply { marginStart = px(16f) })
    }

    fun bind(text: String, sub: String?, chosen: Boolean) {
        label.text = text
        line.text = sub
        line.visibility = if (sub.isNullOrEmpty()) View.GONE else View.VISIBLE
        check.visibility = if (chosen) View.VISIBLE else View.INVISIBLE
    }
}
