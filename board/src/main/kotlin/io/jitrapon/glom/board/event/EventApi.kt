package io.jitrapon.glom.board.event

import io.reactivex.Flowable
import retrofit2.http.*

interface EventApi {

    @POST("5b0922f43500008d00126255/{circleId}/{itemId}?mocky-delay=500ms")
    fun joinEvent(@Path("circleId") circleId: String,
                  @Path("itemId") itemId: String,
                  @Body request: EditAttendeeRequest): Flowable<EditAttendeeResponse>

    @POST("5b0923093500008900126256/{circleId}/{itemId}?mocky-delay=500ms")
    fun leaveEvent(@Path("circleId") circleId: String,
                   @Path("itemId") itemId: String,
                   @Body request: EditAttendeeRequest): Flowable<EditAttendeeResponse>

    @GET("5b48631e2f00009500481854/circle/{circleId}/board/{itemId}/date_polls")
    fun getDatePolls(@Path("circleId") circleId: String,
                     @Path("itemId") itemId: String): Flowable<GetDatePollResponse>

    @PATCH("5b486a852f0000800048187e/circle/{circleId}/board/{itemId}/date_poll/{datePollId}")
    fun updateDatePollCount(@Path("circleId") circleId: String,
                            @Path("itemId") itemId: String,
                            @Path("datePollId") datePollId: String,
                            @Body request: UpdatePollCountRequest): Flowable<UpdatePollCountResponse>

    @POST("5b57f89e300000ff05fe4ccd/circle/{circleId}/board/{itemId}/date_poll")
    fun addDatePoll(@Path("circleId") circleId: String,
                    @Path("itemId") itemId: String): Flowable<EventDatePollResponse>

    @PATCH("5b598ee92f000059005f943c/circle/{circleId}/board/{itemId}")
    fun setDatePollStatus(@Path("circleId") circleId: String,
                          @Path("itemId") itemId: String,
                          @Body request: UpdateDatePollStatusRequest): Flowable<PatchEventResponse>

    @PATCH("5b598ee92f000059005f943c/circle/{circleId}/board/{itemId}")
    fun setDate(@Path("circleId") circleId: String,
                          @Path("itemId") itemId: String,
                          @Body request: UpdateDateRequest): Flowable<PatchEventResponse>

    @GET("/circle/{circleId}/board/{itemId}/place_polls")
    fun getPlacePolls(@Path("circleId") circleId: String,
                      @Path("itemId") itemId: String): Flowable<GetPlacePollResponse>
}
