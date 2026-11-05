package app.morrow.diary.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class PinPolicyTest {
    @Test fun `PINs are 4 to 8 digits`() {
        assertTrue(PinRules.isValid("1234"))
        assertTrue(PinRules.isValid("12345678"))
        assertFalse(PinRules.isValid("123"))
        assertFalse(PinRules.isValid("123456789"))
        assertFalse(PinRules.isValid("12a4"))
        assertFalse(PinRules.isValid(""))
    }

    @Test fun `five free tries, then 30 seconds that doubles`() {
        (0..4).forEach { assertEquals(Duration.ZERO, PinAttemptPolicy.lockoutAfter(it)) }
        assertEquals(Duration.ofSeconds(30), PinAttemptPolicy.lockoutAfter(5))
        assertEquals(Duration.ofSeconds(60), PinAttemptPolicy.lockoutAfter(6))
        assertEquals(Duration.ofSeconds(120), PinAttemptPolicy.lockoutAfter(7))
    }

    @Test fun `the wait is capped at an hour`() {
        assertEquals(Duration.ofHours(1), PinAttemptPolicy.lockoutAfter(30))
        assertEquals(Duration.ofHours(1), PinAttemptPolicy.lockoutAfter(1_000))
    }

    @Test fun `tries left counts down and stops at zero`() {
        assertEquals(5, PinAttemptPolicy.triesLeft(0))
        assertEquals(1, PinAttemptPolicy.triesLeft(4))
        assertEquals(0, PinAttemptPolicy.triesLeft(9))
    }
}
