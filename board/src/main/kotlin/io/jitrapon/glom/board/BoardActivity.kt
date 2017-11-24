package io.jitrapon.glom.board

import android.os.Bundle
import io.jitrapon.glom.base.BaseMainActivity
import io.jitrapon.glom.base.navigation.NavigationItem
import io.jitrapon.glom.base.ui.widget.BadgedBottomNavigationView
import io.jitrapon.glom.base.util.addFragment
import kotlinx.android.synthetic.main.board_activity.*

/**
 * Activity showing list of board items in a Circle
 *
 * @author Jitrapon Tiachunpun
 */
class BoardActivity : BaseMainActivity() {

    //region lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.board_activity)

        tag = "board"
    }

    /**
     * Returns this activity's bottom navigation item
     */
    override fun getSelfNavItem() = NavigationItem.BOARD

    /**
     * Returns this activity's bottom navigation bar
     */
    override fun getBottomNavBar() = board_bottom_navigation as BadgedBottomNavigationView

    override fun onCreateFragment(savedInstanceState: Bundle?) {
        addFragment(R.id.fragment_container, BoardFragment.newInstance())
    }

    //endregion
}
