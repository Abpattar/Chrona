package com.chrona.app.ui.board

import com.chrona.app.domain.model.RelationCreatedBy
import com.chrona.app.domain.model.RelationId
import com.chrona.app.domain.model.RelationType
import com.chrona.app.domain.model.TaskId
import com.chrona.app.domain.model.TaskRelationship
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Wording rules (UI-002, REL-001).
 *
 * Meaning must never depend on colour alone, so every type has a distinct label and sentence.
 */
class RelationPresentationTest {

    private val start: Instant = Instant.parse("2026-10-15T08:00:00Z")

    @Test
    fun `every relation type has a distinct label`() {
        val labels = RelationType.entries.map { it.displayLabel() }

        assertEquals(labels.size, labels.toSet().size)
        assertTrue(labels.none { it.isBlank() })
    }

    @Test
    fun `every relation type has a distinct sentence`() {
        val sentences = RelationType.entries.map { describeRelation("A", it, "B") }

        assertEquals(sentences.size, sentences.toSet().size)
    }

    @Test
    fun `a dependency reads in the waiting direction`() {
        assertEquals(
            "\u201CA\u201D waits for \u201CB\u201D",
            describeRelation("A", RelationType.DEPENDS_ON, "B"),
        )
    }

    @Test
    fun `only a confirmed dependency constrains order`() {
        val confirmed = relationship(RelationType.DEPENDS_ON, confirmedAt = start)
        val suggested = relationship(
            RelationType.DEPENDS_ON,
            createdBy = RelationCreatedBy.SUGGESTED,
            confirmedAt = null,
        )
        val supportive = relationship(RelationType.SUPPORTS, confirmedAt = start)

        assertTrue(confirmed.constrainsOrder())
        assertFalse(suggested.constrainsOrder())
        assertFalse(supportive.constrainsOrder())
    }

    private fun relationship(
        type: RelationType,
        createdBy: RelationCreatedBy = RelationCreatedBy.USER,
        confirmedAt: Instant? = start,
    ) = TaskRelationship(
        id = RelationId("r1"),
        sourceTaskId = TaskId("a"),
        targetTaskId = TaskId("b"),
        type = type,
        createdBy = createdBy,
        confirmedAt = confirmedAt,
        createdAt = start,
    )
}
