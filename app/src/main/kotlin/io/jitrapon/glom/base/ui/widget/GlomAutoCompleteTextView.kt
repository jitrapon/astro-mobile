package io.jitrapon.glom.base.ui.widget

import android.annotation.TargetApi
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.AutoCompleteTextView

/**
 * TextView that will show autocomplete suggestions as user is typing
 *
 * @author Jitrapon Tiachunpun
 */
class GlomAutoCompleteTextView : AutoCompleteTextView, View.OnTouchListener {

    private val rightDrawable: Drawable? by lazy { compoundDrawablesRelative[2] }
    private var onDrawableClickListener: (() -> Unit)? = null

    var shouldReplaceTextOnSelected: Boolean = true

    constructor(context: Context): super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet): super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int): super(context, attrs, defStyle) {
        init()
    }

    @TargetApi(Build.VERSION_CODES.O)
    override fun getAutofillType() = View.AUTOFILL_TYPE_NONE

    /**
     * Called when an autocomplete suggestion is selected
     */
    override fun replaceText(text: CharSequence?) {
        if (shouldReplaceTextOnSelected) super.replaceText(text)
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