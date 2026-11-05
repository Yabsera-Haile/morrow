package app.morrow.diary.security

import java.time.Duration

object PinRules {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    /** The PIN a fresh install starts with. Settings nags until it's changed. */
    const val STARTER_PIN = "1234"

    fun isValid(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }
}

/**
 * Five free tries, then a 30-second wait that doubles with every further miss (capped at an hour).
 * Ten thousand 4-digit PINs at that pace would take years.
 */
object PinAttemptPolicy {
    const val FREE_TRIES = 5
    private val FIRST_LOCKOUT: Duration = Duration.ofSeconds(30)
    private val MAX_LOCKOUT: Duration = Duration.ofHours(1)

    fun lockoutAfter(failures: Int): Duration {
        if (failures < FREE_TRIES) return Duration.ZERO
        val doublings = (failures - FREE_TRIES).coerceAtMost(20)
        return FIRST_LOCKOUT.multipliedBy(1L shl doublings).coerceAtMost(MAX_LOCKOUT)
    }

    fun triesLeft(failures: Int): Int = (FREE_TRIES - failures).coerceAtLeast(0)
}
