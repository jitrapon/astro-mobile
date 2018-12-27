package io.jitrapon.glom.auth

import dagger.Module
import dagger.Provides
import io.jitrapon.glom.auth.oauth.BaseOauthInteractor
import io.jitrapon.glom.auth.oauth.FacebookInteractor
import io.jitrapon.glom.auth.oauth.GoogleInteractor
import io.jitrapon.glom.auth.oauth.LineInteractor
import io.jitrapon.glom.base.domain.user.account.AccountDataSource
import javax.inject.Named

@Module
class AuthModule {

    @Provides
    @AuthScope
    fun provideAuthInteractor(@Named("accountRepository") accountDataSource: AccountDataSource): AuthInteractor =
        AuthInteractor(accountDataSource)

    @Provides
    @AuthScope
    @Named("facebook")
    fun provideFacebookInteractor(): BaseOauthInteractor = FacebookInteractor()

    @Provides
    @AuthScope
    @Named("google")
    fun provideGoogleInteractor(): BaseOauthInteractor = GoogleInteractor()

    @Provides
    @AuthScope
    @Named("line")
    fun providLineInteractor(): BaseOauthInteractor = LineInteractor()
}
