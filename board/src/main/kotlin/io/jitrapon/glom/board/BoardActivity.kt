package io.jitrapon.glom.board

import android.os.Bundle
import io.jitrapon.glom.base.BaseMainActivity
import io.jitrapon.glom.base.navigation.NavigationItem

/**
 * Subclass of the MainActivity. Board activity is the default entry point to the application.
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
}
