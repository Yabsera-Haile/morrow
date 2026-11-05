package app.morrow.diary.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import app.morrow.diary.data.prefs.ThemeMode

@Composable
fun MorrowTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val c = if (dark) DarkMorrowColors else LightMorrowColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            secondary = c.done, onSecondary = c.onDone,
            background = c.paper, onBackground = c.ink,
            surface = c.paper, onSurface = c.ink,
            surfaceVariant = c.card, onSurfaceVariant = c.muted,
            surfaceContainer = c.card, surfaceContainerHigh = c.card,
            outline = c.emptyRing, outlineVariant = c.hairline,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            secondary = c.done, onSecondary = c.onDone,
            background = c.paper, onBackground = c.ink,
            surface = c.paper, onSurface = c.ink,
            surfaceVariant = c.card, onSurfaceVariant = c.muted,
            surfaceContainer = c.card, surfaceContainerHigh = c.card,
            outline = c.emptyRing, outlineVariant = c.hairline,
        )
    }
    CompositionLocalProvider(LocalMorrowColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = MorrowMaterialTypography, content = content)
    }
}

/** Shorthand: `Morrow.colors.accent`. */
object Morrow {
    val colors: MorrowColors
        @Composable @ReadOnlyComposable
        get() = LocalMorrowColors.current
}
