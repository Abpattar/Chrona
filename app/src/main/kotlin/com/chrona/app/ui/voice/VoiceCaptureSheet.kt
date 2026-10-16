package com.chrona.app.ui.voice

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.chrona.app.domain.parse.ClarificationOption
import com.chrona.app.ui.design.PinnedNoteCard
import java.time.Duration

/**
 * The transcript review and correction screen (TASK-001, VOICE-001).
 *
 * Nothing reaches storage from here without a deliberate tap: a spoken request becomes a draft,
 * the draft is shown with every assumption the parser made, and the user confirms or changes
 * it. When a question is outstanding the options come first, because an unanswered question
 * must never be resolved on the user's behalf.
 *
 * The screen also carries the typed fallback in every state, including "cannot listen", so a
 * phone without on-device recognition is not blocked.
 */
@Composable
fun VoiceCaptureSheet(
    state: VoiceUiState,
    onStart: () -> Unit,
    onPermissionResult: (Boolean) -> Unit,
    onAnswer: (ClarificationOption) -> Unit,
    onEditTitle: (String) -> Unit,
    onEditDuration: (String) -> Unit,
    onEditDate: (String) -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onTypeInstead: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        onPermissionResult,
    )
    // Requesting a permission needs an Activity, so it is launched from the UI and reported
    // straight back into the state machine.
    LaunchedEffect(state) {
        if (state is VoiceUiState.RequestingPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .semantics { testTag = "voice_sheet" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Add a task by voice", style = MaterialTheme.typography.headlineSmall)

        when (state) {
            // The route does not render the sheet in this state; the branch keeps the when
            // exhaustive so a new state can never fall through silently.
            VoiceUiState.Closed -> Unit

            VoiceUiState.Idle -> IdleContent(onStart, onTypeInstead)

            VoiceUiState.RequestingPermission -> Message(
                text = "Chrona needs the microphone permission to listen. " +
                    "Nothing is recorded or stored; typing works without it.",
                tag = "voice_permission",
            )

            is VoiceUiState.Listening -> ListeningContent(state, onCancel)

            is VoiceUiState.Review -> ReviewContent(
                state = state,
                onAnswer = onAnswer,
                onEditTitle = onEditTitle,
                onEditDuration = onEditDuration,
                onEditDate = onEditDate,
                onConfirm = onConfirm,
                onTypeInstead = onTypeInstead,
                onReset = onReset,
            )

            is VoiceUiState.Unavailable -> Message(
                text = state.message,
                tag = "voice_unavailable",
                onTypeInstead = onTypeInstead,
                onReset = onReset,
            )

            is VoiceUiState.Failed -> Message(
                text = state.message,
                tag = "voice_failed",
                onRetry = if (state.canRetry) onRetry else null,
                onTypeInstead = onTypeInstead,
                onReset = onReset,
            )

            VoiceUiState.Cancelled -> Message(
                text = "Stopped listening.",
                tag = "voice_cancelled",
                onRetry = onRetry,
                onTypeInstead = onTypeInstead,
                onReset = onReset,
            )

            is VoiceUiState.Saved -> Text("Saved ${state.title}.", Modifier.semantics { testTag = "voice_saved" })
        }
    }
}

@Composable
private fun IdleContent(onStart: () -> Unit, onTypeInstead: (String) -> Unit) {
    Message(
        text = "Tap the microphone, say what needs doing, then check what Chrona heard before " +
            "anything is saved. Speech is recognised on this phone only.",
        tag = "voice_intro",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onStart, modifier = Modifier.semantics { testTag = "voice_start" }) {
            Icon(Icons.Outlined.Mic, contentDescription = null)
            Text("  Speak")
        }
        OutlinedButton(
            onClick = { onTypeInstead("") },
            modifier = Modifier.semantics { testTag = "voice_type_instead" },
        ) {
            Icon(Icons.Outlined.Edit, contentDescription = null)
            Text("  Type instead")
        }
    }
}

@Composable
private fun ListeningContent(state: VoiceUiState.Listening, onCancel: () -> Unit) {
    Message(
        text = "Listening… say what needs doing, then stop.",
        tag = "voice_listening",
    )
    state.partialText?.takeIf { it.isNotBlank() }?.let { partial ->
        // Announced politely: interim speech changes on every callback and must not spam
        // a screen reader.
        Text(
            text = partial,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = "voice_partial" },
        )
    }
    OutlinedButton(onClick = onCancel, modifier = Modifier.semantics { testTag = "voice_stop" }) {
        Text("Stop listening")
    }
}

@Composable
private fun ReviewContent(
    state: VoiceUiState.Review,
    onAnswer: (ClarificationOption) -> Unit,
    onEditTitle: (String) -> Unit,
    onEditDuration: (String) -> Unit,
    onEditDate: (String) -> Unit,
    onConfirm: () -> Unit,
    onTypeInstead: (String) -> Unit,
    onReset: () -> Unit,
) {
    PinnedNoteCard(modifier = Modifier.fillMaxWidth()) {
        Text("Heard", style = MaterialTheme.typography.labelMedium)
        Text(state.transcript, style = MaterialTheme.typography.bodyMedium)
    }

    state.prompt?.let { prompt ->
        PinnedNoteCard(modifier = Modifier.fillMaxWidth()) {
            Text(prompt.question, style = MaterialTheme.typography.titleMedium)
            prompt.options.forEachIndexed { index, option ->
                FilterChip(
                    selected = false,
                    onClick = { onAnswer(option) },
                    label = { Text(option.label) },
                    modifier = Modifier.semantics { testTag = "voice_option_$index" },
                )
            }
            Text(
                "You can also correct the fields below instead of choosing.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    OutlinedTextField(
        value = state.title,
        onValueChange = onEditTitle,
        label = { Text("What needs doing?") },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = "voice_review_title" },
    )
    OutlinedTextField(
        value = state.durationMinutes,
        onValueChange = { onEditDuration(it.filter(Char::isDigit).take(4)) },
        label = { Text("About how many minutes?") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = "voice_review_duration" },
    )
    OutlinedTextField(
        value = state.dateText,
        onValueChange = { onEditDate(it.take(10)) },
        label = { Text("Day, YYYY-MM-DD") },
        singleLine = true,
        supportingText = {
            Text(
                "A day is a request, not a plan. A deadline is a different thing.",
                style = MaterialTheme.typography.labelMedium,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = "voice_review_date" },
    )

    ReviewSummary(state)

    if (state.draft.assumptions.isNotEmpty()) {
        Text("What Chrona assumed", style = MaterialTheme.typography.titleMedium)
        state.draft.assumptions.forEach { assumption ->
            Text(
                text = "• ${assumption.text}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { testTag = "voice_assumption" },
            )
        }
    }

    if (state.problems.isNotEmpty()) {
        Text(
            text = state.problems.joinToString(separator = "\n"),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { testTag = "voice_problems" },
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onConfirm,
            enabled = state.canSave,
            modifier = Modifier.semantics { testTag = "voice_save" },
        ) {
            Text("Save task")
        }
        TextButton(
            onClick = { onTypeInstead(state.title) },
            modifier = Modifier.semantics { testTag = "voice_change" },
        ) {
            Text("Change it")
        }
        TextButton(onClick = onReset, modifier = Modifier.semantics { testTag = "voice_discard" }) {
            Text("Discard")
        }
    }
}

/** The parts of a draft the user did not ask to edit, shown as plain sentences. */
@Composable
private fun ReviewSummary(state: VoiceUiState.Review) {
    val lines = buildList {
        state.draft.detail?.let { add("Details: $it") }
        state.draft.deadline?.let { add("Due by ${it.toLocalDate()} at the end of that day") }
        state.draft.preferredWindow?.let { add("Preferred time ${it.start} to ${it.end}") }
        state.draft.fixedRange?.let { add("Fixed from ${it.start.toLocalDate()} ${it.start.toLocalTime()} to ${it.end.toLocalTime()}") }
        if (state.draft.splittable == true) {
            add("Can be split" + (state.draft.minimumSession?.let { ", at least ${describe(it)} at a time" } ?: ""))
        }
        state.draft.proposedRelations.forEach { add("Suggested dependency on “${it.targetText}” (not yet a constraint)") }
    }
    if (lines.isEmpty()) return
    PinnedNoteCard(modifier = Modifier.fillMaxWidth()) {
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { testTag = "voice_review_summary" },
            )
        }
    }
}

@Composable
private fun Message(
    text: String,
    tag: String,
    onRetry: (() -> Unit)? = null,
    onTypeInstead: ((String) -> Unit)? = null,
    onReset: (() -> Unit)? = null,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        // One live region for the whole sheet, so state changes are announced once.
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                this.testTag = tag
                liveRegion = LiveRegionMode.Polite
            },
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onRetry?.let {
            OutlinedButton(onClick = it, modifier = Modifier.semantics { testTag = "voice_retry" }) {
                Text("Try again")
            }
        }
        onTypeInstead?.let {
            TextButton(
                onClick = { it("") },
                modifier = Modifier.semantics { testTag = "voice_type_instead" },
            ) {
                Text("Type instead")
            }
        }
        onReset?.let {
            TextButton(onClick = it, modifier = Modifier.semantics { testTag = "voice_close" }) {
                Text("Close")
            }
        }
    }
}

private fun describe(duration: Duration): String {
    val minutes = duration.toMinutes()
    return if (minutes % 60 == 0L) "${minutes / 60} h" else "$minutes min"
}