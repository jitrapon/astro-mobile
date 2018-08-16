package io.jitrapon.glom.board.event

/**
 * An event place poll
 */
data class EventPlacePoll(override val id: String,
                          override var users: MutableList<String>,
                          val avatar: String?,
                          val isAiSuggested: Boolean,
                          val location: EventLocation) : EventPoll(id, users)
