package com.statusvideo.statussaver.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_statuses")
data class StatusEntity(
    @PrimaryKey val id: String,
    val filePath: String,
    val savedUri: String,
    val type: String,
    val savedAt: Long
)
