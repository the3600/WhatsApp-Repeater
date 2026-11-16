package com.whatsapprepeater.app.ui.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsapprepeater.app.AppContainer
import com.whatsapprepeater.app.updater.ApkDownloadEvent
import com.whatsapprepeater.core.updater.InstalledAppInfo
import com.whatsapprepeater.core.updater.RemoteReleaseCandidate
import com.whatsapprepeater.core.updater.UpdateCheckResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class UpdatesUiState(
  val installedApp: InstalledAppInfo,
  val repoOwner: String = "the3600",
  val repoName: String = "WhatsApp-Repeater",
  val canInstallUnknownApps: Boolean = false,
  val isChecking: Boolean = false,
  val checkError: String? = null,
  val checkResult: UpdateCheckResult? = null,
  val isDownloading: Boolean = false,
  val downloadProgress: Float = 0f,
  val downloadedApkFile: File? = null,
  val verifiedSha256: String? = null,
  val downloadError: String? = null,
  val incompatibilityAlert: String? = null,
)

class UpdatesViewModel(
  private val container: AppContainer,
) : ViewModel() {

  private val installedInfo = container.updateManager.getInstalledAppInfo()
  private val canInstallFlag = MutableStateFlow(container.updateManager.canRequestPackageInstalls())
  private val isChecking = MutableStateFlow(false)
  private val checkError = MutableStateFlow<String?>(null)
  private val checkResult = MutableStateFlow<UpdateCheckResult?>(null)
  private val isDownloading = MutableStateFlow(false)
  private val downloadProgress = MutableStateFlow(0f)
  private val downloadedApkFile = MutableStateFlow<File?>(null)
  private val verifiedSha256 = MutableStateFlow<String?>(null)
  private val downloadError = MutableStateFlow<String?>(null)
  private val incompatibilityAlert = MutableStateFlow<String?>(null)
  private var downloadJob: Job? = null

  val uiState: StateFlow<UpdatesUiState> = combine(
    container.settingsRepository.settings,
    canInstallFlag,
    isChecking,
    checkError,
    checkResult,
    isDownloading,
    downloadProgress,
    downloadedApkFile,
    verifiedSha256,
    downloadError,
    incompatibilityAlert,
  ) { values ->
    val settings = values[0] as com.whatsapprepeater.core.model.AppSettings
    UpdatesUiState(
      installedApp = installedInfo,
      repoOwner = settings.githubRepoOwner,
      repoName = settings.githubRepoName,
      canInstallUnknownApps = values[1] as Boolean,
      isChecking = values[2] as Boolean,
      checkError = values[3] as String?,
      checkResult = values[4] as UpdateCheckResult?,
      isDownloading = values[5] as Boolean,
      downloadProgress = values[6] as Float,
      downloadedApkFile = values[7] as File?,
      verifiedSha256 = values[8] as String?,
      downloadError = values[9] as String?,
      incompatibilityAlert = values[10] as String?,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = UpdatesUiState(installedApp = installedInfo),
  )

  fun refreshInstallPermission() {
    canInstallFlag.value = container.updateManager.canRequestPackageInstalls()
  }

  fun updateRepository(owner: String, repo: String) {
    viewModelScope.launch {
      container.settingsRepository.updateGitHubRepository(owner, repo)
    }
  }

  fun checkUpdates() {
    if (isChecking.value || isDownloading.value) return
    val current = uiState.value
    viewModelScope.launch {
      isChecking.value = true
      checkError.value = null
      downloadError.value = null
      incompatibilityAlert.value = null
      downloadedApkFile.value = null
      verifiedSha256.value = null

      val result = container.updateManager.checkLatestRelease(
        owner = current.repoOwner,
        repo = current.repoName,
      )
      result.fold(
        onSuccess = { evaluated ->
          checkResult.value = evaluated
          if (evaluated is UpdateCheckResult.IncompatibleUpdate) {
            incompatibilityAlert.value = evaluated.reason
          }
        },
        onFailure = { err ->
          checkError.value = err.message ?: "No se pudo consultar GitHub Releases."
        },
      )
      isChecking.value = false
    }
  }

  fun downloadUpdate(candidate: RemoteReleaseCandidate, expectedSha256: String?) {
    if (isDownloading.value) return
    downloadJob = viewModelScope.launch {
      isDownloading.value = true
      downloadProgress.value = 0f
      downloadError.value = null
      incompatibilityAlert.value = null
      downloadedApkFile.value = null
      verifiedSha256.value = null

      try {
        container.updateManager.downloadAndVerifyApk(candidate, expectedSha256).collect { event ->
          when (event) {
            is ApkDownloadEvent.Progress -> {
              downloadProgress.value = event.fraction
            }
            is ApkDownloadEvent.VerifiedReady -> {
              downloadProgress.value = 1f
              downloadedApkFile.value = event.apkFile
              verifiedSha256.value = event.actualSha256
              isDownloading.value = false
            }
            is ApkDownloadEvent.Incompatible -> {
              incompatibilityAlert.value = event.reason
              isDownloading.value = false
            }
            is ApkDownloadEvent.Failed -> {
              downloadError.value = event.reason
              isDownloading.value = false
            }
          }
        }
      } catch (_: CancellationException) {
        isDownloading.value = false
        downloadProgress.value = 0f
        downloadError.value = "Descarga cancelada por el usuario."
      }
    }
  }

  fun cancelDownload() {
    downloadJob?.cancel()
    downloadJob = null
  }

  fun launchInstaller(): Boolean {
    val file = downloadedApkFile.value ?: return false
    return container.updateManager.launchOfficialInstaller(file)
  }

  companion object {
    fun factory(container: AppContainer): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = UpdatesViewModel(container) as T
      }
  }
}

