package com.chrona.app.domain.simulation

import java.time.LocalDate
import java.time.ZoneId

/**
 * What the user (or life) does on one simulated day.
 *
 * These are the only things that may change a task's status during a run. In particular a task is
 * never completed because its scheduled time passed: completion and missing are user actions, and
 * the simulation scripts them so the run stays deterministic.
 */
data class DayScript(
    val completeTitles: List<String> = emptyList(),
    val missTitles: List<String> = emptyList(),
    val note: String = "",
)

/** A complete, reproducible scenario: a clock plus one [DayScript] per day. */
data class SimulationScenario(
    val id: String,
    val displayName: String,
    val description: String,
    val clock: SimulationClock,
    val days: Map<Int, DayScript>,
) {
    fun scriptFor(dayOffset: Int): DayScript = days[dayOffset] ?: DayScript()
}

/**
 * The scenarios the simulation ships with.
 *
 * Each one is named after the behaviour it is meant to force the planner to exhibit, so a test
 * can point at a scenario and assert something specific rather than "the run passed".
 */
object SimulationScenarios {

    val START_DATE: LocalDate = LocalDate.of(2026, 1, 5)
    val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

    /**
     * The habits a person completes on a day that went roughly to plan.
     *
     * These are ordinary low-stakes routines, which is exactly why they must never crowd out a
     * deadline: the trade-off policy is only real if something genuinely movable wants the slot.
     */
    private val ROUTINE_TITLES: List<String> = listOf(
        "Wake-up routine",
        "Flute practice: Sa-Re-Ga-Ma",
        "Dinner",
    )

    /**
     * Scenario 15: the complete 14-day life.
     *
     * The disruptions are the ones the brief calls for, spread across the run so that recovery is
     * exercised repeatedly rather than once: a missed study block on day 4, a two-hour loss on
     * day 6, a day with only a morning free on day 7, and a whole day lost to a family function
     * on day 13.
     *
     * Every day completes that day's routines as well as whatever else the script names, because a
     * life where nobody ever finishes their morning routine is not a life — and because the
     * "a completed task must not come back" rule needs real completions to be tested against.
     */
    val FULL_LIFE: SimulationScenario = SimulationScenario(
        id = "full-life",
        displayName = "Full 14-day life",
        description = "College, flute, routine, family and deadlines, with disruptions throughout.",
        clock = baseClock(
            interruptedOnDays = mapOf(5 to 120, 9 to 90),
            reducedDays = setOf(6),
            unavailableDays = setOf(12),
        ),
        days = mapOf(
            0 to DayScript(
                completeTitles = ROUTINE_TITLES + listOf("Breakfast", "Exercise"),
                note = "Ordinary first day: routine, flute practice and a light evening.",
            ),
            1 to DayScript(
                completeTitles = ROUTINE_TITLES + listOf("Breakfast", "Travel to college", "DBMS lecture"),
                note = "First college day.",
            ),
            2 to DayScript(
                completeTitles = ROUTINE_TITLES + listOf("Breakfast", "Travel to college"),
                note = "The DBMS assignment arrives this morning, after the first plan was made.",
            ),
            3 to DayScript(
                completeTitles = ROUTINE_TITLES + listOf("Breakfast", "Grocery shopping"),
                missTitles = listOf("DBMS revision: normalisation"),
                note = "Missed study session; recovery must keep it open and reconsider it.",
            ),
            4 to DayScript(
                completeTitles = ROUTINE_TITLES + listOf("Breakfast", "Flute class", "Prepare for the family function"),
                note = "Family function in the evening; the shop is closed earlier than usual.",
            ),
            5 to DayScript(
                completeTitles = ROUTINE_TITLES + listOf("Breakfast", "Physiotherapy session"),
                note = "Two hours lost to an unexpected interruption mid-morning.",
            ),
            6 to DayScript(
                completeTitles = ROUTINE_TITLES + listOf("Breakfast", "Group project: build the schema"),
                note = "Reduced availability: only the morning is workable.",
            ),
            7 to DayScript(
                completeTitles = ROUTINE_TITLES + listOf("Breakfast", "Faculty meeting", "Dinner with friends"),
                note = "Faculty meeting, then dinner with friends.",
            ),
            8 to DayScript(
                completeTitles = ROUTINE_TITLES + listOf("Breakfast", "Presentation rehearsal"),
                note = "Three extra tasks arrived; day 9 is deliberately overloaded.",
            ),
            9 to DayScript(
                completeTitles = ROUTINE_TITLES + listOf("Breakfast", "Rohan's birthday party"),
                note = "Birthday party, and a 90-minute interruption earlier in the day.",
            ),
            10 to DayScript(
                completeTitles = ROUTINE_TITLES + listOf("Breakfast"),
                note = "Ordinary day with a hard assignment due tomorrow.",
            ),
            11 to DayScript(
                completeTitles = ROUTINE_TITLES + listOf("Breakfast", "Aunt Meena's visit"),
                note = "Aunt Meena visits.",
            ),
            12 to DayScript(
                completeTitles = ROUTINE_TITLES + listOf("Breakfast", "Doctor appointment"),
                note = "Whole day consumed by a family function; nothing else could be planned.",
            ),
            13 to DayScript(
                completeTitles = ROUTINE_TITLES + listOf("Breakfast"),
                note = "Final day of the run.",
            ),
        ),
    )


    /** Scenario 1: one ordinary college day, planned and applied in isolation. */
    val NORMAL_COLLEGE_DAY: SimulationScenario = SimulationScenario(
        id = "normal-college-day",
        displayName = "Normal college day",
        description = "A single ordinary day, to check the plan is sensible before anything else runs.",
        clock = baseClock(totalDays = 1),
        days = mapOf(
            0 to DayScript(
                completeTitles = ROUTINE_TITLES + listOf("Breakfast", "Travel to college", "DBMS lecture"),
                note = "A normal college day.",
            ),
        ),
    )

    /**
     * Scenario 11: a single day the user has no hours for, while a full pool of work exists.
     *
     * One day rather than one closed day inside a fortnight: with 13 other days to absorb the
     * backlog, a single unavailable day is not an overload and the scenario would prove nothing.
     * With the whole horizon closed, "everything is unplaceable" is the honest result, and the
     * only acceptable behaviour is to report all of it.
     */
    val OVERLOADED_DAY: SimulationScenario = SimulationScenario(
        id = "overloaded-day",
        displayName = "Overloaded day",
        description = "More work than the day can hold; the surplus must be reported, not deleted.",
        clock = baseClock(totalDays = 1, unavailableDays = setOf(0)),
        days = mapOf(0 to DayScript(note = "The day has no capacity at all.")),
    )

    /** Scenario 14: a provider outage. The offline path must be indistinguishable in its result. */
    val PROVIDER_OUTAGE: SimulationScenario = SimulationScenario(
        id = "provider-outage",
        displayName = "Provider outage",
        description = "No AI provider is reachable; planning must be identical and fully local.",
        clock = baseClock(),
        days = mapOf(0 to DayScript(note = "AI provider unavailable; deterministic path only.")),
    )

    val ALL: List<SimulationScenario> = listOf(
        FULL_LIFE,
        NORMAL_COLLEGE_DAY,
        OVERLOADED_DAY,
        PROVIDER_OUTAGE,
    )

    fun byId(id: String): SimulationScenario? = ALL.firstOrNull { it.id == id }
}

/** The clock a scenario shares, apart from the disruptions that scenario needs. */
private fun baseClock(
    totalDays: Int = 14,
    interruptedOnDays: Map<Int, Int> = emptyMap(),
    reducedDays: Set<Int> = emptySet(),
    unavailableDays: Set<Int> = emptySet(),
) = SimulationClock(
    runId = SimulationDataset.RUN_ID,
    startDate = SimulationScenarios.START_DATE,
    totalDays = totalDays,
    zone = SimulationScenarios.ZONE,
    interruptedByMinutesOnDay = interruptedOnDays,
    reducedAvailabilityOnDays = reducedDays,
    unavailableDays = unavailableDays,
)
