package com.chrona.app.domain.simulation

import com.chrona.app.domain.model.TimeWindow
import com.chrona.app.domain.scheduling.PlanningDay
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The simulated clock.
 *
 * Deliberately **not** the device clock. A life simulation that read `Instant.now()` would
 * produce a different plan every run, so a regression could never be reproduced and "the same
 * inputs give the same result" would be untestable. Every timestamp the simulation touches comes
 * from here, which is what makes replay exact.
 */
data class SimulationClock(
    val runId: String,
    val startDate: LocalDate,
    val totalDays: Int,
    val zone: ZoneId,
    /** When on a simulated day the user "wakes", which is the `now` handed to the engine. */
    val dayStartTime: LocalTime = LocalTime.of(7, 0),
    val dayEndTime: LocalTime = LocalTime.of(23, 0),
    /** When on a simulated day planning stops. */
    val planningTime: LocalTime = LocalTime.of(20, 0),
    val availability: List<TimeWindow> = DEFAULT_AVAILABILITY,
    /** Time lost on this day before planning, e.g. an unexpected interruption. */
    val interruptedByMinutesOnDay: Map<Int, Int> = emptyMap(),
    /** A day where the user genuinely has less time than usual. */
    val reducedAvailabilityOnDays: Set<Int> = emptySet(),
    /** A date with no availability at all, e.g. a family function that consumes the whole day. */
    val unavailableDays: Set<Int> = emptySet(),
) {
    init {
        require(totalDays > 0) { "A simulation needs at least one day" }
    }

    fun dateOf(dayIndex: Int): LocalDate = startDate.plusDays(dayIndex.toLong())

    /** The reference instant planning uses on [dayIndex], after any recorded interruption. */
    fun referenceAt(dayIndex: Int): ZonedDateTime {
        val lost = interruptedByMinutesOnDay[dayIndex] ?: 0
        return dateOf(dayIndex).atTime(dayStartTime).atZone(zone).plusMinutes(lost.toLong())
    }

    /** Availability for one day, honouring reduced and fully unavailable days. */
    fun availabilityOn(dayIndex: Int): List<TimeWindow> = when {
        dayIndex in unavailableDays -> emptyList()
        dayIndex in reducedAvailabilityOnDays -> REDUCED_AVAILABILITY
        else -> availability
    }

    /** The planning days from [fromDay] to the end, each already shaped by this clock's rules. */
    fun planningDays(
        fromDay: Int,
        overrides: Map<Int, List<com.chrona.app.domain.model.TimeRange>> = emptyMap(),
    ): List<PlanningDay> =
        (fromDay until totalDays).map { index ->
            val start = dateOf(index).atTime(dayStartTime).atZone(zone)
            val end = dateOf(index).atTime(dayEndTime).atZone(zone)
            val windows = overrides[index]
                ?: availabilityOn(index).mapNotNull { window ->
                    com.chrona.app.domain.scheduling.DayHorizonFactory.absoluteRange(
                        window, dateOf(index), zone,
                    )
                }
            PlanningDay(
                date = dateOf(index),
                start = start,
                end = end,
                windows = windows,
                // A day the script declares impossible must genuinely have no capacity, not fall
                // back to the whole horizon the way an unconfigured day does.
                isAvailable = index !in unavailableDays,
            )
        }

    companion object {
        /**
         * A student's real day: a morning block, college in the afternoon, an evening block.
         * The 13:00–15:00 gap is lunch and the walk either way, which is why the planner cannot
         * simply treat 00:00–24:00 as one big free block.
         */
        val DEFAULT_AVAILABILITY: List<TimeWindow> = listOf(
            TimeWindow(LocalTime.of(7, 0), LocalTime.of(13, 0)),
            TimeWindow(LocalTime.of(15, 0), LocalTime.of(22, 30)),
        )

        /** A day when only the morning is workable, e.g. a morning that went badly. */
        val REDUCED_AVAILABILITY: List<TimeWindow> = listOf(
            TimeWindow(LocalTime.of(8, 0), LocalTime.of(12, 0)),
        )
    }
}
