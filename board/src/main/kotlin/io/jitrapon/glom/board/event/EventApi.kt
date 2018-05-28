package io.jitrapon.glom.board.event

import io.reactivex.Flowable
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface EventApi {

    @POST("5b0922f43500008d00126255/{circleId}/{itemId}?mocky-delay=500ms")
    fun joinEvent(@Path("circleId") circleId: String,
                  @Path("itemId") itemId: String,
                  @Body request: EditAttendeeRequest): Flowable<EditAttendeeResponse>

    @POST("5b0923093500008900126256/{circleId}/{itemId}?mocky-delay=500ms")
    fun leaveEvent(@Path("circleId") circleId: String,
                   @Path("itemId") itemId: String,
                   @Body request: EditAttendeeRequest): Flowable<EditAttendeeResponse>
}
