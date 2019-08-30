package io.jitrapon.glom.board.item.event

import io.jitrapon.glom.base.model.RepeatInfo
import io.jitrapon.glom.base.util.*
import io.jitrapon.glom.board.BoardItemResponse
import io.jitrapon.glom.board.item.BoardItem
import io.jitrapon.glom.board.item.SyncStatus
import io.jitrapon.glom.board.item.toSyncStatus
import java.util.ArrayList
import java.util.Date
import java.util.LinkedHashMap
import kotlin.collections.HashMap

fun BoardItemResponse.deserialize(circleId: String): EventItem {
    return EventItem(
            BoardItem.TYPE_EVENT,
            itemId,
            createdTime,
            updatedTime,
            owners,
            info.let {
                EventInfo(it["event_name"] as String,
                        it["start_time"].asNullableLong(),
                        it["end_time"].asNullableLong(),
                        (it["location"] as? Map<*, *>)?.let {
                            EventLocation(it["lat"].asNullableDouble(),
                                    it["long"].asNullableDouble(),
                                    it["g_place_id"] as? String?,
                                    it["place_id"] as? String?,
                                    it["name"] as? String?,
                                    it["description"] as? String?,
                                    it["address"] as? String?
                            )
                        },
                        it["note"] as? String?,
                        it["time_zone"] as? String?,
                        it["is_full_day"] as Boolean,
                        (it["repeat"] as? Map<*, *>)?.let {
                            RepeatInfo(null,
                                    it["occurence_id"].asNullableLong(),
                                    it["is_reschedule"] as? Boolean?,
                                    it["unit"].asInt(),
                                    it["interval"].asLong(),
                                    it["until"].asLong(),
                                    it["meta"].asNullableIntList(),
                                    it["start_time"].asNullableLong() ?: 0L
                            )
                        },
                        it["is_date_poll_opened"] as Boolean,
                        it["is_place_poll_opened"] as Boolean,
                        ArrayList(it["attendees"] as List<String>),
                        EventSource(null, null, null, circleId))
            },
            isEditable ?: false,
            SyncStatus.SUCCESS,
            Date()
    )
}

fun EventItem.serializeInfo(): MutableMap<String, Any?> {
    return LinkedHashMap<String, Any?>().apply {
        itemInfo.let {
            put("event_name", it.eventName)
            put("start_time", it.startTime)
            put("end_time", it.endTime)
            put("location", HashMap<String, Any?>().apply {
                it.location.let {
                    put("lat", it?.latitude)
                    put("long", it?.longitude)
                    put("g_place_id", it?.googlePlaceId)
                    put("place_id", it?.placeId)
                    put("name", it?.name)
                    put("description", it?.description)
                    put("address", it?.address)
                }
            })
            put("note", it.note)
            put("time_zone", it.timeZone)
            put("is_full_day", it.isFullDay)
            put("repeat", if (it.repeatInfo == null) null else HashMap<String, Any?>().apply {
                it.repeatInfo?.let {
                    put("unit", it.unit)
                    put("interval", it.interval)
                    put("until", it.until)
                    put("meta", it.meta)
                }
            })
            put("is_date_poll_opened", it.datePollStatus)
            put("is_place_poll_opened", it.placePollStatus)
        }
    }
}

fun List<EventItemFullEntity>.toEventItems(userId: String?, circleId: String): MutableList<BoardItem> {
    val items = ArrayList<BoardItem>()
    for (entity in this) {
        entity.entity.let {
            val location: EventLocation? = if (it.latitude == null && it.longitude == null &&
                    it.googlePlaceId == null && it.placeId == null && it.placeName == null) null
            else EventLocation(it.latitude, it.longitude, it.googlePlaceId, it.placeId, it.placeName,
                    it.placeDescription, it.placeAddress)
            items.add(
                    EventItem(BoardItem.TYPE_EVENT, it.id, null, it.updatedTime,
                            ArrayList<String>().apply { if (it.isOwner) { userId?.let(::add) } },
                            EventInfo(it.name, it.startTime, it.endTime, location, it.note, it.timeZone,
                                    it.isFullDay, null, it.datePollStatus, it.placePollStatus,
                                    entity.attendees.toMutableList(), EventSource(null, null, null, circleId)),
                            it.isEditable, it.syncStatus.toSyncStatus()))
        }
    }
    return items
}

fun EventItem.toEntity(circleId: String, userId: String?, updatedTimeMs: Long): EventItemFullEntity {
    return EventItemFullEntity().apply {
        entity = EventItemEntity(
                itemId, updatedTimeMs, itemInfo.eventName, itemInfo.startTime, itemInfo.endTime, itemInfo.location?.googlePlaceId,
                itemInfo.location?.placeId, itemInfo.location?.latitude, itemInfo.location?.longitude,
                itemInfo.location?.name, itemInfo.location?.description, itemInfo.location?.address,
                itemInfo.note, itemInfo.timeZone, itemInfo.isFullDay, itemInfo.datePollStatus,
                itemInfo.placePollStatus, owners.contains(userId), isEditable, syncStatus.intValue, circleId)
        attendees = itemInfo.attendees
    }
}
