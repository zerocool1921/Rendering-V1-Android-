package com.visualizerstudio.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RenderTaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RenderTaskEntity)

    @Update
    suspend fun update(entity: RenderTaskEntity)

    @Delete
    suspend fun delete(entity: RenderTaskEntity)

    @Query("SELECT * FROM render_task WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RenderTaskEntity?

    @Query(
        "SELECT * FROM render_task WHERE status = 'QUEUED' " +
        "ORDER BY queueOrder ASC LIMIT 1"
    )
    suspend fun getNextQueued(): RenderTaskEntity?

    @Query(
        "UPDATE render_task SET status = :status, errorMessage = :errorMessage WHERE id = :id"
    )
    suspend fun updateStatus(id: String, status: String, errorMessage: String?)

    @Query("SELECT * FROM render_task ORDER BY queueOrder ASC")
    fun observeAll(): Flow<List<RenderTaskEntity>>

    @Query("UPDATE render_task SET queueOrder = :newOrder WHERE id = :id")
    suspend fun updateQueueOrder(id: String, newOrder: Long)
}
