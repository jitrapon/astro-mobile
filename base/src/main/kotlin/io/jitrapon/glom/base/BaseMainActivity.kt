package io.jitrapon.glom.base

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import io.jitrapon.glom.R

/**
 * Base Activity class for the main entry point of the application. Contains shared activity logic
 * for other feature modules. This activity will, by default, launch an Intent to other Activities
 * using linking to maintain navigation for Instant App. Activities that do not want this behavior
 * should subclass this Activity.
 *
 * @author Jitrapon Tiachunpun
 */
abstract class BaseMainActivity : AppCompatActivity() {

    //region activity lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
    }

    //endregion
    //region tab navigation callbacks

    /**
     * View the group's board. By default will use instant app navigation to launch.
     */
    open fun viewBoardTab() {
        InstantAppRouter.navigateTo(this, "board")
    }

    /**
     * View the group's map. By default will use instant app navigation to launch.
     */
    open fun viewMapTab() {
        InstantAppRouter.navigateTo(this, "map")
    }

    /**
     * View the explore tab. By default will use instant app navigation to launch.
     */
    open fun viewExploreTab() {
        InstantAppRouter.navigateTo(this, "explore")
    }

    /**
     * View the group profile tab. By default will use instant app navigation to launch.
     */
    open fun viewGroupProfileTab() {
        InstantAppRouter.navigateTo(this, "profile")
    }

    //endregion
}