package com.whatsapprepeater.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object SplashRoute : NavKey
@Serializable data object HomeRoute : NavKey
@Serializable data object ContactPickerRoute : NavKey
@Serializable data object AutomationTypeRoute : NavKey
@Serializable data object MessageEditorRoute : NavKey
@Serializable data object RepetitionConfigRoute : NavKey
@Serializable data object IntervalConfigRoute : NavKey
@Serializable data object ConfirmationRoute : NavKey
@Serializable data object AutomationRoute : NavKey
@Serializable data object HistoryRoute : NavKey
@Serializable data class HistoryDetailRoute(val recordId: Long) : NavKey
@Serializable data object SettingsRoute : NavKey
@Serializable data object PermissionsRoute : NavKey
@Serializable data object UpdatesRoute : NavKey
@Serializable data object AutoReplyDashboardRoute : NavKey
@Serializable data object AutoReplyRulesRoute : NavKey
@Serializable data class AutoReplyRuleEditorRoute(val ruleId: Long = 0L) : NavKey
@Serializable data object AutoReplyContactFiltersRoute : NavKey
@Serializable data object AutoReplyScheduleRoute : NavKey
@Serializable data object AutoReplyWelcomeRoute : NavKey
@Serializable data object AutoReplyAwayRoute : NavKey
@Serializable data object AutoReplyHistoryRoute : NavKey
@Serializable data object AutoReplyPermissionsRoute : NavKey
