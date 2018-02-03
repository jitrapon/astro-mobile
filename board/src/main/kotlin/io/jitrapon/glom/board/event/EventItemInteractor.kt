package io.jitrapon.glom.board.event

import android.text.TextUtils
import io.jitrapon.glom.base.component.LocalEventNameAutocompleter
import io.jitrapon.glom.base.component.PlaceProvider
import io.jitrapon.glom.base.model.AsyncErrorResult
import io.jitrapon.glom.base.model.AsyncResult
import io.jitrapon.glom.base.model.AsyncSuccessResult
import io.jitrapon.glom.base.model.User
import io.jitrapon.glom.base.repository.UserRepository
import io.jitrapon.glom.board.BoardItem
import io.jitrapon.glom.board.BoardItemRepository
import io.jitrapon.glom.board.BoardRepository
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.*

/**
 * Controller that handles all interactions for editing, saving, and updating
 * changes to event info
 *
 * Created by Jitrapon
 */
class EventItemInteractor : LocalEventNameAutocompleter.Callbacks {

    /**
     * TODO this will be an API instead of doing it client-side
     * Handles the logic to display useful suggestions to user to autocomplete the event name
     */
    private var autoCompleter: LocalEventNameAutocompleter? = null

    /**
     * Callback for when auto-complete is available
     */
    private var autoCompleteCallback: ((List<Any>) -> Unit)? = null

    /**
     * Initialize board item to work with
     */
    fun setItem(item: BoardItem) {
        BoardItemRepository.setCache(item)
    }

    /**
     * Returns the cached board item
     */
    fun getItem(): BoardItem? {
        return BoardItemRepository.getCache()
    }

    /**
     * Must be called to initialize autocomplete feature
     */
    fun initializeNameAutocompleter(placeProvider: PlaceProvider) {
        autoCompleter = LocalEventNameAutocompleter(this, placeProvider)
    }

    /**
     * Call this method to update name letter-by-letter for autocompletion analysis
     */
    fun onNameChanged(text: String) {
        autoCompleter?.updateText(text)
    }

    /**
     * Saves the current state
     */
    fun saveItem(info: EventInfo) {
        BoardItemRepository.save(info)
    }

    /**
     * Returns list of loaded users, if available from specified IDs
     */
    fun getUsers(userIds: List<String>): List<User?>? {
        return ArrayList<User?>().apply {
            userIds.forEach {
                add(UserRepository.getById(it))
            }
        }
    }

    /**
     * Returns the currently signed in User object
     */
    fun getCurrentUser(): User? {
        return UserRepository.getCurrentUser()
    }

    /**
     * Joins the current user to an event
     *
     * @param statusCode - An int value for the new status (0 for DECLINED, 1 for MAYBE, 2 for GOING)
     */
    fun markEventAttendStatusForCurrentUser(itemId: String?, statusCode: Int, onComplete: ((AsyncResult<MutableList<String>?>) -> Unit)) {
        if (itemId == null) {
            onComplete(AsyncErrorResult(Exception("ItemId cannot be NULL")))
            return
        }
        val userId = UserRepository.getCurrentUser()?.userId
        if (TextUtils.isEmpty(userId)) {
            onComplete(AsyncErrorResult(Exception("Current user id cannot be NULL")))
            return
        }

        BoardRepository.getCache()?.let {
            Flowable.fromCallable {
                it.items.find { it.itemId == itemId && it is EventItem }
            }.flatMap {
                        when (statusCode) {
                            2 -> BoardRepository.joinEvent(userId!!, it.itemId)
                            else -> BoardRepository.declineEvent(userId!!, it.itemId)
                        }
                    }.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        onComplete(AsyncSuccessResult(it.attendees))
                    }, {
                        onComplete(AsyncErrorResult(it))
                    }, {
                        //nothing yet
                    })
        }
    }

    //region auto suggestions callbacks

    fun setAutoCompleteCallback(callback: (List<Any>) -> Unit) {
        autoCompleteCallback = callback
    }

    /**
     * Callback when suggestions are available
     */
    override fun onSuggestionsAvailable(results: List<Any>) {
        autoCompleteCallback?.invoke(results)
    }

    //endregion
}