package app.morrow.diary.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morrow.diary.R
import app.morrow.diary.data.CarryOver
import app.morrow.diary.ui.icons.MorrowIcons
import app.morrow.diary.ui.theme.Morrow
import app.morrow.diary.ui.theme.MorrowType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** "From last night": last evening's plans and yesterday's unfinished goals, ready to add to today. */
@Composable
fun CarryOverCard(
    carry: CarryOver,
    today: LocalDate,
    onTransferPlan: (Long) -> Unit,
    onCarryGoal: (Long) -> Unit,
    onTransferAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Morrow.colors
    val locale = Locale.getDefault()
    val yesterday = carry.sourceDate == today.minusDays(1)
    val dayName = remember(carry.sourceDate, locale) { carry.sourceDate.dayOfWeek.getDisplayName(TextStyle.FULL, locale) }
    val dateLabel = remember(carry.sourceDate, locale) { carry.sourceDate.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale)) }

    Column(
        modifier
            .fillMaxWidth()
            .background(c.card, RoundedCornerShape(18.dp))
            .padding(start = 18.dp, end = 10.dp, top = 16.dp, bottom = 16.dp),
    ) {
        Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (yesterday) stringResource(R.string.carry_from_last_night) else stringResource(R.string.carry_from_day, dayName)).uppercase(locale),
                style = MorrowType.overline,
                color = c.muted,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Text(dateLabel, style = MorrowType.caption, color = c.muted)
        }

        carry.plans.forEachIndexed { i, plan ->
            CarryRow(
                text = plan.text,
                added = plan.transferredToGoalId != null,
                showDivider = i < carry.plans.lastIndex || carry.unfinished.isNotEmpty(),
                onAdd = { onTransferPlan(plan.id) },
            )
        }

        if (carry.unfinished.isNotEmpty()) {
            Text(
                (if (yesterday) stringResource(R.string.carry_not_done_yesterday) else stringResource(R.string.carry_not_done_on, dayName)).uppercase(locale),
                style = MorrowType.overline,
                color = c.muted,
                modifier = Modifier.padding(top = 14.dp, bottom = 2.dp).semantics { heading() },
            )
            carry.unfinished.forEachIndexed { i, item ->
                CarryRow(
                    text = item.goal.text,
                    added = item.carried,
                    showDivider = i < carry.unfinished.lastIndex,
                    onAdd = { onCarryGoal(item.goal.id) },
                )
            }
        }

        if (carry.hasPending) {
            val total = carry.plans.size + carry.unfinished.size
            Button(
                onClick = onTransferAll,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                modifier = Modifier.padding(top = 12.dp, end = 8.dp).fillMaxWidth().height(48.dp),
            ) {
                Icon(MorrowIcons.ArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    if (carry.pendingCount == total) stringResource(R.string.carry_transfer_all)
                    else stringResource(R.string.carry_transfer_rest, carry.pendingCount),
                    style = MorrowType.button,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CarryRow(text: String, added: Boolean, showDivider: Boolean, onAdd: () -> Unit) {
    val c = Morrow.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(
                if (showDivider) {
                    Modifier.drawBehind {
                        val stroke = 1.dp.toPx()
                        drawLine(c.hairline, Offset(0f, size.height - stroke / 2), Offset(size.width - 8.dp.toPx(), size.height - stroke / 2), stroke)
                    }
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MorrowType.body, color = c.ink, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
        if (added) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(MorrowIcons.Check, contentDescription = null, tint = c.done, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.carry_added),
                    style = MorrowType.caption.copy(fontWeight = FontWeight.Medium),
                    color = c.done,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        } else {
            val label = stringResource(R.string.cd_add_to_today, text)
            Box(
                Modifier.size(44.dp).clickable(role = Role.Button, onClickLabel = label, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(32.dp).border(1.5.dp, c.accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(MorrowIcons.Plus, contentDescription = label, tint = c.accent, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Shown once everything from last night is on today's list, with a way back. */
@Composable
fun TransferredRow(onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val c = Morrow.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(c.card, RoundedCornerShape(14.dp))
            .padding(start = 16.dp, end = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MorrowIcons.Check, contentDescription = null, tint = c.done, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.carry_all_added),
            style = MorrowType.small,
            color = c.ink,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        TextButton(onClick = onUndo) {
            Text(stringResource(R.string.undo), style = MorrowType.button, color = c.accent)
        }
    }
}
