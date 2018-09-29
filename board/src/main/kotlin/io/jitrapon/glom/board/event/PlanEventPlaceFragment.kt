package io.jitrapon.glom.board.event

import android.view.View
import androidx.core.os.bundleOf
import androidx.lifecycle.Observer
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.ui.BaseFragment
import io.jitrapon.glom.base.ui.widget.recyclerview.HorizontalSpaceItemDecoration
import io.jitrapon.glom.base.ui.widget.recyclerview.VerticalSpaceItemDecoration
import io.jitrapon.glom.base.util.*
import io.jitrapon.glom.board.R
import kotlinx.android.synthetic.main.plan_event_place_fragment.*

/**
 * Screen that shows place polls of the event plan
 *
 * @author Jitrapon Tiachunpun
 */
class PlanEventPlaceFragment : BaseFragment() {

    private lateinit var viewModel: PlanEventViewModel

    companion object {

        const val DELAY_FIRST_LOAD = 200L

        @JvmStatic
        fun newInstance(isFirstVisible: Boolean): PlanEventPlaceFragment {
            return PlanEventPlaceFragment().apply {
                arguments = bundleOf("isFirstVisible" to isFirstVisible)
            }
        }
    }

    /**
     * Returns this fragment's XML layout
     */
    override fun getLayoutId(): Int = R.layout.plan_event_place_fragment

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
        event_plan_place_vote_progressbar.hide()
        event_plan_place_poll_recyclerview.apply {
            adapter = EventPollAdapter(viewModel, TYPE_PLACE_POLL)
            addItemDecoration(VerticalSpaceItemDecoration(context!!.dimen(R.dimen.event_plan_poll_vertical_offset)))
            (itemAnimator as androidx.recyclerview.widget.SimpleItemAnimator).supportsChangeAnimations = false
        }
        event_plan_place_poll_status_button.setOnClickListener {
            viewModel.togglePlacePollStatus()
        }
        event_plan_place_select_poll_button.setOnClickListener {
            viewModel.setPlaceFromPoll()
        }
        event_plan_place_card_recyclerview.apply {
            adapter = EventPollAdapter(viewModel, TYPE_PLACE_CARD)
            addItemDecoration(HorizontalSpaceItemDecoration(context!!.dimen(R.dimen.event_plan_place_card_offset), true))
            (itemAnimator as androidx.recyclerview.widget.SimpleItemAnimator).supportsChangeAnimations = false
        }

        // if this is the first page the user sees, load the plan immediately
        delayRun(DELAY_FIRST_LOAD) {
            arguments?.let {
                if (it.getBoolean("isFirstVisible", false)) {
                    viewModel.loadPlacePolls()
                }
            }
        }
    }

    /**
     * Called when this fragment is ready to subscribe to ViewModel's events
     */
    override fun onSubscribeToObservables() {
        viewModel.getObservablePlacePlan().observe(this@PlanEventPlaceFragment, Observer {
            it?.let {
                when (it.status) {
                    UiModel.Status.LOADING -> {
                        event_plan_place_vote_progressbar.show()
                    }
                    UiModel.Status.SUCCESS -> {
                        event_plan_place_vote_progressbar.hide()

                        // update the place polls
                        if (it.pollChangedIndices.isNullOrEmpty()) {
                            event_plan_place_poll_recyclerview.adapter!!.notifyDataSetChanged()
                        }
                        else {
                            for (index in it.pollChangedIndices!!) {
                                event_plan_place_poll_recyclerview.adapter!!.notifyItemChanged(index)
                            }
                        }

                        // update the card suggestions
                        if (it.cardChangedIndices.isNullOrEmpty()) {
                            event_plan_place_card_recyclerview.adapter!!.notifyDataSetChanged()
                        }
                        else {
                            for (index in it.cardChangedIndices!!) {
                                event_plan_place_card_recyclerview.adapter!!.notifyItemChanged(index)
                            }
                        }
                    }
                    else -> {
                        event_plan_place_vote_progressbar.hide()
                    }
                }
            }
        })

        viewModel.getObservablePlaceStatusButton().observe(this@PlanEventPlaceFragment, Observer {
            it?.let {
                when {
                    it.status == UiModel.Status.POSITIVE -> {
                        event_plan_place_poll_status_button.apply {
                            context?.let {
                                show()
                                setBackgroundColor(it.colorPrimary())
                                setTextColor(it.color(io.jitrapon.glom.R.color.white))
                            }
                        }
                        event_plan_place_select_poll_button.show()
                    }
                    it.status == UiModel.Status.NEGATIVE -> {
                        event_plan_place_poll_status_button.apply {
                            context?.let {
                                show()
                                setBackgroundColor(it.color(io.jitrapon.glom.R.color.white))
                                setTextColor(it.colorPrimary())
                            }
                        }
                        event_plan_place_select_poll_button.hide()
                    }
                    it.status == UiModel.Status.EMPTY -> {
                        event_plan_place_poll_status_button.hide()
                        event_plan_place_select_poll_button.hide()
                    }
                }
                event_plan_place_poll_status_button.text = context?.getString(it.text)
            }
        })

        viewModel.getObservablePlaceSelectButton().observe(this@PlanEventPlaceFragment, Observer {
            it?.let { button ->
                context?.let {
                    event_plan_place_select_poll_button.text = it.getString(button.text)
                }
            }
        })
    }

    /**
     * Called when fragment is visible
     */
    fun onVisible() {
        delayRun(DELAY_FIRST_LOAD) {
            viewModel.loadPlacePolls()
        }
    }
}
