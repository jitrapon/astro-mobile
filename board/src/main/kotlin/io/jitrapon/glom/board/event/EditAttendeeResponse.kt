package io.jitrapon.glom.board.event

/**
 * Created by Jitrapon
 */
data class EditAttendeeResponse(val userId: String? = null,
                                val status: Int? = null,
                                var attendees: MutableList<String>? = null)