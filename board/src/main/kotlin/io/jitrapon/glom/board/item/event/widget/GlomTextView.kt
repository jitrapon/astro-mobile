package io.jitrapon.glom.board.item.event.widget

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.appcompat.widget.AppCompatTextView
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Customized TextView to add new functionalities to Android's default TextView
 *
 * Created by Jitrapon
 */
class GlomTextView : AppCompatTextView, View.OnTouchListener {

    private val rightDrawable: Drawable? by lazy { compoundDrawablesRelative[2] }
    private var onDrawableClickListener: (() -> Unit)? = null

    constructor(context: Context): super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet): super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int): super(context, attrs, defStyle) {
        init()
    }

    private fun init() {
        setDrawableVisible(false)
        setOnTouchListener(this)
    }

    fun setDrawableVisible(visible: Boolean) {
        rightDrawable?.setVisible(visible, true)
        setCompoundDrawablesWithIntrinsicBounds(compoundDrawables[0],
                compoundDrawables[1],
                if (visible) rightDrawable else null,
                compoundDrawables[3])
    }

    fun setOnDrawableClick(listener: (() -> Unit)?) {
        onDrawableClickListener = listener
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        rightDrawable?.let {
            val x = event.x
            if (it.isVisible && x > width - paddingRight - it.intrinsicWidth) {
                if (event.action == MotionEvent.ACTION_UP) {
                    onDrawableClickListener?.invoke()
                }
                return true
            }
        }
        return false
    }
}