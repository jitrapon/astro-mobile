package io.jitrapon.glom.base.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import io.jitrapon.glom.R
import io.jitrapon.glom.base.model.UiActionModel
import io.jitrapon.glom.base.navigation.Router.MODULE_BOARD
import io.jitrapon.glom.base.navigation.Router.MODULE_EXPLORE
import io.jitrapon.glom.base.navigation.Router.MODULE_MAP
import io.jitrapon.glom.base.navigation.Router.MODULE_PROFILE

/**
 * List of all possible navigation items
 *
 * @author Jitrapon Tiachunpun
 */
enum class NavigationItem(@IdRes val id: Int, @StringRes val title: Int,
                                  @DrawableRes val drawable: Int, val isChecked: Boolean, val module: String?) : UiActionModel {
    BOARD(R.id.board_nav_item, R.string.main_nav_item_board_title, R.drawable.ic_board, false, MODULE_BOARD),
    MAP(R.id.map_nav_item, R.string.main_nav_item_map_title, R.drawable.ic_map, false, MODULE_MAP),
    EXPLORE(R.id.explore_nav_item, R.string.main_nav_item_explore_title, R.drawable.ic_explore, false, MODULE_EXPLORE),
    PROFILE(R.id.profile_nav_item, R.string.main_nav_item_profile_title, R.drawable.ic_profile, false, MODULE_PROFILE),
    INVALID(12, 0, 0, false, null);

    companion object {

        fun getById(id: Int): NavigationItem = values().find { it.id == id } ?: INVALID
    }
}