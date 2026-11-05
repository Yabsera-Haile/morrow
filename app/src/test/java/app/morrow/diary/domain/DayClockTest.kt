package app.morrow.diary.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DayClockTest {
    private val fourAm = LocalTime.of(4, 0)
    private val nineThirtyPm = LocalTime.of(21, 30)
    private val nov5 = LocalDate.of(2026, 11, 5)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) = LocalDateTime.of(date, LocalTime.of(hour, minute))

    @Test fun `a 1 AM check-in belongs to the day before`() {
        assertEquals(nov5, DayClock.logicalDate(at(nov5.plusDays(1), 1, 30), fourAm))
    }

    @Test fun `the new day starts exactly at the day-start time`() {
        assertEquals(nov5.minusDays(1), DayClock.logicalDate(at(nov5, 3, 59), fourAm))
        assertEquals(nov5, DayClock.logicalDate(at(nov5, 4, 0), fourAm))
    }

    @Test fun `with a midnight day start, dates are plain calendar dates`() {
        assertEquals(nov5, DayClock.logicalDate(at(nov5, 0, 1), LocalTime.MIDNIGHT))
    }

    @Test fun `phases follow morning, afternoon and the evening check-in time`() {
        assertEquals(DayPhase.MORNING, DayClock.phase(at(nov5, 7), fourAm, nineThirtyPm))
        assertEquals(DayPhase.AFTERNOON, DayClock.phase(at(nov5, 12), fourAm, nineThirtyPm))
        assertEquals(DayPhase.AFTERNOON, DayClock.phase(at(nov5, 21, 29), fourAm, nineThirtyPm))
        assertEquals(DayPhase.EVENING, DayClock.phase(at(nov5, 21, 30), fourAm, nineThirtyPm))
        assertEquals(DayPhase.EVENING, DayClock.phase(at(nov5, 2), fourAm, nineThirtyPm))
    }

    @Test fun `week start respects the chosen first day`() {
        // Thursday, Nov 5 2026
        assertEquals(LocalDate.of(2026, 11, 2), DayClock.weekStart(nov5, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 11, 1), DayClock.weekStart(nov5, DayOfWeek.SUNDAY))
        assertEquals(LocalDate.of(2026, 11, 2), DayClock.weekStart(LocalDate.of(2026, 11, 2), DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 11, 2), DayClock.weekStart(LocalDate.of(2026, 11, 8), DayOfWeek.MONDAY))
    }

    @Test fun `today uses the injected clock`() {
        val clock = DayClock { at(nov5.plusDays(1), 0, 45) }
        assertEquals(nov5, clock.today(fourAm))
    }
}
