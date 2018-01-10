package io.jitrapon.glom.board

import android.os.Bundle
import io.jitrapon.glom.base.ui.BaseActivity
import io.jitrapon.glom.base.util.AppLogger

/**
 * Container activity for expanded board items
 *
 * @author Jitrapon Tiachunpun
 */
class BoardItemActivity : BaseActivity() {

    //region lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.board_item_activity)

        tag = "board_item"
        
        intent?.let {
            val item = it.getParcelableExtra(Const.EXTRA_BOARD_ITEM) as EventItem
            AppLogger.i("Item ID is ${item.itemId}")
        }
    }

    //endregion
}
