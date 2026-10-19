package com.chrona.app.domain.scheduling

import com.chrona.app.domain.model.TimeRange
import com.chrona.app.domain.model.TimeWindow
import com.chrona.app.domain.model.UserPreferences
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One day's absolute planning bounds plus the windows in which flexible work may be placed.
 *
 * [start]/[end] are the *horizon*: the span the engine is allowed to touch at all. [windows] is
 * the *capacity*: the sub-spans inside the horizon where the user says they are actually free.
 * When [windows] is empty the whole horizon is capacity, which is what a user who has never
 * opened Settings gets.
 */
data class DayHorizon(
    val date: LocalDate,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val windows: List<TimeRange>,
) {
    /**
     * True when flexible work cannot be placed at all.
     *
     * Only reachable when the user configured availability that does not overlap the horizon on
     * this date, because "no availability configured" is expanded to the full horizon by
     * [DayHorizonFactory] rather than left empty.
     */
    val isUnavailable: Boolean get() = windows.isEmpty()
}

/**
 * Turns [UserPreferences] into absolute planning bounds for one local date.
 *
 * Every configuration is total: a malformed or hostile preference can never throw here and
 * never produces a backwards or empty horizon. The rule for each oddity is deliberate rather
 * than a silent fallback:
 *
 * - `dayEnd` at or before `dayStart` means the user's day **wraps midnight** (a night-shift
 *   student who starts at 20:00 and stops at 02:00), so `end` lands on the following date.
 * - `dayStart == dayEnd` is rejected: an empty day and a 24-hour day are opposites and must not
 *   be confused. The day falls back to the full local day.
 * - Availability windows are clipped to the horizon, and if that leaves none, the day genuinely
 *   has no capacity: flexible work is then reported as unplaceable rather than being quietly
 *   pushed into an hour the user said they do not have.
 * - An availability window may not wrap midnight ([TimeWindow] rejects that). A day that runs
 *   past midnight is expressed with `dayStart`/`dayEnd`, which is handled above, so there is one
 *   way to say it rather than two that could disagree.
 */
object DayHorizonFactory {

    /** Fallback used when the user has expressed no preference at all. */
    private val FULL_DAY_START: LocalTime = LocalTime.MIDNIGHT

    fun horizonFor(
        date: LocalDate,
        preferences: UserPreferences,
        zone: ZoneId,
        /** Narrows capacity further, e.g. a day when the user has less time than usual. */
        reducedTo: List<TimeWindow> = emptyList(),
    ): DayHorizon {
        val startTime = preferences.dayStart ?: FULL_DAY_START
        val start = date.atTime(startTime).atZone(zone)

        val end = when (val dayEnd = preferences.dayEnd) {
            null -> date.plusDays(1).atStartOfDay(zone)
            else -> {
                val sameDayEnd = date.atTime(dayEnd).atZone(zone)
                if (sameDayEnd.isAfter(start)) sameDayEnd else date.plusDays(1).atTime(dayEnd).atZone(zone)
            }
        }

        val configured = (if (reducedTo.isNotEmpty()) reducedTo else preferences.availabilityWindows)
        val windows = if (configured.isEmpty()) {
            listOf(TimeRange(start, end))
        } else {
            // Clipped to the horizon before merging. Without the clip, a window the user set for
            // 02:00–03:00 would be reported back as available time on a 09:00–17:00 day, and the
            // header would state hours that cannot be used.
            mergeRanges(
                configured.mapNotNull { absoluteRange(it, date, zone) }
                    .mapNotNull { clipTo(it, start, end) },
            )
        }
        return DayHorizon(date, start, end, windows)
    }

    /**
     * The absolute span of a local-time [TimeWindow] on [date].
     *
     * [TimeWindow] requires its end to be after its start, so an availability window cannot wrap
     * midnight. A day that genuinely runs past midnight is expressed with `dayStart`/`dayEnd`
     * instead, which [horizonFor] handles; splitting that across two window types would give two
     * answers to the same question.
     */
    fun absoluteRange(window: TimeWindow, date: LocalDate, zone: ZoneId): TimeRange? {
        val start = date.atTime(window.start).atZone(zone)
        val end = date.atTime(window.end).atZone(zone)
        return if (end.isAfter(start)) TimeRange(start, end) else null
    }

    /** The part of [range] that lies inside `[start, end)`, or null when there is none. */
    private fun clipTo(range: TimeRange, start: ZonedDateTime, end: ZonedDateTime): TimeRange? {
        val from = maxOf(range.start, start)
        val to = minOf(range.end, end)
        return if (to.isAfter(from)) TimeRange(from, to) else null
    }
}
