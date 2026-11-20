package com.whatsapprepeater.app.ui.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsapprepeater.app.AppContainer
import com.whatsapprepeater.app.BuildConfig
import com.whatsapprepeater.app.service.AccessibilitySettingsHelper
import com.whatsapprepeater.core.data.local.DatabaseBackupManager
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SecurityPermissionItem(
  val id: String,
  val title: String,
  val scopeDetail: String,
  val isGranted: Boolean,
)

data class SecurityPrivacyUiState(
  val isAuditing: Boolean = false,
  val lastVerifiedAtMillis: Long = 0L,
  val keystoreHealthy: Boolean = false,
  val keystoreDetail: String = "Pendiente de verificación...",
  val installedCertSha256: String = "",
  val matchesPermanentProductionCert: Boolean = false,
  val appVersionLabel: String = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
  val applicationId: String = BuildConfig.APPLICATION_ID,
  val permissions: List<SecurityPermissionItem> = emptyList(),
  val dbSizeKb: Long = 0L,
  val updateCacheKb: Long = 0L,
  val preMigrationBackupsCount: Int = 0,
  val motor1HistoryCount: Int = 0,
  val motor2HistoryCount: Int = 0,
  val savedTemplatesCount: Int = 0,
  val schedulesCount: Int = 0,
  val feedbackMessage: String? = null,
  val isErrorFeedback: Boolean = false,
) {
  val grantedPermissionsCount: Int
    get() = permissions.count { it.isGranted }
}

class SecurityPrivacyViewModel(
  private val container: AppContainer,
) : ViewModel() {

  private val _uiState = MutableStateFlow(SecurityPrivacyUiState())
  val uiState: StateFlow<SecurityPrivacyUiState> = _uiState.asStateFlow()

  fun runSecurityAudit(context: Context) {
    if (_uiState.value.isAuditing) return
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isAuditing = true)
      val appCtx = context.applicationContext

      val vaultResult = withContext(Dispatchers.Default) {
        container.keystoreVault.verifyHealth()
      }
      val certSha = withContext(Dispatchers.IO) {
        readInstalledSigningCertSha256(appCtx)
      }
      val matchesProdCert = certSha.equals(PERMANENT_PRODUCTION_CERT_SHA256, ignoreCase = true)

      val a11yConnected = container.accessibilityDriver.isServiceConnected.value ||
        AccessibilitySettingsHelper.isAccessibilityEnabledInSettings(appCtx)
      val notifListener = NotificationManagerCompat.getEnabledListenerPackages(appCtx)
        .contains(appCtx.packageName)
      val postNotif = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(appCtx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
      val contacts = ContextCompat.checkSelfPermission(appCtx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
      val exactAlarms = container.schedulerAlarmHelper.canScheduleExactAlarms()
      val unknownSources = container.updateManager.canRequestPackageInstalls()
      val powerManager = appCtx.getSystemService(Context.POWER_SERVICE) as? PowerManager
      val ignoringBattery = powerManager?.isIgnoringBatteryOptimizations(appCtx.packageName) ?: false

      val permissions = listOf(
        SecurityPermissionItem(
          id = "a11y",
          title = "Servicio de Accesibilidad (Motor 1)",
          scopeDetail = "Limitado exclusivamente a com.whatsapp y com.whatsapp.w4b con confirmación previa.",
          isGranted = a11yConnected,
        ),
        SecurityPermissionItem(
          id = "notif_listener",
          title = "Acceso a Notificaciones (Motor 2)",
          scopeDetail = "Lee notificaciones activas de WhatsApp solo si habilitas el autorespondedor.",
          isGranted = notifListener,
        ),
        SecurityPermissionItem(
          id = "post_notif",
          title = "Notificaciones de control y cuenta regresiva",
          scopeDetail = "Muestra parada de emergencia y programaciones activas.",
          isGranted = postNotif,
        ),
        SecurityPermissionItem(
          id = "contacts",
          title = "Lectura de Contactos",
          scopeDetail = "Solo lectura local bajo demanda; nunca se sube ni comparte la agenda.",
          isGranted = contacts,
        ),
        SecurityPermissionItem(
          id = "exact_alarms",
          title = "Alarmas exactas del Programador",
          scopeDetail = "Permite puntualidad al disparar envíos programados.",
          isGranted = exactAlarms,
        ),
        SecurityPermissionItem(
          id = "install_packages",
          title = "Actualizador verificado (GitHub Releases)",
          scopeDetail = "Solo aplica APKs HTTPS con SHA-256 y firma permanente idéntica.",
          isGranted = unknownSources,
        ),
        SecurityPermissionItem(
          id = "battery",
          title = "Exención de optimización de batería",
          scopeDetail = "Reduce cierres en segundo plano en HyperOS / POCO y Android.",
          isGranted = ignoringBattery,
        ),
      )

      val dbKb = container.getDatabaseSizeBytes() / 1024L
      val cacheKb = container.getUpdateCacheSizeBytes() / 1024L
      val preBackups = withContext(Dispatchers.IO) { DatabaseBackupManager.listBackups(appCtx).size }
      val m1Count = runCatching { container.historyRepository.getAllExecutionsOnce().size }.getOrDefault(0)
      val m2Count = runCatching { container.autoReplyRepository.observeHistory(limit = 500).first().size }.getOrDefault(0)
      val tplCount = runCatching { container.savedConfigRepository.observeSavedMessageLists().first().size }.getOrDefault(0)
      val schCount = runCatching { container.scheduledTaskRepository.getAllOnce().size }.getOrDefault(0)

      _uiState.value = _uiState.value.copy(
        isAuditing = false,
        lastVerifiedAtMillis = System.currentTimeMillis(),
        keystoreHealthy = vaultResult.isAvailable && vaultResult.isVerified,
        keystoreDetail = vaultResult.detail,
        installedCertSha256 = certSha,
        matchesPermanentProductionCert = matchesProdCert,
        permissions = permissions,
        dbSizeKb = dbKb,
        updateCacheKb = cacheKb,
        preMigrationBackupsCount = preBackups,
        motor1HistoryCount = m1Count,
        motor2HistoryCount = m2Count,
        savedTemplatesCount = tplCount,
        schedulesCount = schCount,
      )
    }
  }

  fun clearUpdateCache(context: Context) {
    container.clearUpdateCache()
    _uiState.value = _uiState.value.copy(
      feedbackMessage = "Caché temporal de APKs eliminada correctamente.",
      isErrorFeedback = false,
    )
    runSecurityAudit(context)
  }

  fun clearAllHistories(context: Context) {
    viewModelScope.launch {
      container.historyRepository.clearHistory()
      container.autoReplyRepository.clearHistory()
      _uiState.value = _uiState.value.copy(
        feedbackMessage = "Historiales locales de Motor 1 y Motor 2 eliminados.",
        isErrorFeedback = false,
      )
      runSecurityAudit(context)
    }
  }

  fun factoryReset(context: Context) {
    viewModelScope.launch {
      container.factoryResetLocalData()
      _uiState.value = _uiState.value.copy(
        feedbackMessage = "Todos los datos locales, historiales y preferencias han sido restablecidos.",
        isErrorFeedback = false,
      )
      runSecurityAudit(context)
    }
  }

  private fun readInstalledSigningCertSha256(context: Context): String {
    return runCatching {
      val info = context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.GET_SIGNING_CERTIFICATES,
      )
      val signerBytes = info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        ?: return@runCatching "No disponible"
      MessageDigest.getInstance("SHA-256")
        .digest(signerBytes)
        .joinToString("") { "%02x".format(it) }
    }.getOrDefault("No disponible")
  }

  companion object {
    const val PERMANENT_PRODUCTION_CERT_SHA256 =
      "0e582e7eec8712c582f7a48f1ad1d473e2ba149f4ed997914574a4daeb13c109"

    fun factory(container: AppContainer): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SecurityPrivacyViewModel(container) as T
      }
  }
}
