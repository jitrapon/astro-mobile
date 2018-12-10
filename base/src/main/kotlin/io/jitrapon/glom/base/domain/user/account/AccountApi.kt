package io.jitrapon.glom.base.domain.user.account

import io.reactivex.Completable
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

    @POST
    fun signInWithEmailPassword(@Url url: String,
                                @Body request: SignInEmailPasswordRequest): Flowable<AccountInfoResponse>

    @POST
    fun signUpAnonymously(@Url url: String): Flowable<AccountInfoResponse>

    @POST
    fun signOut(@Url url: String): Completable
}
