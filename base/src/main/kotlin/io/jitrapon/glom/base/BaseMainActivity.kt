package io.jitrapon.glom.base

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.bottom_nav_view.*

/**
 * Base Activity class for the main entry point of the application. Contains shared activity logic
 * for other feature modules. This activity will, by default, launch an Intent to other Activities
 * using linking to maintain navigation for Instant App. Activities that do not want this behavior
 * should subclass this Activity.
 *
 * @author Jitrapon Tiachunpun
 */
abstract class BaseMainActivity : AppCompatActivity() {

    /* Navigation item (icon, title) representing this activity.
        This is shown in the navigation bar or drawer */
    private lateinit var selfNavItem: NavigationItem

    //region activity lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selfNavItem = getSelfNavItem()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        setupBottomNavBar(arrayOf(NavigationItem.BOARD, NavigationItem.MAP,
                NavigationItem.EXPLORE, NavigationItem.PROFILE))
    }

    private fun setupBottomNavBar(items: Array<NavigationItem>) {
        bottom_navigation?.let {
            it.menu.let {
                for (item in items) {
                    it.findItem(item.id)?.let {
                        it.isVisible = true
                        it.setIcon(item.drawable)
                        it.setTitle(item.title)
                        if (item == selfNavItem) it.isChecked = true
                    }
                }
            }

            it.setOnNavigationItemSelectedListener {
                NavigationItem.getById(it.itemId).let {
                    if (it != selfNavItem) {
//                        InstantAppRouter.navigateTo(BaseMainActivity@this, it.module)
                        return@setOnNavigationItemSelectedListener true
                    }
                    return@setOnNavigationItemSelectedListener false
                }
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

    //endregion
}