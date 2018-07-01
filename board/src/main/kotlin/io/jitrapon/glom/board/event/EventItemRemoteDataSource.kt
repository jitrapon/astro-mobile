package io.jitrapon.glom.board.event

import io.jitrapon.glom.base.domain.circle.CircleInteractor
import io.jitrapon.glom.base.domain.user.UserInteractor
import io.jitrapon.glom.base.repository.RemoteDataSource
import io.reactivex.Completable
import io.reactivex.Flowable
import java.util.concurrent.TimeUnit

enum class EventApiConst(val code: Int) {
    DECLINED(0), MAYBE(1), GOING(2)
}

class EventItemRemoteDataSource(private val userInteractor: UserInteractor, private val circleInteractor: CircleInteractor) :
        RemoteDataSource(), EventItemDataSource {

    private val api = retrofit.create(EventApi::class.java)

    override fun initWith(item: EventItem) {
        throw NotImplementedError()
    }

    override fun getItem(): Flowable<EventItem> {
        throw NotImplementedError()
    }

    override fun saveItem(info: EventInfo): Completable {
        throw NotImplementedError()
    }

    override fun joinEvent(item: EventItem): Completable {
        return api.joinEvent(circleInteractor.getActiveCircleId(), item.itemId, EditAttendeeRequest(EventApiConst.GOING.code))
                .flatMapCompletable {
                    Completable.fromCallable {
                        if (userInteractor.getCurrentUserId() != it.userId || it.status != EventApiConst.GOING.code) {
                            throw Exception("Response received is not what is expected. userId=${it.userId}, status=${it.status}")
                        }
                    }
                }
    }

    override fun leaveEvent(item: EventItem): Completable {
        return api.leaveEvent(circleInteractor.getActiveCircleId(), item.itemId, EditAttendeeRequest(EventApiConst.DECLINED.code))
                .flatMapCompletable {
                    Completable.fromCallable {
                        if (userInteractor.getCurrentUserId() != it.userId || it.status != EventApiConst.DECLINED.code) {
                            throw Exception("Response received is not what is expected. userId=${it.userId}, status=${it.status}")
                        }
                    }
                }
    }

    override fun getDatePolls(item: EventItem): Flowable<List<EventDatePoll>> {
        return Flowable.just(getDatePolls()).delay(1, TimeUnit.SECONDS)
    }

    private fun getDatePolls(): List<EventDatePoll> {
        return ArrayList<EventDatePoll>().apply {
            add(EventDatePoll("3", mutableListOf("yoshi3003", "fatcat18"), 1531643400000L, 1531650600000L))
            add(EventDatePoll("8", mutableListOf("yoshi3003"), 1532494800000L, null))
            add(EventDatePoll("1", mutableListOf("yoshi3003", "fluffy", "fatcat18"), 1527825600000L, null))
            add(EventDatePoll("6", mutableListOf("yoshi3003", "fluffy", "fatcat18", "panda"), 1532077200000L, null))
            add(EventDatePoll("4", mutableListOf("yoshi3003", "fatcat18"), 1531890000000L, null))
            add(EventDatePoll("5", mutableListOf("yoshi3003", "fluffy", "fatcat18", "panda"), 1531890000000L, null))
            add(EventDatePoll("2", mutableListOf("fatcat18"), 1531211400000L, null))
            add(EventDatePoll("7", mutableListOf("fluffy", "panda"), 1532228400000L, 1532322000000L))
        }
    }
}
