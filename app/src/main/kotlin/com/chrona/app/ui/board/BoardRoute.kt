package com.chrona.app.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chrona.app.di.AppContainer
import com.chrona.app.domain.model.TaskQuery

/**
 * Binds [BoardViewModel] to [BoardScreen].
 *
 * The composition root is consulted only here. Board, Today and Schedule all read the same
 * `TaskRepository`, which is what keeps one canonical set of task records (P05).
 */
@Composable
fun BoardRoute(container: AppContainer) {
    val viewModel: BoardViewModel = viewModel(
        factory = BoardViewModel.Factory(
            loadTasks = { container.taskRepository.list(TaskQuery(includeStatuses = com.chrona.app.domain.model.TaskStatus.entries.toSet())) },
            loadRelationships = { container.relationshipRepository.listAll() },
            addLink = { source, target, type, label ->
                container.addRelationship(source, target, type, label)
            },
            updateLink = { relationship -> container.updateRelationship(relationship) },
            deleteLink = { id -> container.deleteRelationship(id) },
            confirmLink = { id -> container.confirmRelationship(id) },
        ),
    )

    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshOnEntry() }

    BoardScreen(
        state = state,
        onViewMode = viewModel::setViewMode,
        onSearch = viewModel::setSearch,
        onToggleStatus = viewModel::toggleStatus,
        onTogglePriority = viewModel::togglePriority,
        onProject = viewModel::setProject,
        onSelectTask = viewModel::selectTask,
        onSelectRelation = viewModel::selectRelation,
        onAddLink = viewModel::addLinkTo,
        onChangeLinkType = viewModel::changeLinkType,
        onRemoveLink = viewModel::removeLink,
        onConfirmLink = viewModel::confirmSuggestedLink,
        onClearSelection = viewModel::clearSelection,
        onClearMessage = viewModel::clearMessage,
        onClearFilters = viewModel::clearFilters,
        onRefresh = viewModel::refresh,
    )
}
