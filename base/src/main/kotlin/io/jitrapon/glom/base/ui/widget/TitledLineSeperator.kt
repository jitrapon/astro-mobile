package io.jitrapon.glom.base.ui.widget

import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView
import io.jitrapon.glom.R

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
            textView = findViewById<TextView>(R.id.line_seperator_title).apply {
                setText(text)
            }
            typedArray.recycle()
        }
    }

    fun setTitle(text: CharSequence?) {
        textView.text = text
    }
}
