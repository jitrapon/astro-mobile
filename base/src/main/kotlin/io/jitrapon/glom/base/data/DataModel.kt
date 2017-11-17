package io.jitrapon.glom.base.data

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
     * The time at which the model is retrieved by a repository
     */
    val retrievedTime: Date

    /**
     * Type of which the model is retrieved from
     */
    val sourceType: SourceType

    /**
     * A class that encapsulates the data of the model upon successful data retrieval
     */
    interface Data : Parcelable

    /**
     * A class representing failure to retrieve the model. Classes that implements this will contain
     * more information about the error.
     */
    interface Error: Parcelable
}
