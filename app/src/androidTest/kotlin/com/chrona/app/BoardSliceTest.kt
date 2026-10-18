package com.chrona.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.chrona.app.di.AppContainer
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.RelationType
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskQuery
import com.chrona.app.domain.model.TaskStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The P05 acceptance path on a real Android runtime: two tasks become notes, they are linked
 * with a typed relationship through the selection panel, and the same records are readable in
 * both the board canvas and the list alternative.
 *
 * Board and Today read one set of records; this proves the link is a real stored row and that
 * switching representation does not invent or drop a task.
 */
class BoardSliceTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun container(): AppContainer =
        (composeRule.activity.application as ChronaApplication).container

    private fun relationships() = (runBlocking {
        container().relationshipRepository.listAll()
    } as Outcome.Success).value

    private fun activeTasks() = (runBlocking {
        container().taskRepository.list(TaskQuery.ACTIVE)
    } as Outcome.Success).value

    /** Creates one typed task through the real editor and returns its stored id. */
    private fun createTask(title: String): TaskId {
        composeRule.onNodeWithTag("today_add_task").performClick()
        composeRule.onNodeWithTag("editor_title").performTextInput(title)
        composeRule.onNodeWithTag("editor_save").performClick()
        composeRule.waitForIdle()
        return activeTasks().first { it.title == title }.id
    }

    @After
    fun cleanUp() {
        runBlocking {
            // Relationships outlive a task deletion unless they are removed explicitly, and a
            // leaked link would make the next test's count wrong.
            relationships().forEach { container().relationshipRepository.delete(it.id) }
            val tasks = activeTasks() +
                (container().taskRepository.list(TaskQuery.CLOSED) as Outcome.Success).value
            tasks.forEach { container().taskRepository.delete(it.id) }
        }
    }

    /**
     * Drives the sheet's link editor: pick the target task, choose `Depends on`, confirm.
     *
     * The relation type is chosen rather than assumed — a link's meaning is explicit, and the
     * sheet defaults to `Supports`, so leaving it untouched would assert the wrong thing.
     */
    private fun link(source: TaskId, target: TaskId) {
        composeRule.onNodeWithTag("board_add_link_target").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("board_add_link_target_item_${target.value}").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("board_add_link_type").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("board_add_link_type_item_DEPENDS_ON").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("board_add_link_confirm").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun tasksAppearAsNotesAndCanBeLinked() {
        val first = createTask("Write GATE notes")
        val second = createTask("Revise them")

        composeRule.onNodeWithTag("nav_board").performClick()
        composeRule.waitForIdle()

        // Both records show as notes; the board renders the shared rows, not a copy.
        composeRule.onNodeWithTag("board_note_${first.value}").assertIsDisplayed()
        composeRule.onNodeWithTag("board_note_${second.value}").assertIsDisplayed()

        // Select the first note and link it to the second as a dependency.
        composeRule.onNodeWithTag("board_note_${first.value}").performClick()
        composeRule.waitForIdle()
        link(first, second)

        // The link is a real stored row between the two shared tasks.
        val stored = relationships().single()
        assertEquals(first, stored.sourceTaskId)
        assertEquals(second, stored.targetTaskId)
        assertEquals(RelationType.DEPENDS_ON, stored.type)
        assertTrue(stored.isActiveHardDependency)
    }

    @Test
    fun theListAlternativeShowsTheSameRecordsAndTheirLinks() {
        val first = createTask("Write GATE notes")
        val second = createTask("Revise them")

        composeRule.onNodeWithTag("nav_board").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("board_mode_list").performClick()
        composeRule.waitForIdle()

        // Same identities, different representation.
        composeRule.onNodeWithTag("board_list_note_${first.value}").assertIsDisplayed()
        composeRule.onNodeWithTag("board_list_note_${second.value}").assertIsDisplayed()

        // Link from the list alternative, which also runs through the selection panel.
        composeRule.onNodeWithTag("board_list_note_${first.value}").performClick()
        composeRule.waitForIdle()
        link(first, second)

        assertEquals(1, relationships().size)
    }

    @Test
    fun aDependencyStatesItsMeaningInWordsNotJustColour() {
        val first = createTask("Write GATE notes")
        val second = createTask("Revise them")

        composeRule.onNodeWithTag("nav_board").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("board_mode_list").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("board_list_note_${first.value}").performClick()
        composeRule.waitForIdle()
        link(first, second)

        val relationshipId = relationships().single().id.value
        composeRule.onNodeWithTag("board_link_$relationshipId").performClick()
        composeRule.waitForIdle()

        // Meaning is carried by a label and a sentence, never the red thread colour alone.
        composeRule.onNodeWithText("Depends on").assertIsDisplayed()
    }

    @Test
    fun closingTheTasksLeavesTheBoardEmptyAgain() {
        val first = createTask("Write GATE notes")

        composeRule.onNodeWithTag("nav_board").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("board_note_${first.value}").assertIsDisplayed()

        runBlocking { container().taskRepository.delete(first) }

        composeRule.onNodeWithTag("board_refresh").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Nothing to pin yet").assertIsDisplayed()
    }

    @Test
    fun aTaskCanBeCompletedFromTodayAndStillBelongsToTheSameRecord() {
        val id = createTask("Write GATE notes")

        composeRule.onNodeWithTag("nav_board").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("board_note_${id.value}").assertIsDisplayed()

        // Completing it on Today removes it from the open board filter without changing identity.
        composeRule.onNodeWithTag("nav_today").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("complete_${id.value}").performClick()
        composeRule.waitForIdle()
        // Completion is written from a coroutine; wait for the row to land before the board is
        // asked to read it, otherwise the assertion below is a race rather than a claim.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            (runBlocking { container().taskRepository.get(id) } as? Outcome.Success)
                ?.value?.status == TaskStatus.COMPLETED
        }

        composeRule.onNodeWithTag("nav_board").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Nothing to pin yet").assertIsDisplayed()

        // Same record, now closed; the board filter simply hides open work by default.
        val stored = (runBlocking { container().taskRepository.get(id) } as Outcome.Success).value
        assertEquals(TaskStatus.COMPLETED, stored?.status)
    }
}
