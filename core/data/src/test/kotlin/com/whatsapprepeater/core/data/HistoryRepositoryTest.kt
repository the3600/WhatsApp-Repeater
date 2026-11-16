package com.whatsapprepeater.core.data

import com.whatsapprepeater.core.data.local.ExecutionHistoryDao
import com.whatsapprepeater.core.data.local.ExecutionHistoryEntity
import com.whatsapprepeater.core.data.repository.RoomHistoryRepository
import com.whatsapprepeater.core.model.ExecutionFinalStatus
import com.whatsapprepeater.core.model.ExecutionRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryRepositoryTest {

  private class FakeDao : ExecutionHistoryDao {
    private val items = MutableStateFlow<List<ExecutionHistoryEntity>>(emptyList())

    override fun observeRecent(limit: Int): Flow<List<ExecutionHistoryEntity>> =
      items.map { it.sortedByDescending { row -> row.timestampMillis }.take(limit) }

    override fun observeById(id: Long): Flow<ExecutionHistoryEntity?> =
      items.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getAllOnce(): List<ExecutionHistoryEntity> = items.value

    override suspend fun insert(entity: ExecutionHistoryEntity) {
      items.value = items.value + entity.copy(id = (items.value.size + 1).toLong())
    }

    override suspend fun deleteById(id: Long) {
      items.value = items.value.filterNot { it.id == id }
    }

    override suspend fun clearAll() {
      items.value = emptyList()
    }
  }

  @Test
  fun `persists and maps execution records cleanly`() = runTest {
    val repo = RoomHistoryRepository(FakeDao())
    val record = ExecutionRecord(
      batchCode = "WR-10203",
      timestampMillis = 1_700_000_000_000L,
      contactName = "Pedro Ruiz",
      contactPhone = "+57 301 000 1111",
      scheduledCount = 5,
      confirmedCount = 5,
      errorCount = 0,
      finalStatus = ExecutionFinalStatus.COMPLETED,
      statusDetail = "Completado con éxito",
    )

    repo.recordExecution(record)
    val recent = repo.observeRecentExecutions().first()

    assertEquals(1, recent.size)
    assertEquals("Pedro Ruiz", recent.first().contactName)
    assertEquals("WR-10203", recent.first().batchCode)
    assertEquals(ExecutionFinalStatus.COMPLETED, recent.first().finalStatus)
  }
}
