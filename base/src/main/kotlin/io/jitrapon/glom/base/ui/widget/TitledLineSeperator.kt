package io.jitrapon.glom.base.ui.widget

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import io.jitrapon.glom.R
import io.jitrapon.glom.base.util.color
import io.jitrapon.glom.base.util.colorPrimary
import kotlinx.android.synthetic.main.titled_line_seperator.view.*

/**
 * A line seperator widget that can show title in the center
 *
 * @author Jitrapon Tiachunpun
 */
class TitledLineSeperator : ConstraintLayout {

    private lateinit var textView: TextView

    constructor(context: Context): super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet): super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int): super(context, attrs, defStyle) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet? = null) {
        val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.titled_line_seperator, this)

        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.TitledLineSeperator)
            val text = typedArray.getText(R.styleable.TitledLineSeperator_title)
            val textColor = typedArray.getColor(R.styleable.TitledLineSeperator_titleColor, context.colorPrimary())
            val lineColor = typedArray.getColor(R.styleable.TitledLineSeperator_lineColor, context.color(R.color.dark_grey))
            findViewById<TextView>(R.id.line_seperator_title).apply {
                textView = this
                setText(text)
                setTitleColor(textColor)
            }
            setLineColor(lineColor)
            typedArray.recycle()
        }
    }

    fun setTitle(text: CharSequence?) {
        textView.text = text
    }

    fun setTitleColor(@ColorInt color: Int) {
        textView.setTextColor(color)
    }

    fun setLineColor(@ColorInt color: Int) {
        line_seperator_left.background = ColorDrawable(color)
        line_seperator_right.background = ColorDrawable(color)
    }
}
