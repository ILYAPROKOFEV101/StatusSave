package com.statusvideo.statussaver.domain.usecase

import com.statusvideo.statussaver.domain.model.Status
import com.statusvideo.statussaver.domain.repository.StatusRepository
import javax.inject.Inject

class SaveStatusUseCase @Inject constructor(
    private val repository: StatusRepository
) {
    suspend operator fun invoke(status: Status): Result<Unit> = repository.saveStatus(status)
}
