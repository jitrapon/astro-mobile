package io.jitrapon.glom.auth

import io.jitrapon.glom.base.di.ObjectGraph

/**
 * Caller must explicitly clear compoonent
 *
 * Created by Jitrapon
 */
object AuthInjector {

    private var component: AuthComponent? = null

    fun getComponent(): AuthComponent {
        if (component == null) {
            component = DaggerAuthComponent.builder()
                .baseComponent(ObjectGraph.component)
                .build()
        }
        return component!!
    }

    fun clear() {
        component = null
    }
}
