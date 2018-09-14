package io.jitrapon.glom.board

import io.reactivex.Completable
import io.reactivex.Flowable
import retrofit2.http.*

interface BoardApi {

    @GET("circle/{circleId}/board")
    fun getBoard(@Path("circleId") circleId: String, @Query("type") itemType: String?): Flowable<BoardResponse>

    @POST("circle/{circleId}/board")
    fun createBoardItem(@Path("circleId") circleId: String,
                        @Body item: BoardItemRequest): Completable

    @PUT("circle/{circleId}/board/{itemId}")
    fun editBoardItem(@Path("circleId") circleId: String,
                      @Path("itemId") itemId: String,
                      @Body info: MutableMap<String, Any?>): Completable    // https://github.com/square/retrofit/issues/1805

    @DELETE("circle/{circleId}/board/{itemId}")
    fun deleteBoardItem(@Path("circleId") circleId: String,
                      @Path("itemId") itemId: String): Completable
}
