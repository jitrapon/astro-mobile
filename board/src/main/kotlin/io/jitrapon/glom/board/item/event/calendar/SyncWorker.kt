package io.jitrapon.glom.board.item.event.calendar

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.Constraints
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.jitrapon.glom.base.util.AppLogger

const val ACTION_CALENDAR_MODIFIED = "action.CALENDAR_MODIFIED"
const val SYNC_WORKER_TAG = "sync_worker"

/**
 * A Worker that listens to changes in the Calendar Provider and broadcasts
 * events to observers to notify the changes
 *
 * Created by Jitrapon
 */
class SyncWorker(context: Context, params: WorkerParameters): Worker(context, params) {

    companion object {

        @JvmStatic
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
                .build()
            val work = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .addTag(SYNC_WORKER_TAG)
                .build()
            WorkManager.getInstance(context).enqueue(work)
        }

        @JvmStatic
        fun unschedule(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(SYNC_WORKER_TAG)
        }
    }

    override fun doWork(): Result {
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(Intent(ACTION_CALENDAR_MODIFIED))

        schedule(applicationContext)

        return Result.success()
    }

    override fun onStopped() {
        AppLogger.d("SyncWorker is cancelled")
    }
}
