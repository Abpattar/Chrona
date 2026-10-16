package com.chrona.app.ui.board

import com.chrona.app.domain.model.Failure
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.RelationCreatedBy
import com.chrona.app.domain.model.RelationId
import com.chrona.app.domain.model.RelationType
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskOrigin
import com.chrona.app.domain.model.TaskRelationship
import com.chrona.app.domain.model.TaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * The Board state holder (UI-001, UI-002, REL-001, REL-002).
 *
 * Two claims matter here: the board renders the shared task records through the same filter and
 * view mode the user picks, and adding a link goes through validation — a cycle is refused and
 * a suggestion is stored dormant until confirmed.
 */
class BoardViewModelTest {

    private val start: Instant = Instant.parse("2026-10-15T08:00:00Z")

    private val tasks = mutableListOf(
        task("a", project = "GATE"),
        task("b", project = "GATE"),
        task("c", project = "Home", priority = Priority.LOW),
    )
    private val relationships = mutableListOf<TaskRelationship>()
    private var nextRelationId = 1
    private var failAdd = false

    private fun task(
        id: String,
        project: String? = null,
        priority: Priority = Priority.IMPORTANT,
        status: TaskStatus = TaskStatus.INBOX,
    ) = Task(
        id = TaskId(id),
        title = "Task $id",
        priority = priority,
        estimatedDuration = Duration.ofHours(1),
        projectLabel = project,
        status = status,
        origin = TaskOrigin.TYPED,
        createdAt = start,
        updatedAt = start,
    )

    private fun viewModel() = BoardViewModel(
        loadTasks = { Outcome.Success(tasks.toList()) },
        loadRelationships = { Outcome.Success(relationships.toList()) },
        addLink = { source, target, type, label ->
            if (failAdd) {
                Outcome.Failed(Failure.Validation(listOf("That link would create a loop.")))
            } else {
                val relationship = TaskRelationship(
                    id = RelationId("r${nextRelationId++}"),
                    sourceTaskId = source,
                    targetTaskId = target,
                    type = type,
                    label = label,
                    createdBy = RelationCreatedBy.USER,
                    confirmedAt = start,
                    createdAt = start,
                )
                relationships += relationship
                Outcome.Success(relationship)
            }
        },
        updateLink = { updated ->
            val index = relationships.indexOfFirst { it.id == updated.id }
            if (index >= 0) relationships[index] = updated
            Outcome.Success(updated)
        },
        deleteLink = { id ->
            relationships.removeAll { it.id == id }
            Outcome.Success(Unit)
        },
        confirmLink = { id ->
            val index = relationships.indexOfFirst { it.id == id }
            if (index < 0) {
                Outcome.Failed(Failure.NotFound)
            } else {
                val confirmed = relationships[index].copy(confirmedAt = start)
                relationships[index] = confirmed
                Outcome.Success(confirmed)
            }
        },
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh loads the shared task records`() {
        val vm = viewModel()

        assertFalse(vm.state.value.isLoading)
        assertEquals(setOf("a", "b", "c"), vm.state.value.allTasks.map { it.id.value }.toSet())
    }

    @Test
    fun `switching view mode does not change the records`() {
        val vm = viewModel()
        val before = vm.state.value.visibleTasks

        vm.setViewMode(BoardViewMode.LIST)

        assertEquals(BoardViewMode.LIST, vm.state.value.viewMode)
        assertEquals(before, vm.state.value.visibleTasks)
    }

    @Test
    fun `search narrows the visible tasks`() {
        val vm = viewModel()

        vm.setSearch("Task a")

        assertEquals(listOf("a"), vm.state.value.visibleTasks.map { it.id.value })
    }

    @Test
    fun `filtering by project narrows the visible tasks`() {
        val vm = viewModel()

        vm.setProject("Home")

        assertEquals(listOf("c"), vm.state.value.visibleTasks.map { it.id.value })
    }

    @Test
    fun `filtering by due date keeps only tasks due by the bound`() {
        tasks += task("due").copy(
            requestedDate = LocalDate.of(2026, 10, 17),
        )
        val vm = viewModel()

        vm.setDueOnOrBefore(LocalDate.of(2026, 10, 18))

        assertEquals(listOf("due"), vm.state.value.visibleTasks.map { it.id.value })
    }

    @Test
    fun `clearing filters restores the open tasks`() {
        val vm = viewModel()
        vm.setSearch("nothing matches this")

        vm.clearFilters()

        assertEquals(3, vm.state.value.visibleTasks.size)
    }

    @Test
    fun `a thread appears only when both ends are visible`() {
        val vm = viewModel()
        vm.selectTask(TaskId("a"))
        vm.addLinkTo(TaskId("b"), RelationType.DEPENDS_ON)

        // Filter b out; its thread must disappear with it.
        vm.setProject("GATE")
        vm.setSearch("Task a")

        assertTrue(vm.state.value.visibleIds.contains(TaskId("a")))
        assertFalse(vm.state.value.visibleIds.contains(TaskId("b")))
        assertTrue(vm.state.value.visibleRelationships.isEmpty())
    }

    @Test
    fun `adding a link from the selected task stores it and refreshes`() {
        val vm = viewModel()
        vm.selectTask(TaskId("a"))

        vm.addLinkTo(TaskId("b"), RelationType.DEPENDS_ON)

        assertEquals("Link added.", vm.state.value.message)
        assertEquals(1, vm.state.value.relationships.size)
        assertEquals(TaskId("a"), vm.state.value.relationships.single().sourceTaskId)
    }

    @Test
    fun `a refused link surfaces the reason and writes nothing`() {
        val vm = viewModel()
        vm.selectTask(TaskId("a"))
        failAdd = true

        vm.addLinkTo(TaskId("b"), RelationType.DEPENDS_ON)

        assertTrue(vm.state.value.message!!.contains("loop"))
        assertTrue(vm.state.value.relationships.isEmpty())
    }

    @Test
    fun `removing a link drops it and clears the relation selection`() {
        val vm = viewModel()
        vm.selectTask(TaskId("a"))
        vm.addLinkTo(TaskId("b"), RelationType.DEPENDS_ON)
        val id = vm.state.value.relationships.single().id
        vm.selectRelation(id)

        vm.removeLink(id)

        assertTrue(vm.state.value.relationships.isEmpty())
        assertNull(vm.state.value.selectedRelationId)
        assertEquals("Link removed.", vm.state.value.message)
    }

    @Test
    fun `the list alternative and the board show the same identities`() {
        val vm = viewModel()
        val boardIds = vm.state.value.visibleIds

        vm.setViewMode(BoardViewMode.LIST)
        val listIds = vm.state.value.visibleIds

        assertEquals(boardIds, listIds)
    }

    @Test
    fun `a load failure is reported without a stack trace`() {
        val vm = BoardViewModel(
            loadTasks = { Outcome.Failed(Failure.StorageFailure("disk")) },
            loadRelationships = { Outcome.Success(emptyList()) },
            addLink = { _, _, _, _ -> Outcome.Failed(Failure.UnknownFailure("x")) },
            updateLink = { Outcome.Failed(Failure.UnknownFailure("x")) },
            deleteLink = { Outcome.Failed(Failure.UnknownFailure("x")) },
            confirmLink = { Outcome.Failed(Failure.UnknownFailure("x")) },
        )

        assertEquals(
            "Chrona could not reach local storage. Try again.",
            vm.state.value.message,
        )
        assertFalse(vm.state.value.isLoading)
    }
}
