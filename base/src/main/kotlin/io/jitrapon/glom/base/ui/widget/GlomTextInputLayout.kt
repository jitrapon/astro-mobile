package io.jitrapon.glom.base.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.support.annotation.StyleRes
import android.support.design.widget.TextInputLayout
import android.support.v4.view.ViewCompat
import android.support.v4.view.animation.FastOutSlowInInterpolator
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.Interpolator
import android.widget.EditText
import android.widget.TextView
import io.jitrapon.glom.R
import io.jitrapon.glom.base.util.animate

/**
 * Created by Jitrapon
 */
class GlomTextInputLayout : TextInputLayout {

    private val FAST_OUT_SLOW_IN_INTERPOLATOR: Interpolator = FastOutSlowInInterpolator()

    private var mHelperText: CharSequence? = null
    private var mHelperTextColor: ColorStateList? = null
    private var mHelperTextEnabled = false
    private var mErrorEnabled = false
    private var mHelperView: TextView? = null
    private var mHelperTextAppearance = R.style.Widget_Glom_TextInputLayout_HelperTextAppearance

    constructor(context: Context): super(context)

    constructor(context: Context, attrs: AttributeSet): super(context, attrs) {
        getContext().obtainStyledAttributes(attrs, R.styleable.GlomTextInputLayout,0,0).let {
            try {
                mHelperTextColor = it.getColorStateList(R.styleable.GlomTextInputLayout_helperTextColor)
                mHelperText = it.getText(R.styleable.GlomTextInputLayout_helperText)
            }
            finally {
                it.recycle()
            }
        }
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        super.addView(child, index, params)
        if (child is EditText) {
            if (!TextUtils.isEmpty(mHelperText)) {
                setHelperText(mHelperText!!)
            }
        }
    }

    fun getHelperTextAppearance(): Int {
        return mHelperTextAppearance
    }

    fun setHelperTextAppearance(@StyleRes resId: Int) {
        mHelperTextAppearance = resId
    }

    fun setHelperTextColor(color: ColorStateList) {
        mHelperTextColor = color
    }

    fun setHelperTextEnabled(enabled: Boolean) {
        if (mHelperTextEnabled == enabled) return
        if (enabled && mErrorEnabled) {
            isErrorEnabled = false
        }
        if (mHelperTextEnabled != enabled) {
            if (enabled) {
                mHelperView = TextView(context)
                mHelperView?.apply {
                    setTextAppearance(mHelperTextAppearance)
                    mHelperTextColor?.let {
                        setTextColor(it)
                    }
                    text = mHelperText
                    visibility = View.VISIBLE
                    this@GlomTextInputLayout.addView(this)
                    editText?.let {
                        ViewCompat.setPaddingRelative(
                                this,
                                ViewCompat.getPaddingStart(it),
                                0, ViewCompat.getPaddingEnd(it),
                                it.paddingBottom
                        )
                    }
                }
            }
            else {
                mHelperView?.let {
                    removeView(it)
                    mHelperView = null
                }
            }

            mHelperTextEnabled = enabled
        }
    }

    fun setHelperText(helperText: CharSequence?) {
        mHelperText = helperText
        if (!mHelperTextEnabled) {
            if (TextUtils.isEmpty(mHelperText)) return
            setHelperTextEnabled(false)
        }

        mHelperView?.apply {
            if (!TextUtils.isEmpty(mHelperText)) {
                text = mHelperText
                visibility = View.VISIBLE
                alpha = 0.0f
                animate(200L, {
                    alpha(1.0f)
                    interpolator = FAST_OUT_SLOW_IN_INTERPOLATOR
                })
            }
            else if (visibility == View.VISIBLE) {
                animate(200L, {
                    alpha(0.0f)
                    interpolator = FAST_OUT_SLOW_IN_INTERPOLATOR
                }, { _, _ ->
                    text = null
                    visibility = View.GONE
                })
            }
        }
        sendAccessibilityEvent(2048)
    }

    override fun setErrorEnabled(enabled: Boolean) {
        if (mErrorEnabled == enabled) return
        mErrorEnabled = enabled
        if (enabled && mHelperTextEnabled) {
            setHelperTextEnabled(false)
        }

        super.setErrorEnabled(enabled)

        if (!(enabled || TextUtils.isEmpty(mHelperText))) {
            setHelperText(mHelperText!!)
        }
    }
}