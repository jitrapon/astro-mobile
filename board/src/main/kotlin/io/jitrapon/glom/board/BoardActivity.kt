package io.jitrapon.glom.board

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.jitrapon.glom.base.AUTH_REQUEST_CODE
import io.jitrapon.glom.base.BaseMainActivity
import io.jitrapon.glom.base.navigation.NavigationItem
import io.jitrapon.glom.base.ui.widget.BadgedBottomNavigationView
import io.jitrapon.glom.base.util.addFragment
import io.jitrapon.glom.base.util.findFragment
import kotlinx.android.synthetic.main.board_activity.*

/**
 * Activity showing list of board items in a Circle
 *
 * @author Jitrapon Tiachunpun
 */
class BoardActivity : BaseMainActivity() {

    private val boardFragmentTag = "board"

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

    /**
     * Callback triggered when child fragment is initialized
     */
    override fun onCreateFragment(savedInstanceState: Bundle?) {
        addFragment(R.id.board_fragment_container, BoardFragment.newInstance(), boardFragmentTag)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == AUTH_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                (findFragment(boardFragmentTag) as? BoardFragment)?.let {
                    if (it.isVisible) it.onRefresh(100L, true)
                }
            }
        }
    }

    //endregion
}
