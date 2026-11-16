package com.whatsapprepeater.app

import com.whatsapprepeater.app.autoreply.AutoReplyRuntimeState
import com.whatsapprepeater.app.ui.autoreply.AutoReplyViewModel
import com.whatsapprepeater.core.data.repository.AutoReplyRepository
import com.whatsapprepeater.core.data.repository.AutoReplySettingsRepository
import com.whatsapprepeater.core.data.repository.ContactsRepository
import com.whatsapprepeater.core.model.AutoReplyContactFilterMode
import com.whatsapprepeater.core.model.AutoReplyLogEntry
import com.whatsapprepeater.core.model.AutoReplyRule
import com.whatsapprepeater.core.model.AutoReplySchedule
import com.whatsapprepeater.core.model.AutoReplySettings
import com.whatsapprepeater.core.model.AutoReplyTargetApp
import com.whatsapprepeater.core.model.AutomationState
import com.whatsapprepeater.core.model.ContactInfo
import com.whatsapprepeater.core.model.RuleMatchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoReplyViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  private class FakeAutoReplyRepository : AutoReplyRepository {
    var shouldThrow: Throwable? = null
    private var nextId = 1L
    private val rulesFlow = MutableStateFlow<List<AutoReplyRule>>(emptyList())
    private val historyFlow = MutableStateFlow<List<AutoReplyLogEntry>>(emptyList())

    override fun observeRules(): Flow<List<AutoReplyRule>> = rulesFlow
    override suspend fun getRulesOnce(): List<AutoReplyRule> = rulesFlow.value
    override suspend fun getRuleById(id: Long): AutoReplyRule? {
      shouldThrow?.let { throw it }
      return rulesFlow.value.firstOrNull { it.id == id }
    }

    override suspend fun saveRule(rule: AutoReplyRule): Long {
      shouldThrow?.let { throw it }
      val assignedId = if (rule.id > 0L) rule.id else nextId++
      val stored = rule.copy(id = assignedId)
      rulesFlow.value = (rulesFlow.value.filterNot { it.id == assignedId } + stored)
        .sortedWith(compareByDescending<AutoReplyRule> { it.priority }.thenBy { it.id })
      return assignedId
    }

    override suspend fun setRuleEnabled(id: Long, enabled: Boolean) {
      shouldThrow?.let { throw it }
      rulesFlow.value = rulesFlow.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }

    override suspend fun deleteRule(id: Long) {
      shouldThrow?.let { throw it }
      rulesFlow.value = rulesFlow.value.filterNot { it.id == id }
    }

    override fun observeHistory(limit: Int): Flow<List<AutoReplyLogEntry>> = historyFlow
    override suspend fun recordLog(entry: AutoReplyLogEntry) {
      historyFlow.value = listOf(entry.copy(id = nextId++)) + historyFlow.value
    }

    override suspend fun clearHistory() {
      shouldThrow?.let { throw it }
      historyFlow.value = emptyList()
    }
  }

  private class FakeAutoReplySettingsRepository : AutoReplySettingsRepository {
    var shouldThrow: Throwable? = null
    private val flow = MutableStateFlow(AutoReplySettings())
    override val settings: Flow<AutoReplySettings> = flow

    override suspend fun updateSettings(transform: (AutoReplySettings) -> AutoReplySettings) {
      shouldThrow?.let { throw it }
      flow.value = transform(flow.value)
    }

    override suspend fun resetToDefaults() {
      flow.value = AutoReplySettings()
    }
  }

  private class FakeContactsRepository : ContactsRepository {
    override suspend fun loadPhoneContacts(): List<ContactInfo> =
      listOf(ContactInfo("1", "Ana Gómez", "+573001112222"))
  }

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(
    ruleRepo: FakeAutoReplyRepository = FakeAutoReplyRepository(),
    settingsRepo: FakeAutoReplySettingsRepository = FakeAutoReplySettingsRepository(),
    onEmergencyStop: () -> Unit = {},
  ): AutoReplyViewModel = AutoReplyViewModel(
    autoReplyRepository = ruleRepo,
    autoReplySettingsRepository = settingsRepo,
    contactsRepository = FakeContactsRepository(),
    accessibilityConnectedFlow = MutableStateFlow(false),
    automationStateFlow = MutableStateFlow(AutomationState()),
    runtimeStateFlow = MutableStateFlow(AutoReplyRuntimeState()),
    onEmergencyStop = onEmergencyStop,
  )

  @Test
  fun `initializes cleanly without permissions and supports all configuration operations`() = runTest {
    val ruleRepo = FakeAutoReplyRepository()
    val settingsRepo = FakeAutoReplySettingsRepository()
    var emergencyCalled = false
    val vm = createViewModel(ruleRepo, settingsRepo) { emergencyCalled = true }

    val collectJob = backgroundScope.launch(testDispatcher) { vm.uiState.collect() }

    assertFalse(vm.uiState.value.isNotificationAccessEnabled)
    assertFalse(vm.uiState.value.hasContactsPermission)
    assertFalse(vm.uiState.value.settings.masterEnabled)

    vm.setMasterEnabled(true)
    assertTrue(vm.uiState.value.settings.masterEnabled)

    vm.updateTargetApp(AutoReplyTargetApp.WHATSAPP_ONLY)
    assertEquals(AutoReplyTargetApp.WHATSAPP_ONLY, vm.uiState.value.settings.targetApp)

    vm.updateLoopProtection(false)
    assertFalse(vm.uiState.value.settings.preventLoopsEnabled)

    vm.updateGlobalLimits(minIntervalSec = 40, maxReplies = 8)
    assertEquals(40, vm.uiState.value.settings.minIntervalSeconds)
    assertEquals(8, vm.uiState.value.settings.maxRepliesPerConversation)

    vm.updateGlobalSchedule(AutoReplySchedule(enabled = true, startMinutesOfDay = 480, endMinutesOfDay = 1020))
    assertTrue(vm.uiState.value.settings.globalSchedule.enabled)

    vm.updateWelcomeConfig(enabled = true, message = "Hola {nombre}", cooldownMinutes = 120)
    assertTrue(vm.uiState.value.settings.welcomeEnabled)
    assertEquals("Hola {nombre}", vm.uiState.value.settings.welcomeMessage)

    vm.updateAwayConfig(
      enabled = true,
      message = "Fuera de horario",
      schedule = AutoReplySchedule(enabled = true, startMinutesOfDay = 1320, endMinutesOfDay = 420),
    )
    assertTrue(vm.uiState.value.settings.awayEnabled)

    vm.updateContactFilters(
      mode = AutoReplyContactFilterMode.SELECTED_CONTACTS,
      allowGroups = true,
      selected = listOf("Ana Gómez", "Ana Gómez"),
      excluded = listOf("Spam"),
    )
    assertEquals(AutoReplyContactFilterMode.SELECTED_CONTACTS, vm.uiState.value.settings.globalContactFilterMode)
    assertEquals(listOf("Ana Gómez"), vm.uiState.value.settings.selectedContactNamesOrPhones)

    vm.updateHistoryPrivacy(saveHistory = true, saveIncomingPreview = false)
    assertTrue(vm.uiState.value.settings.saveHistoryEnabled)
    vm.clearHistory()

    // Reglas CRUD + tipos de coincidencia
    vm.saveRule(
      AutoReplyRule(
        name = "Precios",
        pattern = "precio",
        matchType = RuleMatchType.CONTAINS_WORD,
        responseTemplate = "Lista enviada",
      ),
    )
    assertEquals(1, vm.uiState.value.rules.size)
    val savedId = vm.uiState.value.rules.first().id

    vm.setRuleEnabled(savedId, false)
    assertFalse(vm.uiState.value.rules.first().enabled)

    vm.saveRule(vm.uiState.value.rules.first().copy(name = "Precios Editado", matchType = RuleMatchType.EXACT))
    assertEquals("Precios Editado", vm.getRuleOnce(savedId)?.name)

    assertNull(vm.validateRulePattern("^hola.*", RuleMatchType.REGEX))
    assertNotNull(vm.validateRulePattern("[unclosed", RuleMatchType.REGEX))
    assertTrue(vm.testPatternMatch("Hola precio hoy", "precio", RuleMatchType.CONTAINS_WORD, false))

    vm.deleteRule(savedId)
    assertTrue(vm.uiState.value.rules.isEmpty())

    vm.emergencyStop()
    assertTrue(emergencyCalled)

    collectJob.cancel()
  }

  @Test
  fun `recovers gracefully from storage errors without crashing viewModelScope`() = runTest {
    val ruleRepo = FakeAutoReplyRepository()
    val settingsRepo = FakeAutoReplySettingsRepository().apply {
      shouldThrow = IllegalStateException("Fallo simulado de almacenamiento")
    }
    val vm = createViewModel(ruleRepo, settingsRepo)
    val collectJob = backgroundScope.launch(testDispatcher) { vm.uiState.collect() }

    vm.setMasterEnabled(true)
    assertTrue(vm.uiState.value.isErrorFeedback)
    assertTrue(vm.uiState.value.feedbackMessage?.contains("Fallo simulado") == true)

    vm.clearFeedback()
    assertFalse(vm.uiState.value.isErrorFeedback)
    assertNull(vm.uiState.value.feedbackMessage)

    settingsRepo.shouldThrow = null
    vm.setMasterEnabled(true)
    assertTrue(vm.uiState.value.settings.masterEnabled)

    collectJob.cancel()
  }
}

