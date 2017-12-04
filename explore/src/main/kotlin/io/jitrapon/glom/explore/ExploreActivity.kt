package io.jitrapon.glom.explore

import android.os.Bundle
import io.jitrapon.glom.base.BaseMainActivity
import io.jitrapon.glom.base.navigation.NavigationItem
import io.jitrapon.glom.base.ui.widget.bottomnav.BadgedBottomNavigationView
import kotlinx.android.synthetic.main.explore_activity.*

class ExploreActivity : BaseMainActivity() {

    override fun onCreateViewModel() {
        //TODO
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.explore_activity)

        tag = "explore"
    }

    override fun getBottomNavBar() = explore_bottom_navigation as BadgedBottomNavigationView

    override fun getSelfNavItem() = NavigationItem.EXPLORE
}
