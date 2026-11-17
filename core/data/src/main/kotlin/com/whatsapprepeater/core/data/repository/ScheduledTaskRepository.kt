package com.whatsapprepeater.core.data.repository

import com.whatsapprepeater.core.data.local.ScheduledTaskDao
import com.whatsapprepeater.core.data.local.ScheduledTaskEntity
import com.whatsapprepeater.core.model.ScheduledTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ScheduledTaskRepository {
  fun observeTasks(): Flow<List<ScheduledTask>>

  suspend fun getAllOnce(): List<ScheduledTask>

  suspend fun getById(id: Long): ScheduledTask?

  suspend fun saveTask(task: ScheduledTask): Long

  suspend fun setEnabled(id: Long, enabled: Boolean)

  suspend fun deleteTask(id: Long)

  suspend fun clearAll()
}

class RoomScheduledTaskRepository(
  private val dao: ScheduledTaskDao,
) : ScheduledTaskRepository {

  override fun observeTasks(): Flow<List<ScheduledTask>> =
    dao.observeAll().map { rows -> rows.map { it.toDomain() } }

  override suspend fun getAllOnce(): List<ScheduledTask> =
    dao.getAllOnce().map { it.toDomain() }

  override suspend fun getById(id: Long): ScheduledTask? =
    dao.getById(id)?.toDomain()

  override suspend fun saveTask(task: ScheduledTask): Long =
    dao.upsert(ScheduledTaskEntity.fromDomain(task))

  override suspend fun setEnabled(id: Long, enabled: Boolean) {
    dao.setEnabled(id, enabled, System.currentTimeMillis())
  }

  override suspend fun deleteTask(id: Long) {
    dao.deleteById(id)
  }

  override suspend fun clearAll() {
    dao.clearAll()
  }
}
