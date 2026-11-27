package ca.dstudio.altitude.ui.panel

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import ca.dstudio.altitude.R

/**
 * One focusable row: a title (and a description under it), a value at the right, and a chevron, a
 * checkbox or a switch at the end. The cursor fills it blue; while its dropdown is open it is
 * *activated* and shows a quiet fill instead, so the list's cursor is the one highlight on screen.
 */
@SuppressLint("ViewConstructor")
internal class RowView(context: Context) : LinearLayout(context) {

    private val title = TextView(context)
    private val description = TextView(context)
    private val value = TextView(context)
    private val chevron = ImageView(context)
    private val check = CheckView(context)
    private val switch = SwitchView(context)
    private val descriptionColours = Design.onCursor(Design.white(0.92f), Design.white(0.58f))

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        minimumHeight = px(76f)
        setPadding(px(16f), px(14f), px(16f), px(14f))
        background = StateListDrawable().apply {
            // Activated before focused: an open dropdown's row keeps focus-like state in some
            // transitions, and its open look has to win.
            addState(intArrayOf(android.R.attr.state_activated), rounded(Design.OPEN_ROW, 12f))
            addState(intArrayOf(android.R.attr.state_focused), rounded(Design.CURSOR, 12f))
            addState(intArrayOf(), rounded(Color.TRANSPARENT, 12f))
        }

        val text = LinearLayout(context).apply {
            orientation = VERTICAL
            isDuplicateParentStateEnabled = true
        }
        title.apply {
            roboto(32f, 300)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        description.apply {
            roboto(23f, 400, lineHeight = 29.9f)
            isDuplicateParentStateEnabled = true
            ellipsize = TextUtils.TruncateAt.END
        }
        text.addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        text.addView(description, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = px(3f)
        })
        addView(text, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        value.apply {
            roboto(25f, 400)
            isDuplicateParentStateEnabled = true
            setTextColor(Design.onCursor(Design.white(0.92f), Design.white(0.66f)))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = px(260f)
            gravity = Gravity.END
        }
        addView(value, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = px(16f)
        })

        for (end in listOf(check, switch)) {
            end.isDuplicateParentStateEnabled = true
            addView(end, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = px(16f)
            })
        }

        chevron.apply {
            alpha = 0.6f
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        addView(chevron, LayoutParams(px(22f), px(22f)).apply { marginStart = px(16f) })
    }

    fun bind(item: Item) {
        var desc: String? = null
        var shown: String? = null
        var chevronRes = 0
        var ticked: Boolean? = null
        var switchedOn: Boolean? = null
        var warn = false
        var descLines = 2
        when (item) {
            is Item.Link -> {
                title.text = item.title
                desc = item.description
                shown = item.value
                warn = item.warn
                chevronRes = R.drawable.ic_chevron_right
            }
            is Item.Choice -> {
                title.text = item.title
                desc = item.description
                shown = item.shown
                // An up chevron only while its list is open (the row is activated then).
                chevronRes = if (isActivated) R.drawable.ic_chevron_up else 0
            }
            is Item.Toggle -> {
                title.text = item.title
                desc = item.description
                if (item.switch) switchedOn = item.on else ticked = item.on
            }
            is Item.Radio -> {
                title.text = item.title
                desc = item.description
                shown = item.value
                // Only the chosen row carries a box.
                if (item.chosen) ticked = true
            }
            is Item.Info -> {
                title.text = item.title
                desc = item.description
                descLines = 4
            }
            is Item.Header -> error("a header is not a row")
        }
        description.text = desc
        description.maxLines = descLines
        description.visibility = if (desc.isNullOrEmpty()) View.GONE else View.VISIBLE
        if (warn) description.setTextColor(Design.AMBER) else description.setTextColor(descriptionColours)
        value.text = shown
        value.visibility = if (shown.isNullOrEmpty()) View.GONE else View.VISIBLE
        check.visibility = if (ticked != null) View.VISIBLE else View.GONE
        check.on = ticked == true
        switch.visibility = if (switchedOn != null) View.VISIBLE else View.GONE
        switch.on = switchedOn == true
        if (chevronRes != 0) {
            chevron.setImageResource(chevronRes)
            chevron.visibility = View.VISIBLE
        } else {
            chevron.visibility = View.GONE
        }
    }

    /** Marks the row as the anchor of an open dropdown, or no longer. */
    fun setOpen(open: Boolean) {
        isActivated = open
        if (open) {
            chevron.setImageResource(R.drawable.ic_chevron_up)
            chevron.visibility = View.VISIBLE
        } else {
            chevron.visibility = View.GONE
        }
    }
}

/** A section's name over its rows: small capitals, not focusable. */
@SuppressLint("ViewConstructor")
internal class HeaderRowView(context: Context) : TextView(context) {
    init {
        roboto(21f, 500, alpha = 0.5f, spacing = 0.1f)
        isAllCaps = true
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        isFocusable = false
        setPadding(px(16f), px(18f), px(16f), px(8f))
    }
}
