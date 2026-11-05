package app.morrow.diary.ui.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.morrow.diary.AppContainer
import app.morrow.diary.R
import app.morrow.diary.data.DiarySession
import app.morrow.diary.data.prefs.MorrowSettings
import app.morrow.diary.security.LockState
import app.morrow.diary.ui.journal.JournalScreen
import app.morrow.diary.ui.lock.ChangePinScreen
import app.morrow.diary.ui.lock.LockScreen
import app.morrow.diary.ui.lock.canUseFingerprint
import app.morrow.diary.ui.lock.enrollFingerprint
import app.morrow.diary.ui.settings.SettingsScreen
import app.morrow.diary.ui.settings.SettingsViewModel
import app.morrow.diary.ui.theme.Morrow
import app.morrow.diary.ui.theme.MorrowType
import app.morrow.diary.ui.today.TodayScreen
import app.morrow.diary.ui.today.TodayViewModel
import app.morrow.diary.ui.week.WeekScreen
import kotlinx.coroutines.launch

object Routes {
    const val TODAY = "today"
    const val JOURNAL = "journal"
    const val WEEK = "week"
    const val SETTINGS = "settings"
    const val CHANGE_PIN = "change_pin?reset={reset}"

    fun changePin(reset: Boolean = false) = "change_pin?reset=$reset"
}

/** Lock screen while locked, the diary while unlocked. */
@Composable
fun MorrowRoot(container: AppContainer, settings: MorrowSettings) {
    val state by container.lock.state.collectAsStateWithLifecycle()
    Crossfade(targetState = state, animationSpec = tween(220), label = "lock") { current ->
        when (current) {
            LockState.Locked -> LockScreen(container.lock, settings, container.dayClock)
            is LockState.Unlocked -> UnlockedApp(container, current.session, settings)
        }
    }
}

/**
 * Everything behind the lock. Its ViewModels live in a store owned by this unlocked session, so
 * locking clears them all, along with anything they read from the diary.
 */
@Composable
private fun UnlockedApp(container: AppContainer, session: DiarySession, settings: MorrowSettings) {
    val owner = remember(session) { SessionViewModels() }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        MorrowNavHost(container)
        FingerprintOffer(container, settings)
    }
}

private class SessionViewModels : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

@Composable
private fun MorrowNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }

    // "Forgot PIN?" answered with a fingerprint: go straight to choosing a new PIN.
    val resetRequested by container.lock.pinResetRequested.collectAsStateWithLifecycle()
    LaunchedEffect(resetRequested) {
        if (resetRequested) {
            nav.navigate(Routes.changePin(reset = true))
            container.lock.pinResetHandled()
        }
    }

    NavHost(
        navController = nav,
        startDestination = Routes.TODAY,
        enterTransition = { fadeIn(tween(180)) },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(180)) },
        popExitTransition = { fadeOut(tween(120)) },
    ) {
        composable(Routes.TODAY) {
            TodayScreen(
                viewModel = viewModel { TodayViewModel(container.settings, container.dayClock) },
                onOpenJournal = { nav.navigate(Routes.JOURNAL) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.JOURNAL) {
            JournalScreen(onBack = back, onOpenWeek = { nav.navigate(Routes.WEEK) })
        }
        composable(Routes.WEEK) {
            WeekScreen(onBack = back)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel { SettingsViewModel(container.settings, container.lock) },
                onBack = back,
                onChangePin = { nav.navigate(Routes.changePin()) },
            )
        }
        composable(
            Routes.CHANGE_PIN,
            arguments = listOf(navArgument("reset") { type = NavType.BoolType; defaultValue = false }),
        ) { entry ->
            ChangePinScreen(
                lock = container.lock,
                reset = entry.arguments?.getBoolean("reset") ?: false,
                onFinished = back,
                onBack = back,
            )
        }
    }
}

/**
 * Right after unlocking with the PIN: offer fingerprint unlock if it's wanted but not set up yet
 * (first run, or Android removed it because a new fingerprint was added to the phone).
 */
@Composable
private fun FingerprintOffer(container: AppContainer, settings: MorrowSettings) {
    val c = Morrow.colors
    val info by container.lock.info.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current as FragmentActivity
    val scope = rememberCoroutineScope()
    var dismissed by rememberSaveable { mutableStateOf(false) }
    val available = remember { canUseFingerprint(context) }

    if (dismissed || !info.ready || !settings.fingerprintUnlock || info.hasFingerprint || !available) return

    val title = stringResource(R.string.fingerprint_enroll_title)
    val subtitle = stringResource(R.string.fingerprint_enroll_subtitle)
    val cancel = stringResource(R.string.cancel)
    AlertDialog(
        onDismissRequest = { dismissed = true },
        confirmButton = {
            TextButton(onClick = {
                dismissed = true
                scope.launch { activity.enrollFingerprint(container.lock, title, subtitle, cancel) }
            }) { Text(stringResource(R.string.turn_on), color = c.accent, style = MorrowType.button) }
        },
        dismissButton = {
            TextButton(onClick = {
                dismissed = true
                scope.launch { container.settings.update { it.copy(fingerprintUnlock = false) } }
            }) { Text(stringResource(R.string.not_now), color = c.muted, style = MorrowType.button) }
        },
        title = { Text(stringResource(R.string.fingerprint_offer_title), style = MorrowType.sectionTitle) },
        text = {
            Text(
                stringResource(if (info.fingerprintInvalidated) R.string.fingerprint_offer_body_invalidated else R.string.fingerprint_offer_body),
                style = MorrowType.body,
            )
        },
        containerColor = c.paper,
    )
}
