package com.whatsapprepeater.core.data.repository

import com.whatsapprepeater.core.data.local.AutoReplyLogDao
import com.whatsapprepeater.core.data.local.AutoReplyLogEntity
import com.whatsapprepeater.core.data.local.AutoReplyRuleDao
import com.whatsapprepeater.core.data.local.AutoReplyRuleEntity
import com.whatsapprepeater.core.model.AutoReplyLogEntry
import com.whatsapprepeater.core.model.AutoReplyRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface AutoReplyRepository {
  fun observeRules(): Flow<List<AutoReplyRule>>

  suspend fun getRulesOnce(): List<AutoReplyRule>

  suspend fun getRuleById(id: Long): AutoReplyRule?

  suspend fun saveRule(rule: AutoReplyRule): Long

  suspend fun setRuleEnabled(id: Long, enabled: Boolean)

  suspend fun deleteRule(id: Long)

  fun observeHistory(limit: Int = 100): Flow<List<AutoReplyLogEntry>>

  suspend fun recordLog(entry: AutoReplyLogEntry)

  suspend fun clearHistory()
}

class RoomAutoReplyRepository(
  private val ruleDao: AutoReplyRuleDao,
  private val logDao: AutoReplyLogDao,
) : AutoReplyRepository {

  override fun observeRules(): Flow<List<AutoReplyRule>> =
    ruleDao.observeAll().map { list -> list.map { it.toDomain() } }

  override suspend fun getRulesOnce(): List<AutoReplyRule> =
    ruleDao.getAllOnce().map { it.toDomain() }

  override suspend fun getRuleById(id: Long): AutoReplyRule? =
    ruleDao.getById(id)?.toDomain()

  override suspend fun saveRule(rule: AutoReplyRule): Long =
    ruleDao.upsert(AutoReplyRuleEntity.fromDomain(rule))

  override suspend fun setRuleEnabled(id: Long, enabled: Boolean) {
    ruleDao.setEnabled(id, enabled, System.currentTimeMillis())
  }

  override suspend fun deleteRule(id: Long) {
    ruleDao.deleteById(id)
  }

  override fun observeHistory(limit: Int): Flow<List<AutoReplyLogEntry>> =
    logDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

  override suspend fun recordLog(entry: AutoReplyLogEntry) {
    logDao.insert(AutoReplyLogEntity.fromDomain(entry))
  }

  override suspend fun clearHistory() {
    logDao.clearAll()
  }
}

