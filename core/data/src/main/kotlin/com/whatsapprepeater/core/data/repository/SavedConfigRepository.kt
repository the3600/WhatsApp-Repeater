package com.whatsapprepeater.core.data.repository

import com.whatsapprepeater.core.data.local.AutomationConfigDao
import com.whatsapprepeater.core.data.local.AutomationConfigEntity
import com.whatsapprepeater.core.data.local.SavedMessageListDao
import com.whatsapprepeater.core.data.local.SavedMessageListEntity
import com.whatsapprepeater.core.model.PersistedAutomationDraft
import com.whatsapprepeater.core.model.SavedMessageList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SavedConfigRepository {
  fun observeSavedMessageLists(): Flow<List<SavedMessageList>>

  suspend fun saveMessageList(list: SavedMessageList): Long

  suspend fun duplicateMessageList(id: Long): Long?

  suspend fun setFavorite(id: Long, favorite: Boolean)

  suspend fun setPinned(id: Long, pinned: Boolean)

  suspend fun recordTemplateUsage(id: Long)

  suspend fun deleteMessageList(id: Long)

  fun observeActiveDraft(): Flow<PersistedAutomationDraft?>

  suspend fun getActiveDraftOnce(): PersistedAutomationDraft?

  suspend fun saveActiveDraft(draft: PersistedAutomationDraft)
}

class RoomSavedConfigRepository(
  private val savedMessageListDao: SavedMessageListDao,
  private val automationConfigDao: AutomationConfigDao,
) : SavedConfigRepository {

  override fun observeSavedMessageLists(): Flow<List<SavedMessageList>> =
    savedMessageListDao.observeAll().map { rows -> rows.map { it.toDomain() } }

  override suspend fun saveMessageList(list: SavedMessageList): Long =
    savedMessageListDao.upsert(SavedMessageListEntity.fromDomain(list))

  override suspend fun duplicateMessageList(id: Long): Long? {
    val existing = savedMessageListDao.getById(id)?.toDomain() ?: return null
    val now = System.currentTimeMillis()
    val copy = existing.copy(
      id = 0L,
      title = "${existing.title} (copia)",
      isPinned = false,
      useCount = 0,
      lastUsedAtMillis = 0L,
      updatedAtMillis = now,
    )
    return savedMessageListDao.upsert(SavedMessageListEntity.fromDomain(copy))
  }

  override suspend fun setFavorite(id: Long, favorite: Boolean) {
    savedMessageListDao.setFavorite(id, favorite)
  }

  override suspend fun setPinned(id: Long, pinned: Boolean) {
    savedMessageListDao.setPinned(id, pinned)
  }

  override suspend fun recordTemplateUsage(id: Long) {
    savedMessageListDao.incrementUsage(id, System.currentTimeMillis())
  }

  override suspend fun deleteMessageList(id: Long) {
    savedMessageListDao.deleteById(id)
  }

  override fun observeActiveDraft(): Flow<PersistedAutomationDraft?> =
    automationConfigDao.observeConfig().map { it?.toDomain() }

  override suspend fun getActiveDraftOnce(): PersistedAutomationDraft? =
    automationConfigDao.getConfigOnce()?.toDomain()

  override suspend fun saveActiveDraft(draft: PersistedAutomationDraft) {
    automationConfigDao.upsert(AutomationConfigEntity.fromDomain(draft))
  }
}

