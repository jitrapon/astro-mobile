package io.jitrapon.glom.base.domain.user.account

import io.jitrapon.glom.base.repository.RemoteDataSource
import io.reactivex.Completable

class AccountRemoteDataSource : RemoteDataSource(), AccountDataSource {

    //TODO add another retrofit pointing to auth endpoint

    override fun getAccount(): AccountInfo? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun saveAccount(userId: String, idToken: String?): Completable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}