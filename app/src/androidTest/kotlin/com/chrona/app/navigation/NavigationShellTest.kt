package com.chrona.app.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.chrona.app.MainActivity
import org.junit.Rule
import org.junit.Test

/**
 * Navigation shell smoke test (P01 gate: "navigation shell launches, no fatal logs").
 * Runs on an emulator or device via `connectedDebugAndroidTest`.
 */
class NavigationShellTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesOnTodayAndEveryDestinationOpens() {
        composeRule.onNodeWithTag("today_screen").assertIsDisplayed()

        ChronaDestination.entries.forEach { destination ->
            composeRule.onNodeWithTag("nav_${destination.route}").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("${destination.route}_screen").assertIsDisplayed()
        }
    }

    @Test
    fun emptyStatesExplainThemselves() {
        composeRule.onNodeWithTag("today_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Nothing queued yet").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_schedule").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("No day planned yet").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_board").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Nothing to pin yet").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_history").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Nothing recorded yet").assertIsDisplayed()

        // Settings has no empty state to reach: every control is always available, so what is
        // asserted is that the privacy centre itself is there to be read.
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_local_first_note").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theThemeWrapsEveryDestination() {
        // Guards against a screen being added outside the themed shell, which would silently
        // drop the paper palette and typography.
        composeRule.onNodeWithTag("chrona_bottom_bar").assertIsDisplayed()
        ChronaDestination.entries.forEach { destination ->
            composeRule.onNodeWithTag("nav_${destination.route}").performClick()
            composeRule.waitForIdle()
        }
        // Settings is a scrolling privacy centre; the note is below the fold on a phone.
        composeRule.onNodeWithTag("settings_local_first_note")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
