package app.morrow.diary.domain

import java.time.LocalDate

/** One quote per diary day, moving through the list in order and wrapping around. */
object QuoteOfTheDay {
    fun pick(date: LocalDate, quotes: List<String>): String? {
        if (quotes.isEmpty()) return null
        return quotes[Math.floorMod(date.toEpochDay(), quotes.size.toLong()).toInt()]
    }
}
