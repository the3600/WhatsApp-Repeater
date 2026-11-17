package com.whatsapprepeater.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedMessageListDao {
  @Query("SELECT * FROM saved_message_lists ORDER BY isPinned DESC, isFavorite DESC, updatedAtMillis DESC")
  fun observeAll(): Flow<List<SavedMessageListEntity>>

  @Query("SELECT * FROM saved_message_lists WHERE id = :id LIMIT 1")
  suspend fun getById(id: Long): SavedMessageListEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: SavedMessageListEntity): Long

  @Query("UPDATE saved_message_lists SET isFavorite = :favorite WHERE id = :id")
  suspend fun setFavorite(id: Long, favorite: Boolean)

  @Query("UPDATE saved_message_lists SET isPinned = :pinned WHERE id = :id")
  suspend fun setPinned(id: Long, pinned: Boolean)

  @Query("UPDATE saved_message_lists SET useCount = useCount + 1, lastUsedAtMillis = :usedAtMillis WHERE id = :id")
  suspend fun incrementUsage(id: Long, usedAtMillis: Long)

  @Query("DELETE FROM saved_message_lists WHERE id = :id")
  suspend fun deleteById(id: Long)
}

@Dao
interface AutomationConfigDao {
  @Query("SELECT * FROM automation_config WHERE id = 1 LIMIT 1")
  fun observeConfig(): Flow<AutomationConfigEntity?>

  @Query("SELECT * FROM automation_config WHERE id = 1 LIMIT 1")
  suspend fun getConfigOnce(): AutomationConfigEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: AutomationConfigEntity)
}

