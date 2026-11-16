package com.whatsapprepeater.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.whatsapprepeater.core.model.ExecutionFinalStatus
import com.whatsapprepeater.core.model.ExecutionRecord
import com.whatsapprepeater.core.model.MessageExecutionTrace
import com.whatsapprepeater.core.model.MessageTraceStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val historyJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class TraceDto(
  val index: Int,
  val messagePreview: String,
  val status: String,
  val timestampMillis: Long,
)

@Entity(tableName = "execution_history")
data class ExecutionHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val timestampMillis: Long,
  val contactName: String,
  val contactPhone: String,
  val scheduledCount: Int,
  val confirmedCount: Int,
  val errorCount: Int,
  val finalStatus: String,
  val statusDetail: String,
  val batchCode: String = "",
  val durationMillis: Long = 0L,
  val averageIntervalSeconds: Double = 0.0,
  val modeLabel: String = "",
  val messageTracesJson: String = "[]",
) {
  fun toDomain(): ExecutionRecord = ExecutionRecord(
    id = id,
    batchCode = batchCode.ifBlank { "WR-${id}" },
    timestampMillis = timestampMillis,
    contactName = contactName,
    contactPhone = contactPhone,
    scheduledCount = scheduledCount,
    confirmedCount = confirmedCount,
    errorCount = errorCount,
    finalStatus = runCatching { ExecutionFinalStatus.valueOf(finalStatus) }
      .getOrDefault(ExecutionFinalStatus.FAILED_ERROR),
    statusDetail = statusDetail,
    durationMillis = durationMillis,
    averageIntervalSeconds = averageIntervalSeconds,
    modeLabel = modeLabel,
    messageTraces = runCatching {
      historyJson.decodeFromString<List<TraceDto>>(messageTracesJson).map {
        MessageExecutionTrace(
          index = it.index,
          messagePreview = it.messagePreview,
          status = runCatching { MessageTraceStatus.valueOf(it.status) }
            .getOrDefault(MessageTraceStatus.CANCELLED),
          timestampMillis = it.timestampMillis,
        )
      }
    }.getOrDefault(emptyList()),
  )

  companion object {
    fun fromDomain(record: ExecutionRecord): ExecutionHistoryEntity = ExecutionHistoryEntity(
      id = record.id,
      timestampMillis = record.timestampMillis,
      contactName = record.contactName,
      contactPhone = record.contactPhone,
      scheduledCount = record.scheduledCount,
      confirmedCount = record.confirmedCount,
      errorCount = record.errorCount,
      finalStatus = record.finalStatus.name,
      statusDetail = record.statusDetail,
      batchCode = record.batchCode,
      durationMillis = record.durationMillis,
      averageIntervalSeconds = record.averageIntervalSeconds,
      modeLabel = record.modeLabel,
      messageTracesJson = historyJson.encodeToString(
        record.messageTraces.map {
          TraceDto(
            index = it.index,
            messagePreview = it.messagePreview,
            status = it.status.name,
            timestampMillis = it.timestampMillis,
          )
        },
      ),
    )
  }
}
