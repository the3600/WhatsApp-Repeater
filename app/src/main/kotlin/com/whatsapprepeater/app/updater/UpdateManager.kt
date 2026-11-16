package com.whatsapprepeater.app.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.whatsapprepeater.app.BuildConfig
import com.whatsapprepeater.core.updater.GitHubReleaseParser
import com.whatsapprepeater.core.updater.InstalledAppInfo
import com.whatsapprepeater.core.updater.RemoteReleaseCandidate
import com.whatsapprepeater.core.updater.Sha256Verifier
import com.whatsapprepeater.core.updater.UpdateCheckResult
import com.whatsapprepeater.core.updater.UpdateCompatibilityChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed interface ApkDownloadEvent {
  data class Progress(val fraction: Float, val downloadedBytes: Long, val totalBytes: Long) : ApkDownloadEvent
  data class VerifiedReady(val apkFile: File, val actualSha256: String) : ApkDownloadEvent
  data class Incompatible(val reason: String) : ApkDownloadEvent
  data class Failed(val reason: String) : ApkDownloadEvent
}

/**
 * Gestiona la consulta de GitHub Releases, descarga segura, validación SHA-256,
 * verificación de compatibilidad (applicationId, versionCode, firma) e invocación
 * del instalador oficial de paquetes de Android tras confirmación del usuario.
 */
class UpdateManager(
  private val context: Context,
) {

  fun getInstalledAppInfo(): InstalledAppInfo {
    val certSha = readInstalledSigningCertSha256()
    return InstalledAppInfo(
      applicationId = BuildConfig.APPLICATION_ID,
      versionCode = BuildConfig.VERSION_CODE.toLong(),
      versionName = BuildConfig.VERSION_NAME,
      signingCertSha256 = certSha,
      deviceSdkInt = Build.VERSION.SDK_INT,
    )
  }

  fun canRequestPackageInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

  fun openUnknownSourcesSettings() {
    val intent = Intent(
      Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
      Uri.parse("package:${context.packageName}"),
    ).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
  }

  suspend fun checkLatestRelease(owner: String, repo: String): Result<UpdateCheckResult> =
    withContext(Dispatchers.IO) {
      runCatching {
        val cleanOwner = owner.trim()
        val cleanRepo = repo.trim()
        require(cleanOwner.isNotEmpty() && cleanRepo.isNotEmpty()) {
          "Debes indicar el propietario y repositorio de GitHub."
        }

        val latestUrl = "https://api.github.com/repos/$cleanOwner/$cleanRepo/releases/latest"
        val candidate = runCatching {
          val raw = fetchText(latestUrl)
          GitHubReleaseParser.parseSingleRelease(raw)
        }.getOrNull() ?: run {
          val listRaw = fetchText("https://api.github.com/repos/$cleanOwner/$cleanRepo/releases?per_page=10")
          GitHubReleaseParser.parseReleaseList(listRaw)
        } ?: return@runCatching UpdateCheckResult.UpToDate(
          currentVersionName = BuildConfig.VERSION_NAME,
          currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
        )

        val metadata = candidate.metadataAssetUrl?.let { url ->
          runCatching { GitHubReleaseParser.parseMetadata(fetchText(url)) }.getOrNull()
        }
        val shaFromAsset = candidate.sha256AssetUrl?.let { url ->
          runCatching { Sha256Verifier.extractSha256FromText(fetchText(url)) }.getOrNull()
        }

        val enrichedCandidate = candidate.copy(metadata = metadata)
        val resolvedSha = shaFromAsset ?: metadata?.sha256 ?: candidate.inlineSha256

        UpdateCompatibilityChecker.evaluate(
          installed = getInstalledAppInfo(),
          candidate = enrichedCandidate,
          resolvedSha256 = resolvedSha,
        )
      }
    }

  fun downloadAndVerifyApk(
    candidate: RemoteReleaseCandidate,
    expectedSha256: String?,
  ): Flow<ApkDownloadEvent> = flow {
    val updatesDir = File(context.cacheDir, "updates").apply {
      mkdirs()
      // Limpiar descargas previas temporales para no acumular caché.
      listFiles()?.forEach { it.delete() }
    }
    val safeName = candidate.apkFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    val targetFile = File(updatesDir, safeName)

    val connection = (URL(candidate.apkDownloadUrl).openConnection() as HttpURLConnection).apply {
      connectTimeout = 15_000
      readTimeout = 30_000
      instanceFollowRedirects = true
      setRequestProperty("User-Agent", "WhatsAppRepeater-Android/${BuildConfig.VERSION_NAME}")
    }

    try {
      val code = connection.responseCode
      if (code !in 200..299) {
        emit(ApkDownloadEvent.Failed("Error HTTP $code al descargar el APK."))
        return@flow
      }

      val totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: candidate.apkSizeBytes
      var downloaded = 0L

      connection.inputStream.use { input ->
        targetFile.outputStream().use { output ->
          val buffer = ByteArray(16 * 1024)
          var bytesRead = input.read(buffer)
          var lastEmitBytes = 0L
          while (bytesRead >= 0) {
            output.write(buffer, 0, bytesRead)
            downloaded += bytesRead
            if (downloaded - lastEmitBytes >= 64 * 1024 || downloaded == totalBytes) {
              val fraction = if (totalBytes > 0) (downloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
              emit(ApkDownloadEvent.Progress(fraction, downloaded, totalBytes))
              lastEmitBytes = downloaded
            }
            bytesRead = input.read(buffer)
          }
        }
      }

      // 1. Verificar integridad criptográfica SHA-256
      val actualSha = Sha256Verifier.computeSha256(targetFile)
      if (!expectedSha256.isNullOrBlank() && !Sha256Verifier.verifyHexMatch(actualSha, expectedSha256)) {
        targetFile.delete()
        emit(
          ApkDownloadEvent.Failed(
            "Error de integridad SHA-256. Esperado: $expectedSha256, obtenido: $actualSha. El archivo fue descartado.",
          ),
        )
        return@flow
      }

      // 2. Verificar compatibilidad real del APK descargado (applicationId, versionCode, firma)
      val incompatibility = inspectDownloadedApkCompatibility(targetFile)
      if (incompatibility != null) {
        targetFile.delete()
        emit(ApkDownloadEvent.Incompatible(incompatibility))
        return@flow
      }

      emit(ApkDownloadEvent.VerifiedReady(targetFile, actualSha))
    } catch (e: Exception) {
      targetFile.delete()
      emit(ApkDownloadEvent.Failed(e.message ?: "Error al descargar o verificar el APK."))
    } finally {
      connection.disconnect()
    }
  }.flowOn(Dispatchers.IO)

  fun launchOfficialInstaller(apkFile: File): Boolean {
    if (!apkFile.exists()) return false
    val uri = FileProvider.getUriForFile(
      context,
      "${BuildConfig.APPLICATION_ID}.fileprovider",
      apkFile,
    )
    val installIntent = Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(uri, "application/vnd.android.package-archive")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
      context.startActivity(installIntent)
      true
    } catch (_: Exception) {
      false
    }
  }

  @Suppress("DEPRECATION")
  private fun inspectDownloadedApkCompatibility(apkFile: File): String? {
    val archiveInfo = context.packageManager.getPackageArchiveInfo(
      apkFile.absolutePath,
      PackageManager.GET_SIGNING_CERTIFICATES,
    ) ?: return "El archivo descargado no es un paquete APK válido."

    val certBytes = archiveInfo.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
    val apkCertSha = certBytes?.let { sha256Hex(it) }

    return UpdateCompatibilityChecker.validateDownloadedApkMetadata(
      installed = getInstalledAppInfo(),
      apkPackageName = archiveInfo.packageName,
      apkVersionCode = archiveInfo.longVersionCode,
      apkSigningCertSha256 = apkCertSha,
    )
  }

  private fun readInstalledSigningCertSha256(): String? {
    return runCatching {
      val info = context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.GET_SIGNING_CERTIFICATES,
      )
      val bytes = info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray() ?: return null
      sha256Hex(bytes)
    }.getOrNull()
  }

  private fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
  }

  private fun fetchText(urlString: String): String {
    val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
      connectTimeout = 15_000
      readTimeout = 20_000
      instanceFollowRedirects = true
      setRequestProperty("Accept", "application/vnd.github+json")
      setRequestProperty("User-Agent", "WhatsAppRepeater-Android/${BuildConfig.VERSION_NAME}")
    }
    return try {
      val code = conn.responseCode
      if (code == 404) {
        throw IllegalStateException(
          "No se encontraron Releases públicos (HTTP 404). Si el repositorio es privado, publica los Releases desde un repositorio público de distribución o verifica el nombre owner/repo.",
        )
      }
      if (code !in 200..299) {
        val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
        throw IllegalStateException("GitHub API respondió HTTP $code${if (err.isNotBlank()) ": ${err.take(140)}" else ""}")
      }
      conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
      conn.disconnect()
    }
  }
}

