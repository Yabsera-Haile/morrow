package app.morrow.diary.ui.today

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morrow.diary.R
import app.morrow.diary.domain.DayPhase
import app.morrow.diary.ui.components.MorrowPage
import app.morrow.diary.ui.components.QuoteBlock
import app.morrow.diary.ui.icons.MorrowIcons
import app.morrow.diary.ui.theme.DarkMorrowColors
import app.morrow.diary.ui.theme.LightMorrowColors
import app.morrow.diary.ui.theme.Morrow
import app.morrow.diary.ui.theme.MorrowType
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Today. During the day it's the morning view: last night's plans and today's goals.
 * From the evening check-in time (or "Start now") it becomes the check-in: tick what you did,
 * send the rest to tomorrow, write notes and tomorrow's plans, then "Done for today".
 */
@Composable
fun TodayScreen(viewModel: TodayViewModel, onOpenJournal: () -> Unit, onOpenSettings: () -> Unit) {
    val header by viewModel.header.collectAsStateWithLifecycle()
    val checkedIn by viewModel.checkedIn.collectAsStateWithLifecycle()
    val current = header ?: return
    val c = Morrow.colors

    var eveningOpened by rememberSaveable(current.date) { mutableStateOf(false) }
    val evening = current.phase == DayPhase.EVENING || eveningOpened || checkedIn == true

    val snackbar = remember { SnackbarHostState() }
    val goalDeleted = stringResource(R.string.goal_deleted)
    val planRemoved = stringResource(R.string.plan_removed)
    val undo = stringResource(R.string.undo)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            launch {
                val (message, restore) = when (event) {
                    is TodayEvent.GoalDeleted -> goalDeleted to { viewModel.restore(event.deleted) }
                    is TodayEvent.PlanDeleted -> planRemoved to { viewModel.restorePlan(event.plan) }
                }
                if (snackbar.showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) restore()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        MorrowPage {
            TodayHeaderRow(current.date, onOpenJournal, onOpenSettings)

            current.quote?.let { QuoteBlock(it, Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp)) }

            Text(
                text = stringResource(
                    when {
                        evening -> R.string.greeting_evening
                        current.phase == DayPhase.MORNING -> R.string.greeting_morning
                        else -> R.string.greeting_afternoon
                    },
                ),
                style = MorrowType.greeting,
                color = c.muted,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
            )

            if (evening) {
                EveningContent(viewModel, current.date, checkedIn == true)
            } else {
                MorningContent(viewModel, current, onStartEvening = { eveningOpened = true })
            }
        }

        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
        ) { data ->
            Snackbar(
                data,
                shape = RoundedCornerShape(14.dp),
                containerColor = c.ink,
                contentColor = c.paper,
                // On the inverted snackbar, the other theme's accent is the readable one.
                actionColor = if (c.isDark) LightMorrowColors.accent else DarkMorrowColors.accent,
            )
        }
    }
}

@Composable
private fun MorningContent(viewModel: TodayViewModel, current: TodayHeader, onStartEvening: () -> Unit) {
    val c = Morrow.colors
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val carry by viewModel.carryOver.collectAsStateWithLifecycle()
    val transferredNow by viewModel.transferredNow.collectAsStateWithLifecycle()

    // Morning with nothing planned yet: put the cursor straight in the goal box.
    var autoFocusDone by rememberSaveable { mutableStateOf(false) }
    val shouldFocus = !autoFocusDone && current.phase == DayPhase.MORNING && goals?.isEmpty() == true && carry?.hasPending != true

    val carryOver = carry
    when {
        carryOver != null && carryOver.hasPending -> CarryOverCard(
            carry = carryOver,
            today = current.date,
            onTransferPlan = viewModel::transferPlan,
            onCarryGoal = viewModel::carryGoal,
            onTransferAll = viewModel::transferAll,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp),
        )
        transferredNow.isNotEmpty() -> TransferredRow(
            onUndo = viewModel::undoTransfer,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp),
        )
    }

    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 26.dp)) {
        val list = goals.orEmpty()
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.today_goals_title),
                style = MorrowType.sectionTitle,
                color = c.ink,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (list.isNotEmpty()) {
                Text(stringResource(R.string.today_goals_count, list.count { it.done }, list.size), style = MorrowType.caption, color = c.muted)
            }
        }
        GoalList(
            goals = list,
            onToggle = viewModel::setDone,
            onRename = viewModel::rename,
            onDelete = viewModel::delete,
            onReorder = viewModel::reorder,
            modifier = Modifier.padding(top = 6.dp),
        )
        AddGoalField(
            onAdd = viewModel::addGoals,
            requestFocus = shouldFocus,
            onFocusRequested = { autoFocusDone = true },
        )
        Text(
            stringResource(R.string.add_goal_hint),
            style = MorrowType.caption,
            color = c.muted,
            modifier = Modifier.padding(start = 34.dp, top = 8.dp),
        )
    }

    EveningFooter(current.eveningTime, onStartEvening, Modifier.padding(top = 36.dp))
}

@Composable
private fun EveningContent(viewModel: TodayViewModel, today: LocalDate, checkedIn: Boolean) {
    val c = Morrow.colors
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val plans by viewModel.plansForTomorrow.collectAsStateWithLifecycle()
    val savedNote by viewModel.savedNote.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    val list = goals.orEmpty()
    val planList = plans.orEmpty()

    Column(Modifier.padding(start = 24.dp, end = 24.dp)) {
        if (list.isNotEmpty()) {
            EveningProgress(list.count { it.done }, list.size, Modifier.padding(top = 18.dp))
        }

        Row(Modifier.padding(top = 26.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.evening_goals_title),
                style = MorrowType.sectionTitle,
                color = c.ink,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (list.isNotEmpty()) Text(stringResource(R.string.evening_goals_hint), style = MorrowType.caption, color = c.muted)
        }
        if (goals != null && list.isEmpty()) {
            Text(stringResource(R.string.evening_no_goals), style = MorrowType.small, color = c.muted, modifier = Modifier.padding(top = 8.dp))
        }
        GoalList(
            goals = list,
            onToggle = viewModel::setDone,
            onRename = viewModel::rename,
            onDelete = viewModel::delete,
            onReorder = viewModel::reorder,
            modifier = Modifier.padding(top = 6.dp),
            trailing = { goal ->
                if (!goal.done) {
                    TomorrowAction(
                        sent = planList.any { it.sourceGoalId == goal.id },
                        goalText = goal.text,
                        onSend = { viewModel.sendToTomorrow(goal) },
                    )
                }
            },
        )

        // Wait for the saved note before showing the field, so typing never races the load.
        savedNote?.let { note ->
            key(today) {
                NotesField(initial = note, onChange = viewModel::onNoteChange, modifier = Modifier.padding(top = 28.dp))
            }
        }

        PlansForTomorrow(
            plans = planList,
            today = today,
            onRemove = viewModel::removePlan,
            onAdd = viewModel::addPlans,
            modifier = Modifier.padding(top = 28.dp),
        )

        CheckinFinish(
            checkedIn = checkedIn,
            onDone = {
                focusManager.clearFocus()
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                viewModel.setCheckinDone(true)
            },
            modifier = Modifier.padding(top = 32.dp, bottom = 32.dp),
        )
    }
}

@Composable
private fun TodayHeaderRow(date: LocalDate, onOpenJournal: () -> Unit, onOpenSettings: () -> Unit) {
    val c = Morrow.colors
    val locale = Locale.getDefault()
    val dateLabel = remember(date, locale) { date.format(DateTimeFormatter.ofPattern("MMMM d", locale)).uppercase(locale) }
    val dayName = remember(date, locale) { date.dayOfWeek.getDisplayName(TextStyle.FULL, locale) }

    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 10.dp, top = 24.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(dateLabel, style = MorrowType.overline, color = c.muted)
            Text(
                dayName,
                style = MorrowType.dayTitle,
                color = c.ink,
                modifier = Modifier.padding(top = 2.dp).semantics { heading() },
            )
        }
        IconButton(onClick = onOpenJournal) {
            Icon(MorrowIcons.Calendar, contentDescription = stringResource(R.string.cd_journal), tint = c.ink, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onOpenSettings) {
            Icon(MorrowIcons.Sliders, contentDescription = stringResource(R.string.cd_settings), tint = c.ink, modifier = Modifier.size(22.dp))
        }
    }
}

/** During the day: the evening check-in, folded away, with a way to start it early. */
@Composable
private fun EveningFooter(eveningTime: LocalTime, onStartNow: () -> Unit, modifier: Modifier = Modifier) {
    val c = Morrow.colors
    val locale = Locale.getDefault()
    val time = remember(eveningTime, locale) { eveningTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)) }
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(c.hairline, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), stroke)
            }
            .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MorrowIcons.Moon, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.evening_footer, time),
            style = MorrowType.small,
            color = c.muted,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        TextButton(onClick = onStartNow) {
            Text(stringResource(R.string.evening_start_now), style = MorrowType.button, color = c.accent)
        }
    }
}
