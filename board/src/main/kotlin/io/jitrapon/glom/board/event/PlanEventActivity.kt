package io.jitrapon.glom.board.event

import android.arch.lifecycle.Observer
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.support.v4.content.ContextCompat
import io.jitrapon.glom.base.ui.BaseActivity
import io.jitrapon.glom.base.ui.widget.GlomProgressDialog
import io.jitrapon.glom.base.util.*
import io.jitrapon.glom.board.Const
import io.jitrapon.glom.board.R
import kotlinx.android.synthetic.main.plan_event_activity.*

/**
 * This activity hosts the entry point to the event overview, as well as, date and place polls for an event.
 * The ViewModel created in this activity will be shared across all fragments in the ViewPager
 *
 * Created by Jitrapon
 */
class PlanEventActivity : BaseActivity() {

    private lateinit var viewModel: PlanEventViewModel

    private val progressDialog: GlomProgressDialog by lazy {
        GlomProgressDialog()
    }

    //region lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.plan_event_activity)

        tag = "plan_event"

        // set up the toolbar
        setupActionBar(event_plan_actionbar) {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
        }

        // create all the fragments inside the viewpager
        createViewPager()
    }

    override fun onCreateViewModel() {
        // instantiate the view model to be shared across child fragments
        viewModel = obtainViewModel(PlanEventViewModel::class.java).apply {
            setItem(placeProvider, intent?.getParcelableExtra(Const.EXTRA_BOARD_ITEM))
        }
    }

    override fun onSubscribeToObservables() {
        subscribeToViewActionObservables(viewModel.observableViewAction)

        viewModel.apply {
            getObservableBackground().observe(this@PlanEventActivity, Observer {
                event_plan_viewpager_background.loadFromUrl(this@PlanEventActivity, it, null, null,
                        ColorDrawable(ContextCompat.getColor(this@PlanEventActivity, R.color.blue_grey)),
                        Transformation.CENTER_CROP,
                        800)
            })
            getObservableNavigation().observe(this@PlanEventActivity, Observer {
                it?.let {
                    setResult(RESULT_OK, Intent().apply {
                        putExtra(Const.EXTRA_BOARD_ITEM, it.payload as? EventItem?)
                    })
                    finish()
                }
            })
        }
    }

    override fun onBackPressed() {
        viewModel.saveItem()
    }

    private fun createViewPager() {
        event_plan_viewpager.apply {
            val firstVisiblePageIndex = viewModel.getFirstVisiblePageIndex()
            createWithFragments(this@PlanEventActivity, arrayOf(
                    PlanEventOverviewFragment.newInstance(),
                    PlanEventDateFragment.newInstance(firstVisiblePageIndex == 1),
                    PlanEventLocationFragment.newInstance(firstVisiblePageIndex == 2)))
            doOnFragmentSelected<PlanEventDateFragment>(supportFragmentManager) { it.onVisible() }
            setCurrentItem(firstVisiblePageIndex, false)
        }
    }

    override fun showLoading(show: Boolean) {
        if (show) {
            progressDialog.show(this)
        }
        else {
            progressDialog.dismiss()
        }
    }

    //endregion
}