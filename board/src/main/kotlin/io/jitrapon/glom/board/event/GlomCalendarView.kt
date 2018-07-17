package io.jitrapon.glom.board.event

import android.content.Context
import android.util.AttributeSet
import com.prolificinteractive.materialcalendarview.MaterialCalendarView

/**
 * A wrapper around third-party implementation of the CalendarView
 *
 * Created by Jitrapon
 */
class GlomCalendarView : MaterialCalendarView {

    constructor(context: Context): super(context)

    constructor(context: Context, attrs: AttributeSet): super(context, attrs)


}
