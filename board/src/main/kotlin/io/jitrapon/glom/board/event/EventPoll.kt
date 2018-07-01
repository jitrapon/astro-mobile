package io.jitrapon.glom.board.event

/**
 * Base class for polls in the planning section of an event
 */
abstract class EventPoll(open val id: String,
                         open var users: MutableList<String>)