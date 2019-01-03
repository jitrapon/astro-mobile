package io.jitrapon.glom.base

import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.jitrapon.glom.R
import io.jitrapon.glom.base.navigation.NavigationItem
import io.jitrapon.glom.base.navigation.Router
import io.jitrapon.glom.base.ui.BaseActivity

const val NAVIGATE_TO_MAIN = "action.navigate.main"
const val AUTH_REQUEST_CODE = 2000

/**
 * Base Activity class for the main entry point of the application. Contains shared activity logic
 * for other feature modules. This activity will, by default, launch an Intent to other Activities
 * using linking to maintain navigation for Instant App. Activities that do not want this behavior
 * should subclass this Activity.
 *
 * @author Jitrapon Tiachunpun
 */
abstract class BaseMainActivity : BaseActivity() {

    /* Navigation item (icon, title) representing this activity.
        This is shown in the navigation bar or drawer */
    private lateinit var selfNavItem: NavigationItem

    companion object {

        private const val BOTTOM_NAV_ANIM_GRACE = 175L
    }

    //region activity lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selfNavItem = getSelfNavItem()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        setupBottomNavBar(getBottomNavBar(), arrayOf(NavigationItem.BOARD, NavigationItem.MAP,
                NavigationItem.EXPLORE, NavigationItem.PROFILE))
    }

    private fun setupBottomNavBar(bottomNavBar: BottomNavigationView, items: Array<NavigationItem>) {
        bottomNavBar.apply {
            for (item in items) {
                menu.findItem(item.id)?.apply {
                    isVisible = true
                    setIcon(item.drawable)
                    setTitle(item.title)
                    if (item == selfNavItem) isChecked = true
                }
            }

            setOnNavigationItemSelectedListener { item ->
                val navItem = NavigationItem.getById(item.itemId)
                if (navItem != selfNavItem) {
                    selectBottomNavItem(navItem)
                    return@setOnNavigationItemSelectedListener true
                }
                return@setOnNavigationItemSelectedListener false
            }
        }
    }

    //endregion
    //region tab navigation callbacks

    /**
     * Returns the navigation drawer item that corresponds to this Activity. Subclasses of
     * BaseActivity override this to indicate what nav drawer item corresponds to them Return
     * NAVDRAWER_ITEM_INVALID to mean that this Activity should not have a Nav Drawer.
     */
    open fun getSelfNavItem() = NavigationItem.INVALID

    /**
     * Launches a new module based on the navigation item that was clicked. Slightly delay the launch
     * so that the nav icon animation has ended first.
     */
    private fun selectBottomNavItem(item: NavigationItem) {
        delayRun(BOTTOM_NAV_ANIM_GRACE) {
            Router.navigate(this, isInstantApp, item.module, !isInstantApp,
                    arrayOf(R.anim.fade_in, R.anim.fade_out))
        }
    }

    /**
     * Returns the bottom navigation view
     */
    abstract fun getBottomNavBar(): BottomNavigationView

    //endregion
}
