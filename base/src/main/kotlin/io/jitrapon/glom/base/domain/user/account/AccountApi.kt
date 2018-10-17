package io.jitrapon.glom.base.domain.user.account

import io.reactivex.Flowable
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Created by Jitrapon
 */
interface AccountApi {

    @POST
    fun refreshIdToken(@Url url: String,
                       @Body request: RefreshIdTokenRequest): Flowable<AccountInfoResponse>
}