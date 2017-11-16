package io.jitrapon.glom.base

import android.support.annotation.DrawableRes
import android.support.annotation.IdRes
import android.support.annotation.StringRes
import io.jitrapon.glom.R

/**
 * List of all possible navigation items
 *
 * @author Jitrapon Tiachunpun
 */
enum class NavigationItem(@IdRes val id: Int, @StringRes val title: Int,
                                  @DrawableRes val drawable: Int, val module: String?) {
    BOARD(R.id.board_nav_item, R.string.main_nav_item_board_title, R.drawable.ic_board, "board"),
    MAP(R.id.map_nav_item, R.string.main_nav_item_map_title, R.drawable.ic_map, "map"),
    EXPLORE(R.id.explore_nav_item, R.string.main_nav_item_explore_title, R.drawable.ic_explore, "explore"),
    PROFILE(R.id.profile_nav_item, R.string.main_nav_item_profile_title, R.drawable.ic_profile, "profile"),
    INVALID(12, 0, 0, null);

    companion object {

        fun getById(id: Int): NavigationItem = values().find { it.id == id } ?: NavigationItem.INVALID
    }
}