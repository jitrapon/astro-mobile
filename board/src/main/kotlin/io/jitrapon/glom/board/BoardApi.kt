package io.jitrapon.glom.board

import io.reactivex.Flowable
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface BoardApi {

    @GET("5af6b5cf3100006600002720/{circleId}?mocky-delay=1000ms")
    fun getBoard(@Path("circleId") circleId: String): Flowable<BoardResponse>

    @POST("")
    fun createBoardItem(@Path("circleId") circleId: String)
}
