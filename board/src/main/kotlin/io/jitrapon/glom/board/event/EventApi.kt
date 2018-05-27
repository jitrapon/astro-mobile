package io.jitrapon.glom.board.event

import io.reactivex.Flowable
import retrofit2.http.GET

interface EventApi {

    @GET("5b0922f43500008d00126255?mocky-delay=500ms")
    fun joinEvent(): Flowable<EditAttendeeResponse>

    @GET("5b0923093500008900126256?mocky-delay=500ms")
    fun leaveEvent(): Flowable<EditAttendeeResponse>
}
