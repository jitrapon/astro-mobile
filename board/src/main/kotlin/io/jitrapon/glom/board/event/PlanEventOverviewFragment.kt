package io.jitrapon.glom.board.event

import android.arch.lifecycle.Observer
import android.support.v4.app.FragmentActivity
import android.support.v7.widget.GridLayoutManager
import android.view.View
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.ui.BaseFragment
import io.jitrapon.glom.base.util.*
import io.jitrapon.glom.board.R
import kotlinx.android.synthetic.main.plan_event_overview_fragment.*

/**
 * Screen that shows overview of the event plan
 *
 * @author Jitrapon Tiachunpun
 */
class PlanEventOverviewFragment : BaseFragment() {

    private lateinit var viewModel: PlanEventViewModel

    companion object {

        @JvmStatic
        fun newInstance(): PlanEventOverviewFragment {
            return PlanEventOverviewFragment()
        }
    }

    override fun getLayoutId(): Int = R.layout.plan_event_overview_fragment

    override fun onCreateViewModel(activity: FragmentActivity) {
        viewModel = obtainViewModel(PlanEventViewModel::class.java, activity)
    }

    override fun onSetupView(view: View) {
        event_plan_attendee_recycler_view.apply {
            layoutManager = GridLayoutManager(context!!, 3)
            adapter = EventItemAttendeeAdapter(activity!!, 1000000,
                    R.layout.user_avatar_with_name_large, R.layout.remaining_indicator_large)
            setHasFixedSize(true)
        }
        event_plan_join_button.setOnClickListener {
            viewModel.toggleAttendStatus()
        }
    }

    private fun getColumnCount(count: Int): Int {
        return when {
            count < 1 -> 1
            context!!.isLandscape() -> Math.min(count, 4)
            else -> Math.min(count, 3)
        }
    }

    override fun onSubscribeToObservables() {
        viewModel.apply {
            getObservableName().observe(this@PlanEventOverviewFragment, Observer {
                it?.let {
                    event_plan_name.text = context?.getString(it)
                }
            })
            getObservableAttendeesLabel().observe(this@PlanEventOverviewFragment, Observer {
                it?.let {
                    event_plan_attendee_label.text = context?.getString(it)
                }
            })
            getObservableJoinButton().observe(this@PlanEventOverviewFragment, Observer {
                it?.let {
                    when {
                        it.status == UiModel.Status.POSITIVE -> event_plan_join_button.apply {
                            context?.let {
                                setBackgroundColor(it.colorPrimary())
                                setTextColor(it.color(io.jitrapon.glom.R.color.white))
                            }
                        }
                        it.status == UiModel.Status.NEGATIVE -> event_plan_join_button.apply {
                            context?.let {
                                setBackgroundColor(it.color(io.jitrapon.glom.R.color.white))
                                setTextColor(it.colorPrimary())
                            }
                        }
                    }
                    event_plan_join_button.text = context?.getString(it.text)
                }
            })
            getObservableAttendees().observe(this@PlanEventOverviewFragment, Observer {
                it?.let {
                    event_plan_attendee_recycler_view.apply {
                        (layoutManager as GridLayoutManager).spanCount = getColumnCount(it.size)
                        (adapter as EventItemAttendeeAdapter).setItems(it)
                    }
                }
            })
        }
    }
}
