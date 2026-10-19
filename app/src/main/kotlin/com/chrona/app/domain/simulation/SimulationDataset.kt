package com.chrona.app.domain.simulation

import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Priority
import java.time.LocalTime

/** Life areas the simulated student actually lives in. Presentation only; never a priority. */
enum class SimulatedCategory(val label: String) {
    ACADEMICS("College"),
    FLUTE("Flute"),
    HEALTH("Health & routine"),
    SOCIAL("Social & family"),
    PERSONAL("Personal"),
}

/**
 * One definition in the simulated life.
 *
 * A definition is a *template*, not a task. It becomes one or more concrete tasks with stable ids
 * through [materialise], which is what lets a recurring activity such as flute practice be a
 * linked set of instances instead of several unrelated rows that look like duplicates.
 */
data class SimulatedTaskDefinition(
    val id: String,
    val title: String,
    val category: SimulatedCategory,
    val priority: Priority,
    val durationMinutes: Int,
    /** Simulated day offset on which the first instance is requested. */
    val firstDayOffset: Int,
    /** Non-null makes this a recurring definition; [occurrences] instances are produced. */
    val repeatEveryDays: Int? = null,
    val occurrences: Int = 1,
    val flexibility: Flexibility = Flexibility.FLEXIBLE,
    /** Local start/end for a fixed commitment, e.g. a lecture or a birthday party. */
    val fixedStartTime: LocalTime? = null,
    val fixedEndTime: LocalTime? = null,
    /** Simulated day offset on which the work is due. */
    val deadlineDayOffset: Int? = null,
    val deadlineTime: LocalTime = LocalTime.of(23, 0),
    val preferredStartTime: LocalTime? = null,
    val splittable: Boolean = false,
    val minimumSessionMinutes: Int? = null,
    /** Definition ids this one waits for. Resolved to task ids at materialisation time. */
    val dependsOn: List<String> = emptyList(),
) {
    init {
        require(durationMinutes > 0) { "$id needs a positive duration" }
        require(occurrences >= 1) { "$id needs at least one occurrence" }
        require((repeatEveryDays ?: 1) >= 1) { "$id cannot repeat backwards" }
        if (flexibility == Flexibility.FIXED) {
            require(fixedStartTime != null && fixedEndTime != null) {
                "$id is fixed and needs an explicit start and end"
            }
        }
        if (splittable) {
            require(minimumSessionMinutes != null) { "$id is splittable and needs a minimum session" }
        }
    }

    /** True when this definition produces more than one instance. */
    val isRecurring: Boolean get() = repeatEveryDays != null || occurrences > 1
}

/**
 * The concrete task an instance of a definition becomes.
 *
 * [id] is derived from the run id and the definition, so the same run replays to the same task
 * ids and history can be joined across days.
 */
data class SimulatedTaskSpec(
    val id: String,
    val definitionId: String,
    val title: String,
    val category: SimulatedCategory,
    val priority: Priority,
    val durationMinutes: Int,
    val requestedDayOffset: Int,
    val flexibility: Flexibility = Flexibility.FLEXIBLE,
    val fixedStartTime: LocalTime? = null,
    val fixedEndTime: LocalTime? = null,
    val deadlineDayOffset: Int? = null,
    val deadlineTime: LocalTime = LocalTime.of(23, 0),
    val preferredStartTime: LocalTime? = null,
    val splittable: Boolean = false,
    val minimumSessionMinutes: Int? = null,
    val dependsOn: List<String> = emptyList(),
    /** Set when the task is injected part-way through the run rather than present at the start. */
    val arrivesOnDayOffset: Int? = null,
)

/**
 * The simulated life: 40 definitions that expand to 58 task instances across 14 days.
 *
 * Deliberately uneven. Some days are ordinary, one is a family function that eats the evening,
 * one is an overloaded day that cannot possibly fit, and the flute class and the birthday party
 * are fixed commitments that flexible work has to work around. Workload is not spread evenly
 * because real term-time workload is not spread evenly, and an evenly loaded simulation would
 * never exercise the capacity rules that matter.
 *
 * Every instance is a separate task with its own id and its own lifecycle. Nothing here is a
 * placeholder for work that never actually reaches the scheduler.
 */
object SimulationDataset {

    /** Fixed run id: replaying the same dataset must reproduce the same task ids. */
    const val RUN_ID: String = "life-2026-01-05"

    val DEFINITIONS: List<SimulatedTaskDefinition> = listOf(
        // ---- College: lectures and labs on several days, each a real fixed commitment --------
        SimulatedTaskDefinition(
            id = "lec-dbms", title = "DBMS lecture", category = SimulatedCategory.ACADEMICS,
            priority = Priority.CRITICAL, durationMinutes = 60,
            firstDayOffset = 1, repeatEveryDays = 2, occurrences = 5,
            flexibility = Flexibility.FIXED,
            fixedStartTime = LocalTime.of(9, 0), fixedEndTime = LocalTime.of(10, 0),
        ),
        SimulatedTaskDefinition(
            id = "lec-os", title = "Operating Systems lecture", category = SimulatedCategory.ACADEMICS,
            priority = Priority.CRITICAL, durationMinutes = 60,
            firstDayOffset = 1, repeatEveryDays = 2, occurrences = 5,
            flexibility = Flexibility.FIXED,
            fixedStartTime = LocalTime.of(10, 15), fixedEndTime = LocalTime.of(11, 15),
        ),
        SimulatedTaskDefinition(
            id = "lec-netsec", title = "Network Security lecture", category = SimulatedCategory.ACADEMICS,
            priority = Priority.CRITICAL, durationMinutes = 75,
            firstDayOffset = 1, repeatEveryDays = 7, occurrences = 2,
            flexibility = Flexibility.FIXED,
            fixedStartTime = LocalTime.of(11, 30), fixedEndTime = LocalTime.of(12, 45),
        ),
        SimulatedTaskDefinition(
            id = "lab-netsec", title = "Network Security lab", category = SimulatedCategory.ACADEMICS,
            priority = Priority.IMPORTANT, durationMinutes = 120,
            firstDayOffset = 2, repeatEveryDays = 7, occurrences = 2,
            flexibility = Flexibility.FIXED,
            fixedStartTime = LocalTime.of(14, 0), fixedEndTime = LocalTime.of(16, 0),
        ),
        SimulatedTaskDefinition(
            id = "commute-to", title = "Travel to college", category = SimulatedCategory.ACADEMICS,
            priority = Priority.IMPORTANT, durationMinutes = 45,
            firstDayOffset = 1, repeatEveryDays = 2, occurrences = 5,
            preferredStartTime = LocalTime.of(8, 0),
        ),
        SimulatedTaskDefinition(
            id = "commute-back", title = "Travel home from college", category = SimulatedCategory.ACADEMICS,
            priority = Priority.IMPORTANT, durationMinutes = 45,
            firstDayOffset = 1, repeatEveryDays = 2, occurrences = 5,
            preferredStartTime = LocalTime.of(16, 30),
        ),

        // ---- College: revision, deadlines and preparation -----------------------------------
        SimulatedTaskDefinition(
            id = "rev-dbms-1", title = "DBMS revision: normalisation", category = SimulatedCategory.ACADEMICS,
            priority = Priority.IMPORTANT, durationMinutes = 90,
            firstDayOffset = 1, deadlineDayOffset = 4, deadlineTime = LocalTime.of(18, 0),
            splittable = true, minimumSessionMinutes = 30,
        ),
        SimulatedTaskDefinition(
            id = "rev-os-1", title = "OS revision: deadlocks", category = SimulatedCategory.ACADEMICS,
            priority = Priority.IMPORTANT, durationMinutes = 75,
            firstDayOffset = 2, deadlineDayOffset = 5, deadlineTime = LocalTime.of(18, 0),
            splittable = true, minimumSessionMinutes = 25,
        ),
        SimulatedTaskDefinition(
            id = "rev-netsec-1", title = "Network Security revision: TLS handshake",
            category = SimulatedCategory.ACADEMICS,
            priority = Priority.IMPORTANT, durationMinutes = 60,
            firstDayOffset = 3, deadlineDayOffset = 6, deadlineTime = LocalTime.of(18, 0),
        ),
        SimulatedTaskDefinition(
            id = "lib-study-1", title = "Library study block", category = SimulatedCategory.ACADEMICS,
            priority = Priority.IMPORTANT, durationMinutes = 120,
            firstDayOffset = 2, repeatEveryDays = 5, occurrences = 2,
            preferredStartTime = LocalTime.of(10, 0),
        ),
        SimulatedTaskDefinition(
            id = "assign-group", title = "Group project: build the schema",
            category = SimulatedCategory.ACADEMICS,
            priority = Priority.CRITICAL, durationMinutes = 150,
            firstDayOffset = 4, deadlineDayOffset = 8, deadlineTime = LocalTime.of(17, 0),
            splittable = true, minimumSessionMinutes = 45,
        ),
        SimulatedTaskDefinition(
            id = "assign-group-dep", title = "Group project: write the report",
            category = SimulatedCategory.ACADEMICS,
            priority = Priority.CRITICAL, durationMinutes = 90,
            firstDayOffset = 4, deadlineDayOffset = 8, deadlineTime = LocalTime.of(17, 0),
            dependsOn = listOf("assign-group"),
        ),
        SimulatedTaskDefinition(
            id = "present-rehearsal", title = "Presentation rehearsal", category = SimulatedCategory.ACADEMICS,
            priority = Priority.IMPORTANT, durationMinutes = 60,
            firstDayOffset = 6, deadlineDayOffset = 9, deadlineTime = LocalTime.of(16, 0),
        ),
        SimulatedTaskDefinition(
            id = "email-prof", title = "Email Professor Nair about the lab extension",
            category = SimulatedCategory.ACADEMICS,
            priority = Priority.IMPORTANT, durationMinutes = 15,
            firstDayOffset = 3, deadlineDayOffset = 3, deadlineTime = LocalTime.of(18, 0),
        ),
        SimulatedTaskDefinition(
            id = "team-meeting", title = "Team meeting", category = SimulatedCategory.ACADEMICS,
            priority = Priority.IMPORTANT, durationMinutes = 45,
            firstDayOffset = 5, deadlineDayOffset = 5, deadlineTime = LocalTime.of(19, 0),
        ),
        SimulatedTaskDefinition(
            id = "faculty-meeting", title = "Faculty meeting", category = SimulatedCategory.ACADEMICS,
            priority = Priority.IMPORTANT, durationMinutes = 30,
            firstDayOffset = 7, flexibility = Flexibility.FIXED,
            fixedStartTime = LocalTime.of(15, 30), fixedEndTime = LocalTime.of(16, 0),
        ),
        SimulatedTaskDefinition(
            id = "exam-prep", title = "Network Security exam preparation",
            category = SimulatedCategory.ACADEMICS,
            priority = Priority.CRITICAL, durationMinutes = 180,
            firstDayOffset = 8, deadlineDayOffset = 12, deadlineTime = LocalTime.of(9, 0),
            splittable = true, minimumSessionMinutes = 45,
        ),
        SimulatedTaskDefinition(
            id = "placement-prep", title = "Internship preparation: resume rewrite",
            category = SimulatedCategory.ACADEMICS,
            priority = Priority.LOW, durationMinutes = 90,
            firstDayOffset = 6,
        ),

        // ---- Flute ---------------------------------------------------------------------------
        SimulatedTaskDefinition(
            id = "flute-class", title = "Flute class", category = SimulatedCategory.FLUTE,
            priority = Priority.CRITICAL, durationMinutes = 60,
            firstDayOffset = 2, repeatEveryDays = 7, occurrences = 2,
            flexibility = Flexibility.FIXED,
            fixedStartTime = LocalTime.of(18, 0), fixedEndTime = LocalTime.of(19, 0),
        ),
        SimulatedTaskDefinition(
            id = "flute-practice-sargama", title = "Flute practice: Sa-Re-Ga-Ma",
            category = SimulatedCategory.FLUTE,
            priority = Priority.IMPORTANT, durationMinutes = 30,
            firstDayOffset = 0, repeatEveryDays = 1, occurrences = 12,
        ),
        SimulatedTaskDefinition(
            id = "flute-breath", title = "Flute breath control exercise", category = SimulatedCategory.FLUTE,
            priority = Priority.LOW, durationMinutes = 15,
            firstDayOffset = 0, repeatEveryDays = 2, occurrences = 7,
        ),
        SimulatedTaskDefinition(
            id = "flute-rhythm", title = "Flute rhythm and tempo exercise", category = SimulatedCategory.FLUTE,
            priority = Priority.LOW, durationMinutes = 20,
            firstDayOffset = 1, repeatEveryDays = 3, occurrences = 4,
        ),
        SimulatedTaskDefinition(
            id = "flute-melody", title = "Learn the new melody", category = SimulatedCategory.FLUTE,
            priority = Priority.IMPORTANT, durationMinutes = 45,
            firstDayOffset = 3, deadlineDayOffset = 10, deadlineTime = LocalTime.of(20, 0),
            splittable = true, minimumSessionMinutes = 20,
        ),
        SimulatedTaskDefinition(
            id = "flute-record", title = "Record a practice take for review", category = SimulatedCategory.FLUTE,
            priority = Priority.LOW, durationMinutes = 25,
            firstDayOffset = 9, deadlineDayOffset = 11, deadlineTime = LocalTime.of(21, 0),
        ),

        // ---- Health and daily routine ---------------------------------------------------------
        SimulatedTaskDefinition(
            id = "wake-routine", title = "Wake-up routine", category = SimulatedCategory.HEALTH,
            priority = Priority.IMPORTANT, durationMinutes = 30,
            firstDayOffset = 0, repeatEveryDays = 1, occurrences = 14,
            preferredStartTime = LocalTime.of(7, 0),
        ),
        SimulatedTaskDefinition(
            id = "breakfast", title = "Breakfast", category = SimulatedCategory.HEALTH,
            priority = Priority.IMPORTANT, durationMinutes = 30,
            firstDayOffset = 0, repeatEveryDays = 2, occurrences = 7,
            preferredStartTime = LocalTime.of(8, 0),
        ),
        SimulatedTaskDefinition(
            id = "dinner", title = "Dinner", category = SimulatedCategory.HEALTH,
            priority = Priority.IMPORTANT, durationMinutes = 45,
            firstDayOffset = 0, repeatEveryDays = 2, occurrences = 7,
            preferredStartTime = LocalTime.of(20, 0),
        ),
        SimulatedTaskDefinition(
            id = "exercise", title = "Exercise", category = SimulatedCategory.HEALTH,
            priority = Priority.LOW, durationMinutes = 45,
            firstDayOffset = 1, repeatEveryDays = 3, occurrences = 4,
        ),
        SimulatedTaskDefinition(
            id = "physio", title = "Physiotherapy session", category = SimulatedCategory.HEALTH,
            priority = Priority.IMPORTANT, durationMinutes = 45,
            firstDayOffset = 5, repeatEveryDays = 7, occurrences = 2,
        ),
        SimulatedTaskDefinition(
            id = "shower-ready", title = "Shower and get ready", category = SimulatedCategory.HEALTH,
            priority = Priority.LOW, durationMinutes = 30,
            firstDayOffset = 1, repeatEveryDays = 2, occurrences = 5,
        ),
        // ---- Social and family ---------------------------------------------------------------
        SimulatedTaskDefinition(
            id = "birthday-party", title = "Rohan's birthday party", category = SimulatedCategory.SOCIAL,
            priority = Priority.CRITICAL, durationMinutes = 180,
            firstDayOffset = 9, flexibility = Flexibility.FIXED,
            fixedStartTime = LocalTime.of(19, 0), fixedEndTime = LocalTime.of(22, 0),
        ),
        SimulatedTaskDefinition(
            id = "birthday-gift", title = "Buy a birthday gift", category = SimulatedCategory.SOCIAL,
            priority = Priority.IMPORTANT, durationMinutes = 60,
            firstDayOffset = 6, deadlineDayOffset = 9, deadlineTime = LocalTime.of(15, 0),
            dependsOn = listOf("birthday-party"),
        ),
        SimulatedTaskDefinition(
            id = "family-function", title = "Family function at Uncle Raj's",
            category = SimulatedCategory.SOCIAL,
            priority = Priority.CRITICAL, durationMinutes = 240,
            firstDayOffset = 4, flexibility = Flexibility.FIXED,
            fixedStartTime = LocalTime.of(16, 0), fixedEndTime = LocalTime.of(20, 0),
        ),
        SimulatedTaskDefinition(
            id = "prepare-function", title = "Prepare for the family function",
            category = SimulatedCategory.SOCIAL,
            priority = Priority.IMPORTANT, durationMinutes = 60,
            firstDayOffset = 3, deadlineDayOffset = 4, deadlineTime = LocalTime.of(16, 0),
        ),
        SimulatedTaskDefinition(
            id = "relative-visit", title = "Aunt Meena's visit", category = SimulatedCategory.SOCIAL,
            priority = Priority.IMPORTANT, durationMinutes = 120,
            firstDayOffset = 11, flexibility = Flexibility.FIXED,
            fixedStartTime = LocalTime.of(17, 0), fixedEndTime = LocalTime.of(19, 0),
        ),
        SimulatedTaskDefinition(
            id = "dinner-friends", title = "Dinner with friends", category = SimulatedCategory.SOCIAL,
            priority = Priority.IMPORTANT, durationMinutes = 120,
            firstDayOffset = 7, flexibility = Flexibility.FIXED,
            fixedStartTime = LocalTime.of(19, 30), fixedEndTime = LocalTime.of(21, 30),
        ),
        SimulatedTaskDefinition(
            id = "call-family", title = "Call home", category = SimulatedCategory.SOCIAL,
            priority = Priority.LOW, durationMinutes = 20,
            firstDayOffset = 2, repeatEveryDays = 4, occurrences = 3,
        ),
        SimulatedTaskDefinition(
            id = "doctor-appointment", title = "Doctor appointment", category = SimulatedCategory.SOCIAL,
            priority = Priority.CRITICAL, durationMinutes = 60,
            firstDayOffset = 12, flexibility = Flexibility.FIXED,
            fixedStartTime = LocalTime.of(11, 0), fixedEndTime = LocalTime.of(12, 0),
        ),

        // ---- Personal and professional --------------------------------------------------------
        SimulatedTaskDefinition(
            id = "code-feature", title = "Build the attendance feature", category = SimulatedCategory.PERSONAL,
            priority = Priority.IMPORTANT, durationMinutes = 120,
            firstDayOffset = 1, deadlineDayOffset = 7, deadlineTime = LocalTime.of(20, 0),
            splittable = true, minimumSessionMinutes = 30,
        ),
        SimulatedTaskDefinition(
            id = "fix-bug", title = "Fix the reminder crash", category = SimulatedCategory.PERSONAL,
            priority = Priority.CRITICAL, durationMinutes = 60,
            firstDayOffset = 2, deadlineDayOffset = 3, deadlineTime = LocalTime.of(19, 0),
        ),
        SimulatedTaskDefinition(
            id = "review-code", title = "Review a friend's pull request", category = SimulatedCategory.PERSONAL,
            priority = Priority.LOW, durationMinutes = 30,
            firstDayOffset = 4, deadlineDayOffset = 8, deadlineTime = LocalTime.of(21, 0),
        ),
        SimulatedTaskDefinition(
            id = "demo-prep", title = "Prepare the project demonstration", category = SimulatedCategory.PERSONAL,
            priority = Priority.IMPORTANT, durationMinutes = 90,
            firstDayOffset = 7, deadlineDayOffset = 9, deadlineTime = LocalTime.of(18, 0),
        ),
        SimulatedTaskDefinition(
            id = "cyber-practice", title = "Cybersecurity practice on the CTF site",
            category = SimulatedCategory.PERSONAL,
            priority = Priority.LOW, durationMinutes = 60,
            firstDayOffset = 3, repeatEveryDays = 3, occurrences = 4,
        ),
        SimulatedTaskDefinition(
            id = "read-docs", title = "Read the Room documentation", category = SimulatedCategory.PERSONAL,
            priority = Priority.IMPORTANT, durationMinutes = 45,
            firstDayOffset = 2, deadlineDayOffset = 6, deadlineTime = LocalTime.of(20, 0),
        ),
        SimulatedTaskDefinition(
            id = "organise-notes", title = "Organise semester notes", category = SimulatedCategory.PERSONAL,
            priority = Priority.LOW, durationMinutes = 60,
            firstDayOffset = 5, deadlineDayOffset = 10, deadlineTime = LocalTime.of(20, 0),
        ),
        SimulatedTaskDefinition(
            id = "expenses", title = "Manage expenses", category = SimulatedCategory.PERSONAL,
            priority = Priority.LOW, durationMinutes = 30,
            firstDayOffset = 8, deadlineDayOffset = 12, deadlineTime = LocalTime.of(21, 0),
        ),
        SimulatedTaskDefinition(
            id = "groceries", title = "Grocery shopping", category = SimulatedCategory.PERSONAL,
            priority = Priority.IMPORTANT, durationMinutes = 60,
            firstDayOffset = 3, repeatEveryDays = 5, occurrences = 2,
        ),
        SimulatedTaskDefinition(
            id = "clean-room", title = "Clean the room", category = SimulatedCategory.PERSONAL,
            priority = Priority.LOW, durationMinutes = 45,
            firstDayOffset = 5,
        ),
        SimulatedTaskDefinition(
            id = "laundry", title = "Laundry", category = SimulatedCategory.PERSONAL,
            priority = Priority.LOW, durationMinutes = 40,
            firstDayOffset = 9, repeatEveryDays = 3, occurrences = 2,
        ),
        SimulatedTaskDefinition(
            id = "errand-station", title = "Errand: pick up the parcel", category = SimulatedCategory.PERSONAL,
            priority = Priority.LOW, durationMinutes = 30,
            firstDayOffset = 10, deadlineDayOffset = 12, deadlineTime = LocalTime.of(18, 0),
        ),
        SimulatedTaskDefinition(
            id = "gaming", title = "Gaming evening", category = SimulatedCategory.PERSONAL,
            priority = Priority.LOW, durationMinutes = 90,
            firstDayOffset = 2, repeatEveryDays = 4, occurrences = 3,
        ),
        SimulatedTaskDefinition(
            id = "free-time", title = "Unstructured free time", category = SimulatedCategory.PERSONAL,
            priority = Priority.LOW, durationMinutes = 60,
            firstDayOffset = 6, repeatEveryDays = 3, occurrences = 3,
        ),
    )

    /**
     * Expands every definition into its concrete task instances.
     *
     * Ids are `sim-<runId>-<definitionId>` for the first instance and `...-<n>` afterwards, so a
     * replay produces byte-identical ids and the history of a task can be joined across every
     * day it exists. [arrivals] injects tasks part-way through the run; those are excluded here
     * and returned separately by [instances] with their arrival day set.
     */
    fun instances(runId: String = RUN_ID): List<SimulatedTaskSpec> =
        DEFINITIONS.flatMap { definition ->
            (0 until definition.occurrences).map { ordinal ->
                val dayOffset = definition.firstDayOffset + (definition.repeatEveryDays ?: 1) * ordinal
                SimulatedTaskSpec(
                    id = if (ordinal == 0) "sim-$runId-${definition.id}"
                    else "sim-$runId-${definition.id}-$ordinal",
                    definitionId = definition.id,
                    title = definition.title,
                    category = definition.category,
                    priority = definition.priority,
                    durationMinutes = definition.durationMinutes,
                    requestedDayOffset = dayOffset,
                    flexibility = definition.flexibility,
                    fixedStartTime = definition.fixedStartTime,
                    fixedEndTime = definition.fixedEndTime,
                    deadlineDayOffset = definition.deadlineDayOffset,
                    deadlineTime = definition.deadlineTime,
                    preferredStartTime = definition.preferredStartTime,
                    splittable = definition.splittable,
                    minimumSessionMinutes = definition.minimumSessionMinutes,
                    // A dependency targets the first instance of its prerequisite: that is the
                    // occurrence the user actually meant.
                    dependsOn = definition.dependsOn.map { "sim-$runId-$it" },
                    arrivesOnDayOffset = null,
                )
            }
        }.filter { it.requestedDayOffset >= 0 }

    /** Tasks that show up mid-run rather than existing at the start. */
    fun lateArrivals(runId: String = RUN_ID): List<SimulatedTaskSpec> = listOf(
        // An assignment the user only learns about on day 2, due on day 5.
        SimulatedTaskSpec(
            id = "sim-$runId-late-assignment",
            definitionId = "late-assignment",
            title = "DBMS assignment: question set 4",
            category = SimulatedCategory.ACADEMICS,
            priority = Priority.CRITICAL,
            durationMinutes = 150,
            requestedDayOffset = 2,
            deadlineDayOffset = 5,
            deadlineTime = LocalTime.of(18, 0),
            splittable = true,
            minimumSessionMinutes = 45,
            arrivesOnDayOffset = 2,
        ),
        // A genuinely urgent piece of work that must displace flexible evening work.
        SimulatedTaskSpec(
            id = "sim-$runId-late-urgent",
            definitionId = "late-urgent",
            title = "OS urgent assignment: scheduling algorithms",
            category = SimulatedCategory.ACADEMICS,
            priority = Priority.CRITICAL,
            durationMinutes = 180,
            requestedDayOffset = 5,
            deadlineDayOffset = 7,
            deadlineTime = LocalTime.of(17, 0),
            arrivesOnDayOffset = 5,
        ),
        // A surprise party announced on day 3 for day 11, several days out.
        SimulatedTaskSpec(
            id = "sim-$runId-late-birthday",
            definitionId = "late-birthday",
            title = "Priya's surprise birthday party",
            category = SimulatedCategory.SOCIAL,
            priority = Priority.CRITICAL,
            durationMinutes = 150,
            requestedDayOffset = 11,
            flexibility = Flexibility.FIXED,
            fixedStartTime = LocalTime.of(20, 0),
            fixedEndTime = LocalTime.of(22, 30),
            arrivesOnDayOffset = 3,
        ),
        // Three extra tasks landing on an already full day 8. One of them will not fit, and the
        // point of the scenario is that it is *reported* rather than quietly dropped.
        SimulatedTaskSpec(
            id = "sim-$runId-overload-1",
            definitionId = "overload-1",
            title = "Rewrite the project README",
            category = SimulatedCategory.PERSONAL,
            priority = Priority.LOW,
            durationMinutes = 90,
            requestedDayOffset = 8,
            arrivesOnDayOffset = 6,
        ),
        SimulatedTaskSpec(
            id = "sim-$runId-overload-2",
            definitionId = "overload-2",
            title = "Update the linked-list exercises",
            category = SimulatedCategory.ACADEMICS,
            priority = Priority.IMPORTANT,
            durationMinutes = 120,
            requestedDayOffset = 8,
            arrivesOnDayOffset = 6,
        ),
        SimulatedTaskSpec(
            id = "sim-$runId-overload-3",
            definitionId = "overload-3",
            title = "Re-record the flute melody",
            category = SimulatedCategory.FLUTE,
            priority = Priority.LOW,
            durationMinutes = 60,
            requestedDayOffset = 8,
            arrivesOnDayOffset = 6,
        ),
    )
}
