package io.jitrapon.glom.board.event

import android.os.Bundle
import io.jitrapon.glom.base.ui.BaseActivity
import io.jitrapon.glom.board.R

/**
 * This activity is the entry point to date and place polls for an event
 *
 * Created by Jitrapon
 */
class PlanEventActivity : BaseActivity() {

    //region lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.plan_event_activity)
    }

    //endregion
}