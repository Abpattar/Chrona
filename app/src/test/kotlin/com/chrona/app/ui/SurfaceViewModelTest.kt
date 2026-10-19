package com.chrona.app.ui

import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskStatus
import com.chrona.app.domain.simulation.InMemorySimulationStore
import com.chrona.app.domain.simulation.LifeSimulationEngine
import com.chrona.app.domain.simulation.productionSimulationEngine
import com.chrona.app.domain.task.label
import com.chrona.app.ui.history.HistoryUiState
import com.chrona.app.ui.history.TaskHistoryEntry
import com.chrona.app.ui.history.reasonLabel
import com.chrona.app.ui.simulation.SimulationViewModel
import com.chrona.app.ui.taskbrowser.TaskBrowserRow
import com.chrona.app.ui.taskbrowser.TaskBrowserUiState
import com.chrona.app.ui.taskbrowser.TaskBrowserViewModel
import com.chrona.app.ui.taskbrowser.TaskFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The browser's filtering and its join with the plan.
 *
 * The row state is pure, so these tests pin the rule the brief cares about most: an unscheduled
 * task must be listed *with its reason*, not quietly omitted. A list that only shows what fitted
 * is how a planner loses a user's trust without ever appearing to fail.
 */
class TaskBrowserUiStateTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val at10: ZonedDateTime = ZonedDateTime.of(2026, 1, 5, 10, 0, 0, 0, zone)

    private fun row(
        id: String,
        title: String,
        status: TaskStatus = TaskStatus.INBOX,
        reason: String? = null,
        fixed: Boolean = false,
        simulated: Boolean = false,
    ) = TaskBrowserRow(
        id = id,
        title = title,
        status = status,
        priority = Priority.IMPORTANT,
        isFixed = fixed,
        origin = if (simulated) com.chrona.app.domain.model.TaskOrigin.SIMULATION else com.chrona.app.domain.model.TaskOrigin.TYPED,
        deadline = null,
        scheduledStart = if (reason == null) at10 else null,
        scheduledEnd = if (reason == null) at10.plusHours(1) else null,
        unscheduledReason = reason,
        isSplit = false,
    )

    private val rows = listOf(
        row("1", "DBMS lecture", fixed = true),
        row("2", "Read documentation", reason = "There was no free time left in your available hours."),
        row("3", "Wake-up routine", status = TaskStatus.COMPLETED),
        row("4", "DBMS revision: normalisation", simulated = true),
        row("5", "Grocery shopping", simulated = true, reason = "Its deadline cannot be met."),
    )

    @Test
    fun `every task is listed when nothing is filtered`() {
        val state = TaskBrowserUiState(isLoading = false, rows = rows, totalCount = rows.size)
        assertEquals(5, state.visible.size)
    }

    @Test
    fun `an unscheduled task is listed with its reason rather than hidden`() {
        val state = TaskBrowserUiState(isLoading = false, rows = rows)
        val stranded = state.visible.single { it.id == "2" }
        assertNotNull("the reason must survive into the row", stranded.unscheduledReason)
    }

    @Test
    fun `search matches the title`() {
        val state = TaskBrowserUiState(isLoading = false, rows = rows, query = "dbms")
        assertEquals(setOf("1", "4"), state.visible.map { it.id }.toSet())
    }

    @Test
    fun `search is case insensitive and trims`() {
        val state = TaskBrowserUiState(isLoading = false, rows = rows, query = "  GROCERY ")
        assertEquals(listOf("5"), state.visible.map { it.id })
    }

    @Test
    fun `a search with no match shows an empty result, not the whole list`() {
        val state = TaskBrowserUiState(isLoading = false, rows = rows, query = "nothing matches this")
        assertTrue(state.visible.isEmpty())
    }

    @Test
    fun `each filter selects the right rows`() {
        fun visible(filter: TaskFilter) =
            TaskBrowserUiState(isLoading = false, rows = rows, filter = filter).visible.map { it.id }.toSet()

        assertEquals(setOf("1", "2", "4", "5"), visible(TaskFilter.ACTIVE))
        assertEquals(setOf("1"), visible(TaskFilter.FIXED))
        assertEquals(setOf("3"), visible(TaskFilter.COMPLETED))
        assertEquals(setOf("2", "5"), visible(TaskFilter.UNSCHEDULED))
        assertEquals(setOf("4", "5"), visible(TaskFilter.SIMULATED))
    }

    @Test
    fun `search and filter compose`() {
        val state = TaskBrowserUiState(
            isLoading = false,
            rows = rows,
            query = "dbms",
            filter = TaskFilter.SIMULATED,
        )
        assertEquals(listOf("4"), state.visible.map { it.id })
    }
}

/** The browser state holder: it must join tasks with the current plan. */
class TaskBrowserViewModelTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val at10: ZonedDateTime = ZonedDateTime.of(2026, 1, 5, 10, 0, 0, 0, zone)
    private val dispatcher = UnconfinedTestDispatcher()

    private fun task(id: String, title: String, status: TaskStatus = TaskStatus.INBOX) = Task(
        id = TaskId(id),
        title = title,
        priority = Priority.IMPORTANT,
        estimatedDuration = Duration.ofMinutes(30),
        status = status,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a scheduled task reports when it is scheduled`() = runTest(dispatcher) {
        val scheduled = task("a", "Scheduled")
        val vm = TaskBrowserViewModel(
            loadTasks = { Outcome.Success(listOf(scheduled)) },
            loadProposal = {
                Outcome.Success(
                    com.chrona.app.domain.scheduling.DeterministicScheduleEngine().propose(
                        com.chrona.app.domain.scheduling.ScheduleInput(
                            tasks = listOf(scheduled),
                            dayStart = at10.withHour(0),
                            dayEnd = at10.withHour(23),
                            now = at10.withHour(9),
                        ),
                    ),
                )
            },
            zoneId = zone,
        )
        vm.refresh()

        val row = vm.state.value.rows.single()
        assertNotNull("a placed task must show its slot", row.scheduledStart)
        assertNull(row.unscheduledReason)
    }

    @Test
    fun `a task with no slot is still listed with a reason`() = runTest(dispatcher) {
        val stranded = task("b", "Stranded")
        val vm = TaskBrowserViewModel(
            loadTasks = { Outcome.Success(listOf(stranded)) },
            loadProposal = {
                Outcome.Success(
                    com.chrona.app.domain.scheduling.DeterministicScheduleEngine().propose(
                        com.chrona.app.domain.scheduling.ScheduleInput(
                            tasks = listOf(stranded.copy(estimatedDuration = Duration.ofHours(30))),
                            dayStart = at10.withHour(0),
                            dayEnd = at10.withHour(23),
                            now = at10.withHour(9),
                        ),
                    ),
                )
            },
            zoneId = zone,
        )
        vm.refresh()

        val row = vm.state.value.rows.single()
        assertNull(row.scheduledStart)
        assertNotNull("an unscheduled task must carry a reason", row.unscheduledReason)
        assertTrue(vm.state.value.unscheduledCount == 1)
    }

    @Test
    fun `a load failure surfaces a message and no rows`() = runTest(dispatcher) {
        val vm = TaskBrowserViewModel(
            loadTasks = { Outcome.Failed(com.chrona.app.domain.model.Failure.StorageFailure("disk")) },
            loadProposal = { Outcome.Success(null) },
            zoneId = zone,
        )
        vm.refresh()

        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.rows.isEmpty())
        assertNotNull(vm.state.value.message)
    }
}

/** The simulation state holder: real counts, real controls, honest reporting. */
class SimulationViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(store: InMemorySimulationStore = InMemorySimulationStore()) =
        SimulationViewModel(store, productionSimulationEngine(store), dispatcher) to store

    @Test
    fun `before loading, the screen says so rather than showing zeros as results`() = runTest(dispatcher) {
        val (vm, _) = viewModel()
        assertFalse(vm.state.value.isStarted)
        assertNotNull(vm.state.value.message)
        assertNull(vm.state.value.checksPassed)
    }

    @Test
    fun `the declared dataset size is reported before any run`() = runTest(dispatcher) {
        val (vm, _) = viewModel()
        assertTrue(vm.state.value.taskDefinitionCount >= 40)
        assertTrue(vm.state.value.taskInstanceCount >= 50)
    }

    @Test
    fun `advancing one day advances exactly one day`() = runTest(dispatcher) {
        val (vm, _) = viewModel()
        vm.load()
        assertEquals(-1, vm.state.value.currentDay)

        vm.advanceDay()

        assertEquals(0, vm.state.value.currentDay)
        assertEquals(1, vm.state.value.days.size)
        assertTrue(vm.state.value.scheduledCount > 0)
    }

    @Test
    fun `advancing repeatedly retains the task pool rather than reseeding it`() = runTest(dispatcher) {
        val (vm, store) = viewModel()
        vm.load()
        vm.advanceDay()
        val afterFirst = (store.loadAll() as Outcome.Success).value.size

        vm.advanceDay()
        val afterSecond = (store.loadAll() as Outcome.Success).value.size

        assertTrue(
            "the pool should not shrink or be replaced",
            afterSecond >= afterFirst,
        )
        assertTrue(afterSecond >= 50)
    }

    @Test
    fun `run to end produces one report per simulated day`() = runTest(dispatcher) {
        val (vm, _) = viewModel()
        vm.load()

        vm.runToEnd()

        assertEquals(14, vm.state.value.days.size)
        assertTrue(vm.state.value.isComplete)
        assertFalse(vm.state.value.canAdvance)
    }

    @Test
    fun `reset clears the run and keeps user tasks`() = runTest(dispatcher) {
        val (vm, store) = viewModel()
        store.putIfAbsent(
            Task(
                id = TaskId("mine"),
                title = "Mine",
                status = TaskStatus.INBOX,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        )
        vm.load()
        vm.advanceDay()

        vm.reset()

        assertEquals(0, vm.state.value.days.size)
        assertEquals(-1, vm.state.value.currentDay)
        assertEquals(
            listOf("mine"),
            (store.loadAll() as Outcome.Success).value.map { it.id.value },
        )
        assertNotNull(vm.state.value.message)
    }

    @Test
    fun `the processing mode shown is the one the engine reported`() = runTest(dispatcher) {
        val store = InMemorySimulationStore()
        val vm = SimulationViewModel(
            store,
            LifeSimulationEngine(
                planner = com.chrona.app.domain.scheduling.DeterministicMultiDayPlanner(
                    com.chrona.app.domain.scheduling.DeterministicScheduleEngine(),
                ),
                store = store,
                processingMode = LifeSimulationEngine.PROCESSING_MODE_DETERMINISTIC,
            ),
            dispatcher,
        )
        vm.load()
        vm.advanceDay()

        assertEquals(LifeSimulationEngine.PROCESSING_MODE_DETERMINISTIC, vm.state.value.processingMode)
    }

    @Test
    fun `a host test run is reported only once imported`() = runTest(dispatcher) {
        val (vm, _) = viewModel()
        assertNull("the screen must not invent a test result", vm.state.value.checksPassed)

        vm.recordHostTestRun(406, 0, "2026-01-19T00:00:00Z")

        assertEquals(406, vm.state.value.checksPassed)
        assertEquals(0, vm.state.value.checksFailed)
        assertEquals("2026-01-19T00:00:00Z", vm.state.value.checksRunAt)
    }

    @Test
    fun `state survives a reload from storage`() = runTest(dispatcher) {
        val store = InMemorySimulationStore()
        val engine = productionSimulationEngine(store)
        val first = SimulationViewModel(store, engine, dispatcher)
        first.load()
        first.advanceDay()
        val recorded = first.state.value.currentDay

        // A second view model stands in for the app being restarted.
        val second = SimulationViewModel(store, engine, dispatcher)

        assertEquals(recorded, second.state.value.currentDay)
        assertEquals(1, second.state.value.days.size)
    }
}

/** Reason codes must read as English, not as machine tokens. */
class HistoryPresentationTest {

    @Test
    fun `every reason code the app writes has a readable label`() {
        listOf(
            "TASK_CREATED", "TASK_EDITED", "TASK_STARTED", "TASK_COMPLETED", "TASK_POSTPONED",
            "TASK_SKIPPED", "TASK_ARCHIVED", "TASK_RESTORED", "TASK_DELETED",
            "SCHEDULE_APPLIED", "SCHEDULE_UNDONE", "SCHEDULE_RECOVERY",
        ).forEach { code ->
            val label = reasonLabel(code)
            assertTrue("reason $code produced '$label'", label.isNotBlank())
            assertFalse("reason $code leaked its machine form", label.contains('_'))
        }
    }

    @Test
    fun `an unknown code degrades to readable words rather than an error`() {
        assertEquals("something new", reasonLabel("SOMETHING_NEW"))
    }

    @Test
    fun `a status change describes where it came from`() {
        val entry = TaskHistoryEntry(
            taskId = "t1",
            taskTitle = "DBMS revision",
            fromStatus = "PLANNED",
            toStatus = "COMPLETED",
            reasonCode = "TASK_COMPLETED",
            changedAt = Instant.EPOCH,
        )
        assertEquals("completed", reasonLabel(entry.reasonCode))
        assertTrue(TaskStatus.PLANNED.label().isNotBlank())
    }

    @Test
    fun `an empty history state is distinguishable from a loaded one`() {
        val empty = HistoryUiState()
        val loaded = HistoryUiState(isLoading = false, completedCount = 3)
        assertTrue(empty.isLoading)
        assertFalse(loaded.isLoading)
        assertEquals(3, loaded.completedCount)
    }

}
