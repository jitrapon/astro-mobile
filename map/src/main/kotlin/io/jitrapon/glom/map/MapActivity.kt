package io.jitrapon.glom.map

import android.content.res.Resources
import android.os.Bundle
import android.view.View
import com.kizitonwose.calendarview.model.CalendarDay
import com.kizitonwose.calendarview.model.DayOwner
import com.kizitonwose.calendarview.ui.DayBinder
import io.jitrapon.glom.base.BaseMainActivity
import io.jitrapon.glom.base.navigation.NavigationItem
import io.jitrapon.glom.base.ui.widget.BadgedBottomNavigationView
import io.jitrapon.glom.base.ui.widget.calendar.DayViewContainer
import io.jitrapon.glom.base.util.attrColor
import io.jitrapon.glom.base.util.color
import io.jitrapon.glom.base.util.px
import kotlinx.android.synthetic.main.map_activity.*
import org.threeten.bp.YearMonth
import org.threeten.bp.temporal.WeekFields
import java.util.*

class MapActivity : BaseMainActivity() {

    override fun onCreateViewModel() {
        //TODO
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.map_activity)

        tag = "map"

        calendar_view.dayBinder = object : DayBinder<DayViewContainer> {
            // Called only when a new container is needed.
            override fun create(view: View) = DayViewContainer(view)

            // Called every time we need to reuse a container.
            override fun bind(container: DayViewContainer, day: CalendarDay) {
                container.textView.text = day.date.dayOfMonth.toString()
                if (day.owner == DayOwner.THIS_MONTH) {
                    container.textView.setTextColor(attrColor(android.R.attr.textColorPrimary))
                }
                else {
                    container.textView.setTextColor(color(io.jitrapon.glom.R.color.grey))
                }
            }
        }
        val currentMonth = YearMonth.now()
        val firstMonth = currentMonth.minusMonths(10)
        val lastMonth = currentMonth.plusMonths(10)
        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        calendar_view.setup(firstMonth, lastMonth, firstDayOfWeek)
        calendar_view.scrollToMonth(currentMonth)
        val width = ((Resources.getSystem().displayMetrics.widthPixels / 7f) + 0.5).toInt()
        calendar_view.dayWidth = width
        calendar_view.dayHeight = 48.px
    }

    override fun getBottomNavBar() = map_bottom_navigation as BadgedBottomNavigationView

    override fun getSelfNavItem() = NavigationItem.MAP
}
