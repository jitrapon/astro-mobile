package io.jitrapon.glom.base.domain.user.settings

import android.content.DialogInterface
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import com.google.android.instantapps.InstantApps
import io.jitrapon.glom.R
import io.jitrapon.glom.base.AUTH_REQUEST_CODE
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.navigation.Router
import io.jitrapon.glom.base.navigation.Router.MODULE_AUTH
import io.jitrapon.glom.base.ui.widget.GlomBottomSheetDialogFragment
import io.jitrapon.glom.base.util.*
import kotlinx.android.synthetic.main.profile_menu_bottom_sheet.*

class ProfileMenuBottomSheet : GlomBottomSheetDialogFragment() {

    private lateinit var viewModel: ProfileMenuViewModel

    private var onDismissHandler: (() -> Unit)? = null

    companion object {

        const val TAG = "profile_menu_bottom_sheet_fragment"
        const val NAVIGATE_ANIM_GRACE = 175L
    }

    override fun getLayoutId() = io.jitrapon.glom.R.layout.profile_menu_bottom_sheet

    override fun onSetupView(view: View) {
        profile_menu_bottom_sheet_auth_button.setOnClickListener {
            viewModel.signInOrSignOut()
        }
    }

    override fun onCreateViewModel(activity: FragmentActivity) {
        viewModel = obtainViewModel(ProfileMenuViewModel::class.java)
    }

    override fun onSubscribeToObservables() {
        subscribeToViewActionObservables(viewModel.getObservableViewAction())

        viewModel.getObservableUserLoginInfo().observe(viewLifecycleOwner, Observer {
            it?.let { uiModel ->
                val placeHolderDrawable = context!!.drawable(R.drawable.ic_empty_account_colored)!!
                profile_menu_bottom_sheet_user_avatar.apply {
                    loadFromUrl(this@ProfileMenuBottomSheet,
                            uiModel.avatar, R.drawable.ic_empty_account_colored, R.drawable.ic_empty_account_colored, placeHolderDrawable, Transformation.CIRCLE_CROP)
                }

                profile_menu_bottom_sheet_user_id.text = context!!.getString(uiModel.userId)

                when (uiModel.loginButtonUiModel.status) {
                    UiModel.Status.POSITIVE -> {
                        profile_menu_bottom_sheet_auth_button.apply {
                            setPositiveTheme()
                            text = context.getString(uiModel.loginButtonUiModel.text)
                        }
                    }
                    UiModel.Status.NEGATIVE -> {
                        profile_menu_bottom_sheet_auth_button.apply {
                            setNegativeTheme()
                            text = context.getString(uiModel.loginButtonUiModel.text)
                        }
                    }
                    UiModel.Status.LOADING -> {
                        profile_menu_bottom_sheet_auth_button.applyState(uiModel.loginButtonUiModel)
                    }
                    else -> { /* do nothing */ }
                }
            }
        })
    }

    override fun navigate(action: String, payload: Any?) {
        activity?.let { activity ->
            if (action == NAVIGATE_TO_AUTHENTICATION) {
                dismiss()
                delayRun(NAVIGATE_ANIM_GRACE) {
                    Router.navigate(activity, InstantApps.isInstantApp(activity), MODULE_AUTH, false,
                            arrayOf(R.anim.slide_up, R.anim.fade_out), AUTH_REQUEST_CODE)
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface?) {
        super.onDismiss(dialog)

        if (viewModel.hasSignedOut) onDismissHandler?.invoke()
    }

    fun setOnDismissHandler(handler: () -> Unit) {
        onDismissHandler = handler
    }
}
