package com.whatsapprepeater.core.updater

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
 * Evalúa compatibilidad de actualización antes de descargar o instalar,
 * protegiendo la identidad del paquete, firma, versionCode y datos del usuario.
 */
object UpdateCompatibilityChecker {

  const val EXPECTED_APPLICATION_ID = "com.whatsapprepeater.android"

  fun evaluate(
    installed: InstalledAppInfo,
    candidate: RemoteReleaseCandidate,
    resolvedSha256: String?,
  ): UpdateCheckResult {
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
    if (!installed.signingCertSha256.isNullOrBlank() &&
      !apkSigningCertSha256.isNullOrBlank() &&
      !installed.signingCertSha256.equals(apkSigningCertSha256, ignoreCase = true)
    ) {
      return "La firma del APK descargado no coincide con la clave de la app instalada. Android rechazaría la actualización sin desinstalar."
    }
    return null
  }
}

