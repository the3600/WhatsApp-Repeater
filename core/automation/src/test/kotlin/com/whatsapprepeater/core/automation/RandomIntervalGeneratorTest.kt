package com.whatsapprepeater.core.automation

import com.whatsapprepeater.core.model.IntervalRange
import com.whatsapprepeater.core.model.SafetyLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RandomIntervalGeneratorTest {

  @Test
  fun `generates values strictly within configured inclusive range`() {
    val generator = RandomIntervalGenerator(Random(42))
    val range = IntervalRange(minSeconds = 15, maxSeconds = 35)

    repeat(200) {
      val value = generator.nextIntervalSeconds(range)
      assertTrue("Valor fuera de rango: $value", value in 15..35)
    }
  }

  @Test
  fun `returns exact value when min equals max`() {
    val generator = RandomIntervalGenerator(Random(1))
    val range = IntervalRange(minSeconds = 20, maxSeconds = 20)
    assertEquals(20, generator.nextIntervalSeconds(range))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `throws when minSeconds exceeds maxSeconds`() {
    val generator = RandomIntervalGenerator()
    generator.nextIntervalSeconds(IntervalRange(minSeconds = 40, maxSeconds = 20))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `throws when minSeconds is below safety minimum`() {
    val generator = RandomIntervalGenerator()
    generator.nextIntervalSeconds(
      IntervalRange(minSeconds = SafetyLimits.MIN_INTERVAL_SECONDS - 1, maxSeconds = 15),
    )
  }
}

