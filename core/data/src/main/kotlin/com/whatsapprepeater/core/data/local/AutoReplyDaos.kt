package com.whatsapprepeater.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AutoReplyRuleDao {
  @Query("SELECT * FROM auto_reply_rules ORDER BY priority DESC, id ASC")
  fun observeAll(): Flow<List<AutoReplyRuleEntity>>

  @Query("SELECT * FROM auto_reply_rules ORDER BY priority DESC, id ASC")
  suspend fun getAllOnce(): List<AutoReplyRuleEntity>

  @Query("SELECT * FROM auto_reply_rules WHERE id = :id LIMIT 1")
  suspend fun getById(id: Long): AutoReplyRuleEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: AutoReplyRuleEntity): Long

  @Query("UPDATE auto_reply_rules SET enabled = :enabled, updatedAtMillis = :updatedAt WHERE id = :id")
  suspend fun setEnabled(id: Long, enabled: Boolean, updatedAt: Long)

  @Query("DELETE FROM auto_reply_rules WHERE id = :id")
  suspend fun deleteById(id: Long)
}

@Dao
interface AutoReplyLogDao {
  @Query("SELECT * FROM auto_reply_history ORDER BY timestampMillis DESC LIMIT :limit")
  fun observeRecent(limit: Int): Flow<List<AutoReplyLogEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entity: AutoReplyLogEntity): Long

  @Query("DELETE FROM auto_reply_history")
  suspend fun clearAll()
}

