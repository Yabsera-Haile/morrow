package app.morrow.diary

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morrow.diary.ui.navigation.MorrowRoot
import app.morrow.diary.ui.theme.Morrow
import app.morrow.diary.ui.theme.MorrowTheme

/** A FragmentActivity because the system fingerprint prompt (androidx.biometric) needs one. */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Hidden from Recents until the setting has loaded; then it follows "Hide in recent apps".
        setHiddenFromRecents(true)

        val container = (application as MorrowApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val current = settings ?: return@setContent // the window's paper background shows meanwhile
            LaunchedEffect(current.hideInRecents) { setHiddenFromRecents(current.hideInRecents) }
            MorrowTheme(current.theme) {
                SystemBarIcons()
                MorrowRoot(container, current)
            }
        }
    }

    /**
     * FLAG_SECURE blanks the app in the Recents screen and blocks screenshots. Debug builds skip
     * it so development screenshots work; installed (release) builds always follow the setting.
     */
    private fun setHiddenFromRecents(hide: Boolean) {
        if (BuildConfig.DEBUG) return
        if (hide) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
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
