package com.whatsapprepeater.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.whatsapprepeater.app.ui.automation.AutomationScreen
import com.whatsapprepeater.app.ui.automation.AutomationViewModel
import com.whatsapprepeater.app.ui.automation.ConfirmationScreen
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
import com.whatsapprepeater.app.ui.config.AutomationTypeScreen
import com.whatsapprepeater.app.ui.config.IntervalConfigScreen
import com.whatsapprepeater.app.ui.config.IntervalConfigViewModel
import com.whatsapprepeater.app.ui.contacts.ContactPickerScreen
import com.whatsapprepeater.app.ui.contacts.ContactPickerViewModel
import com.whatsapprepeater.app.ui.history.HistoryDetailScreen
import com.whatsapprepeater.app.ui.history.HistoryScreen
import com.whatsapprepeater.app.ui.history.HistoryViewModel
import com.whatsapprepeater.app.ui.home.HomeScreen
import com.whatsapprepeater.app.ui.home.HomeViewModel
import com.whatsapprepeater.app.ui.messages.MessageEditorScreen
import com.whatsapprepeater.app.ui.messages.MessageEditorViewModel
import com.whatsapprepeater.app.ui.messages.RepetitionConfigScreen
import com.whatsapprepeater.app.ui.permissions.PermissionsAccessibilityScreen
import com.whatsapprepeater.app.ui.settings.SettingsBackupScreen
import com.whatsapprepeater.app.ui.settings.SettingsBackupViewModel
import com.whatsapprepeater.app.ui.splash.SplashScreen
import com.whatsapprepeater.app.ui.updates.UpdatesScreen
import com.whatsapprepeater.app.ui.updates.UpdatesViewModel
import com.whatsapprepeater.core.model.AppSettings

@Composable
fun MainNavigation(container: AppContainer) {
  val backStack = rememberNavBackStack(SplashRoute)
  val currentSettings by container.settingsRepository.settings.collectAsStateWithLifecycle(
    initialValue = AppSettings(),
  )

  fun resetToRoot(root: NavKey) {
    backStack.clear()
    backStack.add(root)
  }

  fun navigateBackToOrAdd(target: NavKey) {
    val existingIndex = backStack.indexOfLast { it == target }
    if (existingIndex >= 0) {
      while (backStack.size - 1 > existingIndex) {
        backStack.removeLastOrNull()
      }
    } else {
      backStack.add(target)
    }
  }

  fun navigateByBottomTab(tab: BottomTab) {
    when (tab) {
      BottomTab.HOME -> resetToRoot(HomeRoute)
      BottomTab.CREATE -> {
        container.automationEngine.resetToIdle()
        container.resetDraftForNewAutomation(
          defaultMin = currentSettings.defaultMinIntervalSeconds,
          defaultMax = currentSettings.defaultMaxIntervalSeconds,
          preferBusiness = currentSettings.preferWhatsAppBusiness,
        )
        backStack.add(ContactPickerRoute)
      }
      BottomTab.AUTORESPONDER -> resetToRoot(AutoReplyDashboardRoute)
      BottomTab.HISTORY -> resetToRoot(HistoryRoute)
      BottomTab.SETTINGS -> resetToRoot(SettingsRoute)
    }
  }

  NavDisplay(
    backStack = backStack,
    onBack = {
      if (backStack.size > 1) {
        backStack.removeLastOrNull()
      }
    },
    entryProvider = entryProvider {
      entry<SplashRoute> {
        SplashScreen(
          onContinue = { resetToRoot(HomeRoute) },
        )
      }

      entry<HomeRoute> {
        val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
        HomeScreen(
          viewModel = vm,
          onCreateAutomation = { backStack.add(ContactPickerRoute) },
          onResumeSavedDraft = { backStack.add(ConfirmationRoute) },
          onOpenActiveAutomation = { backStack.add(AutomationRoute) },
          onOpenPermissions = { backStack.add(PermissionsRoute) },
          onOpenHistoryTab = { resetToRoot(HistoryRoute) },
          onOpenHistoryDetail = { id -> backStack.add(HistoryDetailRoute(id)) },
          onSelectBottomTab = ::navigateByBottomTab,
        )
      }

      entry<ContactPickerRoute> {
        val vm: ContactPickerViewModel = viewModel(factory = ContactPickerViewModel.factory(container))
        ContactPickerScreen(
          viewModel = vm,
          onBack = { backStack.removeLastOrNull() },
          onConfirmContact = { backStack.add(AutomationTypeRoute) },
        )
      }

      entry<AutomationTypeRoute> {
        AutomationTypeScreen(
          container = container,
          onBack = { backStack.removeLastOrNull() },
          onContinueRepeatMode = { backStack.add(RepetitionConfigRoute) },
          onContinueMultipleMode = { backStack.add(MessageEditorRoute) },
        )
      }

      entry<MessageEditorRoute> {
        val vm: MessageEditorViewModel = viewModel(factory = MessageEditorViewModel.factory(container))
        MessageEditorScreen(
          viewModel = vm,
          onBack = { backStack.removeLastOrNull() },
          onContinue = { backStack.add(IntervalConfigRoute) },
        )
      }

      entry<RepetitionConfigRoute> {
        val vm: MessageEditorViewModel = viewModel(factory = MessageEditorViewModel.factory(container))
        RepetitionConfigScreen(
          viewModel = vm,
          onBack = { backStack.removeLastOrNull() },
          onContinue = { backStack.add(IntervalConfigRoute) },
        )
      }

      entry<IntervalConfigRoute> {
        val vm: IntervalConfigViewModel = viewModel(factory = IntervalConfigViewModel.factory(container))
        IntervalConfigScreen(
          viewModel = vm,
          onBack = { backStack.removeLastOrNull() },
          onContinue = { backStack.add(ConfirmationRoute) },
        )
      }

      entry<ConfirmationRoute> {
        val vm: AutomationViewModel = viewModel(factory = AutomationViewModel.factory(container))
        ConfirmationScreen(
          viewModel = vm,
          onBack = { backStack.removeLastOrNull() },
          onModifyParameters = { navigateBackToOrAdd(ContactPickerRoute) },
          onOpenPermissions = { backStack.add(PermissionsRoute) },
          onStartConfirmed = {
            if (backStack.lastOrNull() != AutomationRoute) {
              backStack.add(AutomationRoute)
            }
          },
        )
      }

      entry<AutomationRoute> {
        val vm: AutomationViewModel = viewModel(factory = AutomationViewModel.factory(container))
        AutomationScreen(
          viewModel = vm,
          onBack = { backStack.removeLastOrNull() },
          onDoneToHome = { resetToRoot(HomeRoute) },
          onNavigateToHistory = { resetToRoot(HistoryRoute) },
        )
      }

      entry<HistoryRoute> {
        val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))
        HistoryScreen(
          viewModel = vm,
          onCreateFirstAutomation = { navigateByBottomTab(BottomTab.CREATE) },
          onOpenDetail = { id -> backStack.add(HistoryDetailRoute(id)) },
          onRetryRecord = { backStack.add(ConfirmationRoute) },
          onOpenBackupSettings = { resetToRoot(SettingsRoute) },
          onOpenPermissions = { backStack.add(PermissionsRoute) },
          onSelectBottomTab = ::navigateByBottomTab,
        )
      }

      entry<HistoryDetailRoute> { route ->
        val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))
        HistoryDetailScreen(
          recordId = route.recordId,
          viewModel = vm,
          onBack = { backStack.removeLastOrNull() },
          onResumePending = { backStack.add(ConfirmationRoute) },
        )
      }

      entry<SettingsRoute> {
        val vm: SettingsBackupViewModel = viewModel(factory = SettingsBackupViewModel.factory(container))
        SettingsBackupScreen(
          viewModel = vm,
          onOpenUpdates = { backStack.add(UpdatesRoute) },
          onOpenPermissions = { backStack.add(PermissionsRoute) },
          onSelectBottomTab = ::navigateByBottomTab,
        )
      }

      entry<PermissionsRoute> {
        val serviceConnected by container.accessibilityDriver.isServiceConnected.collectAsStateWithLifecycle()
        PermissionsAccessibilityScreen(
          isServiceConnected = serviceConnected,
          onBack = { backStack.removeLastOrNull() },
        )
      }

      entry<UpdatesRoute> {
        val vm: UpdatesViewModel = viewModel(factory = UpdatesViewModel.factory(container))
        UpdatesScreen(
          viewModel = vm,
          updateManager = container.updateManager,
          onBack = { backStack.removeLastOrNull() },
        )
      }
      entry<AutoReplyDashboardRoute> {
        val vm: AutoReplyViewModel = viewModel(factory = AutoReplyViewModel.factory(container))
        AutoReplyDashboardScreen(
          viewModel = vm,
          onOpenRules = { backStack.add(AutoReplyRulesRoute) },
          onCreateRule = { backStack.add(AutoReplyRuleEditorRoute(ruleId = 0L)) },
          onOpenContactFilters = { backStack.add(AutoReplyContactFiltersRoute) },
          onOpenSchedule = { backStack.add(AutoReplyScheduleRoute) },
          onOpenWelcome = { backStack.add(AutoReplyWelcomeRoute) },
          onOpenAway = { backStack.add(AutoReplyAwayRoute) },
          onOpenHistory = { backStack.add(AutoReplyHistoryRoute) },
          onOpenAutoReplyPermissions = { backStack.add(AutoReplyPermissionsRoute) },
          onOpenAccessibilityPermissions = { backStack.add(PermissionsRoute) },
          onSelectBottomTab = ::navigateByBottomTab,
        )
      }
      entry<AutoReplyRulesRoute> {
        val vm: AutoReplyViewModel = viewModel(factory = AutoReplyViewModel.factory(container))
        AutoReplyRulesScreen(
          viewModel = vm,
          onBack = { backStack.removeLastOrNull() },
          onCreateRule = { backStack.add(AutoReplyRuleEditorRoute(ruleId = 0L)) },
          onEditRule = { ruleId -> backStack.add(AutoReplyRuleEditorRoute(ruleId = ruleId)) },
        )
      }
      entry<AutoReplyRuleEditorRoute> { key ->
        val vm: AutoReplyViewModel = viewModel(factory = AutoReplyViewModel.factory(container))
        AutoReplyRuleEditorScreen(
          ruleId = key.ruleId,
          viewModel = vm,
          onBack = { backStack.removeLastOrNull() },
        )
      }
      entry<AutoReplyContactFiltersRoute> {
        val vm: AutoReplyViewModel = viewModel(factory = AutoReplyViewModel.factory(container))
        AutoReplyContactFiltersScreen(
          viewModel = vm,
          onBack = { backStack.removeLastOrNull() },
        )
      }
      entry<AutoReplyScheduleRoute> {
        val vm: AutoReplyViewModel = viewModel(factory = AutoReplyViewModel.factory(container))
        AutoReplyScheduleScreen(
          viewModel = vm,
          onBack = { backStack.removeLastOrNull() },
        )
      }
      entry<AutoReplyWelcomeRoute> {
        val vm: AutoReplyViewModel = viewModel(factory = AutoReplyViewModel.factory(container))
        AutoReplyWelcomeScreen(
          viewModel = vm,
          onBack = { backStack.removeLastOrNull() },
        )
      }
      entry<AutoReplyAwayRoute> {
        val vm: AutoReplyViewModel = viewModel(factory = AutoReplyViewModel.factory(container))
        AutoReplyAwayScreen(
          viewModel = vm,
          onBack = { backStack.removeLastOrNull() },
        )
      }
      entry<AutoReplyHistoryRoute> {
        val vm: AutoReplyViewModel = viewModel(factory = AutoReplyViewModel.factory(container))
        AutoReplyHistoryScreen(
          viewModel = vm,
          onBack = { backStack.removeLastOrNull() },
        )
      }
      entry<AutoReplyPermissionsRoute> {
        val vm: AutoReplyViewModel = viewModel(factory = AutoReplyViewModel.factory(container))
        AutoReplyPermissionsScreen(
          viewModel = vm,
          onBack = { backStack.removeLastOrNull() },
        )
      }
    },
  )
}
