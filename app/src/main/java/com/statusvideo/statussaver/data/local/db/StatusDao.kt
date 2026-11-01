package com.statusvideo.statussaver.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StatusDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatus(entity: StatusEntity)

    @Query("SELECT id FROM saved_statuses WHERE id IN (:ids)")
    suspend fun getSavedIds(ids: List<String>): List<String>

    @Query("SELECT * FROM saved_statuses ORDER BY savedAt DESC")
    fun getAllSaved(): Flow<List<StatusEntity>>

    @Query("DELETE FROM saved_statuses WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM saved_statuses WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): StatusEntity?
}
