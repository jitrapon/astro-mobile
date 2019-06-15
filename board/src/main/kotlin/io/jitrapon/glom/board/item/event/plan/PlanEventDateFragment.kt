package io.jitrapon.glom.board.item.event.plan

import android.view.View
import androidx.core.os.bundleOf
import androidx.lifecycle.Observer
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.ui.BaseFragment
import io.jitrapon.glom.base.ui.widget.recyclerview.VerticalSpaceItemDecoration
import io.jitrapon.glom.base.util.*
import io.jitrapon.glom.board.R
import io.jitrapon.glom.board.item.event.widget.DateTimePicker
import kotlinx.android.synthetic.main.plan_event_date_fragment.*

/**
 * Screen that shows date polls of the event plan
 *
 * @author Jitrapon Tiachunpun - Nad
 */
class PlanEventDateFragment : BaseFragment() {

    private lateinit var viewModel: PlanEventViewModel

    private val dateTimePicker: DateTimePicker by lazy {
        DateTimePicker(context!!)
    }

    companion object {

        const val DELAY_FIRST_LOAD = 200L

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
    override fun onCreateViewModel(activity: androidx.fragment.app.FragmentActivity) {
        viewModel = obtainViewModel(PlanEventViewModel::class.java, activity)
    }

    /**
     * Called when any view must be initialized
     */
    override fun onSetupView(view: View) {
//        event_plan_date_calendar.apply {
//            setSelectionMode(GlomCalendarView.SelectionMode.NONE)
//            setSelectableDateRange(Date() to null)
//        }
        event_plan_date_vote_progressbar.hide()
        event_plan_date_poll_recyclerview.apply {
            adapter = EventPollAdapter(viewModel, TYPE_DATE_POLL)
            addItemDecoration(VerticalSpaceItemDecoration(context!!.dimen(R.dimen.event_plan_poll_vertical_offset)))
            (itemAnimator as androidx.recyclerview.widget.SimpleItemAnimator).supportsChangeAnimations = false
        }
        event_plan_date_poll_status_button.setOnClickListener {
            viewModel.toggleDatePollStatus()
        }
        event_plan_date_select_poll_button.setOnClickListener {
            viewModel.setDateTimeFromPoll()
        }

        // if this is the first page the user sees, load the date plan immediately
        delayRun(DELAY_FIRST_LOAD) {
            arguments?.let {
                if (it.getBoolean("isFirstVisible", false)) {
                    viewModel.loadDatePolls()
                }
            }
        }
    }

    /**
     * Called when this fragment is ready to subscribe to ViewModel's events
     */
    override fun onSubscribeToObservables() {
        viewModel.getObservableDatePlan().observe(viewLifecycleOwner, Observer {
            it?.let {
                when (it.status) {
                    UiModel.Status.LOADING -> {
                        event_plan_date_vote_progressbar.show()
                    }
                    UiModel.Status.SUCCESS -> {
                        if (it.itemsChangedIndices.isNullOrEmpty()) {
//                            event_plan_date_calendar.apply {
//                                clear()
//                                setSelectionMode(GlomCalendarView.SelectionMode.MULTIPLE)
//                                for (datePoll in it.datePolls) {
//                                    val endDate = datePoll.calendarEndDate
//                                    if (endDate == null) {
//                                        select(datePoll.calendarStartDate, false)
//                                    }
//                                    else {
//                                        selectRange(datePoll.calendarStartDate, endDate)
//                                    }
//                                }
//                                onDateSelected { date, _ ->
//                                    select(date, false)
//                                    viewModel.showDateTimeRangePicker(date)
//                                }
//                            }

                            event_plan_date_vote_progressbar.hide()
                            event_plan_date_poll_recyclerview.adapter!!.notifyDataSetChanged()
                        }
                        else {
                            for (index in it.itemsChangedIndices!!) {
                                event_plan_date_poll_recyclerview.adapter!!.notifyItemChanged(index)
                            }
                        }
                    }
                    else -> {
//                        event_plan_date_calendar.apply {
//                            selectionMode = MaterialCalendarView.SELECTION_MODE_NONE
//                        }

                        event_plan_date_vote_progressbar.hide()
                    }
                }
            }
        })

        viewModel.getObservableDateTimePicker().observe(viewLifecycleOwner, Observer {
            it?.let { picker ->
                dateTimePicker.showRangePicker(picker, { (startDate, endDate) ->
                    viewModel.addDatePoll(startDate, endDate)
                }, {
                    viewModel.cancelAddDatePoll()

                    // if this date does not belong to any date poll, deselect it
                    val datePolls = viewModel.getDatePolls()
                    var foundInDatePoll = false
                    for (poll in datePolls) {
                        if (poll.calendarStartDate.sameDateAs(picker.startDate)) {
                            foundInDatePoll = true
                            break
                        }
                    }
                    if (!foundInDatePoll) {
//                        picker.startDate?.let { date ->
//                            event_plan_date_calendar.select(date, false, false)
//                        }
                    }
                })
            }
        })

        viewModel.getObservableDateVoteStatusButton().observe(viewLifecycleOwner, Observer {
            it?.let {
                when {
                    it.status == UiModel.Status.POSITIVE -> {
                        event_plan_date_poll_status_button.apply {
                            context?.let {
                                show()
                                setBackgroundColor(it.colorPrimary())
                                setTextColor(it.color(io.jitrapon.glom.R.color.white))
                            }
                        }
                        event_plan_date_select_poll_button.show()
                    }
                    it.status == UiModel.Status.NEGATIVE -> {
                        event_plan_date_poll_status_button.apply {
                            context?.let {
                                show()
                                setBackgroundColor(it.color(io.jitrapon.glom.R.color.white))
                                setTextColor(it.colorPrimary())
                            }
                        }
                        event_plan_date_select_poll_button.hide()
                    }
                    it.status == UiModel.Status.EMPTY -> {
                        event_plan_date_poll_status_button.hide()
                        event_plan_date_select_poll_button.hide()
                    }
                }
                event_plan_date_poll_status_button.text = context?.getString(it.text)
            }
        })

        viewModel.getObservableDateSelectButton().observe(viewLifecycleOwner, Observer {
            it?.let { button ->
                context?.let {
                    event_plan_date_select_poll_button.text = it.getString(button.text)
                }
            }
        })
    }

    //region fragment functions

    /**
     * Called when fragment is visible
     */
    fun onVisible() {
        delayRun(DELAY_FIRST_LOAD) {
            viewModel.loadDatePolls()
        }
    }

    //endregion
}
