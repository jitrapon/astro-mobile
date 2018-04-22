package io.jitrapon.glom.base.model

import android.os.Parcelable
import java.util.*

/**
 * A DataModel is a base class that represents data retrieved by a repository. On the most basic level,
 * an instance of this class has a Data, as well as a nullable Error object indicating failure
 * to retrieve the model data. All models are also parcelable.
 *
 * @author Jitrapon Tiachunpun
 */
interface DataModel : Parcelable {

    /**
     * Optional field, time at which the model is retrieved by a repository
     */
    var retrievedTime: Date?

    /**
     * Represents the error when the model fails to be retrieved. Null if there is no error.
     */
    val error: Throwable?
}
