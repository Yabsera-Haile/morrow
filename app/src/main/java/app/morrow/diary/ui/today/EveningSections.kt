package app.morrow.diary.ui.today

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.morrow.diary.R
import app.morrow.diary.data.db.PlanEntity
import app.morrow.diary.ui.icons.MorrowIcons
import app.morrow.diary.ui.theme.Morrow
import app.morrow.diary.ui.theme.MorrowType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "3 of 5 done" over a thin sage bar. */
@Composable
fun EveningProgress(done: Int, total: Int, modifier: Modifier = Modifier) {
    val c = Morrow.colors
    val fraction by animateFloatAsState(if (total == 0) 0f else done / total.toFloat(), label = "progress")
    Column(modifier.fillMaxWidth()) {
        Text(stringResource(R.string.today_goals_count, done, total), style = MorrowType.small.copy(fontWeight = FontWeight.Medium), color = c.ink)
        Box(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(c.hairline),
        ) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(c.done))
        }
    }
}

/** On a not-done goal in the evening: "→ Tomorrow", or a note once it's in tomorrow's plans. */
@Composable
fun TomorrowAction(sent: Boolean, goalText: String, onSend: () -> Unit) {
    val c = Morrow.colors
    if (sent) {
        Text(
            stringResource(R.string.evening_in_plans),
            style = MorrowType.caption,
            color = c.muted,
            modifier = Modifier.padding(start = 8.dp),
        )
    } else {
        val label = stringResource(R.string.cd_send_to_tomorrow, goalText)
        Box(
            Modifier
                .heightIn(min = 44.dp)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onSend)
                .semantics { contentDescription = label }
                .padding(start = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier
                    .height(32.dp)
                    .background(c.inputSurface, RoundedCornerShape(16.dp))
                    .border(1.dp, c.hairline, RoundedCornerShape(16.dp))
                    .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MorrowIcons.ArrowRight, contentDescription = null, tint = c.accent, modifier = Modifier.size(14.dp))
                Text(
                    stringResource(R.string.evening_tomorrow),
                    style = MorrowType.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = c.accent,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

/** Free-text notes for the day, saved as you type. */
@Composable
fun NotesField(initial: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Morrow.colors
    var value by remember { mutableStateOf(TextFieldValue(initial)) }
    var focused by remember { mutableStateOf(false) }
    val label = stringResource(R.string.evening_notes)
    val placeholder = stringResource(R.string.evening_notes_placeholder)

    Column(modifier.fillMaxWidth()) {
        Text(label, style = MorrowType.sectionTitle, color = c.ink, modifier = Modifier.semantics { heading() })
        BasicTextField(
            value = value,
            onValueChange = {
                val changed = it.text != value.text
                value = it
                if (changed) onChange(it.text)
            },
            textStyle = MorrowType.body.copy(color = c.ink),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            decorationBox = { inner ->
                Box {
                    if (value.text.isEmpty()) Text(placeholder, style = MorrowType.body, color = c.muted)
                    inner()
                }
            },
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .heightIn(min = 140.dp)
                .background(c.inputSurface, RoundedCornerShape(16.dp))
                .border(1.dp, if (focused) c.accent else c.hairline, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = label },
        )
    }
}

/** "Plans for tomorrow": a dotted list with remove buttons, and an Enter-to-add field. */
@Composable
fun PlansForTomorrow(
    plans: List<PlanEntity>,
    today: LocalDate,
    onRemove: (PlanEntity) -> Unit,
    onAdd: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Morrow.colors
    val locale = Locale.getDefault()
    val tomorrow = remember(today, locale) { today.plusDays(1).format(DateTimeFormatter.ofPattern("EEE, MMM d", locale)) }

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.evening_plans_title),
                style = MorrowType.sectionTitle,
                color = c.ink,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Text(tomorrow, style = MorrowType.caption, color = c.muted)
        }
        Column(Modifier.padding(top = 6.dp)) {
            plans.forEach { plan ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .drawWithContent {
                            drawContent()
                            val stroke = 1.dp.toPx()
                            drawLine(c.hairline, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.padding(start = 4.dp, end = 14.dp).size(7.dp).background(c.accent, CircleShape))
                    Text(plan.text, style = MorrowType.body, color = c.ink, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                    val removeLabel = stringResource(R.string.cd_remove_plan, plan.text)
                    Box(
                        Modifier.size(44.dp).clickable(role = Role.Button, onClickLabel = removeLabel) { onRemove(plan) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(MorrowIcons.Close, contentDescription = removeLabel, tint = c.emptyRing, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        AddGoalField(
            onAdd = onAdd,
            requestFocus = false,
            onFocusRequested = {},
            placeholder = stringResource(R.string.evening_plans_placeholder),
            addLabel = stringResource(R.string.cd_add_plan),
        )
    }
}

/** "Done for today", or once it's done, a short confirmation with a way back into editing. */
@Composable
fun CheckinFinish(checkedIn: Boolean, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val c = Morrow.colors
    var editing by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        if (checkedIn && !editing) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(c.card, RoundedCornerShape(16.dp))
                    .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp),
            ) {
                Text(stringResource(R.string.evening_done_title), style = MorrowType.sectionTitle, color = c.ink)
                Text(
                    stringResource(R.string.evening_done_body),
                    style = MorrowType.small,
                    color = c.muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
                // Pulled left by the button's own inner padding, so its text lines up with the text above.
                TextButton(onClick = { editing = true }, modifier = Modifier.padding(top = 2.dp).offset(x = (-12).dp)) {
                    Text(stringResource(R.string.evening_edit), style = MorrowType.button, color = c.accent)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(MorrowIcons.Check, contentDescription = null, tint = c.muted, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.evening_saved), style = MorrowType.caption, color = c.muted, modifier = Modifier.padding(start = 6.dp))
            }
            Button(
                onClick = {
                    editing = false
                    onDone()
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.evening_done_button), style = MorrowType.button)
            }
        }
    }
}
