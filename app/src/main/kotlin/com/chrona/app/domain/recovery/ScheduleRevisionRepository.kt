package com.chrona.app.domain.recovery

import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.RevisionId
import com.chrona.app.domain.scheduling.ScheduleProposal
import com.chrona.app.domain.scheduling.ScheduleRevision
import java.time.Instant

/**
 * Storage contract for applied schedule revisions (SHARED_CONTRACT.md §Recovery/revision).
 *
 * It lives in the domain so the recovery use cases never depend on Room. The one invariant the
 * implementation must hold: apply and undo are each a single transaction that rechecks the
 * expected revision, so a stale proposal can never overwrite newer work (AT-016, AT-017).
 */
interface ScheduleRevisionRepository {

    /** The currently applied revision, or null when no plan has been applied yet. */
    suspend fun current(): Outcome<ScheduleRevision?>

    /**
     * Applies [proposal] if it is still valid: [expectedRevision] must equal the current
     * revision, and every task must still be at the version the proposal was built from.
     */
    suspend fun apply(
        proposal: ScheduleProposal,
        expectedRevision: RevisionId?,
        reasonCode: String,
        appliedAt: Instant,
    ): Outcome<RevisionId>

    /** Restores the plan before [revisionId], but only while it is current and safe to undo. */
    suspend fun undo(revisionId: RevisionId, appliedAt: Instant): Outcome<Unit>
}

/** Stable revision reason codes. Machine codes only; never raw user text. */
object ScheduleRevisionReason {
    const val APPLIED: String = "SCHEDULE_APPLIED"
    const val UNDONE: String = "SCHEDULE_UNDONE"
    const val RECOVERY: String = "SCHEDULE_RECOVERY"
}
