package io.jitrapon.glom.base.domain.circle

import io.reactivex.Flowable
import retrofit2.http.GET

interface CircleApi {

    @GET("5afa65d32e00008f00278eb3?mocky-delay=750ms")
    fun getCircleInfo(): Flowable<CircleInfoResponse>
}
