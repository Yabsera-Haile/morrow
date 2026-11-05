package app.morrow.diary.domain

import app.morrow.diary.data.prefs.DefaultQuotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class QuoteOfTheDayTest {
    private val quotes = DefaultQuotes.all
    private val nov5 = LocalDate.of(2026, 11, 5)

    @Test fun `the same day always gets the same quote`() {
        assertEquals(QuoteOfTheDay.pick(nov5, quotes), QuoteOfTheDay.pick(LocalDate.of(2026, 11, 5), quotes))
    }

    @Test fun `the next day moves to the next quote and the list wraps around`() {
        val todayIndex = quotes.indexOf(QuoteOfTheDay.pick(nov5, quotes))
        val tomorrow = QuoteOfTheDay.pick(nov5.plusDays(1), quotes)
        assertEquals(quotes[(todayIndex + 1) % quotes.size], tomorrow)
        assertEquals(QuoteOfTheDay.pick(nov5, quotes), QuoteOfTheDay.pick(nov5.plusDays(quotes.size.toLong()), quotes))
    }

    @Test fun `every quote shows up within one cycle`() {
        val seen = (0 until quotes.size).map { QuoteOfTheDay.pick(nov5.plusDays(it.toLong()), quotes) }.toSet()
        assertEquals(quotes.toSet(), seen)
    }

    @Test fun `two quotes alternate`() {
        val two = listOf("Admire, then aim.", "I’m not late. I’m on my own clock.")
        assertNotEquals(QuoteOfTheDay.pick(nov5, two), QuoteOfTheDay.pick(nov5.plusDays(1), two))
    }

    @Test fun `no quotes means no quote`() {
        assertNull(QuoteOfTheDay.pick(nov5, emptyList()))
    }
}
