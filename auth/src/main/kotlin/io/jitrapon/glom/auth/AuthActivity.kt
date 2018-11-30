package io.jitrapon.glom.auth

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.animation.DecelerateInterpolator
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import com.google.android.gms.auth.api.credentials.Credential
import com.google.android.gms.auth.api.credentials.CredentialPickerConfig
import com.google.android.gms.auth.api.credentials.Credentials
import com.google.android.gms.auth.api.credentials.CredentialsClient
import com.google.android.gms.auth.api.credentials.HintRequest
import com.google.android.gms.auth.api.credentials.IdentityProviders
import io.jitrapon.glom.base.NAVIGATE_TO_MAIN
import io.jitrapon.glom.base.ui.BaseActivity
import io.jitrapon.glom.base.util.*
import kotlinx.android.synthetic.main.auth_activity.*

const val REQUEST_CODE_CREDENTIALS_HINT = 1000

/**
 * This activity is the main entry to the auth screen. Supports login and sign up flow.
 *
 * Created by Jitrapon
 */
class AuthActivity : BaseActivity() {

    private lateinit var viewModel: AuthViewModel

    private val credentialsClient by lazy {
        Credentials.getClient(this)
    }

    //region activity lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.auth_activity)

        auth_email_input_layout.hide()
        auth_password_input_layout.hide()
        auth_continue_with_email.setOnClickListener {
            var email: CharArray? = null
            var password: CharArray? = null
            auth_email_edit_text.length().let { count ->
                if (count > 0) {
                    email = CharArray(count)
                    auth_email_edit_text.text?.getChars(0, count, email, 0)
                }
            }
            auth_password_edit_text.length().let { count ->
                if (count > 0) {
                    password = CharArray(count)
                    auth_password_edit_text.text?.getChars(0, count, password, 0)
                }
            }
            viewModel.continueWithEmail(email, password)
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

        viewModel.getObservableAuth().observe(this@AuthActivity, Observer {
            it?.let {
                auth_email_input_layout.error = getString(it.emailError)
                auth_password_input_layout.error = getString(it.passwordError)
                if (it.showEmailExpandableLayout) {
                    expandEmailLayout()
                }
            }
        })
        viewModel.getCredentialPickerUiModel().observe(this@AuthActivity, Observer {
            it?.let(::showHintCrendentials)
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // upon selecting a credential from the hint picker, pre-fill the selected email
        if (requestCode == REQUEST_CODE_CREDENTIALS_HINT) {
            if (resultCode == RESULT_OK) {
                (data?.getParcelableExtra(Credential.EXTRA_KEY) as? Credential)?.let {
                    auth_email_edit_text.setText(it.id)
                }
            }
        }
    }

    //endregion

    override fun showLoading(show: Boolean) {
        if (show) {
            progressDialog.show(this, false)
        }
        else {
            progressDialog.dismiss()
        }
    }

    private fun expandEmailLayout() {
        if (auth_email_input_layout.isVisible && auth_password_input_layout.isVisible) return

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

    private fun showHintCrendentials(credentialPickerUiModel: CredentialPickerUiModel) {
        try {
            val hintRequest = HintRequest.Builder()
                .setHintPickerConfig(
                    CredentialPickerConfig.Builder().setShowCancelButton(credentialPickerUiModel.showCancelButton).build()
                )
                .setEmailAddressIdentifierSupported(credentialPickerUiModel.isEmailAddressIdentifierSupported)
                .setAccountTypes(IdentityProviders.GOOGLE, IdentityProviders.FACEBOOK)
                .build()
            val intent = credentialsClient.getHintPickerIntent(hintRequest)
            startIntentSenderForResult(intent.intentSender, REQUEST_CODE_CREDENTIALS_HINT, null, 0, 0, 0)
        }
        catch (ex: Exception) {
            AppLogger.e(ex)
        }
    }

    override fun navigate(action: String, payload: Any?) {
        if (action == NAVIGATE_TO_MAIN) {
            setResult(RESULT_OK)
            finish()
        }
    }
}
