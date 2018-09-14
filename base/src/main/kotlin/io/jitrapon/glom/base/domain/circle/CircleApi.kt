package io.jitrapon.glom.base.domain.circle

import io.reactivex.Flowable
import retrofit2.http.GET
import retrofit2.http.Path

interface CircleApi {

    @GET("circle/{circleId}")
    fun getCircleInfo(@Path("circleId") circleId: String): Flowable<CircleInfoResponse>
}
