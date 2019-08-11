package io.jitrapon.glom.base.ui.widget.calendar

import android.view.View
import java.util.*

/**
 * Interface that controls when a day view decorator should be used
 * given a date
 *
 * Created by Jitrapon
 */
interface DecoratorSource {

    fun addView(view: View)

    fun decorate(date: Date, view: GlomCalendarView.DayViewContainer)
}