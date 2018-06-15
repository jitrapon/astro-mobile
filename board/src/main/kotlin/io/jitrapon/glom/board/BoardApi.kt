package io.jitrapon.glom.board

import io.reactivex.Completable
import io.reactivex.Flowable
import retrofit2.http.*

interface BoardApi {

    @GET("5b237c732f00000e00e0956c/{circleId}/{type}/?mocky-delay=1000ms")
    fun getBoard(@Path("circleId") circleId: String, @Path("type") itemType: String?): Flowable<BoardResponse>

    @POST("5b0d07eb31000064009d54d7/{circleId}?mocky-delay=800ms")
    fun createBoardItem(@Path("circleId") circleId: String,
                        @Body item: BoardItemRequest): Completable

    @PUT("5b0d0f1731000053009d54ee/{circleId}/item/{itemId}?mocky-delay=1000ms")
    fun editBoardItem(@Path("circleId") circleId: String,
                      @Path("itemId") itemId: String,
                      @Body info: MutableMap<String, Any?>): Completable    // https://github.com/square/retrofit/issues/1805

    @DELETE("5b0d172131000063009d5506/{circleId}/item/{itemId}?mocky-delay=1000ms")
    fun deleteBoardItem(@Path("circleId") circleId: String,
                      @Path("itemId") itemId: String): Completable
}
