package com.whatsapprepeater.core.data

import com.whatsapprepeater.core.data.backup.LocalBackupManager
import com.whatsapprepeater.core.model.AppSettings
import com.whatsapprepeater.core.model.AutoReplySettings
import com.whatsapprepeater.core.model.SavedMessageList
import com.whatsapprepeater.core.model.ScheduledTask
import com.whatsapprepeater.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalBackupManagerTest {

  @Test
  fun exportAndImport_roundTripsThemeModeAndDataAndDisablesAutoReplyMaster() {
    val encrypted = LocalBackupManager.exportEncryptedJson(
      passphrase = "test-passphrase-2026",
      settings = AppSettings(themeMode = ThemeMode.LIGHT, defaultMinIntervalSeconds = 12, defaultMaxIntervalSeconds = 28),
      savedLists = listOf(
        SavedMessageList(
          id = 1L,
          title = "Plantilla Bienvenida",
          isMultipleMode = false,
          messages = emptyList(),
          singleMessage = "Hola {nombre}",
          repeatCount = 2,
          updatedAtMillis = 1_700_000_000_000L,
        ),
      ),
      activeDraft = null,
      history = emptyList(),
      autoReplyRules = emptyList(),
      autoReplySettings = AutoReplySettings(masterEnabled = true, awayMessage = "Vuelvo pronto"),
      scheduledTasks = listOf(
        ScheduledTask(
          id = 5L,
          title = "Recordatorio semanal",
          contactId = "c-5",
          contactName = "Ana",
          contactPhone = "+573001112222",
          isMultipleMode = false,
          messages = emptyList(),
          singleMessage = "Recordatorio",
          repeatCount = 1,
          minIntervalSeconds = 15,
          maxIntervalSeconds = 30,
          preferWhatsAppBusiness = false,
          triggerAtMillis = 1_800_000_000_000L,
        ),
      ),
    )

    val payload = LocalBackupManager.importEncryptedJson(encrypted, "test-passphrase-2026")
    assertEquals("LIGHT", payload.settings.themeMode)
    assertEquals(12, payload.settings.defaultMinIntervalSeconds)
    assertEquals(1, payload.savedLists.size)
    assertEquals(1, payload.scheduledTasks.size)
    val restoredAuto = LocalBackupManager.toAutoReplySettings(payload.autoReplySettings!!)
    assertFalse("El autorespondedor nunca debe reactivarse automáticamente tras restaurar", restoredAuto.masterEnabled)
    assertEquals("Vuelvo pronto", restoredAuto.awayMessage)
  }

  @Test
  fun importEncryptedJson_rejectsWrongPassphrase() {
    val encrypted = LocalBackupManager.exportEncryptedJson(
      passphrase = "correct-key-1234",
      settings = AppSettings(),
      savedLists = emptyList(),
      activeDraft = null,
      history = emptyList(),
    )
    assertThrows(IllegalArgumentException::class.java) {
      LocalBackupManager.importEncryptedJson(encrypted, "wrong-key-9999")
    }
  }

  @Test
  fun importEncryptedJson_rejectsTamperedCiphertext() {
    val encrypted = LocalBackupManager.exportEncryptedJson(
      passphrase = "correct-key-1234",
      settings = AppSettings(),
      savedLists = emptyList(),
      activeDraft = null,
      history = emptyList(),
    )
    val tampered = encrypted.replaceFirst("\"cipherTextBase64\": \"", "\"cipherTextBase64\": \"AA")
    assertThrows(IllegalArgumentException::class.java) {
      LocalBackupManager.importEncryptedJson(tampered, "correct-key-1234")
    }
  }
}
