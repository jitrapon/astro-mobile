package io.jitrapon.glom.base.domain.circle

import io.jitrapon.glom.base.repository.Repository
import io.reactivex.Flowable

/**
 * Repository for retrieving and interacting with Circle
 *
 * @author Jitrapon Tiachunpun
 */
class CircleRepository(private val remoteDataSource: CircleDataSource) : Repository<Circle>(), CircleDataSource {

    private var circle: Circle? = null

    override fun getCircle(refresh: Boolean, id: String): Flowable<Circle> {
        circle = circle ?: Circle(id, "my circle", null, null, ArrayList(), null, ArrayList())
        return load(refresh,
                Flowable.just(circle),
                remoteDataSource.getCircle(refresh, id),
                {
                    circle = it
                    circle!!
                }
        )
    }
}
