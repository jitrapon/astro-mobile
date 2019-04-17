package io.jitrapon.glom.board.item.event.widget.datetimepicker

import android.content.DialogInterface
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.TimePicker
import androidx.core.view.children
import androidx.core.view.plusAssign
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import io.jitrapon.glom.base.model.DateTimePickerUiModel
import io.jitrapon.glom.base.model.UiModel
import io.jitrapon.glom.base.ui.widget.GlomBottomSheetDialogFragment
import io.jitrapon.glom.base.util.AppLogger
import io.jitrapon.glom.base.util.attrColor
import io.jitrapon.glom.base.util.color
import io.jitrapon.glom.base.util.colorPrimary
import io.jitrapon.glom.base.util.drawable
import io.jitrapon.glom.base.util.findViewsWithContentDescription
import io.jitrapon.glom.base.util.getString
import io.jitrapon.glom.base.util.hide
import io.jitrapon.glom.base.util.obtainViewModel
import io.jitrapon.glom.base.util.px
import io.jitrapon.glom.base.util.setMargin
import io.jitrapon.glom.base.util.show
import io.jitrapon.glom.board.R
import io.jitrapon.glom.board.item.event.widget.GlomCalendarView
import kotlinx.android.synthetic.main.date_time_picker_bottom_sheet.date_time_picker_bottom_sheet_afternoon_choice
import kotlinx.android.synthetic.main.date_time_picker_bottom_sheet.date_time_picker_bottom_sheet_calendar_view_stub
import kotlinx.android.synthetic.main.date_time_picker_bottom_sheet.date_time_picker_bottom_sheet_cancel_button
import kotlinx.android.synthetic.main.date_time_picker_bottom_sheet.date_time_picker_bottom_sheet_displayed_date
import kotlinx.android.synthetic.main.date_time_picker_bottom_sheet.date_time_picker_bottom_sheet_displayed_time
import kotlinx.android.synthetic.main.date_time_picker_bottom_sheet.date_time_picker_bottom_sheet_done_button
import kotlinx.android.synthetic.main.date_time_picker_bottom_sheet.date_time_picker_bottom_sheet_evening_choice
import kotlinx.android.synthetic.main.date_time_picker_bottom_sheet.date_time_picker_bottom_sheet_full_day_toggle
import kotlinx.android.synthetic.main.date_time_picker_bottom_sheet.date_time_picker_bottom_sheet_morning_choice
import kotlinx.android.synthetic.main.date_time_picker_bottom_sheet.date_time_picker_bottom_sheet_night_choice
import kotlinx.android.synthetic.main.date_time_picker_bottom_sheet.date_time_picker_bottom_sheet_noon_choice
import kotlinx.android.synthetic.main.date_time_picker_bottom_sheet.date_time_picker_bottom_sheet_time_layout
import kotlinx.android.synthetic.main.date_time_picker_bottom_sheet.date_time_picker_date_item_1
import kotlinx.android.synthetic.main.date_time_picker_bottom_sheet.date_time_picker_date_item_2
import kotlinx.android.synthetic.main.date_time_picker_bottom_sheet.date_time_picker_date_item_3
import kotlinx.android.synthetic.main.date_time_picker_bottom_sheet.date_time_picker_date_item_4
import kotlinx.android.synthetic.main.date_time_picker_bottom_sheet.date_time_picker_date_item_5
import kotlinx.android.synthetic.main.date_time_picker_bottom_sheet.date_time_picker_layout
import kotlinx.android.synthetic.main.date_time_picker_bottom_sheet_expanded_layout.date_time_picker_bottom_sheet_expanded_cancel_button
import kotlinx.android.synthetic.main.date_time_picker_bottom_sheet_expanded_layout.date_time_picker_bottom_sheet_expanded_done_button
import kotlinx.android.synthetic.main.date_time_picker_date_item.view.date_time_picker_date_item_date
import kotlinx.android.synthetic.main.date_time_picker_date_item.view.date_time_picker_date_item_day_of_week
import java.util.Date

const val ANIMATION_DELAY = 100L

class BottomSheetDateTimePicker : GlomBottomSheetDialogFragment() {

    private var uiModel: DateTimePickerUiModel? = null

    private lateinit var viewModel: DateTimePickerViewModel

    private lateinit var onDateTimeSetListener: (Date) -> Unit

    private var collapsedViews: ArrayList<View>? = null

    private var expandedViews: ArrayList<View> = ArrayList()

    private var calendarView: GlomCalendarView? = null
    private var timePicker: TimePicker? = null

    private var hasExpanded = false

    override fun getLayoutId() = R.layout.date_time_picker_bottom_sheet

    fun init(uiModel: DateTimePickerUiModel, onDateTimeSetListener: (Date) -> Unit) {
        this.uiModel = uiModel
        this.onDateTimeSetListener = onDateTimeSetListener
    }

    private val bottomSheetStateCallback = object : BottomSheetBehavior.BottomSheetCallback() {

        private var hasExpandedView = false
        private var hasCollapsedView = true

        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            if (!hasCollapsedView && slideOffset < 0.35) {
                expandedViews.forEach {
                    it.hide(ANIMATION_DELAY, invisible = true)
                }
                collapsedViews?.forEach {
                    it.show(ANIMATION_DELAY)
                }

                hasExpandedView = false
                hasCollapsedView = true
            }
            if (!hasExpandedView && slideOffset > 0.65) {
                expandedViews.forEach {
                    it.show(ANIMATION_DELAY)
                }
                collapsedViews?.forEach {
                    it.hide(ANIMATION_DELAY, invisible = true)
                }

                hasExpandedView = true
                hasCollapsedView = false
            }
        }

        override fun onStateChanged(bottomSheet: View, newState: Int) {
            when (newState) {
                BottomSheetBehavior.STATE_HIDDEN -> dialog?.cancel()
                BottomSheetBehavior.STATE_EXPANDED -> {
                    date_time_picker_bottom_sheet_calendar_view_stub?.apply {
                        setOnInflateListener { _, inflated ->
                            expandedViews.add(inflated)
                            calendarView = inflated.findViewById(R.id.date_time_picker_bottom_sheet_calendar)
                            timePicker = inflated.findViewById(R.id.date_time_picker_bottom_sheet_timepicker)
                            date_time_picker_bottom_sheet_expanded_cancel_button.setOnClickListener { dismiss() }
                            date_time_picker_bottom_sheet_expanded_done_button.setOnClickListener {
                                dismiss()
                                onDateTimeSetListener(viewModel.getCurrentDate())
                            }
                        }
                        inflate()
                    }

                    if (!hasExpanded) {
                        resetCalendar()
                        resetTimePicker()
                    }

                    hasExpanded = true
                }
                BottomSheetBehavior.STATE_SETTLING -> {

                }
                BottomSheetBehavior.STATE_DRAGGING -> {

                }
                BottomSheetBehavior.STATE_COLLAPSED -> {
                    if (hasExpanded) {
                        viewModel.requestDateTimeChange()
                    }

                    hasExpanded = false
                }
                BottomSheetBehavior.STATE_HALF_EXPANDED -> {}
            }
        }
    }

    override fun onResume() {
        super.onResume()

        (dialog as? BottomSheetDialog)?.behavior?.apply {
            setBottomSheetCallback(bottomSheetStateCallback)
            peekHeight = 340.px // this can be better calculated and not hard-coded
        }
    }

    override fun onSetupView(view: View) {
        date_time_picker_date_item_1.setOnClickListener { viewModel.selectDateChoice(0) }
        date_time_picker_date_item_2.setOnClickListener { viewModel.selectDateChoice(1) }
        date_time_picker_date_item_3.setOnClickListener { viewModel.selectDateChoice(2) }
        date_time_picker_date_item_4.setOnClickListener { viewModel.selectDateChoice(3) }
        date_time_picker_date_item_5.setOnClickListener { viewModel.selectDateChoice(4) }

        date_time_picker_bottom_sheet_morning_choice.setOnClickListener { viewModel.selectDayTimeChoice(0) }
        date_time_picker_bottom_sheet_noon_choice.setOnClickListener { viewModel.selectDayTimeChoice(1) }
        date_time_picker_bottom_sheet_afternoon_choice.setOnClickListener { viewModel.selectDayTimeChoice(2) }
        date_time_picker_bottom_sheet_evening_choice.setOnClickListener { viewModel.selectDayTimeChoice(3) }
        date_time_picker_bottom_sheet_night_choice.setOnClickListener { viewModel.selectDayTimeChoice(4) }

        date_time_picker_bottom_sheet_cancel_button.setOnClickListener { dismiss() }
        date_time_picker_bottom_sheet_done_button.setOnClickListener {
            dismiss()
            onDateTimeSetListener(viewModel.getCurrentDate())
        }

        date_time_picker_layout.findViewsWithContentDescription(getString(R.string.date_picker_collapsed_view)) {
            collapsedViews = this
        }
    }

    override fun onCreateViewModel(activity: FragmentActivity) {
        viewModel = obtainViewModel(DateTimePickerViewModel::class.java).apply {
            setDateTime(uiModel)
        }
    }

    override fun onSubscribeToObservables() {
        subscribeToViewActionObservables(viewModel.observableViewAction)

        viewModel.getObservableDate().observe(viewLifecycleOwner, Observer {
            date_time_picker_bottom_sheet_displayed_date.text = context!!.getString(it)
        })
        viewModel.getObservableTime().observe(viewLifecycleOwner, Observer {
            date_time_picker_bottom_sheet_displayed_time.text = context!!.getString(it)
        })
        viewModel.getObservableFullDay().observe(viewLifecycleOwner, Observer {
            it?.let {
                date_time_picker_bottom_sheet_full_day_toggle.isChecked = it
            }
        })
        viewModel.getObservableSimpleDateChoices().observe(viewLifecycleOwner, Observer {
            it?.let {
                setSimpleDateText(date_time_picker_date_item_1, it[0])
                setSimpleDateText(date_time_picker_date_item_2, it[1])
                setSimpleDateText(date_time_picker_date_item_3, it[2])
                setSimpleDateText(date_time_picker_date_item_4, it[3])
                setSimpleDateText(date_time_picker_date_item_5, it[4])
            }
        })
        viewModel.getObservableDayTimeChoice().observe(viewLifecycleOwner, Observer {
            it?.let {
                date_time_picker_bottom_sheet_morning_choice.isChecked = it == 0
                date_time_picker_bottom_sheet_noon_choice.isChecked = it == 1
                date_time_picker_bottom_sheet_afternoon_choice.isChecked = it == 2
                date_time_picker_bottom_sheet_evening_choice.isChecked = it == 3
                date_time_picker_bottom_sheet_night_choice.isChecked = it == 4
            }
        })
        viewModel.getObservableTimeChoices().observe(viewLifecycleOwner, Observer {
            it?.let { choices ->
                date_time_picker_bottom_sheet_time_layout.removeAllViews()
                for (choice in choices) {
                    (LayoutInflater.from(context).inflate(R.layout.date_time_picker_chip, null) as Chip).apply {
                        text = context.getString(choice.timeOfDay)
                        setOnClickListener {
                            viewModel.selectTimeChoice(choice)
                        }
                        tag = choice
                        isChecked = choice.status == UiModel.Status.POSITIVE
                        date_time_picker_bottom_sheet_time_layout += this
                        setMargin(right = 3f.px)
                    }
                }
            }
        })
        viewModel.getObservableTimeChoice().observe(viewLifecycleOwner, Observer {
            it?.let {
                for (view in date_time_picker_bottom_sheet_time_layout.children) {
                    if (view is Chip) {
                        view.isChecked = view.tag == it
                    }
                }
            }
        })
    }

    private fun resetCalendar() {
        calendarView?.apply {
            clear()
            select(viewModel.getCurrentDate(), scrollToDate = false, selected = true)
            viewModel.selectCalendarDate(viewModel.getCurrentDate())
            setSelectableDateRange(viewModel.getMinDate() to null)
            onDateSelected { date, isSelected ->
                if (isSelected) viewModel.selectCalendarDate(date)
            }
        }
    }

    private fun resetTimePicker() {
        timePicker?.apply {
            val (hourOfDay, minute) = viewModel.getCurrentTime()
            hour = hourOfDay
            setMinute(minute)
            setOnTimeChangedListener { _, h, m ->
                viewModel.setTime(h, m)
            }
        }
    }

    private fun setSimpleDateText(view: View, uiModel: DateChoiceUiModel) {
        view.date_time_picker_date_item_day_of_week.text = context!!.getString(uiModel.dayOfWeek)
        view.date_time_picker_date_item_date.text = context!!.getString(uiModel.dayOfMonth)
        if (uiModel.status == UiModel.Status.POSITIVE) {
            view.background = context!!.drawable(R.drawable.bg_solid_circle)
            view.backgroundTintList = ColorStateList.valueOf(context!!.colorPrimary())
            view.date_time_picker_date_item_day_of_week.setTextColor(context!!.color(R.color.white))
            view.date_time_picker_date_item_date.setTextColor(context!!.color(R.color.white))
        }
        else {
            view.background = null
            view.backgroundTintList = null
            view.date_time_picker_date_item_day_of_week.setTextColor(context!!.attrColor(android.R.attr.textColorPrimary))
            view.date_time_picker_date_item_date.setTextColor(context!!.attrColor(android.R.attr.textColorSecondary))
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        hasExpanded = false
    }
}
