package io.jitrapon.glom.board

import android.os.Bundle
import io.jitrapon.glom.base.BaseMainActivity
import io.jitrapon.glom.base.navigation.NavigationItem
import io.jitrapon.glom.base.util.addFragment

/**
 * Activity showing list of board items in a Circle
 *
 * @author Jitrapon Tiachunpun
 */
class BoardActivity : BaseMainActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.board_activity)

        tag = "board"
    }

    override fun getSelfNavItem() = NavigationItem.BOARD

    override fun onCreateFragment(savedInstanceState: Bundle?) {
        addFragment(R.id.fragment_container, BoardFragment.newInstance())
    }
}
