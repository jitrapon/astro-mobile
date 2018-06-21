package io.jitrapon.glom.board.event

import android.support.v4.app.FragmentActivity
import android.view.View
import io.jitrapon.glom.base.ui.BaseFragment
import io.jitrapon.glom.base.util.obtainViewModel
import io.jitrapon.glom.board.R

/**
 * Screen that shows date polls of the event plan
 *
 * @author Jitrapon Tiachunpun
 */
class PlanEventLocationFragment : BaseFragment() {

    private lateinit var viewModel: PlanEventViewModel

    companion object {

        @JvmStatic
        fun newInstance(): PlanEventLocationFragment {
            return PlanEventLocationFragment()
        }
    }

    override fun getLayoutId(): Int = R.layout.plan_event_location_fragment

    override fun onCreateViewModel(activity: FragmentActivity) {
        viewModel = obtainViewModel(PlanEventViewModel::class.java, activity)
    }

    override fun onSetupView(view: View) {

    }

    override fun onSubscribeToObservables() {
        subscribeToViewActionObservables(viewModel.getObservableViewAction())
    }
}
