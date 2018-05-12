package io.jitrapon.glom.board

import io.reactivex.Flowable
import retrofit2.http.GET

interface BoardApi {

    @GET("5af6b5cf3100006600002720")
    fun getBoard(): Flowable<BoardResponse>
}
