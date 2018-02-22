package io.jitrapon.glom.base.interactor

import io.jitrapon.glom.base.model.AsyncErrorResult
import io.jitrapon.glom.base.model.AsyncResult
import io.jitrapon.glom.base.model.AsyncSuccessResult
import io.jitrapon.glom.base.model.Circle
import io.jitrapon.glom.base.repository.CircleRepository
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * Interactor dealing with Circle business logic
 *
 * @author Jitrapon Tiachunpun
 */
class CircleInteractor {

    /**
     * Loads a circle from a data source, with an optional specified list of fields
     */
    fun loadCircle(vararg fields: String, onComplete: (AsyncResult<Circle>) -> Unit) {
        CircleRepository.load(*fields)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    onComplete(AsyncSuccessResult(it))
                }, {
                    onComplete(AsyncErrorResult(it))
                }, {
                    //nothing yet
                })
    }

    /**
     * Returns a circle from in-memory cache. CircleInteractor#loadCircle() must be called for this function
     * to return non-null value.
     */
    fun getCircle(): Circle? = CircleRepository.getCache()
}
