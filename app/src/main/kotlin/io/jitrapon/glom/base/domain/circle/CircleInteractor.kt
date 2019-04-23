package io.jitrapon.glom.base.domain.circle

import io.jitrapon.glom.base.domain.user.User
import io.jitrapon.glom.base.domain.user.UserDataSource
import io.jitrapon.glom.base.interactor.BaseInteractor
import io.jitrapon.glom.base.model.PlaceInfo
import io.reactivex.Flowable

/**
 * Interactor dealing with Circle business logic. This class also manages
 * currently active circle state
 *
 * @author Jitrapon Tiachunpun
 */
class CircleInteractor(private val circleDataSource: CircleDataSource, private val userDataSource: UserDataSource): BaseInteractor() {

    private var activeCircleId: String = "dev-circle-1"

    /**
     * Loads a circle from a data source, with an optional specified list of fields
     */
    fun loadCircle(refresh: Boolean): Flowable<Circle> = circleDataSource.getCircle(activeCircleId, refresh)

    /**
     * Returns the currently active (selected) circle ID
     */
    fun getActiveCircleId(): String = activeCircleId

    /**
     * Returns list of custom places in this currently active circle
     */
    fun getActiveCirclePlaces(): List<PlaceInfo> = circleDataSource.getCircle(activeCircleId).blockingFirst().places

    /**
     * Returns list of users in the specified circle
     */
    fun getActiveUsersInCircle(): List<User> = userDataSource.getUsers(activeCircleId).blockingFirst()
}
