package com.statusvideo.statussaver.domain.repository

import com.statusvideo.statussaver.domain.model.Status
import kotlinx.coroutines.flow.Flow

interface StatusRepository {
    fun observeStatuses(): Flow<List<Status>>
    suspend fun saveStatus(status: Status): Result<Unit>
    suspend fun saveAvailableStatuses(): Result<Int>
    fun getSavedStatuses(): Flow<List<Status>>
    suspend fun deleteSaved(id: String): Result<Unit>
}
