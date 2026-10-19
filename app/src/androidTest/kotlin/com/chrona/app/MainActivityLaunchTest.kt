package com.chrona.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/**
 * P01 gate evidence: the debug app launches into Today/Home and the shell opens
 * without a fatal exception (PHASE_01_FOUNDATION.md "Required behavior").
 */
class MainActivityLaunchTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesOnToday() {
        composeRule.onNodeWithTag("today_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Nothing queued yet").assertIsDisplayed()
    }

    /**
     * Every peer destination is reachable from the bottom bar.
     *
     * Tasks joined the bar so the user can reach their own list; Simulation deliberately did not,
     * because six labels no longer fit and it is a detail surface reached from Today.
     */
    @Test
    fun bottomBarExposesEveryTopLevelDestination() {
        composeRule.onAllNodesWithTag("nav_today").assertCountEquals(1)
        composeRule.onAllNodesWithTag("nav_schedule").assertCountEquals(1)
        composeRule.onAllNodesWithTag("nav_tasks").assertCountEquals(1)
        composeRule.onAllNodesWithTag("nav_board").assertCountEquals(1)
        composeRule.onAllNodesWithTag("nav_history").assertCountEquals(1)
        composeRule.onAllNodesWithTag("nav_settings").assertCountEquals(1)
    }

    @Test
    fun theSimulationSurfaceIsReachableFromToday() {
        composeRule.onNodeWithTag("today_open_simulation").assertIsDisplayed()
        composeRule.onNodeWithTag("today_open_simulation").performClick()
        composeRule.onNodeWithTag("simulation_screen").assertIsDisplayed()
    }
}