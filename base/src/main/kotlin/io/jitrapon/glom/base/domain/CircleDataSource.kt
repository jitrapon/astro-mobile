package io.jitrapon.glom.base.domain

import io.jitrapon.glom.base.model.Circle
import io.reactivex.Single

/**
 * Main entry point to circle data
 *
 * Created by Jitrapon
 */
interface CircleDataSource {

    fun getCircle(id: String, vararg params: String): Single<Circle>
}