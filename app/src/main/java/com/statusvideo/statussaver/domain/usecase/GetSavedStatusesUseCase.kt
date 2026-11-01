package com.statusvideo.statussaver.domain.usecase

import com.statusvideo.statussaver.domain.model.Status
import com.statusvideo.statussaver.domain.repository.StatusRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSavedStatusesUseCase @Inject constructor(
    private val repository: StatusRepository
) {
    operator fun invoke(): Flow<List<Status>> = repository.getSavedStatuses()
}
