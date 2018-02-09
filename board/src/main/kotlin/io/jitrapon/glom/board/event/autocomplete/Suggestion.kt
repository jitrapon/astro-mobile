package io.jitrapon.glom.board.event.autocomplete

/**
 * Autocomplete suggestions to be used in conjunction with autocomplete
 *
 * @author Jitrapon Tiachunpun
 */
data class Suggestion(val selectData: Any, val displayText: String? = null, val isConjunction: Boolean = false)