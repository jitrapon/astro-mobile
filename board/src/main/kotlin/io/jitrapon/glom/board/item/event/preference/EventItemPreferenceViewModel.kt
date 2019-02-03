package io.jitrapon.glom.board.item.event.preference

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.jitrapon.glom.base.model.*
import io.jitrapon.glom.base.util.get
import io.jitrapon.glom.base.viewmodel.BaseViewModel
import io.jitrapon.glom.base.viewmodel.run
import io.jitrapon.glom.base.viewmodel.runAsync
import io.jitrapon.glom.board.BoardInjector
import io.jitrapon.glom.board.BoardViewModel
import io.jitrapon.glom.board.R
import javax.inject.Inject

/**
 * ViewModel class responsible for showing and interacting with the event item preference
 *
 * Created by Jitrapon
 */
const val CALENDAR_HEADER_INDEX = 0
const val CARD_STYLE_HEADER_INDEX = 1
const val CARD_ACTION_HEADER_INDEX = 2
const val NOTE_HEADER_INDEX = 3

class EventItemPreferenceViewModel : BaseViewModel() {

    @Inject
    lateinit var interactor: EventItemPreferenceInteractor

    private val observablePreferenceUiModel = MutableLiveData<EventItemPreferenceUiModel>()
    private val preferenceUiModel = EventItemPreferenceUiModel()

    init {
        BoardInjector.getComponent().inject(this)

        loadPreference(false)
    }

    fun loadPreference(refresh: Boolean) {
        observablePreferenceUiModel.value = preferenceUiModel.apply {
            status = UiModel.Status.LOADING
        }

        loadData(refresh, interactor::loadPreference, if (!firstLoadCalled) BoardViewModel.FIRST_LOAD_ANIM_DELAY
        else BoardViewModel.SUBSEQUENT_LOAD_ANIM_DELAY) {
            when (it) {
                is AsyncSuccessResult -> updatePreferenceAsync()
                is AsyncErrorResult -> updatePreference(it.error)
            }
        }
        firstLoadCalled = true
    }

    private fun updatePreferenceAsync() {
        runAsync({
            updatePreference(interactor.preference)
        }, {
            arrayOf({
                observablePreferenceUiModel.value = preferenceUiModel
            }, {
                if (preferenceUiModel.expandStates[CALENDAR_HEADER_INDEX] &&
                        preferenceUiModel.lastExpandHeaderIndex == CALENDAR_HEADER_INDEX) {
                    interactor.preference.calendarPreference.error.let {
                        if (it != null) {
                            if (it is NoCalendarPermissionException) {
                                observableViewAction.value = Snackbar(
                                        AndroidString(R.string.error_no_calendar_permissions),
                                        AndroidString(R.string.permission_grant_action),
                                        ::showGrantCalendarPermissionsDialog,
                                        duration = com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE,
                                        level = MessageLevel.WARNING)
                            } else {
                                observableViewAction.value = Snackbar(
                                        AndroidString(R.string.error_calendar_preference_generic),
                                        level = MessageLevel.ERROR)
                            }
                        }
                    }
                } else if (!preferenceUiModel.expandStates[CALENDAR_HEADER_INDEX] && preferenceUiModel.lastExpandHeaderIndex == CALENDAR_HEADER_INDEX) {
                    observableViewAction.value = Snackbar(AndroidString(), shouldDismiss = true)
                }
            }).run(200L)
        }, {
            handleError(it)

            observablePreferenceUiModel.value = preferenceUiModel.apply {
                status = UiModel.Status.ERROR
            }
        })
    }

    fun selectItem(position: Int) {
        preferenceUiModel.preferences.get(position, null)?.let {
            if (it.isHeaderItem()) {
                toggleHeaderExpandState(it)
            }
            else if (it.isCheckedItem()) {
                toggleCheckedItem(it)
            }
        }
    }

    fun getPreferenceUiModel(position: Int) = preferenceUiModel.preferences[position]

    fun getPreferenceSize() = preferenceUiModel.preferences.size

    private fun toggleHeaderExpandState(uiModel: PreferenceItemUiModel) {
        val headerTag = uiModel.headerTag
        if (headerTag != null) {
            preferenceUiModel.lastExpandHeaderIndex = headerTag
            preferenceUiModel.expandStates.put(headerTag, !preferenceUiModel.expandStates[headerTag])
            updatePreferenceAsync()
        }
    }

    private fun toggleCheckedItem(uiModel: PreferenceItemUiModel) {
        val isToggled = uiModel.isToggled
        if (isToggled != null) {
            uiModel.isToggled = !isToggled

            if (uiModel.headerTag == CALENDAR_HEADER_INDEX) {
                interactor.setCalendarSyncStatus(uiModel.tag, !isToggled)
            }
        }
    }

    private fun updatePreference(preference: EventItemPreference) {
        val items: MutableList<PreferenceItemUiModel> = mutableListOf()
        addCalendarItems(preference, items)
        addSampleGroupItems(items, "Card style", CARD_STYLE_HEADER_INDEX)
        addSampleGroupItems(items, "Card action", CARD_ACTION_HEADER_INDEX)
        addSampleGroupItems(items, "Note", NOTE_HEADER_INDEX)

        preferenceUiModel.preferences.apply {
            clear()
            addAll(items)
        }
        preferenceUiModel.status = if (items.isEmpty()) UiModel.Status.EMPTY else UiModel.Status.SUCCESS
    }

    private fun updatePreference(error: Throwable) {
        handleError(error)

        observablePreferenceUiModel.value = preferenceUiModel.apply {
            status = UiModel.Status.ERROR
        }
    }

    private fun addCalendarItems(preference: EventItemPreference, items: MutableList<PreferenceItemUiModel>) {
        val shouldExpand = preferenceUiModel.expandStates[CALENDAR_HEADER_INDEX]
        items.add(PreferenceItemUiModel(
                UiModel.Status.SUCCESS, CALENDAR_HEADER_INDEX,
                AndroidString(R.string.event_item_preference_calendars), false,
                null, shouldExpand, null, null,
                AndroidImage(R.drawable.ic_calendar_multiple),
                AndroidImage(R.drawable.ic_chevron_up))
        )
        if (shouldExpand) {
            if (preference.calendarPreference.calendars.isEmpty()) {
                items.add(PreferenceItemUiModel(
                        UiModel.Status.EMPTY, CALENDAR_HEADER_INDEX,
                        AndroidString(R.string.event_item_preference_empty), true,
                        null, null, null, null,
                        null, null)
                )
            }
            else {
                preference.calendarPreference.calendars.groupBy { it.accountName }.forEach { (accountName, calendars) ->
                    items.add(PreferenceItemUiModel(
                            UiModel.Status.SUCCESS, CALENDAR_HEADER_INDEX,
                            AndroidString(text = accountName), true,
                            null, null, null, null,
                            null, null)
                    )
                    for (calendar in calendars) {
                        if (calendar.isVisible) {
                            items.add(PreferenceItemUiModel(
                                    UiModel.Status.SUCCESS, CALENDAR_HEADER_INDEX,
                                    AndroidString(text = calendar.displayName), false,
                                    AndroidString(text = calendar.ownerName), null,
                                    calendar.isSyncedToBoard, calendar.calId.toString(),
                                    AndroidImage(R.drawable.ic_checkbox_blank_circle, tint = calendar.color),
                                    null))
                        }
                    }
                }
            }
        }
    }

    private fun addSampleGroupItems(items: MutableList<PreferenceItemUiModel>, title: String, headerIndex: Int) {
        val shouldExpand = preferenceUiModel.expandStates[headerIndex]
        items.add(PreferenceItemUiModel(
                UiModel.Status.SUCCESS, headerIndex,
                AndroidString(text = title), false,
                null, shouldExpand, null, null,
                AndroidImage(R.drawable.ic_emoticon_neutral),
                AndroidImage(R.drawable.ic_chevron_up))
        )
        if (shouldExpand) {
            items.add(PreferenceItemUiModel(
                    UiModel.Status.EMPTY, headerIndex,
                    AndroidString(R.string.event_item_preference_empty), true,
                    null, null, null, null,
                    null, null)
            )
        }
    }

    fun getObservablePreference(): LiveData<EventItemPreferenceUiModel> = observablePreferenceUiModel
}
