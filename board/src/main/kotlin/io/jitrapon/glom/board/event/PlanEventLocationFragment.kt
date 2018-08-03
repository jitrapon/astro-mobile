package io.jitrapon.glom.board.event

import android.support.v4.app.FragmentActivity
import android.view.View
import androidx.os.bundleOf
import io.jitrapon.glom.base.ui.BaseFragment
import io.jitrapon.glom.base.util.hide
import io.jitrapon.glom.base.util.obtainViewModel
import io.jitrapon.glom.board.R
import kotlinx.android.synthetic.main.plan_event_location_fragment.*

/**
 * Screen that shows date polls of the event plan
 *
 * @author Jitrapon Tiachunpun
 */
class PlanEventLocationFragment : BaseFragment() {

    private lateinit var viewModel: PlanEventViewModel

    companion object {

        @JvmStatic
        fun newInstance(isFirstVisible: Boolean): PlanEventLocationFragment {
            return PlanEventLocationFragment().apply {
                arguments = bundleOf("isFirstVisible" to isFirstVisible)
            }
        }
    }

    /**
     * Returns this fragment's XML layout
     */
    override fun getLayoutId(): Int = R.layout.plan_event_location_fragment

    /**
     * Create this fragment's ViewModel instance. The instance is reused from the one
     * instantiated with this fragment's activity
     */
    override fun onCreateViewModel(activity: FragmentActivity) {
        viewModel = obtainViewModel(PlanEventViewModel::class.java, activity)
    }

    /**
     * Called when any view must be initialized
     */
    override fun onSetupView(view: View) {
        event_plan_place_vote_progressbar.hide()

        // if this is the first page the user sees, load the date plan immediately
        arguments?.let {
            if (it.getBoolean("isFirstVisible", false)) {
                viewModel.loadPlacePolls()
            }
        }
    }

    override fun onSubscribeToObservables() {

    }
}
