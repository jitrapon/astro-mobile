package io.jitrapon.glom.auth

import android.content.Intent
import android.content.IntentSender
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
import com.google.android.gms.auth.api.credentials.*
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes.RESOLUTION_REQUIRED
import com.google.android.gms.common.api.ResolvableApiException
import io.jitrapon.glom.base.NAVIGATE_TO_MAIN
import io.jitrapon.glom.base.ui.BaseActivity
import io.jitrapon.glom.base.util.*
import kotlinx.android.synthetic.main.auth_activity.*

const val REQUEST_CODE_CREDENTIALS_HINT = 1000
const val REQUEST_CODE_RESOLVE_CREDENTIALS = 1001
const val REQUEST_CODE_SAVE_CREDENTIALS = 1002
const val REQUEST_CODE_SIGN_IN = 1003

/**
 * This activity is the main entry to the auth screen. Supports login and sign up flow.
 *
 * Created by Jitrapon
 */
class AuthActivity : BaseActivity(), AuthView {

    private lateinit var viewModel: AuthViewModel

    private val credentialsClient by lazy {
        Credentials.getClient(this, CredentialsOptions.Builder()
                .forceEnableSaveDialog()
                .build())
    }

    //region activity lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.auth_activity)

        auth_email_input_layout.hide()
        auth_password_input_layout.hide()
        auth_continue_with_email.setOnClickListener {
            authenticateWithPassword(true)
        }
        auth_create_account.setOnClickListener {
            authenticateWithPassword(false)
        }
        auth_facebook_button.setOnClickListener {
            viewModel.continueWithFacebook(this)
        }
        auth_google_button.setOnClickListener {
            viewModel.continueWithGoogle(this)
        }
        auth_line_button.setOnClickListener {
            viewModel.continueWithLine(this)
        }
    }

    override fun onCreateViewModel() {
        viewModel = obtainViewModel(AuthViewModel::class.java)
    }

    override fun onSubscribeToObservables() {
        subscribeToViewActionObservables(viewModel.observableViewAction)

        viewModel.getObservableBackground().observe(this, Observer {
                auth_scrolling_background.loadFromUrl(this@AuthActivity, it, null, null,
                        ColorDrawable(color(io.jitrapon.glom.R.color.white)),
                        Transformation.CENTER_CROP,
                        800)
        })

        viewModel.getObservableAuth().observe(this, Observer {
            it?.let {
                auth_email_input_layout.error = getString(it.emailError)
                auth_password_input_layout.error = getString(it.passwordError)
                if (it.showEmailExpandableLayout) {
                    expandEmailLayout()
                }
            }
        })

        viewModel.getCredentialPickerUiModel().observe(this, Observer {
            it?.let(::showHintCrendentials)
        })

        viewModel.getCredentialRequestUiModel().observe(this, Observer {
            it?.let(::requestCredentials)
        })

        viewModel.getCredentialSaveUiModel().observe(this, Observer {
            it?.let {
                if (it.shouldDelete) {
                    deleteCredential(it)
                }
                else {
                    saveCredentials(it)
                }
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        viewModel.processOauthResult(this, requestCode, resultCode, data)

        // upon selecting a credential from the hint picker, pre-fill the selected email
        if (requestCode == REQUEST_CODE_CREDENTIALS_HINT) {
            if (resultCode == RESULT_OK) {
                (data?.getParcelableExtra(Credential.EXTRA_KEY) as? Credential)?.let {
                    auth_email_edit_text.setText(it.id)
                }
            }
        }
        else if (requestCode == REQUEST_CODE_RESOLVE_CREDENTIALS) {
            if (resultCode == RESULT_OK) {
                (data?.getParcelableExtra(Credential.EXTRA_KEY) as? Credential)?.let {
                    onCredentialRetrieved(it)
                }
            }
        }
        else if (requestCode == REQUEST_CODE_SAVE_CREDENTIALS) {
            viewModel.onSaveCredentialCompleted()
        }
    }

    //endregion

    override fun onRequireSignIn(intent: Intent) {
        startActivityForResult(intent, REQUEST_CODE_SIGN_IN)
    }

    override fun showLoading(show: Boolean) {
        if (show) {
            progressDialog.show(this, false)
        }
        else {
            progressDialog.dismiss()
        }
    }

    private fun authenticateWithPassword(isSignIn: Boolean) {
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
        viewModel.continueWithEmail(email, password, isSignIn)
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

    private fun requestCredentials(credentialRequestUiModel: CredentialRequestUiModel) {
        val request = CredentialRequest.Builder()
                .setPasswordLoginSupported(credentialRequestUiModel.isPasswordLoginSupported)
                .setAccountTypes(IdentityProviders.GOOGLE, IdentityProviders.FACEBOOK)
                .build()
        credentialsClient.request(request).addOnCompleteListener {
            if (it.isSuccessful) {
                onCredentialRetrieved(it.result.credential)
            }
            else {
                val exception = it.exception
                when (exception) {
                    // when user input is required to select a credential,
                    // the request() task will fail with a ResolvableApiException.
                    // Check that getStatusCode() returns RESOLUTION_REQUIRED
                    is ResolvableApiException -> {
                        if (exception.statusCode == RESOLUTION_REQUIRED) {
                            try {
                                exception.startResolutionForResult(this, REQUEST_CODE_RESOLVE_CREDENTIALS)
                            }
                            catch (ex: IntentSender.SendIntentException) {
                                AppLogger.e(ex)
                            }
                        }
                    }
                    is ApiException -> AppLogger.e(exception)
                }
            }
        }
    }

    private fun saveCredentials(credentialSaveUiModel: CredentialSaveUiModel) {
        val credential = when (credentialSaveUiModel.accountType) {
            AccountType.PASSWORD -> {
                if (credentialSaveUiModel.email == null || credentialSaveUiModel.password == null) {
                    viewModel.onSaveCredentialCompleted()
                    return
                }

                Credential.Builder(String(credentialSaveUiModel.email))
                    .setPassword(String(credentialSaveUiModel.password))
                    .build()
            }
            AccountType.GOOGLE -> {
                if (credentialSaveUiModel.email == null) {
                    viewModel.onSaveCredentialCompleted()
                    return
                }

                Credential.Builder(String(credentialSaveUiModel.email))
                    .setAccountType(IdentityProviders.GOOGLE)
                    .setName(credentialSaveUiModel.name)
                    .build()
            }
            AccountType.FACEBOOK -> {
                if (credentialSaveUiModel.email == null) {
                    viewModel.onSaveCredentialCompleted()
                    return
                }

                Credential.Builder(String(credentialSaveUiModel.email))
                    .setAccountType(IdentityProviders.FACEBOOK)
                    .setName(credentialSaveUiModel.name)
                    .build()
            }
            AccountType.LINE -> {
                null
            }
        }
        credential ?: viewModel.onSaveCredentialCompleted()

        credentialsClient.save(credential!!).addOnCompleteListener {
            when {
                it.isSuccessful -> viewModel.onSaveCredentialCompleted()
                it.isCanceled -> viewModel.onSaveCredentialCompleted()
                else -> {
                    val exception = it.exception
                    when (exception) {
                        is ResolvableApiException -> {
                            // If the user chooses not to save credentials,
                            // the user won't be prompted again to save any account's credentials for the app.
                            // If you call CredentialsClient.save() after a user has opted out, its result will have a status code of CANCELED.
                            // The user can opt in later from the Google Settings app, in the Smart Lock for Passwords section.
                            // The user must enable credential saving for all accounts to be prompted to save credentials next time.
                            if (exception.statusCode == RESOLUTION_REQUIRED) {
                                try {
                                    exception.startResolutionForResult(this, REQUEST_CODE_SAVE_CREDENTIALS)
                                }
                                catch (ex: IntentSender.SendIntentException) {
                                    AppLogger.e(ex)
                                    viewModel.onSaveCredentialCompleted()
                                }
                            }
                            else {
                                exception.let(AppLogger::e)
                                viewModel.onSaveCredentialCompleted()
                            }
                        }
                        else -> {
                            exception?.let(AppLogger::e)
                            viewModel.onSaveCredentialCompleted()
                        }
                    }
                }
            }
        }
    }

    private fun deleteCredential(credentialSaveUiModel: CredentialSaveUiModel) {
        val credential = when (credentialSaveUiModel.accountType) {
            AccountType.PASSWORD -> {
                credentialSaveUiModel.email ?: return

                Credential.Builder(String(credentialSaveUiModel.email))
                        .setPassword(if (credentialSaveUiModel.password == null) null else String(credentialSaveUiModel.password))
                        .build()
            }
            AccountType.GOOGLE -> {
                credentialSaveUiModel.email ?: return

                Credential.Builder(String(credentialSaveUiModel.email))
                    .setAccountType(IdentityProviders.GOOGLE)
                    .setName(credentialSaveUiModel.name)
                    .build()
            }
            AccountType.FACEBOOK -> {
                credentialSaveUiModel.email ?: return

                Credential.Builder(String(credentialSaveUiModel.email))
                    .setAccountType(IdentityProviders.FACEBOOK)
                    .setName(credentialSaveUiModel.name)
                    .build()
            }
            AccountType.LINE -> null
        }
        credential ?: return

        credentialsClient.delete(credential).addOnCompleteListener {
            if (!it.isSuccessful) {
                it.exception?.let(AppLogger::e)
            }
        }
    }

    private fun onCredentialRetrieved(credential: Credential) {
        credential.accountType.let {
            // email / password
            if (it == null) {
                if (credential.password != null) {
                    val email = credential.id.toCharArray()
                    val password = credential.password!!.toCharArray()
                    viewModel.signInWithPassword(email, password)
                }
            }
            else if (it == IdentityProviders.GOOGLE) viewModel.continueWithGoogle(this)
            else if (it == IdentityProviders.FACEBOOK) viewModel.continueWithFacebook(this)
        }
    }

    override fun navigate(action: String, payload: Any?) {
        if (action == NAVIGATE_TO_MAIN) {
            setResult(RESULT_OK)
            finish()
        }
    }
}
