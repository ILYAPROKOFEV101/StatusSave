package com.statusvideo.statussaver.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.statusvideo.statussaver.data.repository.UserPreferencesStore
import com.statusvideo.statussaver.domain.repository.StatusRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class AutoSaveStatusesWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            AutoSaveStatusesWorkerEntryPoint::class.java
        )

        if (!entryPoint.userPreferencesStore().isBackgroundAutoSaveEnabled()) {
            return Result.success()
        }

        return entryPoint.statusRepository()
            .saveAvailableStatuses()
            .fold(
                onSuccess = { savedCount ->
                    Result.success(workDataOf(KEY_SAVED_COUNT to savedCount))
                },
                onFailure = { Result.retry() }
            )
    }

    companion object {
        const val KEY_SAVED_COUNT = "saved_count"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoSaveStatusesWorkerEntryPoint {
    fun statusRepository(): StatusRepository
    fun userPreferencesStore(): UserPreferencesStore
}
