package app.morrow.diary

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morrow.diary.ui.navigation.MorrowNavHost
import app.morrow.diary.ui.theme.Morrow
import app.morrow.diary.ui.theme.MorrowTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Keeps the diary out of the Recents preview and blocks screenshots.
        // Debug builds skip it so development screenshots work; the lock milestone wires this
        // to the "Hide in recent apps" setting.
        if (!BuildConfig.DEBUG) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }

        val container = (application as MorrowApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val current = settings ?: return@setContent // the window's paper background shows meanwhile
            MorrowTheme(current.theme) {
                SystemBarIcons()
                MorrowNavHost(container)
            }
        }
    }
}

/** Dark status-bar icons on paper, light ones on charcoal, even when the theme overrides the system. */
@Composable
private fun SystemBarIcons() {
    val view = LocalView.current
    val lightBackground = !Morrow.colors.isDark
    SideEffect {
        val window = (view.context as ComponentActivity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightBackground
            isAppearanceLightNavigationBars = lightBackground
        }
    }
}
