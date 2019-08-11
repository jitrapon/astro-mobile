package io.jitrapon.glom.board.widget.calendar

import android.content.Context
import android.view.View
import androidx.core.graphics.ColorUtils
import io.jitrapon.glom.base.ui.widget.calendar.DecoratorSource
import io.jitrapon.glom.base.ui.widget.calendar.GlomCalendarView
import io.jitrapon.glom.base.util.colorPrimary
import io.jitrapon.glom.base.util.hide
import io.jitrapon.glom.base.util.show
import io.jitrapon.glom.base.util.tint
import io.jitrapon.glom.board.item.event.EventItem
import java.util.*
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A calendar view decorator that visualizes the amount of events
 * added to a particular calendar day from the opaqueness
 * of the color of the dots displayed underneath the date text
 */
class BoardCalendarDecorator(private val context: Context,
                             private val items: HashMap<Date, List<EventItem>>?,
                             private val maxItemCountInADay: Int) : DecoratorSource {



    override fun addView(view: View) = Unit

    override fun decorate(date: Date, view: GlomCalendarView.DayViewContainer) {
        val events = items?.get(date)
        if (events != null && events.isNotEmpty()) {
            val alphaPercentage = min(events.size.toDouble() / maxItemCountInADay, 1.0)
            val tint = ColorUtils.setAlphaComponent(context.colorPrimary(), (alphaPercentage * 255).roundToInt())
            view.dot.apply {
                show()
                tint(tint)
            }
        }
        else {
            view.dot.hide()
        }
    }
}
