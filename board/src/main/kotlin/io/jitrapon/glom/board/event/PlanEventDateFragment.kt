package io.jitrapon.glom.board.event

import android.arch.lifecycle.Observer
import android.support.v4.app.FragmentActivity
import android.support.v7.widget.SimpleItemAnimator
import android.view.View
import androidx.os.bundleOf
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.ui.BaseFragment
import io.jitrapon.glom.base.ui.widget.recyclerview.VerticalSpaceItemDecoration
import io.jitrapon.glom.base.util.dimen
import io.jitrapon.glom.base.util.hide
import io.jitrapon.glom.base.util.obtainViewModel
import io.jitrapon.glom.base.util.show
import io.jitrapon.glom.board.R
import kotlinx.android.synthetic.main.plan_event_date_fragment.*
import java.util.*

/**
 * Screen that shows date polls of the event plan
 *
 * @author Jitrapon Tiachunpun
 */
class PlanEventDateFragment : BaseFragment() {

    private lateinit var viewModel: PlanEventViewModel

    companion object {

        @JvmStatic
        fun newInstance(isFirstVisible: Boolean): PlanEventDateFragment {
            return PlanEventDateFragment().apply {
                arguments = bundleOf("isFirstVisible" to isFirstVisible)
            }
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
            val today = Date()
            selectionMode = MaterialCalendarView.SELECTION_MODE_NONE
            state().edit()
                    .setMinimumDate(today)
                    .commit()
            setOnDateChangedListener { widget, date, selected ->

            }
        }
        event_plan_date_vote_progressbar.hide()
        event_plan_date_poll_recyclerview.apply {
            adapter = EventPollAdapter(viewModel, true)
            addItemDecoration(VerticalSpaceItemDecoration(context!!.dimen(R.dimen.event_plan_poll_vertical_offset)))
            (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        }

        // if this is the first page the user sees, load the date plan immediately
        arguments?.let {
            if (it.getBoolean("isFirstVisible", false)) {
                viewModel.loadDatePolls()
            }
        }
    }

    /**
     * Called when this fragment is ready to subscribe to ViewModel's events
     */
    override fun onSubscribeToObservables() {
        viewModel.getObservableDatePlan().observe(this, Observer {
            it?.let {
                when (it.status) {
                    UiModel.Status.LOADING -> {
                        event_plan_date_calendar.apply {
                            selectionMode = MaterialCalendarView.SELECTION_MODE_NONE
                        }

                        event_plan_date_vote_progressbar.show()
                    }
                    UiModel.Status.SUCCESS -> {
                        if (it.itemChangedIndex == null) {
                            event_plan_date_calendar.apply {
                                selectionMode = MaterialCalendarView.SELECTION_MODE_RANGE
                                it.datePolls.forEach {
                                    setSelectedDate()
                                    it.calendarStartDate
                                }
                            }

                            event_plan_date_vote_progressbar.hide()
                            event_plan_date_poll_recyclerview.adapter.notifyDataSetChanged()
                        }
                        else {
                            event_plan_date_poll_recyclerview.adapter.notifyItemChanged(it.itemChangedIndex!!)
                        }
                    }
                    else -> {
                        event_plan_date_calendar.apply {
                            selectionMode = MaterialCalendarView.SELECTION_MODE_NONE
                        }

                        event_plan_date_vote_progressbar.hide()
                    }
                }
            }
        })
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
