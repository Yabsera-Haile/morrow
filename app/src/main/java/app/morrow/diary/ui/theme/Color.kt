package app.morrow.diary.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The "warm paper" palette. Every text colour passes WCAG AA on the surfaces it is used on. */
@Immutable
data class MorrowColors(
    val paper: Color,
    val card: Color,
    val group: Color,
    val inputSurface: Color,
    val hairline: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val done: Color,
    val onDone: Color,
    val emptyRing: Color,
    val strike: Color,
    val isDark: Boolean,
)

val LightMorrowColors = MorrowColors(
    paper = Color(0xFFF6F1E7),
    card = Color(0xFFEFE7D9),
    group = Color(0xFFFBF8F2),
    inputSurface = Color(0xFFFCFAF5),
    hairline = Color(0xFFE2D8C8),
    ink = Color(0xFF2A2520),
    muted = Color(0xFF6B6158),
    accent = Color(0xFFA8492A),
    onAccent = Color(0xFFFCFAF5),
    done = Color(0xFF4F6B4A),
    onDone = Color(0xFFF6F1E7),
    emptyRing = Color(0xFF8A7F73),
    strike = Color(0xFFB5AA9C),
    isDark = false,
)

val DarkMorrowColors = MorrowColors(
    paper = Color(0xFF1C1A17),
    card = Color(0xFF2A2622),
    group = Color(0xFF24211D),
    inputSurface = Color(0xFF26231F),
    hairline = Color(0xFF3A352F),
    ink = Color(0xFFEDE6DA),
    muted = Color(0xFFA89E91),
    accent = Color(0xFFE08A64),
    onAccent = Color(0xFF1C1A17),
    done = Color(0xFF8FB088),
    onDone = Color(0xFF1C1A17),
    emptyRing = Color(0xFF7D746A),
    strike = Color(0xFF6B635A),
    isDark = true,
)

val LocalMorrowColors = staticCompositionLocalOf { LightMorrowColors }
