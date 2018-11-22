package io.jitrapon.glom.auth

import dagger.Component
import io.jitrapon.glom.base.di.BaseComponent

@AuthScope
@Component(dependencies = [BaseComponent::class], modules = [AuthModule::class])
interface AuthComponent {

    fun inject(viewModel: AuthViewModel)
}
