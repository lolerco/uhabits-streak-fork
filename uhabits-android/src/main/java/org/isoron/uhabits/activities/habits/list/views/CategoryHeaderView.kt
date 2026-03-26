package org.isoron.uhabits.activities.habits.list.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import org.isoron.uhabits.R
import org.isoron.uhabits.utils.InterfaceUtils
import org.isoron.uhabits.utils.dp
import org.isoron.uhabits.utils.sres

/**
 * A view that renders a category separator header in the habit list.
 * Displays as:  ────── CATEGORY NAME ──────
 */
class CategoryHeaderView(context: Context) : LinearLayout(context) {

    private val label: TextView
    private val lineColor: Int
    private val linePaint: Paint

    var categoryName: String = ""
        set(value) {
            field = value
            label.text = value.uppercase()
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        val verticalPad = InterfaceUtils.dpToPixels(context, 12f).toInt()
        val horizontalPad = InterfaceUtils.dpToPixels(context, 16f).toInt()
        setPadding(horizontalPad, verticalPad, horizontalPad, verticalPad)

        lineColor = sres.getColor(R.attr.contrast60)
        linePaint = Paint().apply {
            strokeWidth = InterfaceUtils.dpToPixels(context, 1f)
            style = Paint.Style.STROKE
            color = lineColor
        }

        label = TextView(context).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(lineColor)
            gravity = Gravity.CENTER
            letterSpacing = 0.15f
            val hPad = InterfaceUtils.dpToPixels(context, 12f).toInt()
            setPadding(hPad, 0, hPad, 0)
        }

        // We draw the lines manually in onDraw to fill available space
        setWillNotDraw(false)
        addView(label, LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        })
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cy = height / 2f
        val pad = InterfaceUtils.dpToPixels(context, 16f)
        val labelLeft = label.left.toFloat()
        val labelRight = label.right.toFloat()

        // Left line
        if (labelLeft > pad) {
            canvas.drawLine(pad, cy, labelLeft, cy, linePaint)
        }
        // Right line
        if (labelRight < width - pad) {
            canvas.drawLine(labelRight, cy, width - pad, cy, linePaint)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        // Center the label in the middle of the view
        val labelWidth = label.measuredWidth
        val labelHeight = label.measuredHeight
        val centerX = (r - l) / 2
        val centerY = (b - t) / 2
        label.layout(
            centerX - labelWidth / 2,
            centerY - labelHeight / 2,
            centerX + labelWidth / 2,
            centerY + labelHeight / 2
        )
    }
}
