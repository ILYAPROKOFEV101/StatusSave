package com.statusvideo.statussaver.domain.usecase

import com.statusvideo.statussaver.domain.repository.StatusRepository
import javax.inject.Inject

class DeleteSavedUseCase @Inject constructor(
    private val repository: StatusRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.deleteSaved(id)
}
