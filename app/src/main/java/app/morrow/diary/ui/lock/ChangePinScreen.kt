package app.morrow.diary.ui.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morrow.diary.R
import app.morrow.diary.security.LockManager
import app.morrow.diary.security.PinResult
import app.morrow.diary.security.PinRules
import app.morrow.diary.ui.components.MorrowPage
import app.morrow.diary.ui.components.SubpageHeader
import app.morrow.diary.ui.theme.Morrow
import app.morrow.diary.ui.theme.MorrowType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Step { CURRENT, NEW, CONFIRM, DONE }

/**
 * Change the Morrow PIN: current PIN → new PIN (4–8 digits) → confirm.
 * With [reset] (arrived via "Forgot PIN?" + fingerprint) the current-PIN step is skipped.
 */
@Composable
fun ChangePinScreen(lock: LockManager<*>, reset: Boolean, onFinished: () -> Unit, onBack: () -> Unit) {
    val c = Morrow.colors
    val info by lock.info.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by rememberSaveable { mutableStateOf(if (reset) Step.NEW else Step.CURRENT) }
    var entry by remember { mutableStateOf("") }
    var firstNew by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    val mismatch = stringResource(R.string.change_pin_mismatch)
    val done = stringResource(R.string.change_pin_done)

    fun fail(text: String) {
        message = text
        entry = ""
        haptics.performHapticFeedback(HapticFeedbackType.Reject)
        scope.launch { shake.wrongPinShake() }
    }

    fun onDigit(digit: Char) {
        if (busy) return
        val max = when (step) {
            Step.CURRENT -> info.pinLength
            Step.NEW -> PinRules.MAX_LENGTH
            Step.CONFIRM -> firstNew.length
            Step.DONE -> return
        }
        if (entry.length >= max) return
        entry += digit
        message = null

        if (step == Step.CURRENT && entry.length == info.pinLength) {
            busy = true
            scope.launch {
                when (val r = lock.verifyPin(entry)) {
                    PinResult.Success -> {
                        step = Step.NEW
                        entry = ""
                    }
                    is PinResult.Wrong -> fail(context.resources.getQuantityString(R.plurals.lock_wrong_pin, r.triesLeft, r.triesLeft))
                    is PinResult.LockedOut -> fail(context.getString(R.string.lock_locked_out, "%d:%02d".format(waitSeconds(r.untilMillis) / 60, waitSeconds(r.untilMillis) % 60)))
                }
                busy = false
            }
        } else if (step == Step.CONFIRM && entry.length == firstNew.length) {
            if (entry != firstNew) {
                step = Step.NEW
                firstNew = ""
                fail(mismatch)
                return
            }
            busy = true
            scope.launch {
                lock.changePin(firstNew)
                step = Step.DONE
                message = done
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                delay(900)
                onFinished()
            }
        }
    }

    MorrowPage {
        SubpageHeader(
            title = stringResource(if (reset) R.string.new_pin_title else R.string.change_pin_title),
            onBack = onBack,
            backLabel = stringResource(if (reset) R.string.back_to_today else R.string.back_to_settings),
        )

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(
                    when (step) {
                        Step.CURRENT -> R.string.change_pin_current
                        Step.NEW -> R.string.change_pin_new
                        Step.CONFIRM, Step.DONE -> R.string.change_pin_confirm
                    },
                ),
                style = MorrowType.sectionTitle,
                color = c.ink,
                textAlign = TextAlign.Center,
            )
            Text(
                if (step == Step.NEW) stringResource(R.string.change_pin_new_hint) else " ",
                style = MorrowType.caption,
                color = c.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp).widthIn(max = 300.dp),
            )

            val total = when (step) {
                Step.CURRENT -> info.pinLength
                Step.NEW -> maxOf(PinRules.MIN_LENGTH, entry.length)
                Step.CONFIRM, Step.DONE -> firstNew.length
            }
            PinDots(total = total, filled = if (step == Step.DONE) total else entry.length, shake = shake, modifier = Modifier.padding(top = 28.dp))

            Box(Modifier.heightIn(min = 44.dp).padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }, contentAlignment = Alignment.Center) {
                message?.let { Text(it, style = MorrowType.small, color = if (step == Step.DONE) c.done else c.accent, textAlign = TextAlign.Center) }
            }

            PinKeypad(
                onDigit = ::onDigit,
                onDelete = { if (!busy && step != Step.DONE) entry = entry.dropLast(1) },
                enabled = !busy && step != Step.DONE,
                modifier = Modifier.padding(top = 6.dp),
            )

            if (step == Step.NEW) {
                Button(
                    onClick = {
                        firstNew = entry
                        entry = ""
                        step = Step.CONFIRM
                    },
                    enabled = PinRules.isValid(entry),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
                    modifier = Modifier.padding(top = 20.dp).width(276.dp).height(52.dp),
                ) {
                    Text(stringResource(R.string.change_pin_continue), style = MorrowType.button)
                }
            }
        }
    }
}

private fun waitSeconds(untilMillis: Long): Long = ((untilMillis - System.currentTimeMillis() + 999) / 1000).coerceAtLeast(0)
