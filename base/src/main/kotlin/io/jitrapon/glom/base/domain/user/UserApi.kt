package io.jitrapon.glom.base.domain.user

import io.reactivex.Flowable
import retrofit2.http.GET
import retrofit2.http.Path

interface UserApi {

    @GET("circle/{circleId}/users")
    fun getUsers(@Path("circleId") circleId: String): Flowable<UsersResponse>
}