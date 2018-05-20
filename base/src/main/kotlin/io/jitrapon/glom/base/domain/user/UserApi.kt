package io.jitrapon.glom.base.domain.user

import io.reactivex.Flowable
import retrofit2.http.GET

interface UserApi {

    @GET("5b00ff00300000660020a71f")
    fun getUsers(): Flowable<UsersResponse>
}