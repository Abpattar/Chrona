import { useState, useEffect, useCallback } from 'react'
import { supabase } from '../lib/supabase'
import {
    sortByPriority,
    getPriorityScore,
    getPriorityColor,
    generateSchedule,
    enrichAndSort,
    generateAction,
    getNotificationAlert,
} from '../lib/priorityEngine'
import PriorityCard from '../components/PriorityCard'
import dayjs from 'dayjs'

// Real-time refresh interval (ms)
const TICK_INTERVAL = 60_000

export default function Priority() {
    const [events, setEvents] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [view, setView] = useState('schedule')
    const [tick, setTick] = useState(0)

    const fetchEvents = useCallback(async () => {
        try {
            const { data, error: fetchError } = await supabase.from('events').select('*')
            if (fetchError) throw fetchError
            setEvents(data || [])
        } catch (err) {
            setError(err.message)
        } finally {
            setLoading(false)
        }
    }, [])

    useEffect(() => { fetchEvents() }, [fetchEvents])

    // Real-time tick: re-render every 60s so priorities/colors/alerts update live
    // Also auto-deletes past events (1h grace period) to keep the view clean
    useEffect(() => {
        const cleanup = () => {
            setTick((t) => t + 1)
            const now = dayjs()
            setEvents((prev) => {
                const expired = prev.filter((e) => now.diff(dayjs(e.event_datetime), 'hour', true) >= 1)
                const remaining = prev.filter((e) => now.diff(dayjs(e.event_datetime), 'hour', true) < 1)
                // Delete expired events from database in background
                if (expired.length > 0) {
                    const ids = expired.map((e) => e.id)
                    supabase.from('events').delete().in('id', ids).then(({ error: delErr }) => {
                        if (delErr) console.error('Auto-cleanup failed:', delErr.message)
                    })
                }
                return remaining
            })
        }
        const id = setInterval(cleanup, TICK_INTERVAL)
        return () => clearInterval(id)
    }, [])

    const handleDeleteEvent = useCallback(async (eventId) => {
        setEvents((prev) => prev.filter((e) => e.id !== eventId))
        const { error: delError } = await supabase.from('events').delete().eq('id', eventId)
        if (delError) {
            console.error('Delete failed:', delError.message)
            fetchEvents()
        }
    }, [fetchEvents])

    // These re-compute every tick because dayjs() inside returns fresh values
    const enrichedEvents = enrichAndSort(events)
    const schedule = generateSchedule(events)

    // Collect active notification alerts for the banner
    const activeAlerts = enrichedEvents
        .filter((e) => e.alert && e.alert.type !== 'past')
        .map((e) => e.alert)

    if (loading) {
        return (
            <div className="flex-1 flex items-center justify-center p-4">
                <div className="text-center glass rounded-xl p-6 shadow-glass">
                    <div className="w-10 h-10 mx-auto rounded-full border-2 border-nv-accent/30 border-t-nv-accent animate-spin mb-3" />
                    <p className="text-nv-text-dim text-xs font-medium uppercase tracking-wider">Calculating priorities...</p>
                </div>
            </div>
        )
    }

    if (error) {
        return (
            <div className="flex-1 flex items-center justify-center p-4">
                <div className="glass rounded-xl p-5 max-w-sm w-full text-center shadow-glass" style={{ borderColor: 'rgba(255, 77, 77, 0.3)' }}>
                    <h2 className="text-nv-critical font-bold text-lg mb-2">System Error</h2>
                    <p className="text-nv-text-dim text-xs font-mono">{error}</p>
                </div>
            </div>
        )
    }

    return (
        <div className="flex-1 overflow-y-auto overflow-x-hidden w-full px-3 sm:px-6 py-5 sm:py-8 max-w-4xl mx-auto animate-fade-in">

            {/* Header */}
            <div className="text-center mb-6 sm:mb-10">
                <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] sm:text-xs font-medium text-nv-accent uppercase tracking-wider mb-4"
                    style={{ background: 'rgba(77, 163, 255, 0.1)', border: '1px solid rgba(77, 163, 255, 0.2)' }}>
                    <div className="w-1.5 h-1.5 rounded-full bg-nv-accent animate-pulse" />
                    Analysis Module
                </div>
                <h1 className="text-2xl sm:text-4xl font-bold text-nv-text tracking-tight leading-none">
                    Priority Hub
                </h1>
                <p className="text-nv-text-dim text-sm max-w-md mx-auto mt-3 leading-relaxed">
                    AI-generated schedule with smart recommendations based on urgency and importance.
                </p>
            </div>

            {/* Notification Alert Banner */}
            {activeAlerts.length > 0 && (
                <div className="mb-6 sm:mb-8 space-y-2 animate-slide-up">
                    {activeAlerts.map((alert, i) => (
                        <div
                            key={i}
                            className="relative rounded-xl p-3 sm:p-4 overflow-hidden"
                            style={{
                                background: `${alert.color}12`,
                                border: `1px solid ${alert.color}40`,
                                boxShadow: `0 0 24px ${alert.color}15`,
                            }}
                        >
                            {/* Pulsing left bar */}
                            <div
                                className="absolute top-0 left-0 w-1.5 h-full animate-pulse"
                                style={{ background: alert.color, boxShadow: `0 0 12px ${alert.color}80` }}
                            />
                            <div className="flex items-start gap-2.5 pl-2.5">
                                <span className="text-xl sm:text-2xl flex-shrink-0">{alert.icon}</span>
                                <div className="min-w-0">
                                    <p className="text-xs sm:text-sm font-bold tracking-tight" style={{ color: alert.color }}>
                                        {alert.title}
                                    </p>
                                    <p className="text-[10px] sm:text-xs text-nv-text-dim font-mono mt-0.5 break-words">
                                        {alert.message}
                                    </p>
                                </div>
                            </div>
                        </div>
                    ))}
                </div>
            )}

            {/* Stats bar */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 sm:gap-3 mb-6 sm:mb-10">
                {[
                    { label: 'Total Events', value: events.length, icon: '📅', glow: '#4da3ff' },
                    { label: 'Critical', value: enrichedEvents.filter((e) => e.priority_score > 15).length, icon: '🔴', glow: '#ff4d4d' },
                    { label: 'Today', value: events.filter((e) => dayjs(e.event_datetime).isSame(dayjs(), 'day')).length, icon: '📌', glow: '#ff9f43' },
                    { label: 'Alerts', value: activeAlerts.length, icon: '🔔', glow: activeAlerts.length > 0 ? '#ff4d4d' : '#3ddc97' },
                ].map((stat) => (
                    <div key={stat.label}
                        className="glass rounded-xl p-3 sm:p-4 text-center transition-all duration-200 hover:-translate-y-0.5 cursor-default"
                        style={{ borderColor: `${stat.glow}15`, boxShadow: `0 0 16px ${stat.glow}08` }}
                    >
                        <span className="text-lg sm:text-xl">{stat.icon}</span>
                        <p className="text-xl sm:text-2xl font-bold text-nv-text mt-0.5">{stat.value}</p>
                        <p className="text-[8px] sm:text-[10px] text-nv-text-muted font-medium uppercase tracking-wider mt-0.5 leading-tight">{stat.label}</p>
                    </div>
                ))}
            </div>

            {/* View toggle */}
            <div className="flex mb-6 sm:mb-8">
                <div className="w-full sm:w-auto flex glass rounded-xl overflow-hidden p-1" style={{ background: 'rgba(10, 17, 40, 0.6)' }}>
                    {[
                        { id: 'schedule', label: 'Schedule' },
                        { id: 'ranked', label: 'Actions' },
                    ].map((tab) => (
                        <button
                            key={tab.id}
                            onClick={() => setView(tab.id)}
                            className={`flex-1 sm:flex-none px-4 sm:px-6 py-2 text-xs sm:text-sm font-semibold tracking-wide transition-all duration-200 rounded-lg ${view === tab.id
                                ? 'text-white'
                                : 'text-nv-text-dim hover:text-nv-text'
                                }`}
                            style={view === tab.id ? {
                                background: 'linear-gradient(135deg, #4da3ff, #6366f1)',
                                boxShadow: '0 2px 12px rgba(77, 163, 255, 0.3)',
                            } : {}}
                        >
                            {tab.label}
                        </button>
                    ))}
                </div>
            </div>

            {/* Content */}
            {events.length === 0 ? (
                <div className="text-center py-14 glass rounded-xl shadow-glass">
                    <div className="w-14 h-14 mx-auto rounded-xl flex items-center justify-center mb-5" style={{ background: 'rgba(77, 163, 255, 0.1)' }}>
                        <span className="text-3xl">🕶️</span>
                    </div>
                    <p className="text-nv-text font-semibold text-sm">No events in system.</p>
                    <p className="text-nv-text-muted text-xs mt-1">Upload an event to get started.</p>
                </div>
            ) : view === 'schedule' ? (
                <div className="space-y-3 sm:space-y-4">
                    {schedule.length === 0 ? (
                        <div className="text-center py-8 glass rounded-xl text-nv-text-dim font-mono text-xs sm:text-sm">
                            No upcoming events detected
                        </div>
                    ) : (
                        schedule.map((item, i) => (
                            <PriorityCard key={item.id} item={item} index={i} onDelete={handleDeleteEvent} />
                        ))
                    )}
                </div>
            ) : (
                /* RANKED / RECOMMENDED ACTIONS VIEW */
                <div className="space-y-3 sm:space-y-4">
                    <div className="text-center mb-6">
                        <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold text-nv-purple"
                            style={{ background: 'rgba(167, 139, 250, 0.1)', border: '1px solid rgba(167, 139, 250, 0.2)' }}>
                            Recommended Actions
                        </span>
                    </div>
                    {enrichedEvents.map((event, i) => {
                        const color = event.color
                        return (
                            <div
                                key={event.id}
                                className="relative animate-slide-up"
                                style={{ animationDelay: `${i * 60}ms`, animationFillMode: 'backwards' }}
                            >
                                <div className="glass rounded-xl p-3.5 sm:p-5 transition-all duration-200 hover:-translate-y-0.5 overflow-hidden"
                                    style={{ borderColor: `${color.border}20`, boxShadow: `0 0 16px ${color.border}10` }}>
                                    {/* Left glow bar */}
                                    <div className="absolute top-0 left-0 w-1 h-full rounded-l-xl" style={{ background: color.border, boxShadow: `0 0 8px ${color.border}60` }} />

                                    <div className="flex flex-col gap-3 pl-2">
                                        {/* Top row: rank, icon, title, category badge */}
                                        <div className="flex items-start gap-3">
                                            <div className="flex-shrink-0 flex flex-col items-center gap-1">
                                                <div className="w-8 h-8 rounded-lg flex items-center justify-center text-xs font-bold text-nv-text"
                                                    style={{ background: 'rgba(255,255,255,0.05)' }}>
                                                    #{i + 1}
                                                </div>
                                                <div className="text-xl">
                                                    {event.actionIcon}
                                                </div>
                                            </div>
                                            <div className="flex-1 min-w-0">
                                                <div className="flex flex-wrap items-center gap-1.5 mb-1">
                                                    <span className="px-2 py-0.5 rounded-full text-[8px] sm:text-[10px] font-semibold uppercase tracking-wider"
                                                        style={{ background: `${color.border}15`, color: color.border, border: `1px solid ${color.border}25` }}>
                                                        {event.category}
                                                    </span>
                                                    {/* Alert badge */}
                                                    {event.alert && event.alert.type !== 'past' && (
                                                        <span className="px-2 py-0.5 rounded-full text-[8px] sm:text-[10px] font-bold uppercase tracking-wider animate-pulse"
                                                            style={{ background: `${event.alert.color}20`, color: event.alert.color, border: `1px solid ${event.alert.color}40` }}>
                                                            {event.alert.icon} {event.alert.title}
                                                        </span>
                                                    )}
                                                    <div className="flex items-center gap-1.5 ml-auto">
                                                        {/* Delete button */}
                                                        <button
                                                            onClick={() => handleDeleteEvent(event.id)}
                                                            className="w-7 h-7 sm:w-8 sm:h-8 flex items-center justify-center rounded-lg text-xs font-bold transition-all duration-150 hover:scale-110 flex-shrink-0"
                                                            style={{
                                                                background: 'rgba(255, 77, 77, 0.15)',
                                                                color: '#ff4d4d',
                                                                border: '1px solid rgba(255, 77, 77, 0.3)',
                                                            }}
                                                            title="Delete event"
                                                        >
                                                            ✕
                                                        </button>
                                                        <div
                                                            className="w-8 h-8 rounded-lg flex flex-col items-center justify-center font-bold flex-shrink-0"
                                                            style={{ background: `${color.border}18`, color: color.border, border: `1px solid ${color.border}30` }}
                                                        >
                                                            <span className="text-xs leading-none">{event.priority_score}</span>
                                                            <span className="text-[5px] uppercase leading-none opacity-70">pts</span>
                                                        </div>
                                                    </div>
                                                </div>
                                                <h3 className="text-sm sm:text-lg font-semibold text-nv-text tracking-tight break-words whitespace-normal">
                                                    {event.title}
                                                </h3>
                                                <p className="text-[10px] text-nv-text-muted font-mono mt-0.5">
                                                    {dayjs(event.event_datetime).format('MMM D · h:mm A')}
                                                    {event.venue && ` · ${event.venue}`}
                                                </p>
                                            </div>
                                        </div>

                                        {/* Action clause */}
                                        <div className="rounded-lg p-3 relative overflow-hidden"
                                            style={{ background: 'rgba(10, 17, 40, 0.6)', border: '1px solid rgba(255,255,255,0.05)' }}>
                                            <p className="text-xs sm:text-sm font-semibold tracking-tight" style={{ color: color.border }}>
                                                {event.action}
                                            </p>
                                            <p className="text-xs text-nv-text-dim font-mono mt-1">
                                                {event.recommendation}
                                            </p>
                                            {event.studyHours && (
                                                <div className="mt-1.5 flex items-center gap-1 text-nv-text-muted font-medium text-[10px] tracking-wider">
                                                    <span>⏱ {event.studyHours}h block required</span>
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                </div>
                            </div>
                        )
                    })}
                </div>
            )}
        </div>
    )
}
