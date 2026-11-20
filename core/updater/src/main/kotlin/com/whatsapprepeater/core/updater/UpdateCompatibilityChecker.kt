package com.whatsapprepeater.core.updater

import java.net.URI

data class InstalledAppInfo(
  val applicationId: String,
  val versionCode: Long,
  val versionName: String,
  val signingCertSha256: String?,
  val deviceSdkInt: Int,
)

sealed interface UpdateCheckResult {
  data class UpToDate(val currentVersionName: String, val currentVersionCode: Long) : UpdateCheckResult

  data class UpdateAvailable(
    val candidate: RemoteReleaseCandidate,
    val resolvedSha256: String?,
  ) : UpdateCheckResult

  data class IncompatibleUpdate(
    val candidate: RemoteReleaseCandidate,
    val reason: String,
  ) : UpdateCheckResult
}

/**
 * Evalúa compatibilidad e integridad de actualizaciones antes de descargar o instalar,
 * validando origen HTTPS confiable, applicationId, versionCode y firma independiente del APK.
 */
object UpdateCompatibilityChecker {

  const val EXPECTED_APPLICATION_ID = "com.whatsapprepeater.android"

  private val trustedDownloadHosts = setOf(
    "github.com",
    "api.github.com",
    "objects.githubusercontent.com",
    "release-assets.githubusercontent.com",
    "github-releases.githubusercontent.com",
  )

  fun validateDownloadUrl(urlString: String): String? {
    val trimmed = urlString.trim()
    if (trimmed.isEmpty()) return "La URL de descarga está vacía."
    val uri = runCatching { URI(trimmed) }.getOrNull()
      ?: return "La URL de descarga tiene un formato inválido."
    if (!uri.scheme.equals("https", ignoreCase = true)) {
      return "Solo se permiten descargas cifradas mediante HTTPS."
    }
    val host = uri.host?.lowercase()?.trim().orEmpty()
    val trusted = host in trustedDownloadHosts || host.endsWith(".githubusercontent.com")
    if (!trusted) {
      return "El dominio remoto ($host) no pertenece a los servidores oficiales de GitHub Releases."
    }
    return null
  }

  fun evaluate(
    installed: InstalledAppInfo,
    candidate: RemoteReleaseCandidate,
    resolvedSha256: String?,
  ): UpdateCheckResult {
    val urlError = validateDownloadUrl(candidate.apkDownloadUrl)
    if (urlError != null) {
      return UpdateCheckResult.IncompatibleUpdate(candidate = candidate, reason = urlError)
    }

    val metadata = candidate.metadata

    if (metadata != null) {
      if (metadata.applicationId.isNotBlank() && metadata.applicationId != installed.applicationId) {
        return UpdateCheckResult.IncompatibleUpdate(
          candidate = candidate,
          reason = "El applicationId remoto (${metadata.applicationId}) no coincide con la app instalada (${installed.applicationId}).",
        )
      }
      if (metadata.minSdk > installed.deviceSdkInt) {
        return UpdateCheckResult.IncompatibleUpdate(
          candidate = candidate,
          reason = "La actualización requiere Android API ${metadata.minSdk} o superior (tu dispositivo usa API ${installed.deviceSdkInt}).",
        )
      }
      if (!metadata.signingCertSha256.isNullOrBlank() &&
        !installed.signingCertSha256.isNullOrBlank() &&
        !metadata.signingCertSha256.equals(installed.signingCertSha256, ignoreCase = true)
      ) {
        return UpdateCheckResult.IncompatibleUpdate(
          candidate = candidate,
          reason = "La firma criptográfica de la versión publicada no coincide con la instalación actual. No se actualizará para proteger tus datos locales.",
        )
      }
      if (metadata.versionCode > 0 && metadata.versionCode <= installed.versionCode) {
        return UpdateCheckResult.UpToDate(installed.versionName, installed.versionCode)
      }
    }

    val currentSemVer = SemanticVersion.parseOrNull(installed.versionName)
    val remoteSemVer = SemanticVersion.parseOrNull(metadata?.versionName ?: candidate.tagName)

    if (currentSemVer != null && remoteSemVer != null) {
      if (remoteSemVer <= currentSemVer && (metadata == null || metadata.versionCode <= installed.versionCode)) {
        return UpdateCheckResult.UpToDate(installed.versionName, installed.versionCode)
      }
    } else if (metadata == null) {
      if (candidate.tagName.removePrefix("v") == installed.versionName) {
        return UpdateCheckResult.UpToDate(installed.versionName, installed.versionCode)
      }
    }

    val sha = resolvedSha256 ?: candidate.metadata?.sha256 ?: candidate.inlineSha256
    return UpdateCheckResult.UpdateAvailable(
      candidate = candidate,
      resolvedSha256 = sha,
    )
  }

  /**
   * Comprobación independiente sobre el binario APK descargado: nunca confía únicamente
   * en metadatos JSON; exige certificado de firma presente y coincidente con el instalado.
   */
  fun validateDownloadedApkMetadata(
    installed: InstalledAppInfo,
    apkPackageName: String?,
    apkVersionCode: Long,
    apkSigningCertSha256: String?,
  ): String? {
    if (apkPackageName != installed.applicationId) {
      return "El APK descargado pertenece a \"$apkPackageName\" y no a \"${installed.applicationId}\"."
    }
    if (apkVersionCode <= installed.versionCode) {
      return "El versionCode del APK ($apkVersionCode) no es superior al instalado (${installed.versionCode})."
    }
    if (apkSigningCertSha256.isNullOrBlank()) {
      return "El APK descargado no contiene un certificado de firma verificable. Actualización bloqueada por seguridad."
    }
    if (installed.signingCertSha256.isNullOrBlank()) {
      return "No se pudo leer la firma de la app instalada para confirmar compatibilidad criptográfica."
    }
    if (!installed.signingCertSha256.equals(apkSigningCertSha256, ignoreCase = true)) {
      return "La firma del APK descargado no coincide con la clave de la app instalada. Android rechazaría la actualización sin desinstalar."
    }
    return null
  }
}
