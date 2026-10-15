package com.chrona.app.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import com.chrona.app.ui.design.ChronaTheme
import org.junit.Rule
import org.junit.Test

/**
 * Navigation shell smoke test (P01 gate: "navigation shell launches, no fatal logs").
 * Runs on an emulator or device via `connectedDebugAndroidTest`.
 */
class NavigationShellTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun launchesOnTodayAndEveryDestinationOpens() {
        composeRule.setContent {
            ChronaTheme { ChronaNavHost() }
        }

        composeRule.onNodeWithTag("today_screen").assertIsDisplayed()

        ChronaDestination.entries.forEach { destination ->
            composeRule.onNodeWithTag("nav_${destination.route}").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("${destination.route}_screen").assertIsDisplayed()
        }
    }

    @Test
    fun emptyStatesExplainThemselves() {
        composeRule.setContent {
            ChronaTheme { ChronaNavHost() }
        }

        composeRule.onNodeWithText("Nothing queued yet").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithText("Nothing to configure yet").assertIsDisplayed()
    }
}