package app.morrow.diary.ui.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morrow.diary.R
import app.morrow.diary.ui.icons.MorrowIcons
import app.morrow.diary.ui.theme.FrauncesText
import app.morrow.diary.ui.theme.Morrow
import kotlin.math.roundToInt

/** Filled dots for typed digits, empty rings for the rest. [shake] moves them sideways after a wrong PIN. */
@Composable
fun PinDots(total: Int, filled: Int, shake: Animatable<Float, AnimationVector1D>? = null, modifier: Modifier = Modifier) {
    val c = Morrow.colors
    val description = stringResource(R.string.pin_dots_description, filled, total)
    Row(
        modifier
            .offset { IntOffset((shake?.value ?: 0f).roundToInt(), 0) }
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(14.dp)
                    .then(
                        if (i < filled) Modifier.background(c.ink, CircleShape)
                        else Modifier.border(1.75.dp, c.emptyRing, CircleShape),
                    ),
            )
        }
    }
}

suspend fun Animatable<Float, AnimationVector1D>.wrongPinShake() {
    snapTo(0f)
    animateTo(
        0f,
        keyframes {
            durationMillis = 360
            -18f at 50
            16f at 110
            -12f at 170
            8f at 230
            -4f at 290
        },
    )
}

/** 1–9, 0 and delete, in big serif numerals, like the design. */
@Composable
fun PinKeypad(onDigit: (Char) -> Unit, onDelete: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val c = Morrow.colors
    val haptics = LocalHapticFeedback.current
    val rows = listOf("123", "456", "789", " 0<")
    val deleteLabel = stringResource(R.string.pin_delete)

    Column(modifier.width(276.dp).alpha(if (enabled) 1f else 0.4f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    val keyModifier = Modifier.width(84.dp).height(60.dp)
                    when (key) {
                        ' ' -> Box(keyModifier)
                        '<' -> Box(
                            keyModifier
                                .clip(RoundedCornerShape(30.dp))
                                .clickable(enabled = enabled, role = Role.Button, onClickLabel = deleteLabel) {
                                    haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                    onDelete()
                                }
                                .semantics { contentDescription = deleteLabel },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(MorrowIcons.Backspace, contentDescription = null, tint = c.muted, modifier = Modifier.size(26.dp))
                        }
                        else -> Box(
                            keyModifier
                                .clip(RoundedCornerShape(30.dp))
                                .clickable(enabled = enabled, role = Role.Button) {
                                    haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                    onDigit(key)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(key.toString(), style = TextStyle(fontFamily = FrauncesText, fontSize = 28.sp), color = c.ink)
                        }
                    }
                }
            }
        }
    }
}
