package io.jitrapon.glom.map

import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import io.jitrapon.glom.base.BaseMainActivity
import io.jitrapon.glom.base.model.AndroidString
import io.jitrapon.glom.base.model.MessageLevel
import io.jitrapon.glom.base.navigation.NavigationItem
import io.jitrapon.glom.base.ui.widget.BadgedBottomNavigationView
import io.jitrapon.glom.base.ui.widget.calendar.GlomCalendarView
import io.jitrapon.glom.base.util.showSnackbar
import kotlinx.android.synthetic.main.map_activity.*

class MapActivity : BaseMainActivity() {

    override fun onCreateViewModel() {
        //TODO
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.map_activity)

        tag = "map"

        map_button4.setOnClickListener {
            map_view_stub.setOnInflateListener { _, inflated ->
                (inflated as? GlomCalendarView)?.init(null, GlomCalendarView.SelectionMode.RANGE_START, true) { date, isSelected ->
                    showSnackbar(MessageLevel.INFO,
                            AndroidString(text = "${if (isSelected) "Selected" else "Unselected"} $date"),
                            duration = Snackbar.LENGTH_LONG)
                }
            }
            map_view_stub.inflate()
        }
    }

    override fun getBottomNavBar() = map_bottom_navigation as BadgedBottomNavigationView

    override fun getSelfNavItem() = NavigationItem.MAP
}
