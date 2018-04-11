package io.jitrapon.glom.base.domain

import io.jitrapon.glom.base.model.Circle
import io.jitrapon.glom.base.model.PlaceInfo
import io.jitrapon.glom.base.repository.Repository
import io.reactivex.Flowable
import java.util.concurrent.TimeUnit

/**
 * Repository for retrieving and interacting with Circle
 *
 * @author Jitrapon Tiachunpun
 */
object CircleRepository : Repository<Circle>() {

    private var circle: Circle? = null

    fun load(vararg param: String): Flowable<Circle> {
        circle = circle ?: Circle("abcd1234", "my circle", null, null,
                ArrayList(), null, getPlaces())
        return Flowable.just(circle!!).delay(300L, TimeUnit.MILLISECONDS)
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

    fun getCache(): Circle? = circle
}
