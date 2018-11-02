package io.jitrapon.glom.base.domain.user.settings

import android.view.View
import androidx.fragment.app.FragmentActivity
import io.jitrapon.glom.base.ui.widget.GlomBottomSheetDialogFragment
import io.jitrapon.glom.base.util.obtainViewModel

class ProfileMenuBottomSheet : GlomBottomSheetDialogFragment() {

    private lateinit var viewModel: ProfileMenuViewModel

    companion object {

        const val TAG = "profile_menu_bottom_sheet_fragment"
    }

    override fun getLayoutId() = io.jitrapon.glom.R.layout.profile_menu_bottom_sheet

    override fun onSetupView(view: View) {

    }

    override fun onCreateViewModel(activity: FragmentActivity) {
        viewModel = obtainViewModel(ProfileMenuViewModel::class.java)
    }

    override fun onSubscribeToObservables() {

    }
}
