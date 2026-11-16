package com.whatsapprepeater.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.execSQL

@Database(
  entities = [
    ExecutionHistoryEntity::class,
    SavedMessageListEntity::class,
    AutomationConfigEntity::class,
    AutoReplyRuleEntity::class,
    AutoReplyLogEntity::class,
  ],
  version = AppDatabase.CURRENT_VERSION,
  exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun executionHistoryDao(): ExecutionHistoryDao
  abstract fun savedMessageListDao(): SavedMessageListDao
  abstract fun automationConfigDao(): AutomationConfigDao
  abstract fun autoReplyRuleDao(): AutoReplyRuleDao
  abstract fun autoReplyLogDao(): AutoReplyLogDao

  companion object {
    const val DB_NAME = "whatsapp_repeater.db"
    const val CURRENT_VERSION = 4

    /**
     * Migración controlada 1 -> 2: conserva `execution_history` y agrega tablas de listas y configuración.
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        migrateSql { sql -> db.execSQL(sql) }
      }

      override fun migrate(connection: SQLiteConnection) {
        migrateSql { sql -> connection.execSQL(sql) }
      }

      private inline fun migrateSql(exec: (String) -> Unit) {
        exec(
          """
          CREATE TABLE IF NOT EXISTS `saved_message_lists` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `title` TEXT NOT NULL,
            `isMultipleMode` INTEGER NOT NULL,
            `messagesJson` TEXT NOT NULL,
            `singleMessage` TEXT NOT NULL,
            `repeatCount` INTEGER NOT NULL,
            `updatedAtMillis` INTEGER NOT NULL
          )
          """.trimIndent(),
        )
        exec(
          """
          CREATE TABLE IF NOT EXISTS `automation_config` (
            `id` INTEGER NOT NULL,
            `contactId` TEXT,
            `contactName` TEXT,
            `contactPhone` TEXT,
            `isMultipleMode` INTEGER NOT NULL,
            `messagesJson` TEXT NOT NULL,
            `singleMessage` TEXT NOT NULL,
            `repeatCount` INTEGER NOT NULL,
            `minIntervalSeconds` INTEGER NOT NULL,
            `maxIntervalSeconds` INTEGER NOT NULL,
            `preferWhatsAppBusiness` INTEGER NOT NULL,
            `updatedAtMillis` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
          )
          """.trimIndent(),
        )
      }
    }

    /**
     * Migración controlada 2 -> 3: agrega campos de trazabilidad por lote/mensaje sin borrar historial.
     */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        migrateSql { sql -> db.execSQL(sql) }
      }

      override fun migrate(connection: SQLiteConnection) {
        migrateSql { sql -> connection.execSQL(sql) }
      }

      private inline fun migrateSql(exec: (String) -> Unit) {
        exec("ALTER TABLE `execution_history` ADD COLUMN `batchCode` TEXT NOT NULL DEFAULT ''")
        exec("ALTER TABLE `execution_history` ADD COLUMN `durationMillis` INTEGER NOT NULL DEFAULT 0")
        exec("ALTER TABLE `execution_history` ADD COLUMN `averageIntervalSeconds` REAL NOT NULL DEFAULT 0.0")
        exec("ALTER TABLE `execution_history` ADD COLUMN `modeLabel` TEXT NOT NULL DEFAULT ''")
        exec("ALTER TABLE `execution_history` ADD COLUMN `messageTracesJson` TEXT NOT NULL DEFAULT '[]'")
      }
    }

    /**
     * Migración controlada 3 -> 4: agrega tablas para el Motor 2 (Autorespuestas) conservando todos los datos previos.
     */
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        migrateSql { sql -> db.execSQL(sql) }
      }

      override fun migrate(connection: SQLiteConnection) {
        migrateSql { sql -> connection.execSQL(sql) }
      }

      private inline fun migrateSql(exec: (String) -> Unit) {
        exec(
          """
          CREATE TABLE IF NOT EXISTS `auto_reply_rules` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `name` TEXT NOT NULL,
            `pattern` TEXT NOT NULL,
            `matchType` TEXT NOT NULL,
            `caseSensitive` INTEGER NOT NULL,
            `responseTemplate` TEXT NOT NULL,
            `enabled` INTEGER NOT NULL,
            `priority` INTEGER NOT NULL,
            `targetApp` TEXT NOT NULL,
            `contactFilterMode` TEXT NOT NULL,
            `allowGroups` INTEGER NOT NULL,
            `selectedContactsJson` TEXT NOT NULL,
            `excludedIdentifiersJson` TEXT NOT NULL,
            `scheduleEnabled` INTEGER NOT NULL,
            `scheduleStartMinutes` INTEGER NOT NULL,
            `scheduleEndMinutes` INTEGER NOT NULL,
            `scheduleDaysJson` TEXT NOT NULL,
            `minIntervalSeconds` INTEGER NOT NULL,
            `maxRepliesPerConversation` INTEGER NOT NULL,
            `delaySeconds` INTEGER NOT NULL,
            `updatedAtMillis` INTEGER NOT NULL
          )
          """.trimIndent(),
        )
        exec(
          """
          CREATE TABLE IF NOT EXISTS `auto_reply_history` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `timestampMillis` INTEGER NOT NULL,
            `packageName` TEXT NOT NULL,
            `chatIdentifier` TEXT NOT NULL,
            `matchedRuleName` TEXT NOT NULL,
            `responseSent` TEXT NOT NULL,
            `incomingPreview` TEXT NOT NULL,
            `status` TEXT NOT NULL,
            `detail` TEXT NOT NULL
          )
          """.trimIndent(),
        )
      }
    }
  }
}
