package io.jitrapon.glom.auth

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.animation.DecelerateInterpolator
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.Observer
import io.jitrapon.glom.base.ui.BaseActivity
import io.jitrapon.glom.base.util.*
import kotlinx.android.synthetic.main.auth_activity.*

/**
 * This activity is the main entry to the auth screen. Supports login and sign up flow.
 *
 * Created by Jitrapon
 */
class AuthActivity : BaseActivity() {

    private lateinit var viewModel: AuthViewModel

    //region activity lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.auth_activity)

        auth_email_input_layout.hide()
        auth_password_input_layout.hide()
        auth_continue_with_email.setOnClickListener {
            invalidateLayoutToEmail()
        }
    }

    override fun onCreateViewModel() {
        viewModel = obtainViewModel(AuthViewModel::class.java)
    }

    override fun onSubscribeToObservables() {
        subscribeToViewActionObservables(viewModel.observableViewAction)

        viewModel.getObservableBackground().observe(this@AuthActivity, Observer {
                auth_scrolling_background.loadFromUrl(this@AuthActivity, it, null, null,
                        ColorDrawable(color(R.color.white)),
                        Transformation.CENTER_CROP,
                        800)
        })
    }

    //endregion

    private fun invalidateLayoutToEmail() {
        auth_email_input_layout.show()
        auth_password_input_layout.show()
        auth_continue_with_email.text = getString(R.string.auth_email_login)

        delayRun(50L) {
            val transition = TransitionSet().apply {
                ordering = TransitionSet.ORDERING_SEQUENTIAL
                addTransition(Fade(Fade.OUT))
                addTransition(ChangeBounds().setInterpolator(DecelerateInterpolator(1.5f)))
                addTransition(Fade(Fade.IN))
            }

            TransitionManager.beginDelayedTransition(auth_constraint_layout, transition)
            ConstraintSet().apply {
                clone(this@AuthActivity, R.layout.auth_activity_continue_with_email)
                applyTo(auth_constraint_layout)
            }
        }
    }
}