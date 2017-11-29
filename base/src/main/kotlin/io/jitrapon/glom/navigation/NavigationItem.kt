package io.jitrapon.glom.navigation

import android.support.annotation.DrawableRes
import android.support.annotation.IdRes
import android.support.annotation.StringRes
import io.jitrapon.glom.R
import io.jitrapon.glom.data.UiActionModel

/**
 * List of all possible navigation items
 *
 * @author Jitrapon Tiachunpun
 */
enum class NavigationItem(@IdRes val id: Int, @StringRes val title: Int,
                                  @DrawableRes val drawable: Int, val isChecked: Boolean, val module: String?) : UiActionModel {
    BOARD(R.id.board_nav_item, R.string.main_nav_item_board_title, R.drawable.ic_board, false, "board"),
    MAP(R.id.map_nav_item, R.string.main_nav_item_map_title, R.drawable.ic_map, false, "map"),
    EXPLORE(R.id.explore_nav_item, R.string.main_nav_item_explore_title, R.drawable.ic_explore, false, "explore"),
    PROFILE(R.id.profile_nav_item, R.string.main_nav_item_profile_title, R.drawable.ic_profile, false, "profile"),
    INVALID(12, 0, 0, false, null);

    companion object {

        fun getById(id: Int): NavigationItem = values().find { it.id == id } ?: INVALID
    }
}