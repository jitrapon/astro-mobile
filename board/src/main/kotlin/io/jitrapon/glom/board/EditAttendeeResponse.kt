package io.jitrapon.glom.board

/**
 * Created by Jitrapon
 */
data class EditAttendeeResponse(val userId: String? = null,
                                val status: Int? = null,
                                var attendees: MutableList<String>? = null)