package com.whatsapprepeater.core.data

import androidx.sqlite.db.SupportSQLiteDatabase
import com.whatsapprepeater.core.data.backup.LocalBackupManager
import com.whatsapprepeater.core.data.local.AppDatabase
import com.whatsapprepeater.core.data.local.DatabaseBackupManager
import com.whatsapprepeater.core.model.AppSettings
import com.whatsapprepeater.core.model.AutoReplyContactFilterMode
import com.whatsapprepeater.core.model.AutoReplyRule
import com.whatsapprepeater.core.model.AutoReplySchedule
import com.whatsapprepeater.core.model.AutoReplySettings
import com.whatsapprepeater.core.model.AutoReplyTargetApp
import com.whatsapprepeater.core.model.ExecutionFinalStatus
import com.whatsapprepeater.core.model.ExecutionRecord
import com.whatsapprepeater.core.model.RuleMatchType
import com.whatsapprepeater.core.model.SavedMessageList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.reflect.Proxy
import java.sql.DriverManager

class DatabaseMigrationTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun `migrates v1 sqlite database through v2 to v3 preserving execution history`() {
    val dbFile = tempFolder.newFile("whatsapp_repeater_v1.db")
    dbFile.delete()
    val jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"

    DriverManager.getConnection(jdbcUrl).use { conn ->
      conn.createStatement().use { stmt ->
        stmt.execute(
          """
          CREATE TABLE IF NOT EXISTS `execution_history` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `timestampMillis` INTEGER NOT NULL,
            `contactName` TEXT NOT NULL,
            `contactPhone` TEXT NOT NULL,
            `scheduledCount` INTEGER NOT NULL,
            `confirmedCount` INTEGER NOT NULL,
            `errorCount` INTEGER NOT NULL,
            `finalStatus` TEXT NOT NULL,
            `statusDetail` TEXT NOT NULL
          )
          """.trimIndent(),
        )
        stmt.execute(
          """
          INSERT INTO `execution_history`
          (`timestampMillis`, `contactName`, `contactPhone`, `scheduledCount`, `confirmedCount`, `errorCount`, `finalStatus`, `statusDetail`)
          VALUES (1700000000000, 'Laura Pérez', '+57 300 111 2222', 4, 4, 0, 'COMPLETED', 'Completado con éxito')
          """.trimIndent(),
        )
        stmt.execute("PRAGMA user_version = 1")
      }
    }

    assertEquals(1, DatabaseBackupManager.readSqliteUserVersion(dbFile))

    DriverManager.getConnection(jdbcUrl).use { conn ->
      conn.createStatement().use { stmt ->
        val proxyDb = Proxy.newProxyInstance(
          SupportSQLiteDatabase::class.java.classLoader,
          arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
          if (method.name == "execSQL" && args?.size == 1) {
            stmt.execute(args[0] as String)
            null
          } else {
            throw UnsupportedOperationException("Método no esperado en migración: ${method.name}")
          }
        } as SupportSQLiteDatabase

        AppDatabase.MIGRATION_1_2.migrate(proxyDb)
        AppDatabase.MIGRATION_2_3.migrate(proxyDb)
        AppDatabase.MIGRATION_3_4.migrate(proxyDb)
        stmt.execute("PRAGMA user_version = ${AppDatabase.CURRENT_VERSION}")

        stmt.executeQuery("SELECT contactName, confirmedCount, batchCode, messageTracesJson FROM execution_history").use { rs ->
          assertTrue(rs.next())
          assertEquals("Laura Pérez", rs.getString(1))
          assertEquals(4, rs.getInt(2))
          assertEquals("", rs.getString(3))
          assertEquals("[]", rs.getString(4))
        }

        stmt.execute(
          """
          INSERT INTO `auto_reply_rules`
          (`name`, `pattern`, `matchType`, `caseSensitive`, `responseTemplate`, `enabled`, `priority`, `targetApp`, `contactFilterMode`, `allowGroups`, `selectedContactsJson`, `excludedIdentifiersJson`, `scheduleEnabled`, `scheduleStartMinutes`, `scheduleEndMinutes`, `scheduleDaysJson`, `minIntervalSeconds`, `maxRepliesPerConversation`, `delaySeconds`, `updatedAtMillis`)
          VALUES ('Bienvenida VIP', 'precio', 'CONTAINS_WORD', 0, 'Hola {nombre}', 1, 10, 'BOTH', 'ALL', 0, '[]', '[]', 0, 540, 1080, '[1,2,3,4,5,6,7]', 30, 5, 0, 1700000002000)
          """.trimIndent(),
        )
        stmt.executeQuery("SELECT name, pattern FROM auto_reply_rules").use { rs ->
          assertTrue(rs.next())
          assertEquals("Bienvenida VIP", rs.getString(1))
          assertEquals("precio", rs.getString(2))
        }

        stmt.execute(
          """
          INSERT INTO `saved_message_lists`
          (`title`, `isMultipleMode`, `messagesJson`, `singleMessage`, `repeatCount`, `updatedAtMillis`)
          VALUES ('Seguimiento clientes', 1, '["Hola","¿Pudiste revisar?"]', '', 1, 1700000001000)
          """.trimIndent(),
        )
        stmt.executeQuery("SELECT title FROM saved_message_lists").use { rs ->
          assertTrue(rs.next())
          assertEquals("Seguimiento clientes", rs.getString(1))
        }
      }
    }

    assertEquals(AppDatabase.CURRENT_VERSION, DatabaseBackupManager.readSqliteUserVersion(dbFile))
  }

  @Test
  fun `encrypts and restores local json backup verifying sha256 integrity`() {
    val encrypted = LocalBackupManager.exportEncryptedJson(
      passphrase = "mi-clave-segura",
      settings = AppSettings(defaultMinIntervalSeconds = 12, defaultMaxIntervalSeconds = 30),
      savedLists = listOf(
        SavedMessageList(
          id = 1L,
          title = "Guardia",
          isMultipleMode = false,
          messages = emptyList(),
          singleMessage = "Confirmado",
          repeatCount = 3,
          updatedAtMillis = 1700000000000L,
        ),
      ),
      activeDraft = null,
      history = listOf(
        ExecutionRecord(
          id = 1L,
          batchCode = "WR-10001",
          timestampMillis = 1700000000000L,
          contactName = "Ana",
          contactPhone = "+573001112222",
          scheduledCount = 3,
          confirmedCount = 3,
          errorCount = 0,
          finalStatus = ExecutionFinalStatus.COMPLETED,
          statusDetail = "OK",
        ),
      ),
    )

    val restored = LocalBackupManager.importEncryptedJson(encrypted, "mi-clave-segura")
    assertEquals(12, restored.settings.defaultMinIntervalSeconds)
    assertEquals(1, restored.savedLists.size)
    assertEquals("Guardia", restored.savedLists.first().title)
    assertEquals(1, restored.history.size)
    assertEquals("WR-10001", restored.history.first().batchCode)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects wrong passphrase when restoring encrypted backup`() {
    val encrypted = LocalBackupManager.exportEncryptedJson(
      passphrase = "clave-correcta",
      settings = AppSettings(),
      savedLists = emptyList(),
      activeDraft = null,
      history = emptyList(),
    )
    LocalBackupManager.importEncryptedJson(encrypted, "clave-equivocada")
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects corrupted or tampered backup payload`() {
    val encrypted = LocalBackupManager.exportEncryptedJson(
      passphrase = "clave-correcta",
      settings = AppSettings(),
      savedLists = emptyList(),
      activeDraft = null,
      history = emptyList(),
    )
    val tampered = encrypted.replaceFirst("\"payloadSha256\": \"", "\"payloadSha256\": \"0")
    LocalBackupManager.importEncryptedJson(tampered, "clave-correcta")
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects non json or invalid base64 backup files gracefully`() {
    LocalBackupManager.importEncryptedJson(
      """{"format":"whatsapp-repeater-backup-v1","algorithm":"AES-256-GCM","saltBase64":"!!!","ivBase64":"!!!","cipherTextBase64":"!!!","payloadSha256":"abc"}""",
      "clave-correcta",
    )
  }

  @Test
  fun `roundtrips encrypted backup including auto reply rules and safe disabled master toggle`() {
    val rule = AutoReplyRule(
      id = 7L,
      name = "Saludo automatico",
      pattern = "hola|buenas",
      matchType = RuleMatchType.REGEX,
      responseTemplate = "Hola {nombre}, recibi tu mensaje.",
      targetApp = AutoReplyTargetApp.WHATSAPP_ONLY,
      contactFilterMode = AutoReplyContactFilterMode.SAVED_CONTACTS_ONLY,
      schedule = AutoReplySchedule(enabled = true, startMinutesOfDay = 540, endMinutesOfDay = 1080),
      delaySeconds = 3,
      minIntervalSeconds = 60,
      maxRepliesPerConversation = 2,
      priority = 10,
      enabled = true,
    )
    val autoSettings = AutoReplySettings(
      masterEnabled = true,
      welcomeEnabled = true,
      welcomeMessage = "Bienvenido {nombre}",
      awayEnabled = true,
      awayMessage = "Volvemos pronto",
      awaySchedule = AutoReplySchedule(enabled = true, startMinutesOfDay = 1320, endMinutesOfDay = 420),
      saveHistoryEnabled = true,
    )

    val encrypted = LocalBackupManager.exportEncryptedJson(
      passphrase = "mi-clave-segura",
      settings = AppSettings(),
      savedLists = emptyList(),
      activeDraft = null,
      history = emptyList(),
      autoReplyRules = listOf(rule),
      autoReplySettings = autoSettings,
    )
    val restored = LocalBackupManager.importEncryptedJson(encrypted, "mi-clave-segura")
    val restoredRules = LocalBackupManager.toAutoReplyRules(restored.autoReplyRules)
    val restoredSettings = restored.autoReplySettings?.let { LocalBackupManager.toAutoReplySettings(it) }

    assertEquals(1, restoredRules.size)
    assertEquals("Saludo automatico", restoredRules.first().name)
    assertEquals(RuleMatchType.REGEX, restoredRules.first().matchType)
    assertEquals(AutoReplyContactFilterMode.SAVED_CONTACTS_ONLY, restoredRules.first().contactFilterMode)
    // Master toggle always restores disabled for safety until the user turns it back on
    assertEquals(false, restoredSettings?.masterEnabled)
    assertEquals(true, restoredSettings?.welcomeEnabled)
    assertEquals("Volvemos pronto", restoredSettings?.awayMessage)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects oversized encrypted backup payload before decoding`() {
    val oversized = "x".repeat(10_000_001)
    LocalBackupManager.importEncryptedJson(oversized, "clave-segura")
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects encrypted backup with invalid salt or iv byte lengths`() {
    val shortSalt = java.util.Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))
    val shortIv = java.util.Base64.getEncoder().encodeToString(byteArrayOf(4, 5))
    val payload = """{"format":"whatsapp-repeater-backup-v1","algorithm":"AES-256-GCM","saltBase64":"$shortSalt","ivBase64":"$shortIv","cipherTextBase64":"AAAA","payloadSha256":"00"}"""
    LocalBackupManager.importEncryptedJson(payload, "clave-segura")
  }
}
