package app.morrow.diary.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.morrow.diary.AppContainer
import app.morrow.diary.ui.journal.JournalScreen
import app.morrow.diary.ui.settings.SettingsScreen
import app.morrow.diary.ui.settings.SettingsViewModel
import app.morrow.diary.ui.today.TodayScreen
import app.morrow.diary.ui.today.TodayViewModel
import app.morrow.diary.ui.week.WeekScreen

object Routes {
    const val TODAY = "today"
    const val JOURNAL = "journal"
    const val WEEK = "week"
    const val SETTINGS = "settings"
}

@Composable
fun MorrowNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }

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
            SettingsScreen(viewModel = viewModel { SettingsViewModel(container.settings) }, onBack = back)
        }
    }
}
