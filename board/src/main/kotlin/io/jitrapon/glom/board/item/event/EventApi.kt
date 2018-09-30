package io.jitrapon.glom.board.item.event

import io.jitrapon.glom.board.item.event.plan.*
import io.reactivex.Flowable
import retrofit2.http.*

interface EventApi {

    @POST("circle/{circleId}/board/{itemId}/attendee")
    fun joinEvent(@Path("circleId") circleId: String,
                  @Path("itemId") itemId: String,
                  @Body request: EditAttendeeRequest): Flowable<EditAttendeeResponse>

    @POST("circle/{circleId}/board/{itemId}/attendee")
    fun leaveEvent(@Path("circleId") circleId: String,
                   @Path("itemId") itemId: String,
                   @Body request: EditAttendeeRequest): Flowable<EditAttendeeResponse>

    @GET("circle/{circleId}/board/{itemId}/date_polls")
    fun getDatePolls(@Path("circleId") circleId: String,
                     @Path("itemId") itemId: String): Flowable<GetDatePollResponse>

    @PATCH("circle/{circleId}/board/{itemId}/date_poll/{datePollId}")
    fun updateDatePollCount(@Path("circleId") circleId: String,
                            @Path("itemId") itemId: String,
                            @Path("datePollId") datePollId: String,
                            @Body request: UpdatePollCountRequest): Flowable<UpdatePollCountResponse>

    @POST("circle/{circleId}/board/{itemId}/date_poll")
    fun addDatePoll(@Path("circleId") circleId: String,
                    @Path("itemId") itemId: String,
                    @Body request: EventDatePollRequest): Flowable<EventDatePollResponse>

    @PATCH("circle/{circleId}/board/{itemId}")
    fun setDatePollStatus(@Path("circleId") circleId: String,
                          @Path("itemId") itemId: String,
                          @Body request: UpdateDatePollStatusRequest): Flowable<PatchEventResponse>

    @PATCH("circle/{circleId}/board/{itemId}")
    fun setDate(@Path("circleId") circleId: String,
                          @Path("itemId") itemId: String,
                          @Body request: EventDatePollRequest): Flowable<PatchEventResponse>

    @GET("circle/{circleId}/board/{itemId}/place_polls")
    fun getPlacePolls(@Path("circleId") circleId: String,
                      @Path("itemId") itemId: String): Flowable<GetPlacePollResponse>

    @PATCH("circle/{circleId}/board/{itemId}/place_poll/{placePollId}")
    fun updatePlacePollCount(@Path("circleId") circleId: String,
                             @Path("itemId") itemId: String,
                             @Path("placePollId") placePollId: String,
                             @Body request: UpdatePollCountRequest): Flowable<UpdatePollCountResponse>

    @POST("circle/{circleId}/board/{itemId}/place_poll")
    fun addPlacePoll(@Path("circleId") circleId: String,
                     @Path("itemId") itemId: String,
                     @Body request: EventPlacePollRequest): Flowable<EventPlacePollResponse>

    @PATCH("circle/{circleId}/board/{itemId}")
    fun setPlacePollStatus(@Path("circleId") circleId: String,
                          @Path("itemId") itemId: String,
                          @Body request: UpdatePlacePollStatusRequest): Flowable<PatchEventResponse>

    @PATCH("circle/{circleId}/board/{itemId}")
    fun setPlace(@Path("circleId") circleId: String,
                @Path("itemId") itemId: String,
                @Body request: UpdatePlaceRequest): Flowable<PatchEventResponse>
}
