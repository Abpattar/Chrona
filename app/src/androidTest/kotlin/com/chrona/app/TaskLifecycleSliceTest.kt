package com.chrona.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import com.chrona.app.data.local.ChronaDatabase
import com.chrona.app.data.repository.RoomTaskRepository
import com.chrona.app.di.AppContainer
import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.TaskQuery
import com.chrona.app.domain.model.TaskStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import java.time.Duration

/**
 * The P02 acceptance path, end to end on a real Android runtime:
 * create a typed task -> it is persisted -> it is listed -> complete it -> it is still there
 * after the database file is reopened.
 *
 * This drives the real Compose UI and the real Room database rather than fakes, because the
 * point of the phase is that the vertical slice genuinely crosses
 * UI -> use case -> domain -> repository -> SQLite.
 */
class TaskLifecycleSliceTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun container(): AppContainer =
        (composeRule.activity.application as ChronaApplication).container

    /**
     * Instrumentation does not wipe app data between test methods, so each test removes what it
     * created. Without this, a later test asserting an empty Today surface would see leftovers.
     */
    @After
    fun cleanUp() {
        runBlocking {
            val tasks = (container().taskRepository.list(TaskQuery.ACTIVE) as Outcome.Success).value +
                (container().taskRepository.list(TaskQuery.CLOSED) as Outcome.Success).value
            tasks.forEach { container().taskRepository.delete(it.id) }
        }
    }

    @Test
    fun createListAndComplete() {
        // 1. A typed task is created through the editor.
        composeRule.onNodeWithTag("today_add_task").performClick()
        composeRule.onNodeWithTag("editor_title").performTextInput("Study for GATE")
        composeRule.onNodeWithTag("editor_duration").performTextInput("120")
        composeRule.onNodeWithTag("editor_save").performClick()
        composeRule.waitForIdle()

        // 2. Storage holds exactly what was typed.
        val created = activeTasks()
        assertEquals(1, created.size)
        assertEquals("Study for GATE", created.single().title)
        assertEquals(Duration.ofHours(2), created.single().estimatedDuration)

        // 3. The row is visible on Today.
        composeRule.onNodeWithTag("task_note_${created.single().id.value}").assertIsDisplayed()

        // 4. Completing from the UI persists the completion and its audit row.
        composeRule.onNodeWithTag("complete_${created.single().id.value}").performClick()
        composeRule.waitForIdle()

        val closed = (runBlocking {
            container().taskRepository.list(TaskQuery.CLOSED)
        } as Outcome.Success).value
        assertEquals(1, closed.size)
        assertEquals(TaskStatus.COMPLETED, closed.single().status)
        assertNotNull(closed.single().completedAt)

        // Completed work leaves the active surface.
        assertEquals(0, activeTasks().size)
    }

    @Test
    fun theTaskSurvivesReopeningTheDatabaseFile() {
        composeRule.onNodeWithTag("today_add_task").performClick()
        composeRule.onNodeWithTag("editor_title").performTextInput("Call the dentist")
        composeRule.onNodeWithTag("editor_save").performClick()
        composeRule.waitForIdle()

        // Reopen the on-disk database the way a cold start does, instead of reusing the
        // connection that is already open. This is the "restarting the process does not lose
        // accepted changes" half of the P02 requirement.
        val reopened = Room.databaseBuilder(
            composeRule.activity,
            ChronaDatabase::class.java,
            ChronaDatabase.FILE_NAME,
        ).build()

        val rows = try {
            runBlocking { RoomTaskRepository(reopened).list(TaskQuery.ACTIVE) }
        } finally {
            reopened.close()
        }

        assertEquals(listOf("Call the dentist"), (rows as Outcome.Success).value.map { it.title })
    }

    @Test
    fun anEmptyTaskIsNeverSaved() {
        composeRule.onNodeWithTag("today_add_task").performClick()
        composeRule.onNodeWithTag("editor_save").assertIsNotEnabled()

        composeRule.onNodeWithTag("editor_title").performTextInput("   ")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("editor_save").assertIsNotEnabled()

        assertEquals(0, activeTasks().size)
    }

    @Test
    fun aTaskWithANewDurationBecomesEditableAndSaveable() {
        composeRule.onNodeWithTag("today_add_task").performClick()
        composeRule.onNodeWithTag("editor_title").performTextInput("Renew passport")
        composeRule.onNodeWithTag("editor_duration").performTextInput("45")
        composeRule.onNodeWithTag("editor_save").assertIsEnabled()
        composeRule.onNodeWithTag("editor_save").performClick()
        composeRule.waitForIdle()

        val task = activeTasks().single()
        assertEquals("Renew passport", task.title)
        assertEquals(Duration.ofMinutes(45), task.estimatedDuration)
    }

    @Test
    fun everyVisibleTaskCarriesAnExplicitPriorityAndStatusLabel() {
        composeRule.onNodeWithTag("today_add_task").performClick()
        composeRule.onNodeWithTag("editor_title").performTextInput("Renew passport")
        composeRule.onNodeWithTag("priority_CRITICAL").performClick()
        composeRule.onNodeWithTag("editor_save").performClick()
        composeRule.waitForIdle()

        // Priority is never carried by colour alone: the label is part of the note's text.
        composeRule.onNodeWithText("Very important").assertIsDisplayed()
        composeRule.onNodeWithText("in your list").assertIsDisplayed()
    }

    private fun activeTasks() = (runBlocking {
        container().taskRepository.list(TaskQuery.ACTIVE)
    } as Outcome.Success).value
}