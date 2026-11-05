package app.morrow.diary.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.DayOfWeek
import java.time.LocalTime

class SettingsStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.newStore() = SettingsStore(
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "settings.preferences_pb") }),
    )

    @Test fun `an empty store gives the defaults`() = runTest {
        val settings = newStore().settings.first()
        assertEquals(MorrowSettings(), settings)
        assertEquals(LocalTime.of(7, 0), settings.morningTime)
        assertEquals(LocalTime.of(4, 0), settings.dayStartsAt)
        assertEquals(9, settings.quotes.size)
    }

    @Test fun `changes are saved and read back`() = runTest {
        val store = newStore()
        store.update {
            it.copy(
                theme = ThemeMode.DARK,
                eveningTime = LocalTime.of(22, 15),
                weeklyDay = DayOfWeek.FRIDAY,
                showQuote = false,
                quotes = listOf("Admire, then aim.", "Rest is part of the plan."),
                autoLock = AutoLock.FIVE_MINUTES,
            )
        }
        val s = store.settings.first()
        assertEquals(ThemeMode.DARK, s.theme)
        assertEquals(LocalTime.of(22, 15), s.eveningTime)
        assertEquals(DayOfWeek.FRIDAY, s.weeklyDay)
        assertEquals(false, s.showQuote)
        assertEquals(listOf("Admire, then aim.", "Rest is part of the plan."), s.quotes)
        assertEquals(AutoLock.FIVE_MINUTES, s.autoLock)
        assertEquals(LocalTime.of(7, 0), s.morningTime) // untouched values keep their defaults
    }

    @Test fun `deleting every quote leaves an empty list, not the defaults`() = runTest {
        val store = newStore()
        store.update { it.copy(quotes = emptyList()) }
        assertEquals(emptyList<String>(), store.settings.first().quotes)
    }

    @Test fun `quote encoding drops blanks and survives punctuation`() {
        val quotes = listOf("I’m not late. I’m on my own clock.", "  ", "Plan it at night. Live it by day.")
        val decoded = SettingsStore.decodeQuotes(SettingsStore.encodeQuotes(quotes))
        assertEquals(listOf(quotes[0], quotes[2]), decoded)
    }
}
