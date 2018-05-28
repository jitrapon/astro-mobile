package io.jitrapon.glom.base.domain.user

import io.reactivex.Flowable
import retrofit2.http.GET
import retrofit2.http.Path

interface UserApi {

    @GET("5b00ff00300000660020a71f/{circleId}")
    fun getUsers(@Path("circleId") circleId: String): Flowable<UsersResponse>
}