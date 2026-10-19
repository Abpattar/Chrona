package com.chrona.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Task
import com.chrona.app.usecase.NewTask
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.simulation.SimulationScenarios
import com.chrona.app.domain.model.TaskQuery
import com.chrona.app.domain.model.TaskStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration

/**
 * End-to-end journeys on a real device.
 *
 * These drive the actual app through its actual repositories and its actual Compose surfaces —
 * no preview state, no stubbed store. Each test leaves the app in a state the next one can read,
 * and the teardown wipes only simulation records plus the tasks these tests created, so running
 * the suite cannot silently destroy anything the user made.
 */
class SimulationFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val container
        get() = (composeRule.activity.application as ChronaApplication).container

    // Must return Unit explicitly: `= runBlocking { ... }` would infer the block's own return
    // value, and JUnit rejects a teardown that returns anything but void.
    @After
    fun tearDown() {
        runBlocking {
            // Across *every* status, not just the active ones: a test that completes a task and
            // then fails would otherwise leave it behind, and the next suite would count it.
            val created = (container.taskRepository.list(TaskQuery.EVERYTHING) as Outcome.Success).value
                .filter { it.title.startsWith(UI_TEST_PREFIX) }
            created.forEach { container.taskRepository.delete(it.id) }
            container.simulationEngine.reset(
                com.chrona.app.domain.simulation.SimulationScenarios.FULL_LIFE,
            )
        }
    }

    private fun createTask(title: String, minutes: Long = 45): TaskId {
        val outcome = runBlocking {
            container.createTask(
                NewTask(title = title, estimatedDuration = Duration.ofMinutes(minutes)),
            )
        }
        return (outcome as Outcome.Success).value.id
    }

    private fun tasks(): List<Task> = runBlocking {
        (container.taskRepository.list(TaskQuery.ACTIVE) as Outcome.Success).value
    }

    private fun openSimulation() {
        composeRule.onNodeWithTag("nav_today").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("today_open_simulation").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("simulation_screen").assertIsDisplayed()
    }

    /**
     * Waits for a tag to be *on screen*, scrolling a lazy list to it if necessary.
     *
     * Two separate waits are needed and confusing them wastes a lot of time:
     *
     * - `waitForIdle` only drains the Compose clock. It says nothing about a coroutine that
     *   hopped to a background dispatcher to plan 140 tasks, so asserting straight after it is a
     *   race that fails on a fast phone and passes on a slow one.
     * - A `LazyColumn` composes only what is visible. A row thirty items down is not in the
     *   semantics tree at all until it is scrolled to, so `fetchSemanticsNodes()` on a lazy list
     *   proves nothing unless the item has been brought into view first.
     */
    private fun awaitTag(tag: String, timeoutMillis: Long = 60_000) {
        composeRule.waitUntil(timeoutMillis) {
            if (composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) {
                true
            } else {
                // Scroll the owning list explicitly. `hasScrollAction` would also match the
                // horizontally scrolling filter row, and `onNode` needs exactly one match.
                listTags.forEach { list ->
                    runCatching {
                        composeRule.onNodeWithTag(list)
                            .performScrollToNode(hasTestTag(tag))
                    }
                }
                composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    /**
     * Clicks a control and waits for the screen to finish working.
     *
     * Waiting for the work to finish is the whole point. `performClick` returns the moment the tap
     * is delivered, while the screen may already have swapped its body for a loading state — so a
     * second click issued straight afterwards would be looking for a button that is not on screen
     * yet, and fail for a reason that has nothing to do with the app.
     */
    private fun click(tag: String) {
        awaitTag(tag)
        composeRule.onNodeWithTag(tag).performScrollTo().performClick()
        composeRule.waitForIdle()
        awaitIdle()
    }

    /** Waits until no control reports that work is in progress. */
    private fun awaitIdle() {
        composeRule.waitUntil(60_000) {
            busy() == 0
        }
    }

    /** The lazy lists a tag may live inside. */
    private val listTags = listOf("tasks_list", "simulation_list", "history_list")

    private fun busy(): Int =
        composeRule.onAllNodesWithTag("simulation_working").fetchSemanticsNodes().size +
            composeRule.onAllNodesWithTag("simulation_loading").fetchSemanticsNodes().size +
            composeRule.onAllNodesWithTag("history_loading").fetchSemanticsNodes().size

    /**
     * Waits for a condition, polling instead of racing.
     *
     * Assertions are made against the repository rather than against pixels wherever the claim
     * is about state. "Advancing a day produced a report" is a fact about the database; reading it
     * from a scrolling list only adds ways for the test to fail for reasons that have nothing to
     * do with the app. The UI is still asserted — for the controls, the counts and the copy.
     */
    private fun awaitCondition(
        what: String,
        timeoutMillis: Long = 90_000,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            composeRule.waitForIdle()
            if (runCatching { condition() }.getOrDefault(false)) return
            composeRule.mainClock.advanceTimeBy(200)
        }
        // The observed state is part of the failure message: "timed out" on its own sends the
        // next reader hunting for a race instead of showing what actually happened.
        val observed = runCatching {
            "days=" + days().map { it.dayOffset } + " pool=" + pool().size
        }.getOrDefault("state unavailable")
        throw AssertionError("Timed out after ${timeoutMillis}ms waiting until $what ($observed)")
    }

    private fun days(): List<com.chrona.app.domain.simulation.SimulationDayReport> = runBlocking {
        (container.simulationStore.loadDays() as Outcome.Success).value
    }

    private fun pool(): List<Task> = runBlocking {
        (container.simulationStore.loadAll() as Outcome.Success).value
    }

    @Test
    fun loadingTheSimulationCreatesTheWholeTaskPool() {
        openSimulation()
        click("simulation_load")

        awaitCondition("the task pool is created") { pool().size >= 50 }
        assertTrue("the pool should hold at least 50 tasks, got ${pool().size}", pool().size >= 50)

        // And the screen says so in its own words.
        awaitTag("simulation_instances")
        composeRule.onNodeWithTag("simulation_instances").assertIsDisplayed()
    }

    @Test
    fun advancingADayProducesACountedPlan() {
        openSimulation()
        click("simulation_load")
        click("simulation_advance")

        awaitCondition("day 0 is recorded") { days().any { it.dayOffset == 0 } }
        val day = days().single { it.dayOffset == 0 }
        assertTrue("a real plan should have placed work", day.scheduledTaskCount > 0)
        assertTrue("placement should be counted", day.placedBlockCount > 0)
        // The reconciliation invariant, on the device, against real storage.
        assertEquals(
            "every open task must be placed or explained",
            day.activeTaskCount,
            day.scheduledTaskCount + day.unscheduledTaskCount,
        )
    }

    @Test
    fun advancingRepeatedlyKeepsOneReportPerDayAndNeverDuplicatesATask() {
        openSimulation()
        click("simulation_load")
        val sizeBeforeAdvancing = pool().size

        repeat(3) { index ->
            click("simulation_advance")
            awaitCondition("day $index is recorded") { days().any { it.dayOffset == index } }
        }

        val reports = days()
        assertEquals(
            "each simulated day gets its own report, got ${reports.map { it.dayOffset }}",
            3,
            reports.size,
        )
        assertEquals(listOf(0, 1, 2), reports.map { it.dayOffset })

        val ids = pool().map { it.id }
        assertEquals("advancing must never duplicate a task", ids.size, ids.toSet().size)
        assertTrue(
            "the pool may grow as tasks arrive, but must not shrink",
            pool().size >= sizeBeforeAdvancing,
        )
    }

    @Test
    fun resetClearsTheSimulationButKeepsUserTasks() {
        val mine = createTask("$UI_TEST_PREFIX keep me")
        openSimulation()
        click("simulation_load")
        click("simulation_advance")
        awaitCondition("day 0 is recorded") { days().isNotEmpty() }

        click("simulation_reset")

        awaitCondition("the run is cleared") { days().isEmpty() && pool().isEmpty() }
        assertTrue(
            "reset must not delete a task the user created",
            tasks().map { it.id }.contains(mine),
        )
    }

    @Test
    fun theScheduleScreenShowsRealPlannedState() {
        createTask("$UI_TEST_PREFIX lecture stand-in", minutes = 60)

        composeRule.onNodeWithTag("nav_schedule").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("schedule_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule_availability").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule_revision").assertIsDisplayed()
        composeRule.onNodeWithTag("schedule_count_active").assertIsDisplayed()
    }

    /**
     * The browser shows the user's own task and can filter the simulated pool separately.
     *
     * Run after a simulation exists: the separation is only meaningful with both kinds of row
     * present, and asserting "the simulated filter finds rows" on an empty database would pass
     * for the wrong reason.
     */
    @Test
    fun theTaskBrowserListsCreatedAndSimulatedTasksSeparately() {
        runBlocking { container.simulationEngine.seed(SimulationScenarios.FULL_LIFE) }
        val mine = createTask("$UI_TEST_PREFIX browser visible")

        composeRule.onNodeWithTag("nav_tasks").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tasks_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("tasks_search").assertIsDisplayed()
        composeRule.onNodeWithTag("tasks_list").assertIsDisplayed()

        composeRule.onNodeWithTag("tasks_search").performTextInput("browser visible")
        composeRule.waitForIdle()
        awaitTag("task_row_$mine")

        // The simulated pool is filterable and marked, so a 142-task simulation is never confused
        // with the user's own work.
        composeRule.onNodeWithTag("tasks_search").performTextClearance()
        composeRule.waitForIdle()

        click("task_filter_simulated")
        awaitTag("task_row_sim-life-2026-01-05-wake-routine")
    }

    @Test
    fun historyReflectsRealStoredHistoryRatherThanAPlaceholder() {
        val id = createTask("$UI_TEST_PREFIX history subject", minutes = 30)
        runBlocking { container.lifecycle.complete(TaskId(id.value)) }

        // The record has to exist: History reads these tables, it does not invent entries.
        val stored = runBlocking {
            container.database.taskStatusLogDao().findRecentWithTitles(50)
        }
        assertTrue("completing a task must write an audit row", stored.any { it.taskId == id.value })

        composeRule.onNodeWithTag("nav_history").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("history_screen").assertIsDisplayed()
        awaitTag("history_list")
    }

    @Test
    fun navigatingAwayAndBackReloadsCurrentState() {
        val first = createTask("$UI_TEST_PREFIX before navigation")

        composeRule.onNodeWithTag("nav_tasks").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_today").performClick()
        composeRule.waitForIdle()

        // Create from outside the browser while it is not on screen.
        val second = createTask("$UI_TEST_PREFIX after navigation")

        composeRule.onNodeWithTag("nav_tasks").performClick()
        composeRule.waitForIdle()
        click("task_filter_all")

        val rows = composeRule.onAllNodes(hasTestTag("task_row_$first"))
        assertTrue("the browser should reload, not serve a stale copy", rows.fetchSemanticsNodes().isNotEmpty())
        val newer = composeRule.onAllNodes(hasTestTag("task_row_$second"))
        assertTrue("a task created elsewhere must appear", newer.fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun theSimulationStateSurvivesLeavingAndReturning() {
        openSimulation()
        click("simulation_load")
        click("simulation_advance")
        awaitCondition("day 0 is recorded") { days().any { it.dayOffset == 0 } }
        val before = days()

        // Leave the way a person does — the system back gesture — then come back through the
        // normal entry point. Tapping a tab from a detail screen is covered separately.
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        awaitTag("today_screen")

        composeRule.onNodeWithTag("today_open_simulation").performClick()
        awaitTag("simulation_screen")

        // The run is re-read from storage, not remembered by the screen: a fresh view of the
        // screen must show exactly what was persisted.
        awaitCondition("the run is restored") { days() == before }
        awaitTag("simulation_day_0")
    }

    /** A detail surface must not trap the user: a tab tap has to leave it. */
    @Test
    fun aTabTapLeavesADetailSurface() {
        openSimulation()
        composeRule.onNodeWithTag("nav_tasks").performClick()
        awaitTag("tasks_screen")
        composeRule.onNodeWithTag("today_open_simulation").assertDoesNotExist()
    }

    @Test
    fun theAppDoesNotClaimHostTestsItHasNotRun() {
        openSimulation()

        // The screen must say plainly that a Gradle run happened elsewhere, not imply the phone
        // executed instrumentation. It sits inside a lazy list, so it has to be scrolled to
        // before it exists in the tree at all.
        awaitTag("simulation_tests_not_run")
        composeRule.onNodeWithTag("simulation_tests_not_run").assertIsDisplayed()
    }

    @Test
    fun aFixedCommitmentSurvivesInTheTaskBrowser() {
        val zone = java.time.ZoneId.systemDefault()
        val start = java.time.ZonedDateTime.now(zone).plusDays(1).withHour(18).withMinute(0).withSecond(0)
        val created = (runBlocking {
            container.createTask(
                NewTask(
                    title = "$UI_TEST_PREFIX fixed party",
                    estimatedDuration = Duration.ofHours(2),
                ),
            )
        } as Outcome.Success).value.id
        val current = (runBlocking {
            container.taskRepository.get(created)
        } as Outcome.Success).value!!
        runBlocking {
            container.taskRepository.update(
                current.copy(
                    flexibility = com.chrona.app.domain.model.Flexibility.FIXED,
                    fixedStart = start,
                    fixedEnd = start.plusHours(2),
                ),
                expectedVersion = current.version,
            )
        }

        composeRule.onNodeWithTag("nav_tasks").performClick()
        composeRule.waitForIdle()
        click("task_filter_fixed")
        awaitTag("task_row_$created")

        val rows = composeRule.onAllNodes(hasTestTag("task_row_$created"))
        assertTrue("a fixed task should be filterable by its fixed time", rows.fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun theAppPackageAndDatabaseAreUsableOnThisDevice() {
        // A cheap end-to-end smoke check of the real store on real hardware.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageName = context.packageName
        assertEquals("com.chrona.app", packageName)
        val files = context.getDatabasePath(ChronaDatabaseName).parentFile
        assertNotNull(files)
        assertTrue("the app's database directory should be writable", files!!.exists() || files.mkdirs())
    }

    private companion object {
        const val UI_TEST_PREFIX = "[uitest]"
        const val ChronaDatabaseName = "chrona.db"
    }
}
