package com.whatsapprepeater.core.automation

import com.whatsapprepeater.core.model.AutomationConfig
import com.whatsapprepeater.core.model.AutomationStatus
import com.whatsapprepeater.core.model.ContactInfo
import com.whatsapprepeater.core.model.ExecutionFinalStatus
import com.whatsapprepeater.core.model.ExecutionRecord
import com.whatsapprepeater.core.model.IntervalRange
import com.whatsapprepeater.core.model.MessageMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class AutomationEngineTest {

  private class FakeAccessibilityDriver(
    initialHeader: String = "Ana Gómez",
  ) : AccessibilityDriver {
    override val isServiceConnected: StateFlow<Boolean> = MutableStateFlow(true)
    var headerText: String = initialHeader
    var typedText: String = ""
    val sentMessages = mutableListOf<String>()
    var forceCaptcha = false

    override fun openWhatsAppChat(contact: ContactInfo, targetPackage: String): Boolean = true
    override fun openWhatsAppHome(targetPackage: String): Boolean = true

    override fun captureScreenSnapshot(): ScreenSnapshot {
      if (forceCaptcha) {
        return ScreenSnapshot(
          foregroundPackage = AutomationConfig.WHATSAPP_PACKAGE,
          screenWidth = 1080,
          screenHeight = 1920,
          root = UiNodeSnapshot(
            nodeId = "root",
            packageName = AutomationConfig.WHATSAPP_PACKAGE,
            children = listOf(
              UiNodeSnapshot(
                nodeId = "captcha",
                text = "CAPTCHA verification required",
                bounds = NodeBounds(100, 300, 900, 420),
              ),
            ),
          ),
        )
      }

      val children = mutableListOf(
        UiNodeSnapshot(
          nodeId = "header",
          text = headerText,
          bounds = NodeBounds(120, 60, 700, 140),
        ),
        UiNodeSnapshot(
          nodeId = "input",
          className = "android.widget.EditText",
          hintText = "Mensaje",
          text = typedText,
          isEditable = true,
          bounds = NodeBounds(60, 1750, 860, 1870),
        ),
      )

      if (typedText.isNotEmpty()) {
        children.add(
          UiNodeSnapshot(
            nodeId = "send",
            contentDescription = "Enviar",
            isClickable = true,
            isEnabled = true,
            bounds = NodeBounds(880, 1750, 1020, 1870),
          ),
        )
      }

      return ScreenSnapshot(
        foregroundPackage = AutomationConfig.WHATSAPP_PACKAGE,
        screenWidth = 1080,
        screenHeight = 1920,
        root = UiNodeSnapshot(
          nodeId = "root",
          packageName = AutomationConfig.WHATSAPP_PACKAGE,
          children = children,
        ),
      )
    }

    override fun clickNode(nodeId: String): Boolean {
      if (nodeId == "send" && typedText.isNotEmpty()) {
        sentMessages.add(typedText)
        typedText = ""
        return true
      }
      return false
    }

    override fun setText(nodeId: String, text: String): Boolean {
      if (nodeId == "input") {
        typedText = text
        return true
      }
      return false
    }

    override fun navigateBack(): Boolean = true
  }

  private val contact = ContactInfo(id = "1", name = "Ana Gómez", phoneNumber = "+57 310 555 0000")

  @Test
  fun `completes full automation flow and logs execution record`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val driver = FakeAccessibilityDriver()
    val records = mutableListOf<ExecutionRecord>()
    val engine = AutomationEngine(
      driver = driver,
      intervalGenerator = RandomIntervalGenerator(Random(7)),
      onExecutionFinished = { records.add(it) },
      delayProvider = { /* sin espera real en test */ },
      scope = TestScope(dispatcher),
    )

    val config = AutomationConfig(
      contact = contact,
      mode = MessageMode.MultipleMessages(listOf("Mensaje 1", "Mensaje 2")),
      intervalRange = IntervalRange(minSeconds = 10, maxSeconds = 15),
      userConfirmed = true,
    )

    engine.start(config)
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf("Mensaje 1", "Mensaje 2"), driver.sentMessages)
    assertEquals(AutomationStatus.COMPLETED, engine.state.value.status)
    assertEquals(2, engine.state.value.sentConfirmed)
    assertEquals(0, engine.state.value.pendingCount)
    assertEquals(1, records.size)
    assertEquals(ExecutionFinalStatus.COMPLETED, records.first().finalStatus)
  }

  @Test
  fun `stops safely when recipient header does not match`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val driver = FakeAccessibilityDriver(initialHeader = "Contacto Desconocido")
    val records = mutableListOf<ExecutionRecord>()
    val engine = AutomationEngine(
      driver = driver,
      onExecutionFinished = { records.add(it) },
      delayProvider = { },
      scope = TestScope(dispatcher),
    )

    engine.start(
      AutomationConfig(
        contact = contact,
        mode = MessageMode.RepeatSingleMessage("Hola", 2),
        intervalRange = IntervalRange(10, 15),
        userConfirmed = true,
      ),
    )
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(driver.sentMessages.isEmpty())
    assertEquals(AutomationStatus.ERROR, engine.state.value.status)
    assertEquals(ExecutionFinalStatus.FAILED_SAFETY_CHECK, records.first().finalStatus)
  }

  @Test
  fun `refuses to start without explicit user confirmation`() = runTest {
    val driver = FakeAccessibilityDriver()
    val engine = AutomationEngine(driver = driver, delayProvider = { })

    engine.start(
      AutomationConfig(
        contact = contact,
        mode = MessageMode.RepeatSingleMessage("Hola", 1),
        intervalRange = IntervalRange(10, 15),
        userConfirmed = false,
      ),
    )

    assertEquals(AutomationStatus.ERROR, engine.state.value.status)
    assertTrue(driver.sentMessages.isEmpty())
  }

  @Test
  fun `stops immediately when captcha or security screen appears`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val driver = FakeAccessibilityDriver().apply { forceCaptcha = true }
    val records = mutableListOf<ExecutionRecord>()
    val engine = AutomationEngine(
      driver = driver,
      onExecutionFinished = { records.add(it) },
      delayProvider = { },
      scope = TestScope(dispatcher),
    )

    engine.start(
      AutomationConfig(
        contact = contact,
        mode = MessageMode.RepeatSingleMessage("Hola", 2),
        intervalRange = IntervalRange(10, 15),
        userConfirmed = true,
      ),
    )
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(driver.sentMessages.isEmpty())
    assertEquals(AutomationStatus.ERROR, engine.state.value.status)
    assertEquals(ExecutionFinalStatus.FAILED_SAFETY_CHECK, records.first().finalStatus)
  }
}

