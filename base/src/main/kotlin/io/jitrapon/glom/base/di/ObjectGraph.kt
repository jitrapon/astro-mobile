package io.jitrapon.glom.base.di

import android.app.Application

/**
 * Singleton class for conveniently inject into classes
 *
 * @author Jitrapon Tiachunpun
 */
object ObjectGraph {

    lateinit var component: BaseComponent

    /**
     * Must be called so that object graph is built, preferably inside Application#onCreate
     */
    fun init(application: Application) {
        component = DaggerBaseComponent.builder()
                .baseModule(BaseModule(application))
                .baseDomainModule(BaseDomainModule())
                .netModule(NetModule())
                .googleModule(GoogleModule())
                .glideModule(GlideModule())
                .build()
    }
}
