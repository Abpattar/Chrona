package com.chrona.app.ui.voice

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.parse.ClarificationOption
import com.chrona.app.ui.design.ChronaTheme
import com.chrona.app.ui.design.glassSurface
import java.time.Duration

/**
 * What a capture says while it is happening.
 *
 * It is an overlay, not a screen: the rest of Chrona stays visible behind it, because a capture
 * is a moment inside whatever the user was doing rather than a place they have to return from.
 *
 * Every state is explicit — listening, working out the tasks, a single outstanding question, what
 * was stored, what went wrong — and typing stays reachable from all of them, so a failed capture
 * is never a dead end.
 */
@Composable
fun VoicePanel(
    state: VoiceUiState,
    onPermissionResult: (Boolean) -> Unit,
    onAnswer: (ClarificationOption) -> Unit,
    onEditTitle: (String) -> Unit,
    onEditDuration: (String) -> Unit,
    onEditDate: (String) -> Unit,
    onCreate: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onTypeInstead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is VoiceUiState.Idle) return

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
            .fillMaxWidth()
            // Capped and scrollable so a long clarification never pushes the microphone off a
            // short screen.
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState())
            .glassSurface(container = ChronaTheme.tokens.glassStrong)
            .padding(20.dp)
            .semantics {
                testTag = VOICE_PANEL_TAG
                liveRegion = LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            // The panel is not rendered in this state; the branch keeps the when exhaustive so a
            // new state can never fall through silently.
            VoiceUiState.Idle -> Unit

            VoiceUiState.RequestingPermission -> Status(
                title = "Chrona needs the microphone",
                message = "Nothing is recorded or stored. Typing works without this permission.",
                tag = "voice_permission",
            )

            is VoiceUiState.Listening -> ListeningContent(state)

            is VoiceUiState.Interpreting -> Status(
                title = "Working out your tasks",
                message = "“${state.transcript}”",
                tag = "voice_interpreting",
            )

            is VoiceUiState.Clarify -> ClarifyContent(
                state = state,
                onAnswer = onAnswer,
                onEditTitle = onEditTitle,
                onEditDuration = onEditDuration,
                onEditDate = onEditDate,
                onCreate = onCreate,
                onDismiss = onDismiss,
                onTypeInstead = onTypeInstead,
            )

            is VoiceUiState.Created -> CreatedContent(
                tasks = state.tasks,
                problems = state.problems,
                onDismiss = onDismiss,
                onTypeInstead = onTypeInstead,
            )

            is VoiceUiState.Unavailable -> Status(
                title = "Voice is unavailable",
                message = state.message,
                tag = "voice_unavailable",
                onPrimary = { TextButton(onClick = onDismiss) { Text("Close") } },
                onTypeInstead = onTypeInstead,
            )

            is VoiceUiState.Failed -> Status(
                title = "That did not work",
                message = state.message,
                tag = "voice_failed",
                onPrimary = if (state.canRetry) {
                    { OutlinedButton(onClick = onRetry) { Text("Try again") } }
                } else {
                    null
                },
                onTypeInstead = onTypeInstead,
            )
        }
    }
}

@Composable
private fun ListeningContent(state: VoiceUiState.Listening) {
    Status(
        title = "Listening",
        message = state.partialText?.takeIf { it.isNotBlank() }
            ?: "Hold the microphone and say everything that needs doing.",
        tag = "voice_listening",
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClarifyContent(
    state: VoiceUiState.Clarify,
    onAnswer: (ClarificationOption) -> Unit,
    onEditTitle: (String) -> Unit,
    onEditDuration: (String) -> Unit,
    onEditDate: (String) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
    onTypeInstead: () -> Unit,
) {
    Text(
        text = "One detail is ambiguous",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = state.question,
        style = MaterialTheme.typography.bodyLarge,
        color = ChronaTheme.tokens.accentBright,
        modifier = Modifier.semantics { testTag = "voice_question" },
    )

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.options.forEachIndexed { index, option ->
            FilterChip(
                selected = false,
                onClick = { onAnswer(option) },
                label = { Text(option.label) },
                modifier = Modifier.semantics { testTag = "voice_option_$index" },
            )
        }
    }

    Text(
        text = "Or set it yourself:",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

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
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = "voice_review_date" },
    )

    if (state.draft.assumptions.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "What Chrona assumed",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.draft.assumptions.forEach { assumption ->
                Text(
                    text = "• ${assumption.text}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { testTag = "voice_assumption" },
                )
            }
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
        OutlinedButton(
            onClick = onCreate,
            enabled = state.canCreate,
            modifier = Modifier.semantics { testTag = "voice_save" },
        ) {
            Text("Add this task")
        }
        TextButton(
            onClick = onTypeInstead,
            modifier = Modifier.semantics { testTag = "voice_change" },
        ) {
            Text("Type instead")
        }
        TextButton(onClick = onDismiss, modifier = Modifier.semantics { testTag = "voice_discard" }) {
            Text("Discard")
        }
    }
}

/**
 * The confirmation: every task this utterance produced, each on its own line.
 *
 * A count alone would hide a second task the user only half intended, so the titles are listed,
 * and anything that failed to store is named rather than dropped.
 */
@Composable
private fun CreatedContent(
    tasks: List<Task>,
    problems: List<String>,
    onDismiss: () -> Unit,
    onTypeInstead: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = ChronaTheme.tokens.accentBright,
        )
        Text(
            text = if (tasks.size == 1) "Added 1 task" else "Added ${tasks.size} tasks",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { testTag = "voice_saved" },
        )
    }

    tasks.forEach { task ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = "voice_created_${task.id.value}" },
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val detail = buildList {
                task.requestedDate?.let { add("On $it") }
                task.estimatedDuration?.let { add("about ${describe(it)}") }
            }
            if (detail.isNotEmpty()) {
                Text(
                    text = detail.joinToString(separator = " · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    problems.forEach { problem ->
        Text(
            text = problem,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { testTag = "voice_problems" },
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onDismiss, modifier = Modifier.semantics { testTag = "voice_close" }) {
            Text("Done")
        }
        TextButton(
            onClick = onTypeInstead,
            modifier = Modifier.semantics { testTag = "voice_type_instead" },
        ) {
            Text("Type instead")
        }
    }
}

/** One live region per panel, so a state change is announced once rather than per element. */
@Composable
private fun Status(
    title: String,
    message: String,
    tag: String,
    onPrimary: (@Composable () -> Unit)? = null,
    onTypeInstead: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics {
                this.testTag = tag
                liveRegion = LiveRegionMode.Polite
            },
        )
        if (onPrimary != null || onTypeInstead != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onPrimary?.let { it() }
                onTypeInstead?.let {
                    TextButton(
                        onClick = it,
                        modifier = Modifier.semantics { testTag = "voice_type_instead" },
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null)
                        Text("  Type instead")
                    }
                }
            }
        }
    }
}

private fun describe(duration: Duration): String {
    val minutes = duration.toMinutes()
    return if (minutes % 60 == 0L) "${minutes / 60} h" else "$minutes min"
}

const val VOICE_PANEL_TAG = "voice_panel"