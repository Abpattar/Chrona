package com.chrona.app.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ViewQuilt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chrona.app.R
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.RelationId
import com.chrona.app.domain.model.RelationType
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskStatus
import com.chrona.app.domain.task.label
import com.chrona.app.ui.design.ChronaColors
import com.chrona.app.ui.design.ChronaTheme
import com.chrona.app.ui.design.EmptyState
import com.chrona.app.ui.design.Pin
import com.chrona.app.ui.design.PinnedNoteCard
import com.chrona.app.ui.design.priorityStyleOf
import com.chrona.app.ui.design.shortLabel
import com.chrona.app.ui.today.ScreenHeader

private val NoteWidth = 140.dp
private val NoteHeight = 96.dp
private val NoteGap = 16.dp

/**
 * Board — the same task records as Today, pinned as notes and joined by red threads.
 *
 * The visual canvas and the list below it render one shared data set: a task is never copied
 * or re-created here. Because the canvas alone is not fully accessible, the list is a
 * first-class alternative with the same records, filters and actions (ACC-001).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    state: BoardUiState,
    onViewMode: (BoardViewMode) -> Unit,
    onSearch: (String) -> Unit,
    onToggleStatus: (TaskStatus) -> Unit,
    onTogglePriority: (Priority) -> Unit,
    onProject: (String?) -> Unit,
    onSelectTask: (TaskId) -> Unit,
    onSelectRelation: (RelationId) -> Unit,
    onAddLink: (TaskId, RelationType) -> Unit,
    onChangeLinkType: (RelationId, RelationType) -> Unit,
    onRemoveLink: (RelationId) -> Unit,
    onConfirmLink: (RelationId) -> Unit,
    onClearSelection: () -> Unit,
    onClearMessage: () -> Unit,
    onClearFilters: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTag = "board_screen" },
    ) {
        ScreenHeader(
            title = stringResource(R.string.nav_board),
            subtitle = "Your tasks as pinned notes, joined by typed relationships.",
            // Reloading lives in the header, because the board itself owns the whole area below
            // it and a control underneath the canvas could never be reached.
            trailing = {
                TextButton(onClick = onRefresh, modifier = Modifier.semantics { testTag = "board_refresh" }) {
                    Text("Refresh")
                }
            },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        state.message?.let { message ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { testTag = "board_message" },
                )
                TextButton(onClick = onClearMessage) { Text("Dismiss") }
            }
        }

        FilterBar(
            state = state,
            onSearch = onSearch,
            onToggleStatus = onToggleStatus,
            onTogglePriority = onTogglePriority,
            onProject = onProject,
            onClearFilters = onClearFilters,
        )

        ViewModeToggle(mode = state.viewMode, onViewMode = onViewMode)

        // The canvas and the list both fill this box, and only this box. Anything laid out as a
        // sibling would be measured against whatever the canvas left behind — which on a real
        // phone is nothing at all.
        Box(modifier = Modifier.weight(1f)) {
            when {
                state.isLoading -> Text(
                    text = "Reading your board\u2026",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )

                state.visibleTasks.isEmpty() -> EmptyState(
                    title = "Nothing to pin yet",
                    message = if (state.allTasks.isEmpty()) {
                        "Add a task on Today and it appears here as a note, ready to link."
                    } else {
                        "No task matches these filters."
                    },
                    icon = Icons.Outlined.ViewQuilt,
                )

                state.viewMode == BoardViewMode.BOARD -> BoardCanvas(
                    state = state,
                    onSelectTask = onSelectTask,
                )

                else -> BoardListAlternative(
                    state = state,
                    onSelectTask = onSelectTask,
                    onSelectRelation = onSelectRelation,
                )
            }
        }

        // Note and thread details sit over the board rather than under it: on a phone the canvas
        // needs the height, and the panel carries the actions that must always be reachable.
        if (state.selectedTask != null || state.selectedRelation != null) {
            ModalBottomSheet(
                onDismissRequest = onClearSelection,
                containerColor = ChronaTheme.tokens.glassStrong,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                SelectionPanel(
                    state = state,
                    onSelectTask = onSelectTask,
                    onSelectRelation = onSelectRelation,
                    onAddLink = onAddLink,
                    onChangeLinkType = onChangeLinkType,
                    onRemoveLink = onRemoveLink,
                    onConfirmLink = onConfirmLink,
                    onClearSelection = onClearSelection,
                )
            }
        }
    }
}

@Composable
private fun ViewModeToggle(mode: BoardViewMode, onViewMode: (BoardViewMode) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = mode == BoardViewMode.BOARD,
            onClick = { onViewMode(BoardViewMode.BOARD) },
            label = { Text("Board") },
            modifier = Modifier.semantics { testTag = "board_mode_board" },
        )
        FilterChip(
            selected = mode == BoardViewMode.LIST,
            onClick = { onViewMode(BoardViewMode.LIST) },
            label = { Text("List") },
            modifier = Modifier.semantics { testTag = "board_mode_list" },
        )
    }
}

@Composable
private fun FilterBar(
    state: BoardUiState,
    onSearch: (String) -> Unit,
    onToggleStatus: (TaskStatus) -> Unit,
    onTogglePriority: (Priority) -> Unit,
    onProject: (String?) -> Unit,
    onClearFilters: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = state.filter.search,
            onValueChange = onSearch,
            label = { Text("Search title or project") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = "board_search" },
        )
        HorizontalScrollRow {
            Priority.entries.forEach { priority ->
                FilterChip(
                    selected = priority in state.filter.priorities,
                    onClick = { onTogglePriority(priority) },
                    label = { Text(priority.shortLabel()) },
                    modifier = Modifier.semantics { testTag = "board_filter_priority_${priority.name}" },
                )
            }
        }
        HorizontalScrollRow {
            TaskStatus.entries.forEach { status ->
                FilterChip(
                    selected = status in state.filter.statuses,
                    onClick = { onToggleStatus(status) },
                    label = { Text(status.label()) },
                    modifier = Modifier.semantics { testTag = "board_filter_status_${status.name}" },
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProjectDropdown(
                projects = state.projects,
                selected = state.filter.projectLabel,
                onSelect = onProject,
            )
            TextButton(
                onClick = onClearFilters,
                modifier = Modifier.semantics { testTag = "board_clear_filters" },
            ) { Text("Clear filters") }
            Text(
                text = "${state.visibleTasks.size} shown",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HorizontalScrollRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun ProjectDropdown(projects: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { testTag = "board_project_filter" },
        ) { Text("Project: ${selected ?: "all"}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("All projects") },
                onClick = { onSelect(null); expanded = false },
            )
            projects.forEach { project ->
                DropdownMenuItem(
                    text = { Text(project) },
                    onClick = { onSelect(project); expanded = false },
                )
            }
        }
    }
}

/**
 * The pinned-note canvas. Pan and zoom respond only to deliberate gestures; nothing animates on
 * its own, so reduced-motion users are not subjected to motion (ACC-001). The list alternative
 * below carries the same records for assistive technology.
 */
@Composable
private fun BoardCanvas(
    state: BoardUiState,
    onSelectTask: (TaskId) -> Unit,
) {
    val layout = state.layout
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ChronaColors.Cork.copy(alpha = 0.35f))
            .clipToBounds()
            .semantics { testTag = "board_canvas" }
            .pointerInput(Unit) {
                detectTransformGestures { _, panChange, zoomChange, _ ->
                    scale = (scale * zoomChange).coerceIn(0.5f, 2.5f)
                    pan += panChange
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = pan.x
                    translationY = pan.y
                },
        ) {
            val cellWidthPx = with(density) { (NoteWidth + NoteGap).toPx() }
            val cellHeightPx = with(density) { (NoteHeight + NoteGap).toPx() }
            val noteWidthPx = with(density) { NoteWidth.toPx() }
            val noteHeightPx = with(density) { NoteHeight.toPx() }
            val pad = with(density) { NoteGap.toPx() }

            Canvas(modifier = Modifier.fillMaxSize()) {
                layout.threads.forEach { thread ->
                    val from = Offset(
                        pad + thread.from.column * cellWidthPx + noteWidthPx / 2f,
                        pad + thread.from.row * cellHeightPx + noteHeightPx / 2f,
                    )
                    val to = Offset(
                        pad + thread.to.column * cellWidthPx + noteWidthPx / 2f,
                        pad + thread.to.row * cellHeightPx + noteHeightPx / 2f,
                    )
                    // A dormant suggestion is drawn faintly so it is visibly not yet a constraint.
                    val active = thread.relationship.constrainsOrder()
                    drawLine(
                        color = if (active) ChronaColors.Thread else ChronaColors.Thread.copy(alpha = 0.35f),
                        start = from,
                        end = to,
                        strokeWidth = if (active) 4f else 2f,
                    )
                }
            }

            layout.notes.forEach { note ->
                val task = state.allTasks.firstOrNull { it.id == note.taskId } ?: return@forEach
                BoardNoteView(
                    task = task,
                    note = note,
                    isSelected = task.id == state.selectedTaskId,
                    linkCount = state.relationships.count {
                        it.sourceTaskId == task.id || it.targetTaskId == task.id
                    },
                    modifier = Modifier.offset(
                        x = NoteGap + (NoteWidth + NoteGap) * note.column,
                        y = NoteGap + (NoteHeight + NoteGap) * note.row,
                    ),
                    onSelect = { onSelectTask(task.id) },
                )
            }
        }
    }
}

@Composable
private fun BoardNoteView(
    task: Task,
    note: BoardNote,
    isSelected: Boolean,
    linkCount: Int,
    modifier: Modifier,
    onSelect: () -> Unit,
) {
    val style = priorityStyleOf(task.priority)
    val label = "${style.label}. ${task.status.label()}. $linkCount links"
    PinnedNoteCard(
        modifier = modifier
            .size(width = NoteWidth, height = NoteHeight)
            .graphicsLayer { rotationZ = note.tiltDegrees }
            .clickable(onClick = onSelect)
            .semantics {
                contentDescription = "${task.title}. $label"
                testTag = "board_note_${task.id.value}"
            },
        accent = style.accent,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pin(color = if (isSelected) ChronaColors.Thread else ChronaTheme.tokens.pin)
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Text(
            text = "${style.label} · ${task.status.label()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        task.projectLabel?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Fully usable list alternative: same records, same filters, same actions — with text labels
 * for every state, so nothing depends on colour, tilt or position (ACC-001).
 */
@Composable
private fun BoardListAlternative(
    state: BoardUiState,
    onSelectTask: (TaskId) -> Unit,
    onSelectRelation: (RelationId) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTag = "board_list" },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.visibleRelationships.isNotEmpty()) {
            item(key = "links_header") { SectionLabel("Connections") }
            items(state.visibleRelationships, key = { it.id.value }) { relationship ->
                RelationRow(
                    relationship = relationship,
                    titles = state.titles,
                    onClick = { onSelectRelation(relationship.id) },
                )
            }
        }
        item(key = "tasks_header") { SectionLabel("Notes") }
        items(state.visibleTasks, key = { it.id.value }) { task ->
            ListNoteRow(task = task, onClick = { onSelectTask(task.id) })
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ListNoteRow(task: Task, onClick: () -> Unit) {
    val style = priorityStyleOf(task.priority)
    PinnedNoteCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { testTag = "board_list_note_${task.id.value}" },
        accent = style.accent,
    ) {
        Text(text = task.title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "${style.label} · ${task.status.label()}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        task.projectLabel?.let {
            Text(
                text = "Project: $it",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RelationRow(
    relationship: com.chrona.app.domain.model.TaskRelationship,
    titles: Map<TaskId, String>,
    onClick: () -> Unit,
) {
    val source = titles[relationship.sourceTaskId] ?: relationship.sourceTaskId.value
    val target = titles[relationship.targetTaskId] ?: relationship.targetTaskId.value
    PinnedNoteCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { testTag = "board_link_${relationship.id.value}" },
        accent = ChronaColors.Thread,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = relationIcon(relationship.type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = describeRelation(source, relationship.type, target),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text = orderMeaning(relationship),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The panel under the board: details for the selected note or thread. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionPanel(
    state: BoardUiState,
    onSelectTask: (TaskId) -> Unit,
    onSelectRelation: (RelationId) -> Unit,
    onAddLink: (TaskId, RelationType) -> Unit,
    onChangeLinkType: (RelationId, RelationType) -> Unit,
    onRemoveLink: (RelationId) -> Unit,
    onConfirmLink: (RelationId) -> Unit,
    onClearSelection: () -> Unit,
) {
    val task = state.selectedTask
    val relation = state.selectedRelation
    if (task == null && relation == null) return

    PinnedNoteCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .verticalScroll(rememberScrollState())
            .semantics { testTag = "board_selection_panel" },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = task?.title ?: "Relationship",
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = onClearSelection) { Text("Close") }
        }

        if (task != null) {
            Text(
                text = "${priorityStyleOf(task.priority).label} · ${task.status.label()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.selectedTaskLinks.isEmpty()) {
                Text(
                    text = "No links yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                state.selectedTaskLinks.forEach { link ->
                    LinkEditorRow(
                        relationship = link,
                        titles = state.titles,
                        onSelectRelation = onSelectRelation,
                        onChangeLinkType = onChangeLinkType,
                        onRemoveLink = onRemoveLink,
                        onConfirmLink = onConfirmLink,
                    )
                }
            }
            AddLinkRow(
                currentTaskId = task.id,
                tasks = state.allTasks.filter { it.id != task.id },
                onAddLink = onAddLink,
            )
        } else if (relation != null) {
            val source = state.titles[relation.sourceTaskId] ?: relation.sourceTaskId.value
            val target = state.titles[relation.targetTaskId] ?: relation.targetTaskId.value
            Text(
                text = describeRelation(source, relation.type, target),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = orderMeaning(relation),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SimpleDropdown(
                    label = relation.type.displayLabel(),
                    items = RelationType.entries.toList(),
                    itemLabel = { it.displayLabel() },
                    onSelect = { onChangeLinkType(relation.id, it) },
                    testTag = "board_relation_type",
                )
                if (relation.createdBy == com.chrona.app.domain.model.RelationCreatedBy.SUGGESTED &&
                    relation.confirmedAt == null
                ) {
                    TextButton(
                        onClick = { onConfirmLink(relation.id) },
                        modifier = Modifier.semantics { testTag = "board_confirm_link" },
                    ) { Text("Confirm") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onRemoveLink(relation.id) },
                    modifier = Modifier.semantics { testTag = "board_remove_link" },
                ) { Text("Remove link") }
                TextButton(onClick = { onSelectTask(relation.sourceTaskId) }) { Text("Open source") }
            }
        }
    }
}

@Composable
private fun LinkEditorRow(
    relationship: com.chrona.app.domain.model.TaskRelationship,
    titles: Map<TaskId, String>,
    onSelectRelation: (RelationId) -> Unit,
    onChangeLinkType: (RelationId, RelationType) -> Unit,
    onRemoveLink: (RelationId) -> Unit,
    onConfirmLink: (RelationId) -> Unit,
) {
    val source = titles[relationship.sourceTaskId] ?: relationship.sourceTaskId.value
    val target = titles[relationship.targetTaskId] ?: relationship.targetTaskId.value
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSelectRelation(relationship.id) },
    ) {
        Text(
            text = describeRelation(source, relationship.type, target),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = orderMeaning(relationship),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (relationship.createdBy == com.chrona.app.domain.model.RelationCreatedBy.SUGGESTED &&
                relationship.confirmedAt == null
            ) {
                TextButton(onClick = { onConfirmLink(relationship.id) }) { Text("Confirm") }
            }
            SimpleDropdown(
                label = relationship.type.displayLabel(),
                items = RelationType.entries.toList(),
                itemLabel = { it.displayLabel() },
                onSelect = { onChangeLinkType(relationship.id, it) },
                testTag = "board_link_type_${relationship.id.value}",
            )
            TextButton(onClick = { onRemoveLink(relationship.id) }) { Text("Remove") }
        }
    }
}

@Composable
private fun AddLinkRow(
    currentTaskId: TaskId,
    tasks: List<Task>,
    onAddLink: (TaskId, RelationType) -> Unit,
) {
    var targetId by remember { mutableStateOf<TaskId?>(null) }
    var type by remember { mutableStateOf(RelationType.SUPPORTS) }
    val titles = tasks.associate { it.id to it.title }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(text = "Add a link", style = MaterialTheme.typography.titleSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SimpleDropdown(
                label = "Choose task",
                items = tasks,
                itemLabel = { it.title },
                onSelect = { targetId = it.id },
                testTag = "board_add_link_target",
                itemTestTag = { "board_add_link_target_item_${it.id.value}" },
            )
            SimpleDropdown(
                label = type.displayLabel(),
                items = RelationType.entries.toList(),
                itemLabel = { it.displayLabel() },
                onSelect = { type = it },
                testTag = "board_add_link_type",
                itemTestTag = { "board_add_link_type_item_${it.name}" },
            )
            OutlinedButton(
                onClick = { targetId?.let { onAddLink(it, type) } },
                enabled = targetId != null && targetId != currentTaskId,
                modifier = Modifier.semantics { testTag = "board_add_link_confirm" },
            ) { Text("Add") }
        }
    }
}

@Composable
private fun <T> SimpleDropdown(
    label: String,
    items: List<T>,
    itemLabel: (T) -> String,
    onSelect: (T) -> Unit,
    testTag: String,
    /**
     * Tag per menu entry. The same words appear on the notes behind the sheet, so an entry can
     * only be addressed unambiguously by identity.
     */
    itemTestTag: ((T) -> String)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { this.testTag = testTag },
        ) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                val tag = itemTestTag?.invoke(item)
                DropdownMenuItem(
                    text = { Text(itemLabel(item)) },
                    onClick = { onSelect(item); expanded = false },
                    modifier = if (tag == null) {
                        Modifier
                    } else {
                        Modifier.semantics { this.testTag = tag }
                    },
                )
            }
        }
    }
}

private fun orderMeaning(relationship: com.chrona.app.domain.model.TaskRelationship): String =
    if (relationship.constrainsOrder()) {
        "This link constrains order."
    } else if (relationship.type.isHardDependency) {
        "Suggested dependency — not active until confirmed."
    } else {
        "Informational link. It does not constrain order."
    }

private fun relationIcon(type: RelationType): ImageVector = when (type) {
    RelationType.DEPENDS_ON -> Icons.Outlined.AccountTree
    RelationType.SUPPORTS -> Icons.Outlined.FavoriteBorder
    RelationType.PART_OF_PROJECT -> Icons.Outlined.Folder
    RelationType.SAME_EVENT -> Icons.Outlined.Event
    RelationType.RELATED_REFERENCE -> Icons.Outlined.Article
}

@Preview(showBackground = true)
@Composable
private fun BoardScreenPreview() {
    ChronaTheme {
        Surface {
            BoardScreen(
                state = BoardUiState(
                    isLoading = false,
                    allTasks = listOf(
                        Task(
                            id = TaskId("preview-1"),
                            title = "Study for GATE",
                            priority = Priority.CRITICAL,
                            createdAt = java.time.Instant.EPOCH,
                            updatedAt = java.time.Instant.EPOCH,
                        ),
                    ),
                ),
                onViewMode = {},
                onSearch = {},
                onToggleStatus = {},
                onTogglePriority = {},
                onProject = {},
                onSelectTask = {},
                onSelectRelation = {},
                onAddLink = { _, _ -> },
                onChangeLinkType = { _, _ -> },
                onRemoveLink = {},
                onConfirmLink = {},
                onClearSelection = {},
                onClearMessage = {},
                onClearFilters = {},
                onRefresh = {},
            )
        }
    }
}
