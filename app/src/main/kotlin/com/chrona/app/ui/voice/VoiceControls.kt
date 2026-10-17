package com.chrona.app.ui.voice

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chrona.app.ui.design.ChronaTheme
import com.chrona.app.ui.design.ambientGlow
import com.chrona.app.ui.design.glassSurface

/**
 * A press shorter than this is treated as "start listening", not "start and stop".
 *
 * Without it, the tap that most people try first would end the session before a word was heard,
 * which reads as a broken button rather than a deliberate hold.
 */
private const val HOLD_TO_FINISH_MILLIS = 250L

/**
 * The one microphone control, fixed at the bottom centre of every Chrona screen.
 *
 * Press and hold to talk, release to send; a short tap starts listening and lets the platform's
 * own silence timeout finish the utterance. State is carried by colour, icon and label together,
 * so the current state is readable without motion and without colour.
 */
@Composable
fun VoiceMicButton(
    state: VoiceUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = ChronaTheme.tokens
    val presentation = state.micPresentation()
    val enabled = presentation.enabled

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // The halo sits behind the button and only appears while Chrona is listening, so the
        // one place that moves is the one place the user is holding.
        if (presentation.listening) {
            val pulse = rememberInfiniteTransition(label = "mic_pulse")
            val scale by pulse.animateFloat(
                initialValue = 1f,
                targetValue = 1.14f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1100, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "mic_pulse_scale",
            )
            Box(
                modifier = Modifier
                    .size(MIC_TOUCH_TARGET)
                    .scale(scale)
                    .clip(CircleShape)
                    .ambientGlow(tokens.accent, strength = 1f),
            )
        }

        Box(
            modifier = Modifier
                .size(MIC_TOUCH_TARGET)
                .clip(CircleShape)
                .glassSurface(CircleShape, container = presentation.container, elevation = 16.dp)
                .semantics {
                    testTag = MIC_BUTTON_TAG
                    role = Role.Button
                    contentDescription = presentation.label
                    stateDescription = presentation.stateDescription
                    // Assistive technology cannot press and hold, so a single activation toggles
                    // the same session the gesture would.
                    onClick(label = presentation.actionLabel) {
                        if (presentation.listening) onStop() else onStart()
                        true
                    }
                }
                .pointerInput(presentation.enabled, presentation.listening) {
                    if (!presentation.enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            onStart()
                            val pressedAt = System.currentTimeMillis()
                            tryAwaitRelease()
                            if (System.currentTimeMillis() - pressedAt >= HOLD_TO_FINISH_MILLIS) {
                                onStop()
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            MicGlyph(presentation, tokens.accent, tokens.accentBright)
        }
    }
}

/** What the button shows, decided once so the composable stays free of state questions. */
private data class MicPresentation(
    val label: String,
    val actionLabel: String,
    val stateDescription: String,
    val container: Color,
    val enabled: Boolean,
    val listening: Boolean,
    val busy: Boolean,
    val succeeded: Boolean,
    val refused: Boolean,
)

@Composable
private fun VoiceUiState.micPresentation(): MicPresentation {
    val tokens = ChronaTheme.tokens
    val base = MicPresentation(
        label = "Hold to speak",
        actionLabel = "Hold to speak",
        stateDescription = "Idle",
        container = tokens.accent,
        enabled = true,
        listening = false,
        busy = false,
        succeeded = false,
        refused = false,
    )
    return when (this) {
        VoiceUiState.Idle -> base

        VoiceUiState.RequestingPermission -> base.copy(
            label = "Asking for microphone permission",
            stateDescription = "Asking for permission",
            container = tokens.accent.copy(alpha = 0.5f),
        )

        is VoiceUiState.Listening -> base.copy(
            label = "Listening. Release to send.",
            actionLabel = "Stop listening",
            stateDescription = partialText?.let { "Listening: $it" } ?: "Listening",
            container = tokens.accentBright,
            enabled = true,
            listening = true,
        )

        is VoiceUiState.Interpreting -> base.copy(
            label = "Working out your tasks",
            stateDescription = "Working out your tasks",
            container = tokens.glassStrong,
            enabled = false,
            busy = true,
        )

        // A pending question needs a decision, so the microphone stays out of the way rather than
        // discarding it on the next press.
        is VoiceUiState.Clarify -> base.copy(
            label = "Answer the question to add this task",
            stateDescription = "Waiting for your answer",
            container = tokens.glassStrong,
            enabled = false,
        )

        is VoiceUiState.Created -> base.copy(
            label = "Tasks added",
            stateDescription = "Added ${tasks.size} ${if (tasks.size == 1) "task" else "tasks"}",
            container = tokens.accent.copy(alpha = 0.85f),
            succeeded = true,
        )

        is VoiceUiState.Failed -> base.copy(
            label = "Try holding to speak again",
            stateDescription = "Last attempt failed",
            container = tokens.glassStrong,
            refused = true,
        )

        is VoiceUiState.Unavailable -> base.copy(
            label = message,
            stateDescription = "Microphone unavailable",
            container = tokens.glassStrong,
            enabled = false,
            refused = true,
        )
    }
}

@Composable
private fun MicGlyph(presentation: MicPresentation, accent: Color, accentBright: Color) {
    when {
        presentation.busy -> CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            color = accentBright,
            strokeWidth = 2.dp,
        )

        presentation.listening -> Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(4) { index ->
                val transition = rememberInfiniteTransition(label = "bar_$index")
                val scale by transition.animateFloat(
                    initialValue = 0.35f,
                    // Staggered so the bars read as a moving waveform rather than a blink.
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 420 + index * 90,
                            easing = LinearEasing,
                        ),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "bar_${index}_scale",
                )
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 22.dp)
                        .scale(1f, scale)
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(accentBright, accent),
                            ),
                        ),
                )
            }
        }

        presentation.succeeded -> Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(28.dp),
        )

        presentation.refused -> Icon(
            imageVector = Icons.Outlined.MicOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )

        else -> Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(30.dp),
        )
    }
}

/**
 * A compact caption under the microphone.
 *
 * One line, no layout shift: it states what the button will do, which is the only thing a user
 * needs before their first capture.
 */
@Composable
fun VoiceMicCaption(state: VoiceUiState, modifier: Modifier = Modifier) {
    val text = when (state) {
        is VoiceUiState.Listening -> state.partialText?.takeIf { it.isNotBlank() } ?: "Listening…"
        is VoiceUiState.Interpreting -> "Working out your tasks…"
        is VoiceUiState.Created -> state.tasks.joinToString(separator = " · ") { it.title }
        else -> "Hold to speak"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .widthIn(max = 220.dp)
            .padding(top = 6.dp)
            .semantics { testTag = MIC_CAPTION_TAG },
    )
}

private val MIC_TOUCH_TARGET = 64.dp
const val MIC_BUTTON_TAG = "voice_mic_button"
const val MIC_CAPTION_TAG = "voice_mic_caption"