package io.jitrapon.glom.base.util

import android.animation.Animator
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import io.jitrapon.glom.base.model.AnimationItem

/**
 * Created by Jitrapon
 */
object AnimationUtils {

    /**
     * Safely plays an animation from specified file. Specify true to indicate that
     * animation should be restarted if it's currently playing. False will let the currently playing animation finishes.
     */
    fun play(view: LottieAnimationView, animation: AnimationItem, shouldRestartIfPlaying: Boolean = true,
             loopForever: Boolean = false, useHardwareAcceleration: Boolean = false) {
        view.apply {
            try {
                if (shouldRestartIfPlaying && isAnimating) progress = 0f
                else {
                    setAnimation(animation.fileName)
                    repeatCount = if (loopForever) LottieDrawable.INFINITE else 0
                    addAnimatorListener(object : Animator.AnimatorListener {
                        override fun onAnimationRepeat(p0: Animator?) {
                            AppLogger.i("Animation ${animation.name} is repeated")
                        }

                        override fun onAnimationEnd(p0: Animator?) {
                            if (useHardwareAcceleration) useHardwareAcceleration(false)
                            removeAnimatorListener(this)
                            hide(500L)
                            AppLogger.i("Animation ${animation.name} has ended")
                        }

                        override fun onAnimationCancel(p0: Animator?) {
                            AppLogger.i("Animation ${animation.name} is cancelled")
                        }

                        override fun onAnimationStart(p0: Animator?) {
                            show()
                            if (useHardwareAcceleration) useHardwareAcceleration()
                            AppLogger.i("Animation ${animation.name} has started")
                        }
                    })
                    playAnimation()
                }
            }
            catch (ex: Exception) {
                AppLogger.e(ex)
            }
        }
    }
}