package io.jitrapon.glom.board

/**
 * All constants for this module that should not belong in Strings.xml
 * i.e. used for storing constants such as Intent ACTIONS, EXTRAS, REQUEST CODES, etc.
 *
 * @author Jitrapon Tiachunpun
 */
object Const {

    const val EXTRA_BOARD_ITEM = "android.intent.EXTRA_BOARD_ITEM"
    const val EXTRA_IS_BOARD_ITEM_MODIFIED = "android.intent.EXTRA_IS_BOARD_ITEM_MODIFIED"
    const val EXTRA_IS_BOARD_ITEM_NEW = "android.intent.EXTRA_IS_BOARD_ITEM_NEW"
    const val EDIT_ITEM_RESULT_CODE = 1001
    const val NAVIGATE_TO_MAP_SEARCH = "action.navigate.map.search"
    const val NAVIGATE_TO_EVENT_PLAN = "action.navigate.event.plan"
    const val PLAN_EVENT_RESULT_CODE = 1002
    const val NAVIGATE_BACK = "action.navigate.back"
    const val NAVIGATE_TO_PLACE_PICKER = "action.navigate.placepicker"
    const val PLACE_PICKER_RESULT_CODE = 1003
}
