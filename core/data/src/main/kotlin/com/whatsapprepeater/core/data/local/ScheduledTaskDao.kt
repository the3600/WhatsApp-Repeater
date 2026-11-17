package com.whatsapprepeater.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledTaskDao {
  @Query("SELECT * FROM scheduled_tasks ORDER BY enabled DESC, triggerAtMillis ASC")
  fun observeAll(): Flow<List<ScheduledTaskEntity>>

  @Query("SELECT * FROM scheduled_tasks ORDER BY triggerAtMillis ASC")
  suspend fun getAllOnce(): List<ScheduledTaskEntity>

  @Query("SELECT * FROM scheduled_tasks WHERE id = :id LIMIT 1")
  suspend fun getById(id: Long): ScheduledTaskEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: ScheduledTaskEntity): Long

  @Query("UPDATE scheduled_tasks SET enabled = :enabled, updatedAtMillis = :updatedAt WHERE id = :id")
  suspend fun setEnabled(id: Long, enabled: Boolean, updatedAt: Long)

  @Query("DELETE FROM scheduled_tasks WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM scheduled_tasks")
  suspend fun clearAll()
}
