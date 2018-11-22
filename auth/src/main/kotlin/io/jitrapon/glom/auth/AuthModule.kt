package io.jitrapon.glom.auth

import dagger.Module
import dagger.Provides
import io.jitrapon.glom.base.domain.user.account.AccountDataSource
import javax.inject.Named

@Module
class AuthModule {

    @Provides
    @AuthScope
    fun provideAuthInteractor(@Named("accountRepository") accountDataSource: AccountDataSource): AuthInteractor =
        AuthInteractor(accountDataSource)
}
