package io.jitrapon.glom.board

import android.os.Bundle

/**
 * Container activity for expanded board items
 *
 * @author Jitrapon Tiachunpun
 */
class EventItemActivity : BoardItemActivity() {

    //region lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tag = "board_item_event"
    }

    //endregion
    //region other view callbacks

    override fun onActivityWillFinish() {

    }

    override fun getLayout(): Int = R.layout.board_item_activity

    override fun onBeginTransitionAnimationStart() {

    }

    override fun onBeginTransitionAnimationEnd() {

    }

    override fun onFinishTransitionAnimationStart() {

    }

    override fun onFinishTransitionAnimationEnd() {

    }

    //endregion
}
