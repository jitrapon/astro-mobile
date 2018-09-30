package io.jitrapon.glom.board.item.event.plan

/**
 * Base class for polls in the planning section of an event
 */
abstract class EventPoll(open val id: String,
                         open var users: MutableList<String>)