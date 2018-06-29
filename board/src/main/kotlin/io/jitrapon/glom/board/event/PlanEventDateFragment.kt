package io.jitrapon.glom.board.event

import android.support.v4.app.FragmentActivity
import android.view.View
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import io.jitrapon.glom.base.ui.BaseFragment
import io.jitrapon.glom.base.util.hide
import io.jitrapon.glom.base.util.obtainViewModel
import io.jitrapon.glom.board.R
import kotlinx.android.synthetic.main.plan_event_date_fragment.*

/**
 * Screen that shows date polls of the event plan
 *
 * @author Jitrapon Tiachunpun
 */
class PlanEventDateFragment : BaseFragment() {

    private lateinit var viewModel: PlanEventViewModel

    companion object {

        @JvmStatic
        fun newInstance(): PlanEventDateFragment {
            return PlanEventDateFragment()
        }
    }

    /**
     * Returns this fragment's XML layout
     */
    override fun getLayoutId(): Int = R.layout.plan_event_date_fragment

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
        event_plan_date_calendar.apply {
            selectionMode = MaterialCalendarView.SELECTION_MODE_MULTIPLE
        }
        event_plan_date_vote_progressbar.hide()
    }

    /**
     * Called when this fragment is ready to subscribe to ViewModel's events
     */
    override fun onSubscribeToObservables() {
        subscribeToViewActionObservables(viewModel.getObservableViewAction())
    }

    //region fragment functions

    /**
     * Called when fragment is visible
     */
    fun onVisible() {
        viewModel.loadDatePolls()
    }

    //endregion
}
