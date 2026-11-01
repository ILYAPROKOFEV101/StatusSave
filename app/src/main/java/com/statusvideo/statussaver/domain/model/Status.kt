package com.statusvideo.statussaver.domain.model

import android.net.Uri

data class Status(
    val id: String,
    val uri: Uri,
    val type: MediaType,
    val timestamp: Long
)

enum class MediaType {
    IMAGE, VIDEO
}
