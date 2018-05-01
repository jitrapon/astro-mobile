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
    internal lateinit var retrofit: Retrofit

    init {
        ObjectGraph.component.inject(this)
    }
}
