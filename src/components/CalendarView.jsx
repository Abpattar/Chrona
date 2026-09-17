import React, { useState, useEffect, useCallback, useMemo } from 'react'
import { supabase } from '../lib/supabase'
import { getPriorityScore, getPriorityColor, getSeverityColor } from '../lib/priorityEngine'
import dayjs from 'dayjs'
import isoWeek from 'dayjs/plugin/isoWeek'
import weekday from 'dayjs/plugin/weekday'

dayjs.extend(isoWeek)
dayjs.extend(weekday)

const START_HOUR = 5
const END_HOUR = 23
const HOURS = Array.from({ length: END_HOUR - START_HOUR + 1 }, (_, i) => i + START_HOUR)
const HOUR_HEIGHT = 56 // pixels per hour

function getEventStyle(event) {
    const start = dayjs(event.start_time || event.event_datetime)
    const hour = start.hour()
    const minute = start.minute()
    const duration = event.end_time ? dayjs(event.end_time).diff(start, 'minute') : (event.estimated_prep_hours ? event.estimated_prep_hours * 60 : 60)
    const topOffset = Math.max(0, ((hour - START_HOUR) * HOUR_HEIGHT) + (minute / 60) * HOUR_HEIGHT)
    const height = Math.max((duration / 60) * HOUR_HEIGHT, 36)

    return {
        position: 'absolute',
        top: `${topOffset}px`,
        height: `${height}px`,
        left: '2px',
        right: '2px',
        zIndex: 10,
    }
}

function EventChip({ event, onComplete, onDelete, onRepair }) {
    const score = getPriorityScore(event)
    const color = getPriorityColor(score)
    const severity = getSeverityColor(event.severity_level || (event.priority === 1 ? 'critical' : event.priority === 2 ? 'high' : event.priority === 3 ? 'medium' : 'low'))
    const isPast = dayjs(event.start_time || event.event_datetime).isBefore(dayjs())
    const style = getEventStyle(event, color)

    return (
        <div
            className="rounded-lg px-2 py-1.5 cursor-pointer transition-all duration-200 hover:shadow-card-hover hover:-translate-y-px group overflow-hidden"
            style={{
                ...style,
                background: `${color.border}12`,
                borderLeft: `3px solid ${color.border}`,
                opacity: isPast ? 0.55 : 1,
            }}
        >
            <div className="flex items-start justify-between gap-1">
                <div className="min-w-0 flex-1">
                    <p className="text-[10px] sm:text-xs font-semibold truncate leading-tight" style={{ color: 'var(--ch-text)' }}>
                        {event.title}
                    </p>
                    <p className="text-[9px] font-mono mt-0.5" style={{ color: 'var(--ch-text-dim)' }}>
                        {dayjs(event.start_time || event.event_datetime).format('h:mm A')}
                    </p>
                    {event.venue && (
                        <p className="text-[8px] mt-0.5 truncate" style={{ color: 'var(--ch-text-muted)' }}>📍 {event.venue}</p>
                    )}
                </div>
                <span className="text-[8px] font-bold px-1 py-0.5 rounded flex-shrink-0"
                    style={{ background: `${severity.bg}`, color: severity.text }}>
                    {(event.severity_level || (event.priority === 1 ? 'cri' : event.priority === 2 ? 'hig' : event.priority === 3 ? 'med' : 'low')).slice(0, 3).toUpperCase()}
                </span>
            </div>

            {/* Hover actions */}
            <div className="hidden group-hover:flex gap-1 mt-1.5 animate-fade-in">
                <button onClick={(e) => { e.stopPropagation(); onComplete(event.id) }}
                    className="flex-1 rounded py-0.5 text-[8px] font-bold transition-colors"
                    style={{ background: 'rgba(76,175,130,0.15)', color: 'var(--ch-success)' }}>
                    ✓ Done
                </button>
                {onRepair && (
                    <button onClick={(e) => { e.stopPropagation(); onRepair(event) }}
                        className="flex-1 rounded py-0.5 text-[8px] font-bold transition-colors"
                        style={{ background: 'rgba(232,168,76,0.15)', color: 'var(--ch-warning)' }}>
                        Repair
                    </button>
                )}
                <button onClick={(e) => { e.stopPropagation(); onDelete(event.id) }}
                    className="rounded px-1.5 py-0.5 text-[8px] font-bold transition-colors"
                    style={{ background: 'rgba(217,79,79,0.1)', color: 'var(--ch-critical)' }}>
                    ✕
                </button>
            </div>
        </div>
    )
}

export default function CalendarView({ onOpenRepair }) {
    const [events, setEvents] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [currentWeekStart, setCurrentWeekStart] = useState(dayjs().startOf('isoWeek'))
    const [viewMode, setViewMode] = useState('week') // 'week' | 'day'
    const [selectedDay, setSelectedDay] = useState(dayjs())
    const gridRef = React.useRef(null)

    const fetchSchedule = useCallback(async () => {
        try {
            const res = await fetch('/api/schedule/items', {
                headers: { 'Content-Type': 'application/json' }
            })
            if (!res.ok) throw new Error('Failed to fetch schedule')
            const data = await res.json()
            setEvents(data.items || [])
        } catch (err) {
            setError(err.message)
        } finally {
            setLoading(false)
        }
    }, [])

    useEffect(() => { fetchSchedule() }, [fetchSchedule])

    // Auto-scroll to current time
    useEffect(() => {
        if (!loading && gridRef.current) {
            const now = dayjs()
            const scrollTo = Math.max(0, ((now.hour() - START_HOUR - 1) * HOUR_HEIGHT))
            gridRef.current.scrollTop = scrollTo
        }
    }, [loading])

    const handleComplete = useCallback(async (eventId) => {
        try {
            // Optimistic update
            setEvents(prev => prev.filter(e => e.id !== eventId))
            await fetch('/api/behavior/update', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    task_id: eventId,
                    actual_complete: new Date().toISOString(),
                    confirmed_at: new Date().toISOString()
                })
            })
        } catch (err) { console.error(err) }
    }, [])

    const handleDelete = useCallback(async (eventId) => {
        // Treat delete as "skip" for behavioral updates
        try {
            // Optimistic update
            setEvents(prev => prev.filter(e => e.id !== eventId))
            await fetch('/api/behavior/update', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    task_id: eventId,
                    actual_complete: null, // Skipped
                    confirmed_at: new Date().toISOString()
                })
            })
        } catch (err) { console.error(err) }
    }, [])

    const weekDays = useMemo(() => {
        return Array.from({ length: 7 }, (_, i) => currentWeekStart.add(i, 'day'))
    }, [currentWeekStart])

    const eventsForDay = useCallback((day) => {
        return events.filter(ev => dayjs(ev.start_time || ev.event_datetime).isSame(day, 'day'))
    }, [events])

    const prevWeek = () => setCurrentWeekStart(prev => prev.subtract(1, 'week'))
    const nextWeek = () => setCurrentWeekStart(prev => prev.add(1, 'week'))
    const goToday = () => {
        setCurrentWeekStart(dayjs().startOf('isoWeek'))
        setSelectedDay(dayjs())
    }

    const totalEvents = events.length
    const todayEvents = events.filter(e => dayjs(e.start_time || e.event_datetime).isSame(dayjs(), 'day')).length

    if (loading) {
        return (
            <div className="flex-1 flex items-center justify-center">
                <div className="text-center">
                    <div className="w-10 h-10 mx-auto rounded-full border-2 border-ch-peach-soft border-t-ch-peach animate-spin mb-3" />
                    <p className="text-xs font-semibold" style={{ color: 'var(--ch-text-dim)' }}>Loading schedule...</p>
                </div>
            </div>
        )
    }

    return (
        <div className="flex flex-col h-full overflow-hidden">
            {/* Header */}
            <div className="flex-shrink-0 px-4 sm:px-6 py-3 sm:py-4 flex items-center justify-between flex-wrap gap-3"
                style={{ borderBottom: '1px solid rgba(180,140,110,0.1)', background: 'var(--ch-surface)' }}>
                <div className="flex items-center gap-3">
                    <h1 className="text-xl sm:text-2xl font-bold tracking-tight" style={{ color: 'var(--ch-text)' }}>
                        Calendar
                    </h1>
                    <span className="text-[10px] font-bold uppercase tracking-wider px-2 py-1 rounded-full"
                        style={{ background: 'rgba(232,146,124,0.08)', color: 'var(--ch-peach)', border: '1px solid rgba(232,146,124,0.12)' }}>
                        {totalEvents} events
                    </span>
                    {todayEvents > 0 && (
                        <span className="text-[10px] font-bold uppercase tracking-wider px-2 py-1 rounded-full"
                            style={{ background: 'rgba(76,175,130,0.08)', color: 'var(--ch-success)', border: '1px solid rgba(76,175,130,0.12)' }}>
                            {todayEvents} today
                        </span>
                    )}
                </div>

                <div className="flex items-center gap-2">
                    {/* Navigation */}
                    <div className="flex items-center rounded-lg overflow-hidden" style={{ border: '1px solid rgba(180,140,110,0.12)' }}>
                        <button onClick={prevWeek} className="px-2.5 py-1.5 text-sm transition-colors hover:bg-[rgba(232,146,124,0.06)]" style={{ color: 'var(--ch-text-dim)' }}>‹</button>
                        <button onClick={goToday} className="px-3 py-1.5 text-xs font-semibold transition-colors hover:bg-[rgba(232,146,124,0.06)]"
                            style={{ color: 'var(--ch-peach)', borderLeft: '1px solid rgba(180,140,110,0.1)', borderRight: '1px solid rgba(180,140,110,0.1)' }}>
                            Today
                        </button>
                        <button onClick={nextWeek} className="px-2.5 py-1.5 text-sm transition-colors hover:bg-[rgba(232,146,124,0.06)]" style={{ color: 'var(--ch-text-dim)' }}>›</button>
                    </div>

                    <span className="text-sm font-semibold" style={{ color: 'var(--ch-text)' }}>
                        {currentWeekStart.format('MMMM YYYY')}
                    </span>
                </div>
            </div>

            {/* Day headers */}
            <div className="flex-shrink-0 grid gap-0" style={{
                gridTemplateColumns: '56px repeat(7, 1fr)',
                borderBottom: '1px solid rgba(180,140,110,0.1)',
                background: 'var(--ch-surface)',
            }}>
                {/* Empty corner */}
                <div className="h-16" style={{ borderRight: '1px solid rgba(180,140,110,0.06)' }} />

                {weekDays.map((day) => {
                    const isToday = day.isSame(dayjs(), 'day')
                    const dayEventCount = eventsForDay(day).length
                    return (
                        <div key={day.format('YYYY-MM-DD')}
                            className="flex flex-col items-center justify-center h-16 relative"
                            style={{
                                borderRight: '1px solid rgba(180,140,110,0.06)',
                                background: isToday ? 'rgba(232,146,124,0.03)' : 'transparent',
                            }}
                        >
                            <span className="text-[10px] font-semibold uppercase tracking-wider" style={{ color: 'var(--ch-text-muted)' }}>
                                {day.format('ddd')}
                            </span>
                            <span className={`text-lg font-bold mt-0.5 w-9 h-9 flex items-center justify-center rounded-full`}
                                style={isToday ? {
                                    background: 'linear-gradient(135deg, #E8927C, #D47560)',
                                    color: '#fff',
                                    boxShadow: '0 2px 8px rgba(232,146,124,0.35)',
                                } : {
                                    color: 'var(--ch-text)',
                                }}>
                                {day.format('DD')}
                            </span>
                            {dayEventCount > 0 && (
                                <div className="absolute bottom-1 flex gap-0.5">
                                    {Array.from({ length: Math.min(dayEventCount, 4) }).map((_, i) => (
                                        <div key={i} className="w-1 h-1 rounded-full" style={{ background: 'var(--ch-peach)' }} />
                                    ))}
                                </div>
                            )}
                        </div>
                    )
                })}
            </div>

            {/* Calendar grid */}
            <div ref={gridRef} className="flex-1 overflow-y-auto overflow-x-hidden relative scroll-smooth" style={{ background: 'var(--ch-bg)' }}>
                <div className="grid gap-0 relative" style={{
                    gridTemplateColumns: '56px repeat(7, 1fr)',
                    minHeight: `${HOURS.length * HOUR_HEIGHT}px`,
                }}>
                    {/* Time labels column */}
                    <div className="relative" style={{ borderRight: '1px solid rgba(180,140,110,0.08)' }}>
                        {HOURS.map((hour) => (
                            <div key={hour} className="absolute w-full flex items-start justify-end pr-2" style={{ top: `${(hour - START_HOUR) * HOUR_HEIGHT}px`, height: `${HOUR_HEIGHT}px` }}>
                                <span className="text-[10px] font-mono -mt-1.5" style={{ color: 'var(--ch-text-muted)' }}>
                                    {hour === 0 ? '12 AM' : hour < 12 ? `${hour} AM` : hour === 12 ? '12 PM' : `${hour - 12} PM`}
                                </span>
                            </div>
                        ))}
                    </div>

                    {/* Day columns */}
                    {weekDays.map((day) => {
                        const isToday = day.isSame(dayjs(), 'day')
                        const dayEvents = eventsForDay(day)

                        return (
                            <div key={day.format('YYYY-MM-DD')} className="relative"
                                style={{
                                    borderRight: '1px solid rgba(180,140,110,0.06)',
                                    background: isToday ? 'rgba(232,146,124,0.015)' : 'transparent',
                                }}
                            >
                                {/* Hour grid lines */}
                                {HOURS.map((hour) => (
                                    <div key={hour} className="absolute w-full" style={{
                                        top: `${(hour - START_HOUR) * HOUR_HEIGHT}px`,
                                        height: '1px',
                                        background: 'rgba(180,140,110,0.06)',
                                    }} />
                                ))}

                                {/* Current time indicator */}
                                {isToday && (() => {
                                    const now = dayjs()
                                    const currentHour = now.hour()
                                    const currentMinute = now.minute()
                                    if (currentHour >= START_HOUR && currentHour <= END_HOUR) {
                                        const top = ((currentHour - START_HOUR) * HOUR_HEIGHT) + (currentMinute / 60) * HOUR_HEIGHT
                                        return (
                                            <div className="absolute w-full z-20" style={{ top: `${top}px` }}>
                                                <div className="flex items-center">
                                                    <div className="w-2.5 h-2.5 rounded-full -ml-1.5" style={{ background: 'var(--ch-critical)', boxShadow: '0 0 6px rgba(217,79,79,0.4)' }} />
                                                    <div className="flex-1 h-[2px]" style={{ background: 'var(--ch-critical)', opacity: 0.6 }} />
                                                </div>
                                            </div>
                                        )
                                    }
                                    return null
                                })()}

                                {/* Event chips */}
                                {dayEvents.map((ev) => (
                                    <EventChip
                                        key={ev.id}
                                        event={ev}
                                        onComplete={handleComplete}
                                        onDelete={handleDelete}
                                        onRepair={onOpenRepair}
                                    />
                                ))}
                            </div>
                        )
                    })}
                </div>
            </div>
        </div>
    )
}
