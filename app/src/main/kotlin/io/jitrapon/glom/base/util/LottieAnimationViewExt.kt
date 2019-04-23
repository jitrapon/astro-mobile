package io.jitrapon.glom.base.util

import android.animation.Animator
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import io.jitrapon.glom.base.model.AnimationItem

object AnimationState {

    var currentAnimation: AnimationItem? = null
    var useHardWareAcceleration: Boolean = true
    var loopForever: Boolean = false
}

/**
 * Safely plays an animation from specified file. Specify true to indicate that
 * animation should be restarted if it's currently playing. False will let the currently playing animation finishes.
 */
fun LottieAnimationView.animate(animation: AnimationItem, shouldRestartIfPlaying: Boolean = true,
         loopForever: Boolean = false, useHardwareAcceleration: Boolean = true) {
    try {
        AnimationState.useHardWareAcceleration = useHardwareAcceleration
        if (shouldRestartIfPlaying && isAnimating && AnimationState.currentAnimation == animation) progress = 0f
        else {
            removeAnimatorListener(animatorListener)
            setAnimation(animation.fileName)
            AnimationState.currentAnimation = animation
            progress = 0f
            repeatCount = if (loopForever) LottieDrawable.INFINITE else 0
            addAnimatorListener(animatorListener)
            playAnimation()
        }
    }
    catch (ex: Exception) {
        AppLogger.e(ex)
    }
}

var LottieAnimationView.animatorListener: Animator.AnimatorListener
    get() = object : Animator.AnimatorListener {
        override fun onAnimationRepeat(p0: Animator?) {
        }

        override fun onAnimationEnd(p0: Animator?) {
            if (AnimationState.useHardWareAcceleration) useHardwareAcceleration(false)
            if (!AnimationState.loopForever) {
                removeAnimatorListener(this)
                hide(500L)
            }
        }

        override fun onAnimationCancel(p0: Animator?) {
        }

        override fun onAnimationStart(p0: Animator?) {
            show()
            if (AnimationState.useHardWareAcceleration) useHardwareAcceleration()
        }
    }

    set(value) {}