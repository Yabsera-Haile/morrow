package app.morrow.diary.ui.today

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.morrow.diary.R
import app.morrow.diary.data.db.GoalEntity
import app.morrow.diary.domain.GoalText
import app.morrow.diary.ui.icons.MorrowIcons
import app.morrow.diary.ui.theme.Morrow
import app.morrow.diary.ui.theme.MorrowType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Today's goals. Tap the circle to tick, tap the text to edit, swipe left to delete,
 * long-press and drag to reorder. Screen readers get the same as actions.
 */
@Composable
fun GoalList(
    goals: List<GoalEntity>,
    onToggle: (GoalEntity, Boolean) -> Unit,
    onRename: (GoalEntity, String) -> Unit,
    onDelete: (GoalEntity) -> Unit,
    onReorder: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable (GoalEntity) -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val dbOrder = goals.map { it.id }
    val byId = goals.associateBy { it.id }

    // While dragging (and until the database catches up) the list shows its own order.
    var localOrder by remember { mutableStateOf<List<Long>?>(null) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    val heights = remember { mutableStateMapOf<Long, Int>() }

    LaunchedEffect(dbOrder) {
        if (draggingId == null && (localOrder == dbOrder || localOrder?.toSet() != dbOrder.toSet())) localOrder = null
    }
    val order = localOrder?.takeIf { it.toSet() == dbOrder.toSet() } ?: dbOrder
    val currentOrder by rememberUpdatedState(order)
    val latestReorder by rememberUpdatedState(onReorder)

    val moveUp = stringResource(R.string.action_move_up)
    val moveDown = stringResource(R.string.action_move_down)
    val deleteLabel = stringResource(R.string.action_delete)
    val editLabel = stringResource(R.string.action_edit)

    fun move(id: Long, by: Int) {
        val o = currentOrder
        val i = o.indexOf(id)
        val j = i + by
        if (i < 0 || j !in o.indices) return
        val next = o.toMutableList().apply { add(j, removeAt(i)) }
        localOrder = next
        latestReorder(next)
    }

    Column(modifier.animateContentSize()) {
        order.forEach { id ->
            val goal = byId[id] ?: return@forEach
            key(id) {
                val isDragging = draggingId == id
                GoalRow(
                    goal = goal,
                    editing = editingId == id,
                    onToggle = { onToggle(goal, it) },
                    onStartEdit = { editingId = id },
                    onFinishEdit = { text ->
                        editingId = null
                        onRename(goal, text)
                    },
                    onDelete = { onDelete(goal) },
                    trailing = trailing?.let { content -> { content(goal) } },
                    modifier = Modifier
                        .onSizeChanged { heights[id] = it.height }
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffset else 0f
                            shadowElevation = if (isDragging) 8.dp.toPx() else 0f
                            scaleX = if (isDragging) 1.02f else 1f
                            scaleY = if (isDragging) 1.02f else 1f
                        }
                        .pointerInput(id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    editingId = null
                                    draggingId = id
                                    dragOffset = 0f
                                    localOrder = currentOrder
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val o = localOrder ?: return@detectDragGesturesAfterLongPress
                                    val i = o.indexOf(id)
                                    if (dragOffset > 0 && i < o.lastIndex) {
                                        val h = heights[o[i + 1]] ?: return@detectDragGesturesAfterLongPress
                                        if (dragOffset > h / 2f) {
                                            localOrder = o.toMutableList().apply { add(i + 1, removeAt(i)) }
                                            dragOffset -= h
                                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        }
                                    } else if (dragOffset < 0 && i > 0) {
                                        val h = heights[o[i - 1]] ?: return@detectDragGesturesAfterLongPress
                                        if (-dragOffset > h / 2f) {
                                            localOrder = o.toMutableList().apply { add(i - 1, removeAt(i)) }
                                            dragOffset += h
                                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    val o = localOrder
                                    draggingId = null
                                    dragOffset = 0f
                                    if (o != null) latestReorder(o)
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragOffset = 0f
                                    localOrder = null
                                },
                            )
                        }
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(editLabel) { editingId = id; true },
                                CustomAccessibilityAction(moveUp) { move(id, -1); true },
                                CustomAccessibilityAction(moveDown) { move(id, 1); true },
                                CustomAccessibilityAction(deleteLabel) { onDelete(goal); true },
                            )
                        },
                )
            }
        }
    }
}

@Composable
private fun GoalRow(
    goal: GoalEntity,
    editing: Boolean,
    onToggle: (Boolean) -> Unit,
    onStartEdit: () -> Unit,
    onFinishEdit: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Morrow.colors
    val scope = rememberCoroutineScope()
    val swipe = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(1) }

    Box(
        modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width }
            // Drawn after the row, so the row's own background can't cover the divider.
            .drawWithContent {
                drawContent()
                val stroke = 1.dp.toPx()
                drawLine(c.hairline, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
            },
    ) {
        // Revealed behind the row while swiping left.
        if (swipe.value < 0f) {
            Row(
                Modifier.matchParentSize().background(c.card).padding(end = 20.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.action_delete), style = MorrowType.button, color = c.accent)
            }
        }
        Row(
            Modifier
                .offset { IntOffset(swipe.value.roundToInt(), 0) }
                .fillMaxWidth()
                .background(c.paper)
                .heightIn(min = 52.dp)
                .pointerInput(goal.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (swipe.value < -width * 0.35f) {
                                    swipe.animateTo(-width.toFloat(), tween(160))
                                    onDelete()
                                } else {
                                    swipe.animateTo(0f, spring())
                                }
                            }
                        },
                        onDragCancel = { scope.launch { swipe.animateTo(0f, spring()) } },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            scope.launch { swipe.snapTo((swipe.value + amount).coerceIn(-width.toFloat(), 0f)) }
                        },
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckCircle(done = goal.done, label = goal.text, onToggle = onToggle, modifier = Modifier.offset(x = (-10).dp))
            if (editing) {
                InlineEdit(goal.text, onFinishEdit, Modifier.weight(1f).padding(vertical = 12.dp))
            } else {
                Text(
                    goal.text,
                    style = MorrowType.goal.copy(
                        textDecoration = if (goal.done) TextDecoration.LineThrough else null,
                    ),
                    color = if (goal.done) c.muted else c.ink,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClickLabel = stringResource(R.string.action_edit), onClick = onStartEdit)
                        .padding(vertical = 13.dp),
                )
                trailing?.invoke()
            }
        }
    }
}

@Composable
fun CheckCircle(done: Boolean, label: String, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Morrow.colors
    val haptics = LocalHapticFeedback.current
    val fill by animateColorAsState(if (done) c.done else Color.Transparent, label = "check")
    Box(
        modifier
            .size(44.dp)
            .toggleable(value = done, role = Role.Checkbox) {
                haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                onToggle(it)
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .background(fill, CircleShape)
                .then(if (done) Modifier else Modifier.border(1.75.dp, c.emptyRing, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(MorrowIcons.CheckBold, contentDescription = null, tint = c.onDone, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun InlineEdit(initial: String, onDone: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Morrow.colors
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focus = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    fun finish() {
        if (finished) return
        finished = true
        onDone(value.text)
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = value,
        onValueChange = { value = it },
        singleLine = true,
        textStyle = MorrowType.goal.copy(color = c.ink),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { finish() }),
        modifier = modifier
            .focusRequester(focus)
            .onFocusChanged {
                if (hadFocus && !it.isFocused) finish()
                hadFocus = it.isFocused
            },
    )
}

/**
 * The "Add a goal…" line. Enter adds what's typed and keeps the keyboard open for the next one;
 * pasting several lines adds them all.
 */
@Composable
fun AddGoalField(
    onAdd: (List<String>) -> Unit,
    requestFocus: Boolean,
    onFocusRequested: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.add_goal_placeholder),
    addLabel: String = stringResource(R.string.cd_add_goal),
) {
    val c = Morrow.colors
    var value by remember { mutableStateOf(TextFieldValue("")) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focus.requestFocus()
            onFocusRequested()
        }
    }

    fun addTyped() {
        val lines = GoalText.splitLines(value.text)
        if (lines.isNotEmpty()) onAdd(lines)
        value = TextFieldValue("")
    }

    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(c.hairline, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .offset(x = (-10).dp)
                .size(44.dp)
                .clickable(role = Role.Button, onClickLabel = addLabel) { addTyped() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(MorrowIcons.Plus, contentDescription = addLabel, tint = c.accent, modifier = Modifier.size(22.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = { next ->
                val (finished, rest) = GoalText.takeFinishedLines(next.text)
                if (next.text.contains('\n')) {
                    if (finished.isNotEmpty()) onAdd(finished)
                    value = TextFieldValue(rest, TextRange(rest.length))
                } else {
                    value = next
                }
            },
            singleLine = false,
            maxLines = 4,
            textStyle = MorrowType.goal.copy(color = c.ink),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            decorationBox = { inner ->
                Box(Modifier.padding(vertical = 14.dp)) {
                    if (value.text.isEmpty()) Text(placeholder, style = MorrowType.goal, color = c.muted)
                    inner()
                }
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus)
                .semantics { contentDescription = placeholder },
        )
    }
}
