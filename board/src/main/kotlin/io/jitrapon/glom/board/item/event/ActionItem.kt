package io.jitrapon.glom.board.item.event

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.jitrapon.glom.base.model.UiActionModel
import io.jitrapon.glom.board.R

/**
 * List of all possible action items for event items
 *
 * @author Jitrapon Tiachunpun
 */
enum class ActionItem(@StringRes val title: Int, @DrawableRes val drawable: Int): UiActionModel {
    PLAN_LOCATION(R.string.event_item_action_plan, R.drawable.ic_plan_event),
    PLAN_DATETIME(R.string.event_item_action_plan, R.drawable.ic_plan_event),
    MAP(R.string.event_item_action_view_map, R.drawable.ic_google_maps),
    DIRECTION(R.string.event_item_action_navigate, R.drawable.ic_direction),
    CALL(R.string.event_item_action_call_place, R.drawable.phone),
    JOIN(R.string.event_item_action_join, R.drawable.ic_emoticon_neutral),
    LEAVE(R.string.event_item_action_leave, R.drawable.ic_emoticon_excited),
    PICK_PLACE(R.string.event_item_pick_place, R.drawable.ic_marker),
    INVALID(-1,-1);
}
