package com.whatsapprepeater.app.service

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.whatsapprepeater.core.automation.AccessibilityDriver
import com.whatsapprepeater.core.automation.NodeBounds
import com.whatsapprepeater.core.automation.ScreenSnapshot
import com.whatsapprepeater.core.automation.UiNodeSnapshot
import com.whatsapprepeater.core.model.AutomationConfig
import com.whatsapprepeater.core.model.ContactInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Puente concreto entre [AutomationEngine] y [AutomationAccessibilityService].
 */
class AndroidAccessibilityDriver(
  private val appContext: Context,
) : AccessibilityDriver {

  private val _isServiceConnected = MutableStateFlow(false)
  override val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

  @Volatile
  private var serviceRef: AutomationAccessibilityService? = null

  private val nodesLock = Any()
  @Volatile
  private var activeNodes: Map<String, AccessibilityNodeInfo> = emptyMap()

  fun attachService(service: AutomationAccessibilityService) {
    serviceRef = service
    _isServiceConnected.value = true
  }

  fun detachService(service: AutomationAccessibilityService) {
    if (serviceRef === service) {
      serviceRef = null
      synchronized(nodesLock) {
        activeNodes = emptyMap()
      }
      _isServiceConnected.value = false
    }
  }

  override fun openWhatsAppChat(contact: ContactInfo, targetPackage: String): Boolean {
    val digits = contact.normalizedDigits
    if (digits.isEmpty()) return openWhatsAppHome(targetPackage)

    val packagesToTry = listOf(
      targetPackage,
      AutomationConfig.WHATSAPP_PACKAGE,
      AutomationConfig.WHATSAPP_BUSINESS_PACKAGE,
    ).distinct()

    for (pkg in packagesToTry) {
      if (!isPackageInstalled(pkg)) continue
      val deepLink = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("https://api.whatsapp.com/send?phone=$digits")
        setPackage(pkg)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
      }
      try {
        appContext.startActivity(deepLink)
        return true
      } catch (_: ActivityNotFoundException) {
        // Probar siguiente paquete o abrir pantalla principal.
      } catch (_: SecurityException) {
        // Probar siguiente paquete o abrir pantalla principal.
      }
    }
    return openWhatsAppHome(targetPackage)
  }

  override fun openWhatsAppHome(targetPackage: String): Boolean {
    val packagesToTry = listOf(
      targetPackage,
      AutomationConfig.WHATSAPP_PACKAGE,
      AutomationConfig.WHATSAPP_BUSINESS_PACKAGE,
    ).distinct()

    for (pkg in packagesToTry) {
      val launchIntent = appContext.packageManager.getLaunchIntentForPackage(pkg) ?: continue
      launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
      try {
        appContext.startActivity(launchIntent)
        return true
      } catch (_: Exception) {
        // Intentar el siguiente paquete disponible.
      }
    }
    return false
  }

  override fun captureScreenSnapshot(): ScreenSnapshot? {
    val service = serviceRef ?: return null
    val rootNode = try {
      service.rootInActiveWindow
    } catch (_: Exception) {
      null
    } ?: return null

    val metrics = appContext.resources.displayMetrics
    val activePkg = rootNode.packageName?.toString() ?: service.lastForegroundPackage
    if (activePkg != null &&
      activePkg != AutomationConfig.WHATSAPP_PACKAGE &&
      activePkg != AutomationConfig.WHATSAPP_BUSINESS_PACKAGE
    ) {
      // Nunca recorrer ni almacenar nodos de aplicaciones externas fuera de WhatsApp.
      synchronized(nodesLock) {
        activeNodes = emptyMap()
      }
      return ScreenSnapshot(
        foregroundPackage = activePkg,
        screenWidth = metrics.widthPixels,
        screenHeight = metrics.heightPixels,
        root = null,
      )
    }

    val nodeRegistry = HashMap<String, AccessibilityNodeInfo>()
    var counter = 0

    fun mapNode(node: AccessibilityNodeInfo, depth: Int): UiNodeSnapshot? {
      if (depth > 24 || counter >= 350) return null
      return runCatching {
        val id = "n_${counter++}"
        nodeRegistry[id] = node

        val rect = Rect()
        node.getBoundsInScreen(rect)

        val children = buildList {
          val childCount = node.childCount
          for (i in 0 until childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            mapNode(child, depth + 1)?.let { add(it) }
          }
        }

        UiNodeSnapshot(
          nodeId = id,
          packageName = node.packageName?.toString(),
          className = node.className?.toString(),
          viewIdResourceName = node.viewIdResourceName,
          text = node.text?.toString(),
          contentDescription = node.contentDescription?.toString(),
          hintText = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            node.hintText?.toString()
          } else {
            null
          },
          isClickable = node.isClickable,
          isEditable = node.isEditable,
          isEnabled = node.isEnabled,
          isVisibleToUser = node.isVisibleToUser,
          bounds = NodeBounds(rect.left, rect.top, rect.right, rect.bottom),
          children = children,
        )
      }.getOrNull()
    }

    val mappedRoot = mapNode(rootNode, 0)
    synchronized(nodesLock) {
      activeNodes = nodeRegistry
    }

    return ScreenSnapshot(
      foregroundPackage = runCatching { rootNode.packageName?.toString() }.getOrNull() ?: service.lastForegroundPackage,
      screenWidth = metrics.widthPixels,
      screenHeight = metrics.heightPixels,
      root = mappedRoot,
    )
  }

  override fun clickNode(nodeId: String): Boolean {
    val node = synchronized(nodesLock) { activeNodes[nodeId] } ?: return false
    return runCatching {
      if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
        return@runCatching true
      }
      var parent = node.parent
      var hops = 0
      while (parent != null && hops < 4) {
        if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
          return@runCatching true
        }
        parent = parent.parent
        hops++
      }
      false
    }.getOrDefault(false)
  }

  override fun setText(nodeId: String, text: String): Boolean {
    val node = synchronized(nodesLock) { activeNodes[nodeId] } ?: return false
    return runCatching {
      node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
      val args = Bundle().apply {
        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
      }
      node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }.getOrDefault(false)
  }

  override fun navigateBack(): Boolean {
    val service = serviceRef ?: return false
    return runCatching {
      service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }.getOrDefault(false)
  }

  override fun clearNodeCache() {
    synchronized(nodesLock) {
      activeNodes = emptyMap()
    }
  }

  fun isWhatsAppInstalled(): Boolean =
    isPackageInstalled(AutomationConfig.WHATSAPP_PACKAGE) ||
      isPackageInstalled(AutomationConfig.WHATSAPP_BUSINESS_PACKAGE)

  private fun isPackageInstalled(packageName: String): Boolean {
    return try {
      appContext.packageManager.getPackageInfo(packageName, 0)
      true
    } catch (_: Exception) {
      false
    }
  }
}

