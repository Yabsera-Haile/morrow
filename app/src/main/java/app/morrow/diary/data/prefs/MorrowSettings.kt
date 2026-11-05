package app.morrow.diary.data.prefs

import java.time.DayOfWeek
import java.time.LocalTime

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AutoLock(val seconds: Long) {
    IMMEDIATELY(0),
    THIRTY_SECONDS(30),
    ONE_MINUTE(60),
    FIVE_MINUTES(300),
}

/**
 * Everything the user can set. Nothing in here is diary content, which is why it lives in the
 * plain preferences file and can be read while the app is locked (e.g. the lock screen quote).
 */
data class MorrowSettings(
    val morningEnabled: Boolean = true,
    val morningTime: LocalTime = LocalTime.of(7, 0),
    val eveningEnabled: Boolean = true,
    val eveningTime: LocalTime = LocalTime.of(21, 30),
    val weeklyEnabled: Boolean = true,
    val weeklyDay: DayOfWeek = DayOfWeek.SUNDAY,
    val weeklyTime: LocalTime = LocalTime.of(19, 0),
    val smartSkip: Boolean = true,
    val dayStartsAt: LocalTime = LocalTime.of(4, 0),
    val weekStartsOn: DayOfWeek = DayOfWeek.MONDAY,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val showStats: Boolean = true,
    val showQuote: Boolean = true,
    val quotes: List<String> = DefaultQuotes.all,
    val fingerprintUnlock: Boolean = true,
    val autoLock: AutoLock = AutoLock.ONE_MINUTE,
    val hideInRecents: Boolean = true,
)

object DefaultQuotes {
    val all = listOf(
        "Admire, then aim.",
        "I’m not late. I’m on my own clock.",
        "Slow is still forward.",
        "Unfinished isn’t failed. It’s carried over.",
        "Plan it at night. Live it by day.",
        "Rest is part of the plan.",
        "One thing done beats five things maybe.",
        "My pace is not a delay.",
        "Today is a draft. Tomorrow I edit.",
    )
}
