package com.whatsapprepeater.core.autoresponder

import com.whatsapprepeater.core.model.AutoReplyDecision
import com.whatsapprepeater.core.model.AutoReplyLogStatus
import com.whatsapprepeater.core.model.AutoReplyRule
import com.whatsapprepeater.core.model.AutoReplySettings
import com.whatsapprepeater.core.model.AutoReplyTargetApp
import com.whatsapprepeater.core.model.AutomationConfig
import com.whatsapprepeater.core.model.IncomingNotificationEvent
import java.time.ZoneId

/**
 * Motor local de decisiones para autorespuestas sobre notificaciones Android.
 * Es completamente independiente del motor de repetición (AccessibilityService).
 */
class AutoReplyEngine(
  private val clockMillis: () -> Long = { System.currentTimeMillis() },
  private val zoneId: ZoneId = ZoneId.systemDefault(),
) {

  private val lock = Any()

  // Evita procesar la misma notificación más de una vez.
  private val processedFingerprints = object : LinkedHashMap<String, Long>(256, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 250
  }

  // Hashes de respuestas recién enviadas para cortar ecos o bucles de autorespondedores.
  private val recentOutgoingReplies = ArrayDeque<Pair<String, Long>>()

  // Registro por conversación y regla/tipo para cooldown, límites y bucles rápidos.
  private val lastReplyByBucketMillis = HashMap<String, Long>()
  private val replyCountByBucket = HashMap<String, Int>()
  private val rapidIncomingTimestampsByConv = HashMap<String, ArrayDeque<Long>>()
  private val lastWelcomeByConvMillis = HashMap<String, Long>()

  fun evaluate(
    event: IncomingNotificationEvent,
    settings: AutoReplySettings,
    rules: List<AutoReplyRule>,
    savedContacts: SavedContactsLookup = SavedContactsLookup(),
  ): AutoReplyDecision? {
    val convKey = buildConversationKey(event)

    // 1. Validar paquete permitido y activación general.
    if (!settings.masterEnabled) return null
    if (event.packageName != AutomationConfig.WHATSAPP_PACKAGE &&
      event.packageName != AutomationConfig.WHATSAPP_BUSINESS_PACKAGE
    ) {
      return null
    }
    if (!matchesTargetApp(event.packageName, settings.targetApp)) {
      return AutoReplyDecision.Skip(
        status = AutoReplyLogStatus.SKIPPED_FILTER,
        reason = "La aplicación origen (${event.packageName}) está excluida en ajustes globales.",
        conversationKey = convKey,
      )
    }

    // 2. Ignorar resúmenes agrupados, mensajes propios o notificaciones vacías.
    if (event.isGroupSummary || event.isSelfMessage) return null
    val trimmedText = event.messageText.trim()
    if (trimmedText.isEmpty()) return null

    val now = clockMillis()

    synchronized(lock) {
      // 3. Deduplicación estricta de notificación ya vista.
      val fingerprint = "${event.notificationKey}|${event.timestampMillis}|${RuleMatcher.normalizeInsensitive(trimmedText).hashCode()}"
      if (processedFingerprints.containsKey(fingerprint)) {
        return null
      }
      processedFingerprints[fingerprint] = now

      // 4. Prevención de bucles entre autorespondedores.
      if (settings.preventLoopsEnabled) {
        purgeExpiredOutgoing(now)
        val normIncoming = RuleMatcher.normalizeInsensitive(trimmedText)
        if (recentOutgoingReplies.any { it.first == normIncoming }) {
          return AutoReplyDecision.Skip(
            status = AutoReplyLogStatus.SKIPPED_LOOP_GUARD,
            reason = "Protección antibucle: texto idéntico a una respuesta enviada recientemente.",
            conversationKey = convKey,
          )
        }

        val rapidQueue = rapidIncomingTimestampsByConv.getOrPut(convKey) { ArrayDeque() }
        while (rapidQueue.isNotEmpty() && now - rapidQueue.first() > 20_000L) {
          rapidQueue.removeFirst()
        }
        rapidQueue.addLast(now)
        if (rapidQueue.size >= 4) {
          return AutoReplyDecision.Skip(
            status = AutoReplyLogStatus.SKIPPED_LOOP_GUARD,
            reason = "Protección antibucle: demasiados mensajes consecutivos en menos de 20 s.",
            conversationKey = convKey,
          )
        }
      }

      // 5. Verificar si Android entregó acción RemoteInput válida.
      if (!event.hasReplyAction) {
        return AutoReplyDecision.Skip(
          status = AutoReplyLogStatus.SKIPPED_NO_REMOTE_INPUT,
          reason = "La notificación no incluye acción de respuesta directa (RemoteInput).",
          conversationKey = convKey,
        )
      }

      val globalScheduleOk = ScheduleEvaluator.isActiveAt(settings.globalSchedule, now, zoneId)
      val senderOrTitle = event.senderName?.takeIf { it.isNotBlank() } ?: event.conversationTitle

      // 6. Evaluar reglas ordenadas por prioridad descendente (y id ascendente).
      val orderedRules = rules
        .filter { it.enabled }
        .sortedWith(compareByDescending<AutoReplyRule> { it.priority }.thenBy { it.id })

      var lastRuleSkipReason: Pair<AutoReplyLogStatus, String>? = null

      for (rule in orderedRules) {
        if (!matchesTargetApp(event.packageName, rule.targetApp)) continue

        if (!RuleMatcher.matches(trimmedText, rule.pattern, rule.matchType, rule.caseSensitive)) {
          continue
        }

        val ruleScheduleOk = if (rule.schedule.enabled) {
          ScheduleEvaluator.isActiveAt(rule.schedule, now, zoneId)
        } else {
          globalScheduleOk
        }
        if (!ruleScheduleOk) {
          lastRuleSkipReason = AutoReplyLogStatus.SKIPPED_SCHEDULE to
            "Regla '${rule.name}' fuera de horario."
          continue
        }

        val combinedExclusions = (settings.excludedIdentifiers + rule.excludedIdentifiers).distinct()
        val contactOk = ContactFilterEvaluator.isAllowed(
          event = event,
          mode = rule.contactFilterMode,
          allowGroups = rule.allowGroups,
          selectedIdentifiers = rule.selectedContactNamesOrPhones,
          excludedIdentifiers = combinedExclusions,
          savedContacts = savedContacts,
        )
        if (!contactOk) {
          lastRuleSkipReason = AutoReplyLogStatus.SKIPPED_FILTER to
            "Contacto/grupo bloqueado por filtros de la regla '${rule.name}'."
          continue
        }

        val bucket = "$convKey#rule#${rule.id}"
        val cooldownSec = rule.minIntervalSeconds.coerceAtLeast(0)
        val lastSent = lastReplyByBucketMillis[bucket] ?: 0L
        if (cooldownSec > 0 && lastSent > 0L && (now - lastSent) < cooldownSec * 1000L) {
          lastRuleSkipReason = AutoReplyLogStatus.SKIPPED_COOLDOWN to
            "Tiempo mínimo de espera (${cooldownSec}s) activo para '${rule.name}'."
          continue
        }

        val maxLimit = rule.maxRepliesPerConversation
        val currentCount = replyCountByBucket[bucket] ?: 0
        if (maxLimit > 0 && currentCount >= maxLimit) {
          lastRuleSkipReason = AutoReplyLogStatus.SKIPPED_LIMIT to
            "Límite de $maxLimit respuestas alcanzado para '${rule.name}'."
          continue
        }

        val resolved = TemplateVariableResolver.resolve(
          template = rule.responseTemplate,
          senderOrChatName = senderOrTitle,
          incomingMessage = trimmedText,
          timestampMillis = now,
          zoneId = zoneId,
        )
        if (resolved.isBlank()) continue

        return AutoReplyDecision.Reply(
          ruleId = rule.id,
          ruleName = rule.name,
          responseText = resolved,
          delaySeconds = rule.delaySeconds.coerceIn(0, 60),
          conversationKey = convKey,
        )
      }

      if (lastRuleSkipReason != null) {
        return AutoReplyDecision.Skip(
          status = lastRuleSkipReason.first,
          reason = lastRuleSkipReason.second,
          conversationKey = convKey,
        )
      }

      // 7. Evaluar filtros globales para Bienvenida y Ausencia.
      val globalContactOk = ContactFilterEvaluator.isAllowed(
        event = event,
        mode = settings.globalContactFilterMode,
        allowGroups = settings.allowGroupsByDefault,
        selectedIdentifiers = settings.selectedContactNamesOrPhones,
        excludedIdentifiers = settings.excludedIdentifiers,
        savedContacts = savedContacts,
      )
      if (!globalContactOk) {
        return AutoReplyDecision.Skip(
          status = AutoReplyLogStatus.SKIPPED_FILTER,
          reason = "Conversación excluida por los filtros globales de contactos.",
          conversationKey = convKey,
        )
      }

      // 8. Mensaje de Ausencia (si está activo y su horario aplica).
      if (settings.awayEnabled &&
        settings.awayMessage.isNotBlank() &&
        ScheduleEvaluator.isActiveAt(settings.awaySchedule, now, zoneId)
      ) {
        val bucket = "$convKey#away"
        val cooldown = settings.minIntervalSeconds.coerceAtLeast(5)
        val lastSent = lastReplyByBucketMillis[bucket] ?: 0L
        if (lastSent > 0L && (now - lastSent) < cooldown * 1000L) {
          return AutoReplyDecision.Skip(
            status = AutoReplyLogStatus.SKIPPED_COOLDOWN,
            reason = "Enfriamiento activo para mensaje de ausencia.",
            conversationKey = convKey,
          )
        }
        val count = replyCountByBucket[bucket] ?: 0
        if (settings.maxRepliesPerConversation > 0 && count >= settings.maxRepliesPerConversation) {
          return AutoReplyDecision.Skip(
            status = AutoReplyLogStatus.SKIPPED_LIMIT,
            reason = "Límite de respuestas alcanzado para mensaje de ausencia.",
            conversationKey = convKey,
          )
        }
        val text = TemplateVariableResolver.resolve(
          template = settings.awayMessage,
          senderOrChatName = senderOrTitle,
          incomingMessage = trimmedText,
          timestampMillis = now,
          zoneId = zoneId,
        )
        if (text.isNotBlank()) {
          return AutoReplyDecision.Reply(
            ruleId = null,
            ruleName = "Mensaje de ausencia",
            responseText = text,
            delaySeconds = 0,
            conversationKey = convKey,
          )
        }
      }

      // 9. Mensaje de Bienvenida (si está activo y dentro de horario global).
      if (settings.welcomeEnabled && settings.welcomeMessage.isNotBlank() && globalScheduleOk) {
        val lastWelcome = lastWelcomeByConvMillis[convKey] ?: 0L
        val welcomeWindowMs = settings.welcomeCooldownMinutes.coerceAtLeast(1) * 60_000L
        if (lastWelcome == 0L || (now - lastWelcome) >= welcomeWindowMs) {
          val text = TemplateVariableResolver.resolve(
            template = settings.welcomeMessage,
            senderOrChatName = senderOrTitle,
            incomingMessage = trimmedText,
            timestampMillis = now,
            zoneId = zoneId,
          )
          if (text.isNotBlank()) {
            return AutoReplyDecision.Reply(
              ruleId = null,
              ruleName = "Mensaje de bienvenida",
              responseText = text,
              delaySeconds = 0,
              conversationKey = convKey,
            )
          }
        }
      }

      return AutoReplyDecision.Skip(
        status = AutoReplyLogStatus.SKIPPED_NO_MATCH,
        reason = "Ninguna regla coincidió con el mensaje entrante.",
        conversationKey = convKey,
      )
    }
  }

  /**
   * Debe invocarse únicamente cuando `RemoteInput` despachó la respuesta correctamente.
   */
  fun recordReplyDispatched(decision: AutoReplyDecision.Reply) {
    val now = clockMillis()
    synchronized(lock) {
      val bucket = when {
        decision.ruleId != null -> "${decision.conversationKey}#rule#${decision.ruleId}"
        decision.ruleName == "Mensaje de ausencia" -> "${decision.conversationKey}#away"
        else -> {
          lastWelcomeByConvMillis[decision.conversationKey] = now
          "${decision.conversationKey}#welcome"
        }
      }
      lastReplyByBucketMillis[bucket] = now
      replyCountByBucket[bucket] = (replyCountByBucket[bucket] ?: 0) + 1
      recentOutgoingReplies.addLast(RuleMatcher.normalizeInsensitive(decision.responseText) to now)
      purgeExpiredOutgoing(now)
    }
  }

  fun clearRuntimeState() {
    synchronized(lock) {
      processedFingerprints.clear()
      recentOutgoingReplies.clear()
      lastReplyByBucketMillis.clear()
      replyCountByBucket.clear()
      rapidIncomingTimestampsByConv.clear()
      lastWelcomeByConvMillis.clear()
    }
  }

  private fun purgeExpiredOutgoing(nowMillis: Long) {
    while (recentOutgoingReplies.isNotEmpty() && nowMillis - recentOutgoingReplies.first().second > 120_000L) {
      recentOutgoingReplies.removeFirst()
    }
  }

  private fun buildConversationKey(event: IncomingNotificationEvent): String =
    "${event.packageName}|${RuleMatcher.normalizeInsensitive(event.conversationTitle)}"

  private fun matchesTargetApp(pkg: String, target: AutoReplyTargetApp): Boolean = when (target) {
    AutoReplyTargetApp.BOTH -> true
    AutoReplyTargetApp.WHATSAPP_ONLY -> pkg == AutomationConfig.WHATSAPP_PACKAGE
    AutoReplyTargetApp.WHATSAPP_BUSINESS_ONLY -> pkg == AutomationConfig.WHATSAPP_BUSINESS_PACKAGE
  }
}

