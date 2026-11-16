package com.whatsapprepeater.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.whatsapprepeater.core.data.local.AutoReplyLogEntity
import com.whatsapprepeater.core.data.local.AutoReplyRuleEntity
import com.whatsapprepeater.core.data.repository.DataStoreAutoReplySettingsRepository
import com.whatsapprepeater.core.model.AutoReplyContactFilterMode
import com.whatsapprepeater.core.model.AutoReplyLogEntry
import com.whatsapprepeater.core.model.AutoReplyLogStatus
import com.whatsapprepeater.core.model.AutoReplyRule
import com.whatsapprepeater.core.model.AutoReplySchedule
import com.whatsapprepeater.core.model.AutoReplyTargetApp
import com.whatsapprepeater.core.model.RuleMatchType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreAutoReplySettingsRepositoryTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
      val updated = transform(state.value)
      state.value = updated
      return updated
    }
  }

  @Test
  fun `persists and reloads all auto reply settings including schedules on DataStore`() = runBlocking {
    val file = File(tempFolder.newFolder(), "test_autoreply.preferences_pb")
    val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repo1 = DataStoreAutoReplySettingsRepository(
      PreferenceDataStoreFactory.create(scope = scope1) { file },
    )

    val initial = repo1.settings.first()
    assertFalse(initial.masterEnabled)
    assertFalse(initial.saveHistoryEnabled)

    repo1.updateSettings {
      it.copy(
        masterEnabled = true,
        targetApp = AutoReplyTargetApp.WHATSAPP_BUSINESS_ONLY,
        globalContactFilterMode = AutoReplyContactFilterMode.SELECTED_CONTACTS,
        allowGroupsByDefault = true,
        selectedContactNamesOrPhones = listOf("Ana Gómez", "+573001112222"),
        excludedIdentifiers = listOf("Spam", "Grupo Familia"),
        globalSchedule = AutoReplySchedule(
          enabled = true,
          startMinutesOfDay = 8 * 60 + 30,
          endMinutesOfDay = 19 * 60 + 15,
          daysOfWeek = setOf(1, 2, 3, 4, 5),
        ),
        welcomeEnabled = true,
        welcomeMessage = "Hola {nombre}, bienvenido.",
        welcomeCooldownMinutes = 180,
        awayEnabled = true,
        awayMessage = "Volvemos a las {hora}.",
        awaySchedule = AutoReplySchedule(
          enabled = true,
          startMinutesOfDay = 21 * 60,
          endMinutesOfDay = 7 * 60,
          daysOfWeek = setOf(6, 7),
        ),
        minIntervalSeconds = 45,
        maxRepliesPerConversation = 4,
        preventLoopsEnabled = true,
        saveHistoryEnabled = true,
        saveIncomingPreviewEnabled = true,
      )
    }

    val updated = repo1.settings.first()
    assertTrue(updated.masterEnabled)
    assertEquals(AutoReplyTargetApp.WHATSAPP_BUSINESS_ONLY, updated.targetApp)
    assertEquals(AutoReplyContactFilterMode.SELECTED_CONTACTS, updated.globalContactFilterMode)
    assertTrue(updated.allowGroupsByDefault)
    assertEquals(listOf("Ana Gómez", "+573001112222"), updated.selectedContactNamesOrPhones)
    assertEquals(listOf("Spam", "Grupo Familia"), updated.excludedIdentifiers)
    assertEquals(510, updated.globalSchedule.startMinutesOfDay)
    assertEquals(1155, updated.globalSchedule.endMinutesOfDay)
    assertEquals(setOf(1, 2, 3, 4, 5), updated.globalSchedule.daysOfWeek)
    assertTrue(updated.welcomeEnabled)
    assertTrue(updated.awayEnabled)
    assertEquals(1260, updated.awaySchedule.startMinutesOfDay)
    assertEquals(420, updated.awaySchedule.endMinutesOfDay)
    assertEquals(45, updated.minIntervalSeconds)
    assertEquals(4, updated.maxRepliesPerConversation)
    assertTrue(updated.saveHistoryEnabled)
    assertTrue(updated.saveIncomingPreviewEnabled)
    scope1.cancel()
  }

  @Test
  fun `supports multiple consecutive updates and resetToDefaults without serialization exception`() = runBlocking {
    val repo = DataStoreAutoReplySettingsRepository(InMemoryPreferencesDataStore())

    repo.updateSettings {
      it.copy(
        masterEnabled = true,
        globalSchedule = AutoReplySchedule(enabled = true, startMinutesOfDay = 600, endMinutesOfDay = 1200),
      )
    }
    assertTrue(repo.settings.first().masterEnabled)
    assertEquals(600, repo.settings.first().globalSchedule.startMinutesOfDay)

    repo.updateSettings {
      it.copy(
        awayEnabled = true,
        awaySchedule = AutoReplySchedule(enabled = true, startMinutesOfDay = 1320, endMinutesOfDay = 420),
      )
    }
    assertTrue(repo.settings.first().awayEnabled)
    assertEquals(1320, repo.settings.first().awaySchedule.startMinutesOfDay)

    repo.resetToDefaults()
    val reset = repo.settings.first()
    assertFalse(reset.masterEnabled)
    assertFalse(reset.welcomeEnabled)
    assertFalse(reset.awayEnabled)
    assertFalse(reset.saveHistoryEnabled)
  }

  @Test
  fun `roundtrips AutoReplyRuleEntity and AutoReplyLogEntity domain models`() {
    val rule = AutoReplyRule(
      id = 9L,
      name = "Regex Precios",
      pattern = "^(precio|costo)\\b",
      matchType = RuleMatchType.REGEX,
      caseSensitive = true,
      responseTemplate = "Hola {nombre}, precios hoy {fecha}",
      enabled = true,
      priority = 25,
      targetApp = AutoReplyTargetApp.WHATSAPP_ONLY,
      contactFilterMode = AutoReplyContactFilterMode.SAVED_CONTACTS_ONLY,
      allowGroups = true,
      selectedContactNamesOrPhones = listOf("Carlos"),
      excludedIdentifiers = listOf("Bot"),
      schedule = AutoReplySchedule(enabled = true, startMinutesOfDay = 600, endMinutesOfDay = 1200, daysOfWeek = setOf(1, 3, 5)),
      minIntervalSeconds = 90,
      maxRepliesPerConversation = 3,
      delaySeconds = 2,
      updatedAtMillis = 1700000000000L,
    )
    assertEquals(rule, AutoReplyRuleEntity.fromDomain(rule).toDomain())

    val log = AutoReplyLogEntry(
      id = 4L,
      timestampMillis = 1700000001000L,
      packageName = "com.whatsapp",
      chatIdentifier = "Ana Gómez",
      matchedRuleName = "Regex Precios",
      responseSent = "Hola Ana Gómez",
      incomingPreview = "precio",
      status = AutoReplyLogStatus.SENT,
      detail = "Enviado mediante RemoteInput",
    )
    assertEquals(log, AutoReplyLogEntity.fromDomain(log).toDomain())
  }
}
