package com.whatsapprepeater.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExecutionHistoryDao {
  @Query("SELECT * FROM execution_history ORDER BY timestampMillis DESC LIMIT :limit")
  fun observeRecent(limit: Int): Flow<List<ExecutionHistoryEntity>>

  @Query("SELECT * FROM execution_history WHERE id = :id LIMIT 1")
  fun observeById(id: Long): Flow<ExecutionHistoryEntity?>

  @Query("SELECT * FROM execution_history ORDER BY timestampMillis DESC")
  suspend fun getAllOnce(): List<ExecutionHistoryEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entity: ExecutionHistoryEntity)

  @Query("DELETE FROM execution_history WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM execution_history")
  suspend fun clearAll()
}
