import dayjs from 'dayjs'

const CATEGORY_WEIGHTS = {
    exam: 10,
    hackathon: 9,
    assignment: 8,
    meeting: 6,
    personal: 4,
    reminder: 2,
}

// Study time recommendations per category (in hours)
const STUDY_HOURS = {
    exam: 3,
    hackathon: 4,
    assignment: 2,
}

/**
 * Get the weight for a given category (case-insensitive).
 */
export function getCategoryWeight(category) {
    if (!category) return 3
    const key = category.toLowerCase().trim()
    return CATEGORY_WEIGHTS[key] ?? 3
}

/**
 * Calculate urgency score based on hours remaining.
 * Smoother 8-tier curve for finer priority transitions.
 */
export function getUrgencyScore(eventDatetime) {
    const now = dayjs()
    const eventTime = dayjs(eventDatetime)
    const hoursRemaining = eventTime.diff(now, 'hour', true)

    if (hoursRemaining <= 0) return 0   // Past
    if (hoursRemaining < 1) return 12   // Critical — imminent (ensures ALL categories turn red)
    if (hoursRemaining < 3) return 10   // Critical/High — very close
    if (hoursRemaining < 6) return 8    // High — approaching
    if (hoursRemaining < 12) return 6   // High
    if (hoursRemaining < 24) return 4   // Medium
    if (hoursRemaining < 48) return 3   // Medium-low
    return 2                            // Low — plenty of time
}

/**
 * Calculate priorityScore = category_weight + urgency_score
 */
export function getPriorityScore(event) {
    return getCategoryWeight(event.category) + getUrgencyScore(event.event_datetime)
}

/**
 * Get color based on priority score.
 */
export function getPriorityColor(score) {
    if (score > 15) return { bg: '#ff475720', border: '#ff4757', text: '#ff4757', label: 'Critical' }  // Red
    if (score > 10) return { bg: '#ffa50220', border: '#ffa502', text: '#ffa502', label: 'High' }      // Orange
    if (score > 5) return { bg: '#3498db20', border: '#3498db', text: '#3498db', label: 'Medium' }     // Blue
    return { bg: '#2ecc7120', border: '#2ecc71', text: '#2ecc71', label: 'Low' }                       // Green
}

/**
 * Calculate distance from center node based on priority.
 */
export function getNodeDistance(score) {
    return Math.max(100, 200 - score * 10)
}

/**
 * Sort events by priority (descending), then by datetime (ascending) for tiebreaker.
 */
export function sortByPriority(events) {
    return [...events].sort((a, b) => {
        const scoreA = getPriorityScore(a)
        const scoreB = getPriorityScore(b)
        if (scoreB !== scoreA) return scoreB - scoreA
        return dayjs(a.event_datetime).valueOf() - dayjs(b.event_datetime).valueOf()
    })
}

/**
 * Check if a category is a "study" type that generates study blocks.
 */
function isStudyCategory(category) {
    const studyCategories = ['exam', 'hackathon', 'assignment']
    return studyCategories.includes((category || '').toLowerCase().trim())
}

// ============================================================
// NOTIFICATION ALERT ENGINE
// ============================================================

/**
 * Get a notification alert for an event based on how close it is.
 * Returns null if no alert is needed, or an alert object.
 */
export function getNotificationAlert(event) {
    const now = dayjs()
    const eventTime = dayjs(event.event_datetime)
    const hours = eventTime.diff(now, 'hour', true)
    const minutes = Math.max(0, Math.round(eventTime.diff(now, 'minute', true)))

    // No alerts for past events or events > 3 hours away
    if (hours <= 0) {
        return {
            type: 'past',
            severity: 'info',
            title: 'Event has started or passed',
            message: `${event.title} was scheduled at ${eventTime.format('h:mm A')}`,
            icon: '⏰',
            color: '#94a3b8',
        }
    }

    if (hours <= 1) {
        return {
            type: 'imminent',
            severity: 'critical',
            title: 'Starting very soon!',
            message: `${event.title} in ${minutes} min — leave now / final preparations!`,
            icon: '🚨',
            color: '#ff4757',
        }
    }

    if (hours <= 2) {
        return {
            type: 'arriving_soon',
            severity: 'warning',
            title: 'Arriving soon — prepare now!',
            message: `${event.title} in ~${minutes} min — start getting ready`,
            icon: '🔔',
            color: '#ffa502',
        }
    }

    if (hours <= 3) {
        return {
            type: 'heads_up',
            severity: 'notice',
            title: 'Less than 3 hours left',
            message: `${event.title} at ${eventTime.format('h:mm A')} — wrap up current work`,
            icon: '📢',
            color: '#3498db',
        }
    }

    return null // No notification needed
}

// ============================================================
// HELPER: human-readable time remaining
// ============================================================

function formatTimeRemaining(hours) {
    if (hours <= 0) return 'now'
    if (hours < 1) {
        const mins = Math.round(hours * 60)
        return `${mins} min`
    }
    if (hours < 24) {
        const h = Math.floor(hours)
        const m = Math.round((hours - h) * 60)
        return m > 0 ? `${h}h ${m}m` : `${h}h`
    }
    const days = Math.floor(hours / 24)
    const remainingH = Math.round(hours - days * 24)
    return remainingH > 0 ? `${days}d ${remainingH}h` : `${days}d`
}

// ============================================================
// ACTION CLAUSE ENGINE
// ============================================================

/**
 * Generate an action and recommendation for an event based on
 * its category and how much time remains.
 */
export function generateAction(event) {
    const now = dayjs()
    const eventTime = dayjs(event.event_datetime)
    const hours = eventTime.diff(now, 'hour', true)
    const cat = (event.category || '').toLowerCase().trim()
    const timeStr = formatTimeRemaining(hours)
    const timeLabel = eventTime.format('h:mm A')

    let action = ''
    let recommendation = ''
    let studyHours = null
    let icon = '📌'

    // ---- Category-based rules ----
    if (cat === 'exam') {
        action = `Study for ${event.title}`
        studyHours = STUDY_HOURS.exam
        icon = '📖'

        if (hours <= 0) {
            recommendation = 'Event has passed'
        } else if (hours < 1) {
            recommendation = `⚠️ EXAM IN ${timeStr}! Final review only — focus on key formulas`
        } else if (hours < 3) {
            recommendation = `🚨 ${timeStr} left — intensive cram session NOW (${studyHours}h recommended)`
        } else if (hours < 6) {
            recommendation = `🔔 ${timeStr} until exam — study immediately (${studyHours}h recommended)`
        } else if (hours < 12) {
            recommendation = `📢 Exam at ${timeLabel} (${timeStr} left) — deep study session needed`
        } else if (hours < 24) {
            recommendation = `Start preparation today — ${timeStr} remaining (${studyHours}h blocks)`
        } else if (hours < 48) {
            recommendation = `Plan focused revision schedule — ${timeStr} until exam`
        } else {
            recommendation = `Create study plan — you have ${timeStr} to prepare`
        }
    } else if (cat === 'hackathon') {
        action = `Work on project: ${event.title}`
        studyHours = STUDY_HOURS.hackathon
        icon = '🚀'

        if (hours <= 0) {
            recommendation = 'Event has passed'
        } else if (hours < 1) {
            recommendation = `⚠️ STARTS IN ${timeStr}! Finalize setup and team coordination`
        } else if (hours < 3) {
            recommendation = `🚨 ${timeStr} left — last prep window! Review tools & plan approach`
        } else if (hours < 12) {
            recommendation = `🔔 Build core features now — ${timeStr} remaining (${studyHours}h block)`
        } else if (hours < 24) {
            recommendation = `Focus on MVP today — ${timeStr} left until kickoff`
        } else {
            recommendation = `Plan project milestones — ${timeStr} to prepare`
        }
    } else if (cat === 'assignment') {
        action = `Complete: ${event.title}`
        studyHours = STUDY_HOURS.assignment
        icon = '✏️'

        if (hours <= 0) {
            recommendation = 'Past deadline'
        } else if (hours < 1) {
            recommendation = `⚠️ DUE IN ${timeStr}! Submit immediately if ready`
        } else if (hours < 3) {
            recommendation = `🚨 ${timeStr} to deadline — finish and submit NOW`
        } else if (hours < 6) {
            recommendation = `🔔 Deadline approaching — ${timeStr} left, focus intensely`
        } else if (hours < 24) {
            recommendation = `Allocate ${studyHours}h focused work today — ${timeStr} remaining`
        } else {
            recommendation = `Schedule ${studyHours}h work block — ${timeStr} until deadline`
        }
    } else if (cat === 'meeting') {
        action = `Prepare for meeting: ${event.title}`
        icon = '🤝'

        if (hours <= 0) {
            recommendation = 'Meeting has passed'
        } else if (hours < 0.5) {
            recommendation = `⚠️ MEETING IN ${timeStr}! Join now or head to venue`
        } else if (hours < 1) {
            recommendation = `🚨 Starting in ${timeStr} — final agenda check, be ready to join`
        } else if (hours < 2) {
            recommendation = `🔔 Meeting at ${timeLabel} (${timeStr}) — review agenda & prep notes`
        } else if (hours < 24) {
            recommendation = `Prepare discussion points today — meeting in ${timeStr}`
        } else {
            recommendation = `Review agenda in advance — meeting in ${timeStr}`
        }
    } else if (cat === 'personal') {
        action = `Prepare for: ${event.title}`
        icon = '🎉'

        if (hours <= 0) {
            recommendation = 'Event has passed'
        } else if (hours < 1) {
            recommendation = `⚠️ EVENT IN ${timeStr}! Get ready and leave now!`
        } else if (hours < 2) {
            recommendation = `🔔 Arriving soon! ${event.title} at ${timeLabel} — start getting ready`
        } else if (hours < 3) {
            recommendation = `📢 ${timeStr} left — plan your travel / preparation time`
        } else if (hours < 24) {
            recommendation = `Reminder: ${event.title} at ${timeLabel} today (${timeStr} away)`
        } else {
            recommendation = `Reminder: ${eventTime.format('MMM D')} at ${timeLabel} — ${timeStr} away`
        }
    } else if (cat === 'reminder') {
        action = event.title
        icon = '🔔'
        if (hours <= 0) {
            recommendation = 'Past reminder'
        } else if (hours < 1) {
            recommendation = `⚠️ Reminder in ${timeStr}!`
        } else {
            recommendation = `Reminder at ${timeLabel} — ${timeStr} from now`
        }
    } else {
        action = event.title
        icon = '📅'
        if (hours <= 0) {
            recommendation = 'Event passed'
        } else if (hours < 1) {
            recommendation = `⚠️ Coming up in ${timeStr}! Prepare now`
        } else if (hours < 3) {
            recommendation = `🔔 ${event.title} at ${timeLabel} — ${timeStr} left, get ready`
        } else {
            recommendation = `Coming up at ${timeLabel} — ${timeStr} away`
        }
    }

    return { action, recommendation, studyHours, icon }
}

/**
 * Build a full enriched event object with priority + action clauses + notification.
 */
export function enrichEvent(event) {
    const score = getPriorityScore(event)
    const color = getPriorityColor(score)
    const { action, recommendation, studyHours, icon } = generateAction(event)
    const alert = getNotificationAlert(event)

    return {
        ...event,
        priority_score: score,
        color,
        action,
        recommendation,
        studyHours,
        actionIcon: icon,
        alert,
    }
}

/**
 * Enrich and sort all events.
 */
export function enrichAndSort(events) {
    return sortByPriority(events).map(enrichEvent)
}

// ============================================================
// SCHEDULE GENERATION
// ============================================================

/**
 * Generate a recommended schedule from events.
 */
export function generateSchedule(events) {
    const now = dayjs()
    const schedule = []

    const futureEvents = events
        .filter((e) => dayjs(e.event_datetime).isAfter(now))
        .sort((a, b) => dayjs(a.event_datetime).valueOf() - dayjs(b.event_datetime).valueOf())

    if (futureEvents.length === 0) return []

    let cursor = now.startOf('minute')

    const prioritized = sortByPriority(futureEvents)
    const allocatedStudy = new Map()

    for (const event of prioritized) {
        if (isStudyCategory(event.category)) {
            const score = getPriorityScore(event)
            let studyMinutes
            if (score > 15) studyMinutes = 180
            else if (score > 10) studyMinutes = 120
            else if (score > 5) studyMinutes = 90
            else studyMinutes = 60
            allocatedStudy.set(event.id, { event, studyMinutes, allocated: 0 })
        }
    }

    for (let i = 0; i < futureEvents.length; i++) {
        const event = futureEvents[i]
        const eventTime = dayjs(event.event_datetime)
        const bufferMinutes = 30
        const availableUntilEvent = eventTime.subtract(bufferMinutes, 'minute')

        if (cursor.isBefore(availableUntilEvent)) {
            const studyEntries = [...allocatedStudy.values()]
                .filter((s) => s.allocated < s.studyMinutes && dayjs(s.event.event_datetime).isAfter(cursor))
                .sort((a, b) => getPriorityScore(b.event) - getPriorityScore(a.event))

            for (const study of studyEntries) {
                if (!cursor.isBefore(availableUntilEvent)) break

                const remaining = study.studyMinutes - study.allocated
                const availableMinutes = availableUntilEvent.diff(cursor, 'minute')
                const blockMinutes = Math.min(remaining, availableMinutes, 90)

                if (blockMinutes >= 15) {
                    const { action, recommendation, icon } = generateAction(study.event)
                    schedule.push({
                        id: `study-${study.event.id}-${study.allocated}`,
                        type: 'study',
                        title: action,
                        category: study.event.category,
                        startTime: cursor.format('h:mm A'),
                        endTime: cursor.add(blockMinutes, 'minute').format('h:mm A'),
                        startDate: cursor.format('MMM D'),
                        duration: blockMinutes,
                        priority: getPriorityScore(study.event),
                        action,
                        recommendation,
                        actionIcon: icon,
                    })
                    cursor = cursor.add(blockMinutes, 'minute')
                    study.allocated += blockMinutes

                    if (cursor.isBefore(availableUntilEvent) && availableUntilEvent.diff(cursor, 'minute') > 30) {
                        const breakDuration = 15
                        schedule.push({
                            id: `break-${cursor.valueOf()}`,
                            type: 'break',
                            title: 'Break',
                            startTime: cursor.format('h:mm A'),
                            endTime: cursor.add(breakDuration, 'minute').format('h:mm A'),
                            startDate: cursor.format('MMM D'),
                            duration: breakDuration,
                            priority: 0,
                            action: 'Take a break',
                            recommendation: 'Rest and recharge',
                            actionIcon: '☕',
                        })
                        cursor = cursor.add(breakDuration, 'minute')
                    }
                }
            }
        }

        const { action, recommendation, icon } = generateAction(event)
        const eventAlert = getNotificationAlert(event)
        const eventEntry = {
            id: `event-${event.id}`,
            type: isStudyCategory(event.category) ? 'exam' : 'event',
            title: event.title,
            category: event.category,
            startTime: eventTime.format('h:mm A'),
            endTime: eventTime.add(1, 'hour').format('h:mm A'),
            startDate: eventTime.format('MMM D'),
            venue: event.venue,
            duration: 60,
            priority: getPriorityScore(event),
            action,
            recommendation,
            actionIcon: icon,
            alert: eventAlert,
        }
        schedule.push(eventEntry)

        cursor = eventTime.add(1, 'hour')
    }

    return schedule
}
