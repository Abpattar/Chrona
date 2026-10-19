package com.chrona.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Navigation route identity is shared state across Today/Schedule/Board/History/
 * Settings (UI-001). Routes must stay unique and stable, otherwise saved state
 * and future deep links break silently.
 */
class ChronaDestinationTest {

    @Test
    fun `every destination has a stable route in a known order`() {
        assertEquals(
            listOf("today", "schedule", "tasks", "board", "history", "settings", "simulation"),
            ChronaDestination.entries.map { it.route },
        )
    }

    /**
     * The bottom bar shows peers only.
     *
     * Tasks joined the bar so a user can actually reach their own list and the 140 simulated
     * tasks; Simulation stays out of it because six labels no longer fit a phone and it is a
     * place to inspect results rather than a daily surface.
     */
    @Test
    fun `the bottom bar shows exactly the top level destinations`() {
        assertEquals(
            listOf("today", "schedule", "tasks", "board", "history", "settings"),
            ChronaDestination.topLevel.map { it.route },
        )
    }

    @Test
    fun `simulation is reachable but is not a tab`() {
        assertTrue(ChronaDestination.entries.contains(ChronaDestination.SIMULATION))
        assertTrue(!ChronaDestination.topLevel.contains(ChronaDestination.SIMULATION))
    }

    @Test
    fun `routes are unique`() {
        val routes = ChronaDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun `every destination resolves a string label`() {
        ChronaDestination.entries.forEach { destination ->
            assertTrue(
                "missing label for ${destination.route}",
                destination.labelRes != 0,
            )
        }
    }

    @Test
    fun `today is the start destination`() {
        assertEquals(ChronaDestination.TODAY.route, ChronaDestination.entries.first().route)
    }
}