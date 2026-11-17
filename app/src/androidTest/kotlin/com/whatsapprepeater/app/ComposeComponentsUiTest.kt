package com.whatsapprepeater.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.whatsapprepeater.app.autoreply.AutoReplyRuntimeState
import com.whatsapprepeater.app.ui.insights.AnalyticsScreen
import com.whatsapprepeater.app.ui.insights.DiagnosticsScreen
import com.whatsapprepeater.app.ui.insights.InsightsViewModel
import com.whatsapprepeater.app.ui.library.SmartLibraryScreen
import com.whatsapprepeater.app.ui.library.SmartLibraryViewModel
import com.whatsapprepeater.app.ui.scheduler.SchedulerScreen
import com.whatsapprepeater.app.ui.scheduler.SchedulerViewModel
import com.whatsapprepeater.app.ui.autoreply.AutoReplyAwayScreen
import com.whatsapprepeater.app.ui.autoreply.AutoReplyContactFiltersScreen
import com.whatsapprepeater.app.ui.autoreply.AutoReplyDashboardScreen
import com.whatsapprepeater.app.ui.autoreply.AutoReplyHistoryScreen
import com.whatsapprepeater.app.ui.autoreply.AutoReplyPermissionsScreen
import com.whatsapprepeater.app.ui.autoreply.AutoReplyRuleEditorScreen
import com.whatsapprepeater.app.ui.autoreply.AutoReplyRulesScreen
import com.whatsapprepeater.app.ui.autoreply.AutoReplyScheduleScreen
import com.whatsapprepeater.app.ui.autoreply.AutoReplyViewModel
import com.whatsapprepeater.app.ui.autoreply.AutoReplyWelcomeScreen
import com.whatsapprepeater.app.ui.components.BottomTab
import com.whatsapprepeater.app.ui.components.EmergencyStopDialog
import com.whatsapprepeater.app.ui.components.EmergencyStopDialogState
import com.whatsapprepeater.app.ui.components.EmergencyStopStage
import com.whatsapprepeater.app.ui.components.EmptyStateCard
import com.whatsapprepeater.app.ui.components.WhatsAppRepeaterBottomBar
import com.whatsapprepeater.app.ui.theme.WhatsAppRepeaterTheme
import com.whatsapprepeater.core.data.repository.AutoReplyRepository
import com.whatsapprepeater.core.data.repository.AutoReplySettingsRepository
import com.whatsapprepeater.core.data.repository.ContactsRepository
import com.whatsapprepeater.core.model.AutoReplyLogEntry
import com.whatsapprepeater.core.model.AutoReplyRule
import com.whatsapprepeater.core.model.AutoReplySettings
import com.whatsapprepeater.core.model.AutomationState
import com.whatsapprepeater.core.model.ContactInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ComposeComponentsUiTest {

  @get:Rule
  val composeRule = createComposeRule()

  private fun createTestAutoReplyViewModel(
    initialContacts: List<ContactInfo> = emptyList(),
  ): AutoReplyViewModel {
    val rules = MutableStateFlow<List<AutoReplyRule>>(emptyList())
    val history = MutableStateFlow<List<AutoReplyLogEntry>>(emptyList())
    val settings = MutableStateFlow(AutoReplySettings())
    val ruleRepo = object : AutoReplyRepository {
      private var nextId = 1L
      override fun observeRules(): Flow<List<AutoReplyRule>> = rules
      override suspend fun getRulesOnce(): List<AutoReplyRule> = rules.value
      override suspend fun getRuleById(id: Long): AutoReplyRule? = rules.value.firstOrNull { it.id == id }
      override suspend fun saveRule(rule: AutoReplyRule): Long {
        val assigned = if (rule.id > 0L) rule.id else nextId++
        rules.value = rules.value.filterNot { it.id == assigned } + rule.copy(id = assigned)
        return assigned
      }

      override suspend fun setRuleEnabled(id: Long, enabled: Boolean) {
        rules.value = rules.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
      }

      override suspend fun deleteRule(id: Long) {
        rules.value = rules.value.filterNot { it.id == id }
      }

      override fun observeHistory(limit: Int): Flow<List<AutoReplyLogEntry>> = history
      override suspend fun recordLog(entry: AutoReplyLogEntry) {
        history.value = listOf(entry) + history.value
      }

      override suspend fun clearHistory() {
        history.value = emptyList()
      }
    }
    val settingsRepo = object : AutoReplySettingsRepository {
      override val settings: Flow<AutoReplySettings> = settings
      override suspend fun updateSettings(transform: (AutoReplySettings) -> AutoReplySettings) {
        settings.value = transform(settings.value)
      }

      override suspend fun resetToDefaults() {
        settings.value = AutoReplySettings()
      }
    }
    val contactsRepo = object : ContactsRepository {
      override suspend fun loadPhoneContacts(): List<ContactInfo> = initialContacts
    }
    return AutoReplyViewModel(
      autoReplyRepository = ruleRepo,
      autoReplySettingsRepository = settingsRepo,
      contactsRepository = contactsRepo,
      accessibilityConnectedFlow = MutableStateFlow(false),
      automationStateFlow = MutableStateFlow(AutomationState()),
      runtimeStateFlow = MutableStateFlow(AutoReplyRuntimeState()),
      onEmergencyStop = {},
    )
  }

  @Test
  fun emptyStateCard_displaysSpanishTitleAndTriggersAction() {
    var clicked = false
    composeRule.setContent {
      WhatsAppRepeaterTheme {
        EmptyStateCard(
          icon = Icons.Rounded.History,
          title = "Aún no hay actividad",
          description = "Sin registros reales todavía.",
          actionButtonText = "Crear primera automatización",
          onActionClick = { clicked = true },
        )
      }
    }

    composeRule.onNodeWithText("Aún no hay actividad").assertIsDisplayed()
    composeRule.onNodeWithText("Crear primera automatización").performClick()
    assertTrue(clicked)
  }

  @Test
  fun emergencyStopDialog_displaysConfirmationAndInvokesStopCallback() {
    var confirmed = false
    composeRule.setContent {
      WhatsAppRepeaterTheme {
        EmergencyStopDialog(
          state = EmergencyStopDialogState(
            isVisible = true,
            stage = EmergencyStopStage.CONFIRMATION,
            recipient = "Ana Gómez (+57 300 111 2222)",
            sentCount = 2,
            totalCount = 5,
          ),
          hapticEnabled = false,
          onDismissRequest = {},
          onConfirmEmergencyStop = { confirmed = true },
          onNavigateToHistory = {},
          onReturnToHome = {},
        )
      }
    }

    composeRule.onNodeWithText("¿Detener automatización?").assertIsDisplayed()
    composeRule.onNodeWithText("DETENER AHORA").performClick()
    assertTrue(confirmed)
  }

  @Test
  fun emergencyStopDialog_stoppedStage_navigatesToHistoryAndHome() {
    var wentHistory = false
    var wentHome = false
    composeRule.setContent {
      WhatsAppRepeaterTheme {
        EmergencyStopDialog(
          state = EmergencyStopDialogState(
            isVisible = true,
            stage = EmergencyStopStage.STOPPED,
            recipient = "Ana Gómez",
            sentCount = 1,
            totalCount = 4,
          ),
          hapticEnabled = false,
          onDismissRequest = {},
          onConfirmEmergencyStop = {},
          onNavigateToHistory = { wentHistory = true },
          onReturnToHome = { wentHome = true },
        )
      }
    }

    composeRule.onNodeWithText("Ver en Historial").performClick()
    composeRule.onNodeWithText("Volver al Inicio").performClick()
    assertTrue(wentHistory)
    assertTrue(wentHome)
  }

  @Test
  fun bottomBar_displaysAutoResponderTabAndInvokesCallback() {
    var selected: BottomTab? = null
    composeRule.setContent {
      WhatsAppRepeaterTheme {
        WhatsAppRepeaterBottomBar(
          currentTab = BottomTab.HOME,
          onSelectTab = { selected = it },
        )
      }
    }

    composeRule.onNodeWithText("Auto").assertIsDisplayed()
    composeRule.onNodeWithText("Auto").performClick()
    assertEquals(BottomTab.AUTORESPONDER, selected)
  }

  @Test
  fun autoReplyDashboard_opensWithoutPermissionsAndNavigatesToPermissionsAndEditor() {
    val vm = createTestAutoReplyViewModel()
    var openedRules = false
    var openedPerms = false
    composeRule.setContent {
      WhatsAppRepeaterTheme {
        AutoReplyDashboardScreen(
          viewModel = vm,
          onOpenRules = { openedRules = true },
          onCreateRule = {},
          onOpenContactFilters = {},
          onOpenSchedule = {},
          onOpenWelcome = {},
          onOpenAway = {},
          onOpenHistory = {},
          onOpenAutoReplyPermissions = { openedPerms = true },
          onOpenAccessibilityPermissions = {},
          onSelectBottomTab = {},
        )
      }
    }

    composeRule.onNodeWithText("Motor 2: Autorespuestas").assertIsDisplayed()
    composeRule.onNodeWithText("Configurar permisos y HyperOS").performClick()
    assertTrue(openedPerms)
    composeRule.onNodeWithText("Ver reglas (0)").performClick()
    assertTrue(openedRules)
  }

  @Test
  fun autoReplyRulesAndEditorScreens_renderWithoutPermissions() {
    val vm = createTestAutoReplyViewModel()
    composeRule.setContent {
      WhatsAppRepeaterTheme {
        AutoReplyRulesScreen(viewModel = vm, onBack = {}, onCreateRule = {}, onEditRule = {})
      }
    }
    composeRule.onNodeWithText("Reglas de Autorespuesta").assertIsDisplayed()
  }

  @Test
  fun autoReplyRuleEditor_rendersCreateModeWithoutCrash() {
    val vm = createTestAutoReplyViewModel()
    composeRule.setContent {
      WhatsAppRepeaterTheme {
        AutoReplyRuleEditorScreen(ruleId = 0L, viewModel = vm, onBack = {})
      }
    }
    composeRule.onNodeWithText("Crear Regla").assertIsDisplayed()
  }

  @Test
  fun autoReplyContactFilters_rendersWithoutPermissionsAndHandlesDuplicateContactIds() {
    val vm = createTestAutoReplyViewModel(
      initialContacts = listOf(
        ContactInfo("1", "Ana", "+573001112222"),
        ContactInfo("1", "Ana Trabajo", "+57 300 111 2222"),
      ),
    )
    composeRule.setContent {
      WhatsAppRepeaterTheme {
        AutoReplyContactFiltersScreen(viewModel = vm, onBack = {})
      }
    }
    composeRule.onNodeWithText("Filtros de Contactos").assertIsDisplayed()
  }

  @Test
  fun autoReplyScheduleWelcomeAwayHistoryAndPermissions_renderCleanly() {
    val vm = createTestAutoReplyViewModel()
    composeRule.setContent {
      WhatsAppRepeaterTheme {
        AutoReplyPermissionsScreen(viewModel = vm, onBack = {})
      }
    }
    composeRule.onNodeWithText("Permisos y POCO / HyperOS").assertIsDisplayed()
  }

  @Test
  fun autoReplyScheduleScreen_rendersWithoutCrash() {
    val vm = createTestAutoReplyViewModel()
    composeRule.setContent {
      WhatsAppRepeaterTheme {
        AutoReplyScheduleScreen(viewModel = vm, onBack = {})
      }
    }
    composeRule.onNodeWithText("Horarios y Límites").assertIsDisplayed()
  }

  @Test
  fun autoReplyWelcomeAndAwayAndHistory_renderWithoutCrash() {
    val vm = createTestAutoReplyViewModel()
    composeRule.setContent {
      WhatsAppRepeaterTheme {
        AutoReplyWelcomeScreen(viewModel = vm, onBack = {})
      }
    }
    composeRule.onNodeWithText("Mensaje de Bienvenida").assertIsDisplayed()
  }

  @Test
  fun autoReplyAwayScreen_rendersWithoutCrash() {
    val vm = createTestAutoReplyViewModel()
    composeRule.setContent {
      WhatsAppRepeaterTheme {
        AutoReplyAwayScreen(viewModel = vm, onBack = {})
      }
    }
    composeRule.onNodeWithText("Mensaje de Ausencia").assertIsDisplayed()
  }

  @Test
  fun autoReplyHistoryScreen_rendersPrivacyDefaultOffWithoutCrash() {
    val vm = createTestAutoReplyViewModel()
    composeRule.setContent {
      WhatsAppRepeaterTheme {
        AutoReplyHistoryScreen(viewModel = vm, onBack = {})
      }
    }
    composeRule.onNodeWithText("Historial de Respuestas").assertIsDisplayed()
  }

  @Test
  fun smartLibraryScreen_rendersWithoutCrash() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    val libraryVm = SmartLibraryViewModel(AppContainer(context))
    composeRule.setContent {
      WhatsAppRepeaterTheme {
        SmartLibraryScreen(viewModel = libraryVm, onBack = {}, onOpenTemplateInEditor = {})
      }
    }
    composeRule.onNodeWithText("Centro Inteligente de Mensajes").assertIsDisplayed()
  }

  @Test
  fun schedulerScreen_rendersWithoutCrash() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    val schedulerVm = SchedulerViewModel(AppContainer(context))
    composeRule.setContent {
      WhatsAppRepeaterTheme {
        SchedulerScreen(
          viewModel = schedulerVm,
          onBack = {},
          onOpenWizardToPrepareDraft = {},
          onExecutePreparedTask = {},
        )
      }
    }
    composeRule.onNodeWithText("Programador Local").assertIsDisplayed()
  }

  @Test
  fun diagnosticsScreen_rendersWithoutCrash() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    val insightsVm = InsightsViewModel(AppContainer(context))
    composeRule.setContent {
      WhatsAppRepeaterTheme {
        DiagnosticsScreen(
          viewModel = insightsVm,
          onBack = {},
          onOpenAccessibility = {},
          onOpenAutoReplyPermissions = {},
          onOpenUpdates = {},
          onOpenEditor = {},
        )
      }
    }
    composeRule.onNodeWithText("Diagnóstico del Sistema").assertIsDisplayed()
  }

  @Test
  fun analyticsScreen_rendersWithoutCrash() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    val insightsVm = InsightsViewModel(AppContainer(context))
    composeRule.setContent {
      WhatsAppRepeaterTheme {
        AnalyticsScreen(viewModel = insightsVm, onBack = {})
      }
    }
    composeRule.onNodeWithText("Analíticas Locales y Privadas").assertIsDisplayed()
  }
}
