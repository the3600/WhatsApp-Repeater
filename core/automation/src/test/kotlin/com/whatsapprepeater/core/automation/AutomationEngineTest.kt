package com.whatsapprepeater.core.automation

import com.whatsapprepeater.core.model.AutomationConfig
import com.whatsapprepeater.core.model.AutomationStatus
import com.whatsapprepeater.core.model.ContactInfo
import com.whatsapprepeater.core.model.ExecutionFinalStatus
import com.whatsapprepeater.core.model.ExecutionRecord
import com.whatsapprepeater.core.model.IntervalRange
import com.whatsapprepeater.core.model.MessageMode
import com.whatsapprepeater.core.model.MessageTraceStatus
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
    var foregroundPackage: String = AutomationConfig.WHATSAPP_PACKAGE
    var typedText: String = ""
    val sentMessages = mutableListOf<String>()
    var forceCaptcha = false
    var clearInputOnSend = true
    var onAfterFirstSend: (() -> Unit)? = null

    override fun openWhatsAppChat(contact: ContactInfo, targetPackage: String): Boolean = true
    override fun openWhatsAppHome(targetPackage: String): Boolean = true

    override fun captureScreenSnapshot(): ScreenSnapshot {
      if (foregroundPackage != AutomationConfig.WHATSAPP_PACKAGE &&
        foregroundPackage != AutomationConfig.WHATSAPP_BUSINESS_PACKAGE
      ) {
        return ScreenSnapshot(
          foregroundPackage = foregroundPackage,
          screenWidth = 1080,
          screenHeight = 1920,
          root = UiNodeSnapshot(nodeId = "other_root", packageName = foregroundPackage),
        )
      }

      if (forceCaptcha) {
        return ScreenSnapshot(
          foregroundPackage = foregroundPackage,
          screenWidth = 1080,
          screenHeight = 1920,
          root = UiNodeSnapshot(
            nodeId = "root",
            packageName = foregroundPackage,
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
        foregroundPackage = foregroundPackage,
        screenWidth = 1080,
        screenHeight = 1920,
        root = UiNodeSnapshot(
          nodeId = "root",
          packageName = foregroundPackage,
          children = children,
        ),
      )
    }

    override fun clickNode(nodeId: String): Boolean {
      if (nodeId == "send" && typedText.isNotEmpty()) {
        if (clearInputOnSend) {
          sentMessages.add(typedText)
          typedText = ""
          if (sentMessages.size == 1) {
            onAfterFirstSend?.invoke()
          }
        }
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
  fun `completes full automation flow and logs execution record with confirmed traces`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val driver = FakeAccessibilityDriver()
    val records = mutableListOf<ExecutionRecord>()
    val engine = AutomationEngine(
      driver = driver,
      intervalGenerator = RandomIntervalGenerator(Random(7)),
      onExecutionFinished = { records.add(it) },
      delayProvider = { },
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
    assertTrue(records.first().messageTraces.all { it.status == MessageTraceStatus.CONFIRMED })
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

  @Test
  fun `aborts safely if input box does not clear after clicking send`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val driver = FakeAccessibilityDriver().apply { clearInputOnSend = false }
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

    assertEquals(0, engine.state.value.sentConfirmed)
    assertEquals(AutomationStatus.ERROR, engine.state.value.status)
    assertEquals(ExecutionFinalStatus.FAILED_SAFETY_CHECK, records.first().finalStatus)
    assertEquals(MessageTraceStatus.FAILED, records.first().messageTraces.first().status)
  }

  @Test
  fun `emergency stop after first message purges remaining queue as cancelled`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val driver = FakeAccessibilityDriver()
    val records = mutableListOf<ExecutionRecord>()
    lateinit var engine: AutomationEngine
    engine = AutomationEngine(
      driver = driver,
      onExecutionFinished = { records.add(it) },
      delayProvider = { },
      scope = TestScope(dispatcher),
    )
    driver.onAfterFirstSend = { engine.stop() }

    engine.start(
      AutomationConfig(
        contact = contact,
        mode = MessageMode.MultipleMessages(listOf("Uno", "Dos", "Tres")),
        intervalRange = IntervalRange(10, 15),
        userConfirmed = true,
      ),
    )
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf("Uno"), driver.sentMessages)
    assertEquals(AutomationStatus.STOPPED, engine.state.value.status)
    assertTrue(engine.state.value.finishedAtMillis > 0L)
    assertEquals(1, records.size)
    val rec = records.first()
    assertEquals(ExecutionFinalStatus.STOPPED_BY_USER, rec.finalStatus)
    assertEquals(1, rec.confirmedCount)
    assertEquals(2, rec.cancelledCount)
    assertEquals(MessageTraceStatus.CONFIRMED, rec.messageTraces[0].status)
    assertEquals(MessageTraceStatus.CANCELLED, rec.messageTraces[1].status)
    assertEquals(MessageTraceStatus.CANCELLED, rec.messageTraces[2].status)
  }

  @Test
  fun `aborts when foreground switches to an external app before next message`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val driver = FakeAccessibilityDriver()
    val records = mutableListOf<ExecutionRecord>()
    val engine = AutomationEngine(
      driver = driver,
      onExecutionFinished = { records.add(it) },
      delayProvider = { },
      scope = TestScope(dispatcher),
    )
    driver.onAfterFirstSend = { driver.foregroundPackage = "com.android.settings" }

    engine.start(
      AutomationConfig(
        contact = contact,
        mode = MessageMode.RepeatSingleMessage("Hola", 2),
        intervalRange = IntervalRange(10, 15),
        userConfirmed = true,
      ),
    )
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(listOf("Hola"), driver.sentMessages)
    assertEquals(AutomationStatus.ERROR, engine.state.value.status)
    assertEquals(ExecutionFinalStatus.FAILED_SAFETY_CHECK, records.first().finalStatus)
  }

  @Test
  fun `stop followed immediately by resetToIdle preserves accurate execution history counts`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val driver = FakeAccessibilityDriver()
    val records = mutableListOf<ExecutionRecord>()
    lateinit var engine: AutomationEngine
    engine = AutomationEngine(
      driver = driver,
      onExecutionFinished = { records.add(it) },
      delayProvider = { },
      scope = TestScope(dispatcher),
    )
    driver.onAfterFirstSend = {
      engine.stop()
      engine.resetToIdle()
    }

    engine.start(
      AutomationConfig(
        contact = contact,
        mode = MessageMode.MultipleMessages(listOf("Msg 1", "Msg 2", "Msg 3")),
        intervalRange = IntervalRange(10, 15),
        userConfirmed = true,
      ),
    )
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, records.size)
    val rec = records.first()
    assertEquals(3, rec.scheduledCount)
    assertEquals(1, rec.confirmedCount)
    assertEquals(2, rec.cancelledCount)
    assertEquals(ExecutionFinalStatus.STOPPED_BY_USER, rec.finalStatus)
  }

  @Test
  fun `rejects oversized 1000 message payload above safety limit without launching`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val driver = FakeAccessibilityDriver()
    val records = mutableListOf<ExecutionRecord>()
    val engine = AutomationEngine(
      driver = driver,
      onExecutionFinished = { records.add(it) },
      delayProvider = { },
      scope = TestScope(dispatcher),
    )

    val oversizedBatch = (1..1000).map { "Mensaje #$it" }
    engine.start(
      AutomationConfig(
        contact = contact,
        mode = MessageMode.MultipleMessages(oversizedBatch),
        intervalRange = IntervalRange(5, 6),
        userConfirmed = true,
      ),
    )
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(0, driver.sentMessages.size)
    assertEquals(0, records.size)
    assertEquals(AutomationStatus.ERROR, engine.state.value.status)
  }

  @Test
  fun `stress test 20x50 sequential messages complete across 1000 total dispatches without race`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val driver = FakeAccessibilityDriver()
    val records = mutableListOf<ExecutionRecord>()
    val engine = AutomationEngine(
      driver = driver,
      onExecutionFinished = { records.add(it) },
      delayProvider = { },
      scope = TestScope(dispatcher),
    )

    val batch = (1..50).map { "Mensaje #$it" }
    val config = AutomationConfig(
      contact = contact,
      mode = MessageMode.MultipleMessages(batch),
      intervalRange = IntervalRange(5, 6),
      userConfirmed = true,
    )

    repeat(20) {
      // Repeated start calls must not double-launch
      engine.start(config)
      engine.start(config)
      dispatcher.scheduler.advanceUntilIdle()
      assertEquals(AutomationStatus.COMPLETED, engine.state.value.status)
      engine.resetToIdle()
    }

    assertEquals(1000, driver.sentMessages.size)
    assertEquals(20, records.size)
    assertTrue(records.all { it.scheduledCount == 50 && it.confirmedCount == 50 && it.messageTraces.size == 50 })
  }
}
