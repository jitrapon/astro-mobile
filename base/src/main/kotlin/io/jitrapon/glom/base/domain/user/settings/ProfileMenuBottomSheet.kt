package io.jitrapon.glom.base.domain.user.settings

import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import io.jitrapon.glom.R
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.ui.widget.GlomBottomSheetDialogFragment
import io.jitrapon.glom.base.util.*
import kotlinx.android.synthetic.main.profile_menu_bottom_sheet.*

class ProfileMenuBottomSheet : GlomBottomSheetDialogFragment() {

    private lateinit var viewModel: ProfileMenuViewModel

    companion object {

        const val TAG = "profile_menu_bottom_sheet_fragment"
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

        viewModel.getObservableUserLoginInfo().observe(this, Observer {
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
                    else -> { /* do nothing */ }
                }
            }
        })
    }
}
