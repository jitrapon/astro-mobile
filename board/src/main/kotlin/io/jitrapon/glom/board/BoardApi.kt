package io.jitrapon.glom.board

import io.reactivex.Flowable
import retrofit2.http.GET

interface BoardApi {

    @GET("5af57b16310000780000248e")
    fun getBoard(): Flowable<BoardResponse>
}
