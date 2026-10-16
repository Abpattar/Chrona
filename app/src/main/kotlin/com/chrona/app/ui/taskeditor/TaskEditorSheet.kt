package com.chrona.app.ui.taskeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.task.TaskValidator
import com.chrona.app.ui.design.shortLabel
import java.time.LocalDate

/**
 * Typed task creation — the complete first path to a stored task (TASK-001, UX-001).
 *
 * Voice capture enters this same sheet in P03; typing must remain usable on its own, with no
 * permission and no recognizer involved.
 *
 * The submit button is disabled while the domain validator rejects the input, and the reasons
 * are shown as text. Validation lives in `TaskValidator`, not here, so the editor and every
 * other entry point apply identical rules.
 */
@Composable
fun TaskEditorSheet(
    onSubmit: (
        title: String,
        details: String?,
        priority: Priority,
        estimatedMinutes: Int?,
        deadlineDate: LocalDate?,
    ) -> Unit,
    onDismiss: () -> Unit,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier,
    /** Text the editor opens with, used when a voice draft is handed over for correction. */
    initialTitle: String = "",
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var details by rememberSaveable { mutableStateOf("") }
    var durationText by rememberSaveable { mutableStateOf("") }
    var deadlineText by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf(Priority.IMPORTANT) }

    val problems = remember(title, durationText, deadlineText) {
        validate(title, durationText, deadlineText)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .semantics { testTag = "task_editor" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("New task", style = MaterialTheme.typography.headlineSmall)

        // Voice is offered here and nowhere else in the Today surface: one explicit tap, no
        // background listening, and typing stays available at every point.
        TextButton(
            onClick = onSpeak,
            modifier = Modifier.semantics { testTag = "editor_speak" },
        ) {
            Text("Speak instead")
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("What needs doing?") },
            singleLine = false,
            isError = title.isNotBlank() && problems.any { it.startsWith("A task needs a title") },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = "editor_title" },
        )

        OutlinedTextField(
            value = details,
            onValueChange = { details = it },
            label = { Text("Details (optional)") },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = "editor_details" },
        )

        OutlinedTextField(
            value = durationText,
            onValueChange = { durationText = it.filter(Char::isDigit).take(4) },
            label = { Text("About how many minutes? (optional)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = "editor_duration" },
        )

        OutlinedTextField(
            value = deadlineText,
            onValueChange = { deadlineText = it.take(10) },
            label = { Text("Due date, YYYY-MM-DD (optional)") },
            singleLine = true,
            supportingText = {
                Text(
                    // Deadline and preferred time are deliberately different things; say so.
                    "A due date is the latest acceptable day. It is not a request to schedule it then.",
                    style = MaterialTheme.typography.labelMedium,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = "editor_deadline" },
        )

        Text("Priority", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Priority.entries.forEach { option ->
                FilterChip(
                    selected = priority == option,
                    onClick = { priority = option },
                    // Label text carries the meaning; colour never does it alone.
                    label = { Text(option.shortLabel()) },
                    modifier = Modifier.semantics { testTag = "priority_${option.name}" },
                )
            }
        }

        if (problems.isNotEmpty()) {
            Text(
                text = problems.joinToString(separator = "\n"),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { testTag = "editor_problems" },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onSubmit(
                        title.trim(),
                        details.trim().takeIf { it.isNotEmpty() },
                        priority,
                        durationText.toIntOrNull(),
                        deadlineText.trim().takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                    )
                },
                enabled = problems.isEmpty(),
                modifier = Modifier.semantics { testTag = "editor_save" },
            ) {
                Text("Save task")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.semantics { testTag = "editor_cancel" }) {
                Text("Cancel")
            }
        }
    }
}

/**
 * Editor-side preview of the domain rules.
 *
 * Mirrors [TaskValidator] for immediate feedback, but the authoritative check still happens in
 * `CreateTaskUseCase`; the editor must never be the only thing validating a task.
 */
private fun validate(title: String, durationText: String, deadlineText: String): List<String> = buildList {
    TaskValidator.validateTitle(title)?.let { add(it) }
    val minutes = durationText.toIntOrNull()
    if (minutes != null && minutes <= 0) {
        add("Estimated duration must be greater than zero.")
    }
    if (minutes != null && minutes > 24 * 60) {
        add("Estimated duration cannot be longer than 24 hours.")
    }
    val trimmedDeadline = deadlineText.trim()
    if (trimmedDeadline.isNotEmpty()) {
        val parsed = runCatching { LocalDate.parse(trimmedDeadline) }.getOrNull()
        if (parsed == null) {
            add("Use the date format YYYY-MM-DD.")
        } else if (parsed < LocalDate.now()) {
            add("The deadline is already in the past.")
        }
    }
}

/** Wording shown on the priority chips; also the accessible label. */
fun Priority.displayLabel(): String = when (this) {
    Priority.LOW -> "Low"
    Priority.IMPORTANT -> "Important"
    Priority.CRITICAL -> "Very important"
}