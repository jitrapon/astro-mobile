package io.jitrapon.glom.base.util

import android.content.res.Resources
import android.util.TypedValue

/**
 * Created by Jitrapon
 */
val Float.px: Int
    get() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, Resources.getSystem().displayMetrics).toInt()

val Int.px: Int
    get() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, toFloat(), Resources.getSystem().displayMetrics).toInt()