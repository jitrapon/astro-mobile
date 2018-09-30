package io.jitrapon.glom.board.item.event.plan

import io.jitrapon.glom.board.item.event.EventLocation


/**
 * An event place poll
 */
data class EventPlacePoll(override val id: String,
                          override var users: MutableList<String>,
                          val avatar: String?,
                          val isAiSuggested: Boolean,
                          var location: EventLocation) : EventPoll(id, users)
