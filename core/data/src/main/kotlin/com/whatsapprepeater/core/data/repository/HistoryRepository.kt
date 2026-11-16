package com.whatsapprepeater.core.data.repository

import com.whatsapprepeater.core.data.local.ExecutionHistoryDao
import com.whatsapprepeater.core.data.local.ExecutionHistoryEntity
import com.whatsapprepeater.core.model.ExecutionRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface HistoryRepository {
  fun observeRecentExecutions(limit: Int = 50): Flow<List<ExecutionRecord>>

  fun observeExecutionById(id: Long): Flow<ExecutionRecord?>

  suspend fun getAllExecutionsOnce(): List<ExecutionRecord>

  suspend fun recordExecution(record: ExecutionRecord)

  suspend fun deleteExecution(id: Long)

  suspend fun clearHistory()
}

class RoomHistoryRepository(
  private val dao: ExecutionHistoryDao,
) : HistoryRepository {
  override fun observeRecentExecutions(limit: Int): Flow<List<ExecutionRecord>> =
    dao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

  override fun observeExecutionById(id: Long): Flow<ExecutionRecord?> =
    dao.observeById(id).map { it?.toDomain() }

  override suspend fun getAllExecutionsOnce(): List<ExecutionRecord> =
    dao.getAllOnce().map { it.toDomain() }

  override suspend fun recordExecution(record: ExecutionRecord) {
    dao.insert(ExecutionHistoryEntity.fromDomain(record))
  }

  override suspend fun deleteExecution(id: Long) {
    dao.deleteById(id)
  }

  override suspend fun clearHistory() {
    dao.clearAll()
  }
}
