package io.jitrapon.glom.board.item.event.preference

import io.jitrapon.glom.board.item.event.calendar.CalendarEntity

/**
 * Customization option preferences for event item list in a board
 *
 * Created by Jitrapon
 */
data class EventItemPreference(
    val calendars: List<CalendarEntity>    /* list of calendars that are both synced and unsynced to the board */
)
