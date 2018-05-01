package io.jitrapon.glom.base.domain.circle

import io.jitrapon.glom.base.model.PlaceInfo
import io.jitrapon.glom.base.repository.Repository
import io.reactivex.Single

/**
 * Repository for retrieving and interacting with Circle
 *
 * @author Jitrapon Tiachunpun
 */
class CircleRepository : Repository<Circle>(), CircleDataSource {

    private var circle: Circle? = null

    override fun getCircle(id: String, vararg params: String): Single<Circle> {
        circle = circle ?: Circle(id, "my circle", null, null,
                ArrayList(), null, getPlaces())
        return Single.just(circle!!)
    }

    private fun getPlaces(): MutableList<PlaceInfo> {
        return ArrayList<PlaceInfo>().apply {
            add(PlaceInfo("Home", "Home sweet home at pattanakarn 56", null,
                    13.732756, 100.643237, null, "1"))
            add(PlaceInfo("Condo", null, null,
                    13.722591, 100.580225, null, "2"))
            add(PlaceInfo("Office", null, null, null,
                    null, "ChIJf0arHuOe4jARtaepvwrv7Zs", "3"))
        }
    }
}
