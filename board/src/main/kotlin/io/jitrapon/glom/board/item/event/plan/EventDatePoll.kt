package io.jitrapon.glom.board.item.event.plan

/**
 * An event date poll with an optional end time
 */
data class EventDatePoll(override val id: String,
                         override var users: MutableList<String>,
                         var startTime: Long,
                         var endTime: Long?) : EventPoll(id, users)
