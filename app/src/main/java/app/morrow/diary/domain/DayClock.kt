package app.morrow.diary.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

enum class DayPhase { MORNING, AFTERNOON, EVENING }

/**
 * Turns wall-clock time into diary time.
 *
 * A diary day starts at [dayStartsAt] (default 4 AM), not midnight, so writing the evening
 * check-in at 1 AM still counts for the day before.
 */
class DayClock(private val nowProvider: () -> LocalDateTime = { LocalDateTime.now() }) {

    fun now(): LocalDateTime = nowProvider()

    fun today(dayStartsAt: LocalTime): LocalDate = logicalDate(now(), dayStartsAt)

    companion object {
        fun logicalDate(at: LocalDateTime, dayStartsAt: LocalTime): LocalDate =
            if (at.toLocalTime() < dayStartsAt) at.toLocalDate().minusDays(1) else at.toLocalDate()

        /** Morning until noon, evening from the evening check-in time (and after midnight). */
        fun phase(at: LocalDateTime, dayStartsAt: LocalTime, eveningFrom: LocalTime): DayPhase {
            val t = at.toLocalTime()
            return when {
                t < dayStartsAt -> DayPhase.EVENING
                t >= eveningFrom -> DayPhase.EVENING
                t < LocalTime.NOON -> DayPhase.MORNING
                else -> DayPhase.AFTERNOON
            }
        }

        fun weekStart(date: LocalDate, weekStartsOn: DayOfWeek): LocalDate =
            date.with(TemporalAdjusters.previousOrSame(weekStartsOn))
    }
}
