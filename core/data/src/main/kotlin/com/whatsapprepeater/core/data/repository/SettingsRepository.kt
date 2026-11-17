package com.whatsapprepeater.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.whatsapprepeater.core.model.AppSettings
import com.whatsapprepeater.core.model.SafetyLimits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "whatsapp_repeater_settings",
  corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

interface SettingsRepository {
  val settings: Flow<AppSettings>

  suspend fun updateDefaultIntervals(minSeconds: Int, maxSeconds: Int)

  suspend fun updateMaxRepeatCount(maxRepeatCount: Int)

  suspend fun updatePreferWhatsAppBusiness(preferBusiness: Boolean)

  suspend fun updateHapticFeedback(enabled: Boolean)

  suspend fun updateKeepScreenOn(enabled: Boolean)

  suspend fun updateVacationPause(enabled: Boolean)

  suspend fun updateGitHubRepository(owner: String, repo: String)

  suspend fun resetToDefaults()
}

class DataStoreSettingsRepository(
  private val context: Context,
) : SettingsRepository {

  private object Keys {
    val MIN_INTERVAL = intPreferencesKey("default_min_interval_seconds")
    val MAX_INTERVAL = intPreferencesKey("default_max_interval_seconds")
    val MAX_REPEAT_COUNT = intPreferencesKey("max_repeat_count")
    val PREFER_BUSINESS = booleanPreferencesKey("prefer_whatsapp_business")
    val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback_enabled")
    val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on_during_automation")
    val VACATION_PAUSE = booleanPreferencesKey("scheduler_vacation_pause")
    val GITHUB_OWNER = stringPreferencesKey("github_repo_owner")
    val GITHUB_REPO = stringPreferencesKey("github_repo_name")
  }

  override val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
    val minInterval = (prefs[Keys.MIN_INTERVAL] ?: SafetyLimits.DEFAULT_MIN_INTERVAL_SECONDS)
      .coerceIn(SafetyLimits.MIN_INTERVAL_SECONDS, SafetyLimits.MAX_INTERVAL_SECONDS)
    val maxInterval = (prefs[Keys.MAX_INTERVAL] ?: SafetyLimits.DEFAULT_MAX_INTERVAL_SECONDS)
      .coerceIn(minInterval, SafetyLimits.MAX_INTERVAL_SECONDS)
    val maxRepeat = (prefs[Keys.MAX_REPEAT_COUNT] ?: SafetyLimits.DEFAULT_MAX_REPEAT_COUNT)
      .coerceIn(SafetyLimits.MIN_REPEAT_COUNT, SafetyLimits.MAX_REPEAT_COUNT_HARD_LIMIT)
    val preferBusiness = prefs[Keys.PREFER_BUSINESS] ?: false
    val haptic = prefs[Keys.HAPTIC_FEEDBACK] ?: true
    val keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true
    val vacationPause = prefs[Keys.VACATION_PAUSE] ?: false
    val owner = prefs[Keys.GITHUB_OWNER]?.takeIf { it.isNotBlank() } ?: "the3600"
    val repo = prefs[Keys.GITHUB_REPO]?.takeIf { it.isNotBlank() } ?: "WhatsApp-Repeater"

    AppSettings(
      defaultMinIntervalSeconds = minInterval,
      defaultMaxIntervalSeconds = maxInterval,
      maxRepeatCount = maxRepeat,
      preferWhatsAppBusiness = preferBusiness,
      hapticFeedbackEnabled = haptic,
      keepScreenOnDuringAutomation = keepScreenOn,
      vacationPauseEnabled = vacationPause,
      githubRepoOwner = owner,
      githubRepoName = repo,
    )
  }

  override suspend fun updateDefaultIntervals(minSeconds: Int, maxSeconds: Int) {
    val safeMin = minSeconds.coerceIn(SafetyLimits.MIN_INTERVAL_SECONDS, SafetyLimits.MAX_INTERVAL_SECONDS)
    val safeMax = maxSeconds.coerceIn(safeMin, SafetyLimits.MAX_INTERVAL_SECONDS)
    context.settingsDataStore.edit { prefs ->
      prefs[Keys.MIN_INTERVAL] = safeMin
      prefs[Keys.MAX_INTERVAL] = safeMax
    }
  }

  override suspend fun updateMaxRepeatCount(maxRepeatCount: Int) {
    val safeLimit = maxRepeatCount.coerceIn(
      SafetyLimits.MIN_REPEAT_COUNT,
      SafetyLimits.MAX_REPEAT_COUNT_HARD_LIMIT,
    )
    context.settingsDataStore.edit { prefs ->
      prefs[Keys.MAX_REPEAT_COUNT] = safeLimit
    }
  }

  override suspend fun updatePreferWhatsAppBusiness(preferBusiness: Boolean) {
    context.settingsDataStore.edit { prefs ->
      prefs[Keys.PREFER_BUSINESS] = preferBusiness
    }
  }

  override suspend fun updateHapticFeedback(enabled: Boolean) {
    context.settingsDataStore.edit { prefs ->
      prefs[Keys.HAPTIC_FEEDBACK] = enabled
    }
  }

  override suspend fun updateKeepScreenOn(enabled: Boolean) {
    context.settingsDataStore.edit { prefs ->
      prefs[Keys.KEEP_SCREEN_ON] = enabled
    }
  }

  override suspend fun updateVacationPause(enabled: Boolean) {
    context.settingsDataStore.edit { prefs ->
      prefs[Keys.VACATION_PAUSE] = enabled
    }
  }

  override suspend fun updateGitHubRepository(owner: String, repo: String) {
    val cleanOwner = owner.trim()
    val cleanRepo = repo.trim()
    if (cleanOwner.isEmpty() || cleanRepo.isEmpty()) return
    context.settingsDataStore.edit { prefs ->
      prefs[Keys.GITHUB_OWNER] = cleanOwner
      prefs[Keys.GITHUB_REPO] = cleanRepo
    }
  }

  override suspend fun resetToDefaults() {
    context.settingsDataStore.edit { it.clear() }
  }
}
