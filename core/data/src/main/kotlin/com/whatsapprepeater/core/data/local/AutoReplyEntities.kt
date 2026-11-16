package com.whatsapprepeater.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.whatsapprepeater.core.model.AutoReplyContactFilterMode
import com.whatsapprepeater.core.model.AutoReplyLogEntry
import com.whatsapprepeater.core.model.AutoReplyLogStatus
import com.whatsapprepeater.core.model.AutoReplyRule
import com.whatsapprepeater.core.model.AutoReplySchedule
import com.whatsapprepeater.core.model.AutoReplyTargetApp
import com.whatsapprepeater.core.model.RuleMatchType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val autoReplyJson = Json { ignoreUnknownKeys = true }

@Entity(tableName = "auto_reply_rules")
data class AutoReplyRuleEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val name: String,
  val pattern: String,
  val matchType: String,
  val caseSensitive: Boolean,
  val responseTemplate: String,
  val enabled: Boolean,
  val priority: Int,
  val targetApp: String,
  val contactFilterMode: String,
  val allowGroups: Boolean,
  val selectedContactsJson: String,
  val excludedIdentifiersJson: String,
  val scheduleEnabled: Boolean,
  val scheduleStartMinutes: Int,
  val scheduleEndMinutes: Int,
  val scheduleDaysJson: String,
  val minIntervalSeconds: Int,
  val maxRepliesPerConversation: Int,
  val delaySeconds: Int,
  val updatedAtMillis: Long,
) {
  fun toDomain(): AutoReplyRule = AutoReplyRule(
    id = id,
    name = name,
    pattern = pattern,
    matchType = runCatching { RuleMatchType.valueOf(matchType) }.getOrDefault(RuleMatchType.CONTAINS_PHRASE),
    caseSensitive = caseSensitive,
    responseTemplate = responseTemplate,
    enabled = enabled,
    priority = priority,
    targetApp = runCatching { AutoReplyTargetApp.valueOf(targetApp) }.getOrDefault(AutoReplyTargetApp.BOTH),
    contactFilterMode = runCatching { AutoReplyContactFilterMode.valueOf(contactFilterMode) }
      .getOrDefault(AutoReplyContactFilterMode.ALL),
    allowGroups = allowGroups,
    selectedContactNamesOrPhones = runCatching {
      autoReplyJson.decodeFromString<List<String>>(selectedContactsJson)
    }.getOrDefault(emptyList()),
    excludedIdentifiers = runCatching {
      autoReplyJson.decodeFromString<List<String>>(excludedIdentifiersJson)
    }.getOrDefault(emptyList()),
    schedule = AutoReplySchedule(
      enabled = scheduleEnabled,
      startMinutesOfDay = scheduleStartMinutes,
      endMinutesOfDay = scheduleEndMinutes,
      daysOfWeek = runCatching {
        autoReplyJson.decodeFromString<Set<Int>>(scheduleDaysJson)
      }.getOrDefault(setOf(1, 2, 3, 4, 5, 6, 7)),
    ),
    minIntervalSeconds = minIntervalSeconds,
    maxRepliesPerConversation = maxRepliesPerConversation,
    delaySeconds = delaySeconds,
    updatedAtMillis = updatedAtMillis,
  )

  companion object {
    fun fromDomain(rule: AutoReplyRule): AutoReplyRuleEntity = AutoReplyRuleEntity(
      id = rule.id,
      name = rule.name,
      pattern = rule.pattern,
      matchType = rule.matchType.name,
      caseSensitive = rule.caseSensitive,
      responseTemplate = rule.responseTemplate,
      enabled = rule.enabled,
      priority = rule.priority,
      targetApp = rule.targetApp.name,
      contactFilterMode = rule.contactFilterMode.name,
      allowGroups = rule.allowGroups,
      selectedContactsJson = autoReplyJson.encodeToString(rule.selectedContactNamesOrPhones),
      excludedIdentifiersJson = autoReplyJson.encodeToString(rule.excludedIdentifiers),
      scheduleEnabled = rule.schedule.enabled,
      scheduleStartMinutes = rule.schedule.startMinutesOfDay,
      scheduleEndMinutes = rule.schedule.endMinutesOfDay,
      scheduleDaysJson = autoReplyJson.encodeToString(rule.schedule.daysOfWeek),
      minIntervalSeconds = rule.minIntervalSeconds,
      maxRepliesPerConversation = rule.maxRepliesPerConversation,
      delaySeconds = rule.delaySeconds,
      updatedAtMillis = rule.updatedAtMillis,
    )
  }
}

@Entity(tableName = "auto_reply_history")
data class AutoReplyLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val timestampMillis: Long,
  val packageName: String,
  val chatIdentifier: String,
  val matchedRuleName: String,
  val responseSent: String,
  val incomingPreview: String,
  val status: String,
  val detail: String,
) {
  fun toDomain(): AutoReplyLogEntry = AutoReplyLogEntry(
    id = id,
    timestampMillis = timestampMillis,
    packageName = packageName,
    chatIdentifier = chatIdentifier,
    matchedRuleName = matchedRuleName,
    responseSent = responseSent,
    incomingPreview = incomingPreview,
    status = runCatching { AutoReplyLogStatus.valueOf(status) }.getOrDefault(AutoReplyLogStatus.SENT),
    detail = detail,
  )

  companion object {
    fun fromDomain(entry: AutoReplyLogEntry): AutoReplyLogEntity = AutoReplyLogEntity(
      id = entry.id,
      timestampMillis = entry.timestampMillis,
      packageName = entry.packageName,
      chatIdentifier = entry.chatIdentifier,
      matchedRuleName = entry.matchedRuleName,
      responseSent = entry.responseSent,
      incomingPreview = entry.incomingPreview,
      status = entry.status.name,
      detail = entry.detail,
    )
  }
}

