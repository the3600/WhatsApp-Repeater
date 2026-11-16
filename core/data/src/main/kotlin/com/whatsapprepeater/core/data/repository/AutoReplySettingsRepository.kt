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
import com.whatsapprepeater.core.model.AutoReplyContactFilterMode
import com.whatsapprepeater.core.model.AutoReplySchedule
import com.whatsapprepeater.core.model.AutoReplySettings
import com.whatsapprepeater.core.model.AutoReplyTargetApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.autoReplyDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "whatsapp_repeater_autoreply_settings",
  corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

interface AutoReplySettingsRepository {
  val settings: Flow<AutoReplySettings>

  suspend fun updateSettings(transform: (AutoReplySettings) -> AutoReplySettings)

  suspend fun resetToDefaults()
}

@Serializable
private data class SchedulePrefDto(
  val enabled: Boolean = false,
  val startMinutesOfDay: Int = 9 * 60,
  val endMinutesOfDay: Int = 18 * 60,
  val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
) {
  fun toDomain(): AutoReplySchedule = AutoReplySchedule(
    enabled = enabled,
    startMinutesOfDay = startMinutesOfDay.coerceIn(0, 1439),
    endMinutesOfDay = endMinutesOfDay.coerceIn(0, 1439),
    daysOfWeek = daysOfWeek.filter { it in 1..7 }.toSet(),
  )

  companion object {
    fun fromDomain(schedule: AutoReplySchedule): SchedulePrefDto = SchedulePrefDto(
      enabled = schedule.enabled,
      startMinutesOfDay = schedule.startMinutesOfDay.coerceIn(0, 1439),
      endMinutesOfDay = schedule.endMinutesOfDay.coerceIn(0, 1439),
      daysOfWeek = schedule.daysOfWeek.filter { it in 1..7 }.toSet(),
    )
  }
}

class DataStoreAutoReplySettingsRepository internal constructor(
  private val dataStore: DataStore<Preferences>,
) : AutoReplySettingsRepository {

  constructor(context: Context) : this(context.applicationContext.autoReplyDataStore)

  private val json = Json { ignoreUnknownKeys = true }

  private object Keys {
    val MASTER_ENABLED = booleanPreferencesKey("ar_master_enabled")
    val TARGET_APP = stringPreferencesKey("ar_target_app")
    val CONTACT_FILTER_MODE = stringPreferencesKey("ar_contact_filter_mode")
    val ALLOW_GROUPS = booleanPreferencesKey("ar_allow_groups")
    val SELECTED_CONTACTS_JSON = stringPreferencesKey("ar_selected_contacts_json")
    val EXCLUDED_IDENTIFIERS_JSON = stringPreferencesKey("ar_excluded_identifiers_json")
    val GLOBAL_SCHEDULE_JSON = stringPreferencesKey("ar_global_schedule_json")
    val WELCOME_ENABLED = booleanPreferencesKey("ar_welcome_enabled")
    val WELCOME_MESSAGE = stringPreferencesKey("ar_welcome_message")
    val WELCOME_COOLDOWN_MINUTES = intPreferencesKey("ar_welcome_cooldown_minutes")
    val AWAY_ENABLED = booleanPreferencesKey("ar_away_enabled")
    val AWAY_MESSAGE = stringPreferencesKey("ar_away_message")
    val AWAY_SCHEDULE_JSON = stringPreferencesKey("ar_away_schedule_json")
    val MIN_INTERVAL_SECONDS = intPreferencesKey("ar_min_interval_seconds")
    val MAX_REPLIES_PER_CONV = intPreferencesKey("ar_max_replies_per_conv")
    val PREVENT_LOOPS = booleanPreferencesKey("ar_prevent_loops")
    val SAVE_HISTORY = booleanPreferencesKey("ar_save_history")
    val SAVE_INCOMING_PREVIEW = booleanPreferencesKey("ar_save_incoming_preview")
  }

  override val settings: Flow<AutoReplySettings> =
    dataStore.data.map(::extractFromPrefs)

  override suspend fun updateSettings(transform: (AutoReplySettings) -> AutoReplySettings) {
    dataStore.edit { prefs ->
      val current = extractFromPrefs(prefs)
      val next = transform(current)
      prefs[Keys.MASTER_ENABLED] = next.masterEnabled
      prefs[Keys.TARGET_APP] = next.targetApp.name
      prefs[Keys.CONTACT_FILTER_MODE] = next.globalContactFilterMode.name
      prefs[Keys.ALLOW_GROUPS] = next.allowGroupsByDefault
      prefs[Keys.SELECTED_CONTACTS_JSON] = json.encodeToString(next.selectedContactNamesOrPhones)
      prefs[Keys.EXCLUDED_IDENTIFIERS_JSON] = json.encodeToString(next.excludedIdentifiers)
      prefs[Keys.GLOBAL_SCHEDULE_JSON] = json.encodeToString(SchedulePrefDto.fromDomain(next.globalSchedule))
      prefs[Keys.WELCOME_ENABLED] = next.welcomeEnabled
      prefs[Keys.WELCOME_MESSAGE] = next.welcomeMessage
      prefs[Keys.WELCOME_COOLDOWN_MINUTES] = next.welcomeCooldownMinutes.coerceIn(1, 10_080)
      prefs[Keys.AWAY_ENABLED] = next.awayEnabled
      prefs[Keys.AWAY_MESSAGE] = next.awayMessage
      prefs[Keys.AWAY_SCHEDULE_JSON] = json.encodeToString(SchedulePrefDto.fromDomain(next.awaySchedule))
      prefs[Keys.MIN_INTERVAL_SECONDS] = next.minIntervalSeconds.coerceIn(0, 3_600)
      prefs[Keys.MAX_REPLIES_PER_CONV] = next.maxRepliesPerConversation.coerceIn(1, 100)
      prefs[Keys.PREVENT_LOOPS] = next.preventLoopsEnabled
      prefs[Keys.SAVE_HISTORY] = next.saveHistoryEnabled
      prefs[Keys.SAVE_INCOMING_PREVIEW] = next.saveIncomingPreviewEnabled
    }
  }

  override suspend fun resetToDefaults() {
    dataStore.edit { it.clear() }
  }

  private fun extractFromPrefs(prefs: Preferences): AutoReplySettings {
    val defaults = AutoReplySettings()
    return AutoReplySettings(
      masterEnabled = prefs[Keys.MASTER_ENABLED] ?: defaults.masterEnabled,
      targetApp = prefs[Keys.TARGET_APP]?.let {
        runCatching { AutoReplyTargetApp.valueOf(it) }.getOrNull()
      } ?: defaults.targetApp,
      globalContactFilterMode = prefs[Keys.CONTACT_FILTER_MODE]?.let {
        runCatching { AutoReplyContactFilterMode.valueOf(it) }.getOrNull()
      } ?: defaults.globalContactFilterMode,
      allowGroupsByDefault = prefs[Keys.ALLOW_GROUPS] ?: defaults.allowGroupsByDefault,
      selectedContactNamesOrPhones = prefs[Keys.SELECTED_CONTACTS_JSON]?.let {
        runCatching { json.decodeFromString<List<String>>(it) }.getOrNull()
      } ?: defaults.selectedContactNamesOrPhones,
      excludedIdentifiers = prefs[Keys.EXCLUDED_IDENTIFIERS_JSON]?.let {
        runCatching { json.decodeFromString<List<String>>(it) }.getOrNull()
      } ?: defaults.excludedIdentifiers,
      globalSchedule = prefs[Keys.GLOBAL_SCHEDULE_JSON]?.let {
        runCatching { json.decodeFromString<SchedulePrefDto>(it).toDomain() }.getOrNull()
      } ?: defaults.globalSchedule,
      welcomeEnabled = prefs[Keys.WELCOME_ENABLED] ?: defaults.welcomeEnabled,
      welcomeMessage = prefs[Keys.WELCOME_MESSAGE] ?: defaults.welcomeMessage,
      welcomeCooldownMinutes = (prefs[Keys.WELCOME_COOLDOWN_MINUTES] ?: defaults.welcomeCooldownMinutes)
        .coerceIn(1, 10_080),
      awayEnabled = prefs[Keys.AWAY_ENABLED] ?: defaults.awayEnabled,
      awayMessage = prefs[Keys.AWAY_MESSAGE] ?: defaults.awayMessage,
      awaySchedule = prefs[Keys.AWAY_SCHEDULE_JSON]?.let {
        runCatching { json.decodeFromString<SchedulePrefDto>(it).toDomain() }.getOrNull()
      } ?: defaults.awaySchedule,
      minIntervalSeconds = (prefs[Keys.MIN_INTERVAL_SECONDS] ?: defaults.minIntervalSeconds)
        .coerceIn(0, 3_600),
      maxRepliesPerConversation = (prefs[Keys.MAX_REPLIES_PER_CONV] ?: defaults.maxRepliesPerConversation)
        .coerceIn(1, 100),
      preventLoopsEnabled = prefs[Keys.PREVENT_LOOPS] ?: defaults.preventLoopsEnabled,
      saveHistoryEnabled = prefs[Keys.SAVE_HISTORY] ?: defaults.saveHistoryEnabled,
      saveIncomingPreviewEnabled = prefs[Keys.SAVE_INCOMING_PREVIEW] ?: defaults.saveIncomingPreviewEnabled,
    )
  }
}
