package io.jitrapon.glom.board

import android.os.Bundle
import android.view.WindowManager
import io.jitrapon.glom.base.ui.BaseActivity

/**
 * Base parent activity to be used to show different board item when in detailed, expanded mode
 *
 * @author Jitrapon Tiachunpun
 */
abstract class BoardItemActivity : BaseActivity() {

    /*
     * Indicates whether or not this activity has been started yet
     */
    private var isActivityStarted: Boolean = false

    /**
     * Transition listener for properly animating objects at respect times to transition animations
     */
    private val transitionListener = object : android.transition.Transition.TransitionListener {

        override fun onTransitionStart(p0: android.transition.Transition?) {
            if (!isActivityStarted) onBeginTransitionAnimationStart()
            else {
                undimBackground()
                onFinishTransitionAnimationStart()
            }
        }

        override fun onTransitionEnd(p0: android.transition.Transition?) {
            if (!isActivityStarted) {
                dimBackground()
                onBeginTransitionAnimationEnd()
                isActivityStarted = true
            }
            else onFinishTransitionAnimationEnd()
        }

        override fun onTransitionResume(p0: android.transition.Transition?) {}

        override fun onTransitionPause(p0: android.transition.Transition?) {}

        override fun onTransitionCancel(p0: android.transition.Transition?) {}
    }

    //region lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(getLayout())

        window.apply {
            // must be called, otherwise layout won't appear
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)

            // set transition element
            sharedElementEnterTransition.addListener(transitionListener)
        }
    }

    /**
     * We want to make sure to save user's changes before finishing this activity
     */
    override fun onBackPressed() {
        onActivityWillFinish()

        super.onBackPressed()
    }

    //endregion
    //region other callbacks

    /**
     * Returns the current board item representing this view
     */
    protected fun getBoardItemFromIntent() = intent?.getParcelableExtra<BoardItem?>(Const.EXTRA_BOARD_ITEM)

    /**
     * Called when activity is about to finish. Child classes should save any data here
     */
    abstract fun onActivityWillFinish()

    /**
     * Returns the layout ID of this activity to be inflated
     */
    abstract fun getLayout(): Int

    /**
     * Called when activity is about to start and animation transition starts
     */
    open fun onBeginTransitionAnimationStart() {}

    /**
     * Called when activity is about to start and animation transition ends
     */
    open fun onBeginTransitionAnimationEnd() {}

    /**
     * Called when activity is about to finish and animation transition starts
     */
    open fun onFinishTransitionAnimationStart() {}

    /**
     * Called when activity is about to finish and animation transition ends
     */
    open fun onFinishTransitionAnimationEnd() {}

    /**
     * Dims background behind this activity
     */
    private fun dimBackground(dimAmount: Float = 0.6f) {
        window.apply {
            attributes.dimAmount = dimAmount
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }

    /**
     * Undims the background behind this activity
     */
    private fun undimBackground() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }

    //endregion
}
