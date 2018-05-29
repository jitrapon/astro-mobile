package io.jitrapon.glom.board.event

import io.jitrapon.glom.base.model.RepeatInfo
import io.jitrapon.glom.base.util.*
import io.jitrapon.glom.board.BoardItem
import io.jitrapon.glom.board.BoardItemResponse
import java.util.*
import kotlin.collections.HashMap

fun BoardItemResponse.deserialize(): EventItem {
    return EventItem(BoardItem.TYPE_EVENT, itemId, createdTime, updatedTime, owners,
            info.let {
                EventInfo(it["event_name"] as String,
                        it["start_time"].asNullableLong(),
                        it["end_time"].asNullableLong(),
                        (it["location"] as? Map<*, *>)?.let {
                            EventLocation(it["lat"].asNullableDouble(),
                                    it["long"].asNullableDouble(),
                                    it["g_place_id"] as? String?,
                                    it["place_id"] as? String?
                            )
                        },
                        it["note"] as? String?,
                        it["time_zone"] as? String?,
                        it["is_full_day"] as Boolean,
                        (it["repeat"] as? Map<*, *>)?.let {
                            RepeatInfo(it["occurence_id"].asNullableInt(),
                                    it["is_reschedule"] as? Boolean?,
                                    it["unit"].asInt(),
                                    it["interval"].asLong(),
                                    it["until"].asLong(),
                                    it["meta"].asNullableIntList()
                            )
                        },
                        it["is_date_poll_opened"] as Boolean,
                        it["is_date_poll_opened"] as Boolean,
                        ArrayList(it["attendees"] as List<String>))
            }, Date())
}

fun EventItem.serializeInfo(): MutableMap<String, Any?> {
    return LinkedHashMap<String, Any?>().apply {
        itemInfo.let {
            put("event_name", it.eventName)
            put("start_time", it.startTime)
            put("end_time", it.endTime)
            put("location", if (it.location == null) null else HashMap<String, Any?>().apply {
                it.location?.let {
                    put("lat", it.latitude)
                    put("long", it.longitude)
                    put("g_place_id", it.googlePlaceId)
                    put("place_id", it.placeId)
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