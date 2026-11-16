package com.whatsapprepeater.core.data.local

import android.content.Context
import java.io.File
import java.io.RandomAccessFile

/**
 * Crea un respaldo de seguridad de la base de datos SQLite antes de ejecutar
 * una migración de esquema, permitiendo recuperación si ocurriera un fallo externo.
 */
object DatabaseBackupManager {

  private const val BACKUP_DIR_NAME = "db_backups"
  private const val MAX_BACKUPS_TO_KEEP = 3

  fun backupBeforeMigrationIfNeeded(
    context: Context,
    dbName: String = AppDatabase.DB_NAME,
    targetVersion: Int = AppDatabase.CURRENT_VERSION,
  ): File? {
    val dbFile = context.getDatabasePath(dbName)
    if (!dbFile.exists() || dbFile.length() < 100L) return null

    val existingVersion = readSqliteUserVersion(dbFile) ?: return null
    if (existingVersion <= 0 || existingVersion >= targetVersion) return null

    val backupDir = File(context.filesDir, BACKUP_DIR_NAME).apply { mkdirs() }
    val timestamp = System.currentTimeMillis()
    val backupFile = File(backupDir, "${dbName}_v${existingVersion}_to_v${targetVersion}_$timestamp.db")

    return runCatching {
      dbFile.copyTo(backupFile, overwrite = true)
      listOf("-wal", "-shm").forEach { suffix ->
        val companion = File("${dbFile.absolutePath}$suffix")
        if (companion.exists()) {
          companion.copyTo(File("${backupFile.absolutePath}$suffix"), overwrite = true)
        }
      }
      pruneOldBackups(backupDir)
      backupFile
    }.getOrNull()
  }

  fun listBackups(context: Context): List<File> {
    val backupDir = File(context.filesDir, BACKUP_DIR_NAME)
    if (!backupDir.exists()) return emptyList()
    return backupDir
      .listFiles { file -> file.isFile && file.name.endsWith(".db") }
      ?.sortedByDescending { it.lastModified() }
      ?: emptyList()
  }

  fun restoreLatestBackup(
    context: Context,
    dbName: String = AppDatabase.DB_NAME,
  ): Boolean {
    val latest = listBackups(context).firstOrNull() ?: return false
    val targetDb = context.getDatabasePath(dbName)
    targetDb.parentFile?.mkdirs()
    return runCatching {
      latest.copyTo(targetDb, overwrite = true)
      listOf("-wal", "-shm").forEach { suffix ->
        val srcCompanion = File("${latest.absolutePath}$suffix")
        val dstCompanion = File("${targetDb.absolutePath}$suffix")
        if (srcCompanion.exists()) {
          srcCompanion.copyTo(dstCompanion, overwrite = true)
        } else if (dstCompanion.exists()) {
          dstCompanion.delete()
        }
      }
      true
    }.getOrDefault(false)
  }

  /**
   * Lee el campo `user_version` del encabezado SQLite (offset 60, entero big-endian de 4 bytes)
   * sin abrir ni modificar la base de datos.
   */
  internal fun readSqliteUserVersion(file: File): Int? {
    return runCatching {
      RandomAccessFile(file, "r").use { raf ->
        if (raf.length() < 64L) return null
        raf.seek(60L)
        raf.readInt()
      }
    }.getOrNull()
  }

  private fun pruneOldBackups(backupDir: File) {
    val dbBackups = backupDir
      .listFiles { file -> file.isFile && file.name.endsWith(".db") }
      ?.sortedByDescending { it.lastModified() }
      ?: return

    dbBackups.drop(MAX_BACKUPS_TO_KEEP).forEach { old ->
      old.delete()
      File("${old.absolutePath}-wal").delete()
      File("${old.absolutePath}-shm").delete()
    }
  }
}

