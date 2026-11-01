package com.statusvideo.statussaver.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundStatusSaveScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    fun schedulePeriodicAutoSave() {
        val request = PeriodicWorkRequestBuilder<AutoSaveStatusesWorker>(
            repeatInterval = PERIODIC_INTERVAL_MINUTES,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(defaultConstraints())
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun runAutoSaveNow() {
        val request = OneTimeWorkRequestBuilder<AutoSaveStatusesWorker>()
            .setConstraints(defaultConstraints())
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelAutoSave() {
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(ONE_TIME_WORK_NAME)
    }

    private fun defaultConstraints(): Constraints =
        Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

    companion object {
        private const val WORK_TAG = "status_auto_save"
        private const val PERIODIC_WORK_NAME = "background_status_auto_save"
        private const val ONE_TIME_WORK_NAME = "background_status_auto_save_now"
        private const val PERIODIC_INTERVAL_MINUTES = 15L
    }
}
