package io.jitrapon.glom.base.domain.circle

import io.jitrapon.glom.base.repository.RemoteDataSource
import io.reactivex.Flowable

class CircleRemoteDataSource : RemoteDataSource(), CircleDataSource {

    private val api = retrofit.create(CircleApi::class.java)

    override fun getCircle(refresh: Boolean, id: String, vararg params: String): Flowable<Circle> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
