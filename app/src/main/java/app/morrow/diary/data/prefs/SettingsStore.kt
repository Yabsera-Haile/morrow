package app.morrow.diary.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val store: DataStore<Preferences>) {

    val settings: Flow<MorrowSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::read)
        .distinctUntilChanged()

    suspend fun update(transform: (MorrowSettings) -> MorrowSettings) {
        store.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private fun read(p: Preferences): MorrowSettings {
        val d = MorrowSettings()
        return MorrowSettings(
            morningEnabled = p[MORNING_ON] ?: d.morningEnabled,
            morningTime = p[MORNING_AT]?.let(::minutesToTime) ?: d.morningTime,
            eveningEnabled = p[EVENING_ON] ?: d.eveningEnabled,
            eveningTime = p[EVENING_AT]?.let(::minutesToTime) ?: d.eveningTime,
            weeklyEnabled = p[WEEKLY_ON] ?: d.weeklyEnabled,
            weeklyDay = p[WEEKLY_DAY]?.let(DayOfWeek::of) ?: d.weeklyDay,
            weeklyTime = p[WEEKLY_AT]?.let(::minutesToTime) ?: d.weeklyTime,
            smartSkip = p[SMART_SKIP] ?: d.smartSkip,
            dayStartsAt = p[DAY_STARTS_AT]?.let(::minutesToTime) ?: d.dayStartsAt,
            weekStartsOn = p[WEEK_STARTS_ON]?.let(DayOfWeek::of) ?: d.weekStartsOn,
            theme = p[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: d.theme,
            showStats = p[SHOW_STATS] ?: d.showStats,
            showQuote = p[SHOW_QUOTE] ?: d.showQuote,
            quotes = p[QUOTES]?.let(::decodeQuotes) ?: d.quotes,
            fingerprintUnlock = p[FINGERPRINT] ?: d.fingerprintUnlock,
            autoLock = p[AUTO_LOCK]?.let { name -> AutoLock.entries.firstOrNull { it.name == name } } ?: d.autoLock,
            hideInRecents = p[HIDE_IN_RECENTS] ?: d.hideInRecents,
        )
    }

    private fun write(p: MutablePreferences, s: MorrowSettings) {
        p[MORNING_ON] = s.morningEnabled
        p[MORNING_AT] = timeToMinutes(s.morningTime)
        p[EVENING_ON] = s.eveningEnabled
        p[EVENING_AT] = timeToMinutes(s.eveningTime)
        p[WEEKLY_ON] = s.weeklyEnabled
        p[WEEKLY_DAY] = s.weeklyDay.value
        p[WEEKLY_AT] = timeToMinutes(s.weeklyTime)
        p[SMART_SKIP] = s.smartSkip
        p[DAY_STARTS_AT] = timeToMinutes(s.dayStartsAt)
        p[WEEK_STARTS_ON] = s.weekStartsOn.value
        p[THEME] = s.theme.name
        p[SHOW_STATS] = s.showStats
        p[SHOW_QUOTE] = s.showQuote
        p[QUOTES] = encodeQuotes(s.quotes)
        p[FINGERPRINT] = s.fingerprintUnlock
        p[AUTO_LOCK] = s.autoLock.name
        p[HIDE_IN_RECENTS] = s.hideInRecents
    }

    companion object {
        private val MORNING_ON = booleanPreferencesKey("morning_on")
        private val MORNING_AT = intPreferencesKey("morning_at")
        private val EVENING_ON = booleanPreferencesKey("evening_on")
        private val EVENING_AT = intPreferencesKey("evening_at")
        private val WEEKLY_ON = booleanPreferencesKey("weekly_on")
        private val WEEKLY_DAY = intPreferencesKey("weekly_day")
        private val WEEKLY_AT = intPreferencesKey("weekly_at")
        private val SMART_SKIP = booleanPreferencesKey("smart_skip")
        private val DAY_STARTS_AT = intPreferencesKey("day_starts_at")
        private val WEEK_STARTS_ON = intPreferencesKey("week_starts_on")
        private val THEME = stringPreferencesKey("theme")
        private val SHOW_STATS = booleanPreferencesKey("show_stats")
        private val SHOW_QUOTE = booleanPreferencesKey("show_quote")
        private val QUOTES = stringPreferencesKey("quotes")
        private val FINGERPRINT = booleanPreferencesKey("fingerprint_unlock")
        private val AUTO_LOCK = stringPreferencesKey("auto_lock")
        private val HIDE_IN_RECENTS = booleanPreferencesKey("hide_in_recents")

        /** Quotes are single lines, so a control character is a safe separator. */
        private const val QUOTE_SEPARATOR = '\u001E'

        internal fun encodeQuotes(quotes: List<String>): String =
            quotes.map { it.replace(QUOTE_SEPARATOR, ' ').trim() }.filter { it.isNotEmpty() }.joinToString(QUOTE_SEPARATOR.toString())

        internal fun decodeQuotes(text: String): List<String> =
            if (text.isEmpty()) emptyList() else text.split(QUOTE_SEPARATOR)

        private fun minutesToTime(minutes: Int): LocalTime = LocalTime.of((minutes / 60) % 24, minutes % 60)
        private fun timeToMinutes(time: LocalTime): Int = time.hour * 60 + time.minute
    }
}
