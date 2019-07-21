package io.jitrapon.glom.map

import android.os.Bundle
import android.widget.LinearLayout
import io.jitrapon.glom.base.BaseMainActivity
import io.jitrapon.glom.base.navigation.NavigationItem
import io.jitrapon.glom.base.ui.widget.BadgedBottomNavigationView
import io.jitrapon.glom.base.ui.widget.calendar.GlomCalendarView
import kotlinx.android.synthetic.main.map_activity.*

class MapActivity : BaseMainActivity() {

    override fun onCreateViewModel() {
        //TODO
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.map_activity)

        tag = "map"

        calendar_view.init(
                calendar_view_month_textview,
                calendar_item_day_legend as LinearLayout,
                GlomCalendarView.SelectionMode.RANGE_START,
                true) { date, isSelected ->

        }
    }

    override fun getBottomNavBar() = map_bottom_navigation as BadgedBottomNavigationView

    override fun getSelfNavItem() = NavigationItem.MAP
}
