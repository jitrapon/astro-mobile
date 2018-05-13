package io.jitrapon.glom.base.repository

import io.jitrapon.glom.base.di.ObjectGraph
import retrofit2.Retrofit
import javax.inject.Inject

/**
 * Base class for all remote data sources. Provides all child remote data source classes
 * with shared Retrofit Singleton instance
 *
 * Created by Jitrapon
 */
@Suppress("LeakingThis")
open class RemoteDataSource {

    @Inject
    lateinit var retrofit: Retrofit

    init {
        ObjectGraph.component.inject(this)
    }

    /**
     * Below contains convenient extension functions to convert
     * Moshi's deserialization way of converting Json number to Java's Double
     */

    fun Any?.asNullableLong(): Long? = (this as? Double?)?.toLong()

    fun Any?.asNullableDouble(): Double? = this as? Double?

    fun Any?.asNullableInt(): Int? = (this  as? Double?)?.toInt()

    fun Any?.asInt(): Int = (this as Double).toInt()

    fun Any?.asLong(): Long = (this as Double).toLong()

    @Suppress("UNCHECKED_CAST")
    fun Any?.asNullableIntList(): List<Int>? {
        return (this as? List<Double>)?.map {
            it.toInt()
        }
    }
}
