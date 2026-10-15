package com.chrona.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

    @Test
    fun bottomBarExposesAllFiveDestinations() {
        composeRule.onAllNodesWithTag("nav_today").assertCountEquals(1)
        composeRule.onAllNodesWithTag("nav_schedule").assertCountEquals(1)
        composeRule.onAllNodesWithTag("nav_board").assertCountEquals(1)
        composeRule.onAllNodesWithTag("nav_history").assertCountEquals(1)
        composeRule.onAllNodesWithTag("nav_settings").assertCountEquals(1)
    }
}