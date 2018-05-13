package io.jitrapon.glom.base.domain.circle

import io.reactivex.Flowable

/**
 * Main entry point to circle data
 *
 * Created by Jitrapon
 */
interface CircleDataSource {

    fun getCircle(refresh: Boolean, id: String, vararg params: String): Flowable<Circle>
}