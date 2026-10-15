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
    fun `all five top level destinations exist`() {
        assertEquals(
            listOf("today", "schedule", "board", "history", "settings"),
            ChronaDestination.entries.map { it.route },
        )
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