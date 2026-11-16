package com.whatsapprepeater.core.data.backup

import com.whatsapprepeater.core.model.AppSettings
import com.whatsapprepeater.core.model.ExecutionFinalStatus
import com.whatsapprepeater.core.model.ExecutionRecord
import com.whatsapprepeater.core.model.MessageExecutionTrace
import com.whatsapprepeater.core.model.MessageTraceStatus
import com.whatsapprepeater.core.model.PersistedAutomationDraft
import com.whatsapprepeater.core.model.SavedMessageList
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class BackupPayload(
  val schemaVersion: Int = 1,
  val exportedAtMillis: Long,
  val settings: BackupSettingsDto,
  val savedLists: List<BackupSavedListDto>,
  val activeDraft: BackupDraftDto? = null,
  val history: List<BackupHistoryDto>,
)

@Serializable
data class BackupSettingsDto(
  val defaultMinIntervalSeconds: Int,
  val defaultMaxIntervalSeconds: Int,
  val maxRepeatCount: Int,
  val preferWhatsAppBusiness: Boolean,
  val hapticFeedbackEnabled: Boolean,
  val keepScreenOnDuringAutomation: Boolean,
  val githubRepoOwner: String,
  val githubRepoName: String,
)

@Serializable
data class BackupSavedListDto(
  val title: String,
  val isMultipleMode: Boolean,
  val messages: List<String>,
  val singleMessage: String,
  val repeatCount: Int,
  val updatedAtMillis: Long,
)

@Serializable
data class BackupDraftDto(
  val contactId: String?,
  val contactName: String?,
  val contactPhone: String?,
  val isMultipleMode: Boolean,
  val messages: List<String>,
  val singleMessage: String,
  val repeatCount: Int,
  val minIntervalSeconds: Int,
  val maxIntervalSeconds: Int,
  val preferWhatsAppBusiness: Boolean,
  val updatedAtMillis: Long,
)

@Serializable
data class BackupHistoryDto(
  val batchCode: String,
  val timestampMillis: Long,
  val contactName: String,
  val contactPhone: String,
  val scheduledCount: Int,
  val confirmedCount: Int,
  val errorCount: Int,
  val finalStatus: String,
  val statusDetail: String,
  val durationMillis: Long,
  val averageIntervalSeconds: Double,
  val modeLabel: String,
  val traces: List<BackupTraceDto> = emptyList(),
)

@Serializable
data class BackupTraceDto(
  val index: Int,
  val messagePreview: String,
  val status: String,
  val timestampMillis: Long,
)

@Serializable
data class EncryptedBackupEnvelope(
  val format: String = "whatsapp-repeater-backup-v1",
  val algorithm: String = "AES-256-GCM",
  val saltBase64: String,
  val ivBase64: String,
  val cipherTextBase64: String,
  val payloadSha256: String,
)

/**
 * Exporta e importa copias de seguridad locales en formato JSON cifrado con AES-256-GCM
 * y verificación de integridad SHA-256 (sin subir datos a servidores externos).
 */
object LocalBackupManager {

  private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
  }

  private const val ITERATIONS = 65_536
  private const val KEY_LENGTH_BITS = 256
  private const val GCM_TAG_BITS = 128

  fun exportEncryptedJson(
    passphrase: String,
    settings: AppSettings,
    savedLists: List<SavedMessageList>,
    activeDraft: PersistedAutomationDraft?,
    history: List<ExecutionRecord>,
  ): String {
    require(passphrase.length >= 4) { "La clave de respaldo debe tener al menos 4 caracteres." }

    val payload = BackupPayload(
      exportedAtMillis = System.currentTimeMillis(),
      settings = BackupSettingsDto(
        defaultMinIntervalSeconds = settings.defaultMinIntervalSeconds,
        defaultMaxIntervalSeconds = settings.defaultMaxIntervalSeconds,
        maxRepeatCount = settings.maxRepeatCount,
        preferWhatsAppBusiness = settings.preferWhatsAppBusiness,
        hapticFeedbackEnabled = settings.hapticFeedbackEnabled,
        keepScreenOnDuringAutomation = settings.keepScreenOnDuringAutomation,
        githubRepoOwner = settings.githubRepoOwner,
        githubRepoName = settings.githubRepoName,
      ),
      savedLists = savedLists.map {
        BackupSavedListDto(
          title = it.title,
          isMultipleMode = it.isMultipleMode,
          messages = it.messages,
          singleMessage = it.singleMessage,
          repeatCount = it.repeatCount,
          updatedAtMillis = it.updatedAtMillis,
        )
      },
      activeDraft = activeDraft?.let {
        BackupDraftDto(
          contactId = it.contactId,
          contactName = it.contactName,
          contactPhone = it.contactPhone,
          isMultipleMode = it.isMultipleMode,
          messages = it.messages,
          singleMessage = it.singleMessage,
          repeatCount = it.repeatCount,
          minIntervalSeconds = it.minIntervalSeconds,
          maxIntervalSeconds = it.maxIntervalSeconds,
          preferWhatsAppBusiness = it.preferWhatsAppBusiness,
          updatedAtMillis = it.updatedAtMillis,
        )
      },
      history = history.map { rec ->
        BackupHistoryDto(
          batchCode = rec.batchCode,
          timestampMillis = rec.timestampMillis,
          contactName = rec.contactName,
          contactPhone = rec.contactPhone,
          scheduledCount = rec.scheduledCount,
          confirmedCount = rec.confirmedCount,
          errorCount = rec.errorCount,
          finalStatus = rec.finalStatus.name,
          statusDetail = rec.statusDetail,
          durationMillis = rec.durationMillis,
          averageIntervalSeconds = rec.averageIntervalSeconds,
          modeLabel = rec.modeLabel,
          traces = rec.messageTraces.map { t ->
            BackupTraceDto(
              index = t.index,
              messagePreview = t.messagePreview,
              status = t.status.name,
              timestampMillis = t.timestampMillis,
            )
          },
        )
      },
    )

    val plainBytes = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
    val payloadSha = sha256Hex(plainBytes)

    val random = SecureRandom()
    val salt = ByteArray(16).also { random.nextBytes(it) }
    val iv = ByteArray(12).also { random.nextBytes(it) }
    val secretKey = deriveAesKey(passphrase, salt)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
    val cipherBytes = cipher.doFinal(plainBytes)

    val envelope = EncryptedBackupEnvelope(
      saltBase64 = Base64.getEncoder().encodeToString(salt),
      ivBase64 = Base64.getEncoder().encodeToString(iv),
      cipherTextBase64 = Base64.getEncoder().encodeToString(cipherBytes),
      payloadSha256 = payloadSha,
    )
    return json.encodeToString(envelope)
  }

  fun importEncryptedJson(rawEnvelopeJson: String, passphrase: String): BackupPayload {
    require(passphrase.length >= 4) { "Ingresa la clave usada para cifrar el respaldo." }

    val envelope = try {
      json.decodeFromString<EncryptedBackupEnvelope>(rawEnvelopeJson)
    } catch (_: Exception) {
      throw IllegalArgumentException("El archivo seleccionado no tiene un formato de respaldo válido.")
    }
    require(envelope.format == "whatsapp-repeater-backup-v1") {
      "El archivo seleccionado no corresponde a un respaldo de WhatsApp Repeater."
    }

    val plainBytes = try {
      val salt = Base64.getDecoder().decode(envelope.saltBase64)
      val iv = Base64.getDecoder().decode(envelope.ivBase64)
      val cipherBytes = Base64.getDecoder().decode(envelope.cipherTextBase64)
      val secretKey = deriveAesKey(passphrase, salt)

      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
      cipher.doFinal(cipherBytes)
    } catch (_: Exception) {
      throw IllegalArgumentException("Clave incorrecta o archivo de respaldo dañado/alterado.")
    }

    val actualSha = sha256Hex(plainBytes)
    require(actualSha.equals(envelope.payloadSha256, ignoreCase = true)) {
      "El respaldo falló la verificación de integridad SHA-256."
    }

    return try {
      json.decodeFromString<BackupPayload>(plainBytes.toString(Charsets.UTF_8))
    } catch (_: Exception) {
      throw IllegalArgumentException("El contenido del respaldo está dañado y no pudo leerse.")
    }
  }

  fun toHistoryRecords(items: List<BackupHistoryDto>): List<ExecutionRecord> =
    items.map { dto ->
      ExecutionRecord(
        batchCode = dto.batchCode,
        timestampMillis = dto.timestampMillis,
        contactName = dto.contactName,
        contactPhone = dto.contactPhone,
        scheduledCount = dto.scheduledCount,
        confirmedCount = dto.confirmedCount,
        errorCount = dto.errorCount,
        finalStatus = runCatching { ExecutionFinalStatus.valueOf(dto.finalStatus) }
          .getOrDefault(ExecutionFinalStatus.FAILED_ERROR),
        statusDetail = dto.statusDetail,
        durationMillis = dto.durationMillis,
        averageIntervalSeconds = dto.averageIntervalSeconds,
        modeLabel = dto.modeLabel,
        messageTraces = dto.traces.map { t ->
          MessageExecutionTrace(
            index = t.index,
            messagePreview = t.messagePreview,
            status = runCatching { MessageTraceStatus.valueOf(t.status) }
              .getOrDefault(MessageTraceStatus.CANCELLED),
            timestampMillis = t.timestampMillis,
          )
        },
      )
    }

  private fun deriveAesKey(passphrase: String, salt: ByteArray): SecretKeySpec {
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
    val keyBytes = factory.generateSecret(spec).encoded
    return SecretKeySpec(keyBytes, "AES")
  }

  private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

