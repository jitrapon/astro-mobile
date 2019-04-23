package io.jitrapon.glom.base.domain.circle

import io.reactivex.Flowable

/**
 * Main entry point to circle data
 *
 * Created by Jitrapon
 */
interface CircleDataSource {

    fun getCircle(id: String, refresh: Boolean = false): Flowable<Circle>
}