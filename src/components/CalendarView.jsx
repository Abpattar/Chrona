import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react'
import { api, TEST_USER_ID } from '../lib/api'
import dayjs from 'dayjs'
import isoWeek from 'dayjs/plugin/isoWeek'

dayjs.extend(isoWeek)

const START_HOUR = 6
const END_HOUR = 23
const HOURS = Array.from({ length: END_HOUR - START_HOUR + 1 }, (_, i) => i + START_HOUR)
const HOUR_HEIGHT = 64

function getMovabilityStyle(ev) {
  const movability = ev.movability || (ev.flexibility === false ? 'immovable' : 'fluid')
  const isCompleted = ev.status === 'completed'
  const isSuggested = ev.status === 'suggested' || ev.auto_rescheduled

  if (isCompleted) {
    return {
      border: '1px solid rgba(16, 185, 129, 0.3)',
      borderLeft: '3px solid #10b981',
      bg: 'rgba(16, 185, 129, 0.08)',
      badge: '✓ Fulfilled',
      badgeColor: '#34d399',
    }
  }
  if (movability === 'immovable') {
    return {
      border: '1px solid rgba(248, 250, 252, 0.25)',
      borderLeft: '3px solid #f8fafc',
      bg: 'rgba(248, 250, 252, 0.06)',
      badge: '◈ Ancient Anchor',
      badgeColor: '#f8fafc',
    }
  }
  if (movability === 'negotiable') {
    return {
      border: '1px solid rgba(245, 158, 11, 0.3)',
      borderLeft: '3px solid #f59e0b',
      bg: 'rgba(245, 158, 11, 0.08)',
      badge: '✧ Sacred Vow',
      badgeColor: '#fbbf24',
    }
  }
  return {
    border: isSuggested ? '1px dashed rgba(56, 189, 248, 0.4)' : '1px solid rgba(56, 189, 248, 0.25)',
    borderLeft: '3px solid #38bdf8',
    bg: 'rgba(56, 189, 248, 0.06)',
    badge: isSuggested ? '✦ Suggested' : '✦ Wandering Thought',
    badgeColor: '#38bdf8',
  }
}

function TransitionGapPill({ prevEvent, nextEvent }) {
  const prevEnd = dayjs(prevEvent.end_time)
  const nextStart = dayjs(nextEvent.start_time)
  const gapMins = nextStart.diff(prevEnd, 'minute')

  if (gapMins < 5 || gapMins > 120) return null

  const topOffset = ((prevEnd.hour() - START_HOUR) * HOUR_HEIGHT) + (prevEnd.minute() / 60) * HOUR_HEIGHT
  const height = Math.max((gapMins / 60) * HOUR_HEIGHT, 22)
  const reason = nextEvent.transition_reason || prevEvent.post_transition_reason || `${gapMins}m transition & recovery`

  return (
    <div
      className="absolute left-1 right-1 rounded-md px-2 flex items-center gap-1.5 overflow-hidden pointer-events-auto"
      style={{
        top: `${topOffset}px`,
        height: `${height}px`,
        background: 'repeating-linear-gradient(45deg, rgba(245,158,11,0.06), rgba(245,158,11,0.06) 6px, rgba(245,158,11,0.12) 6px, rgba(245,158,11,0.12) 12px)',
        border: '1px dashed rgba(245, 158, 11, 0.35)',
        zIndex: 5,
      }}
      title={reason}
    >
      <span className="text-[10px]">🧭</span>
      <span className="text-[9px] font-mono text-amber-300/90 truncate">
        {gapMins}m buffer · {reason}
      </span>
    </div>
  )
}

function EventChip({ event, onComplete, onNudge, onDelete, onExplain }) {
  const start = dayjs(event.start_time || event.event_datetime)
  const end = event.end_time ? dayjs(event.end_time) : start.add(event.duration_minutes || 60, 'minute')
  const duration = Math.max(end.diff(start, 'minute'), 30)
  const topOffset = Math.max(0, ((start.hour() - START_HOUR) * HOUR_HEIGHT) + (start.minute() / 60) * HOUR_HEIGHT)
  const height = Math.max((duration / 60) * HOUR_HEIGHT, 40)
  const visual = getMovabilityStyle(event)
  const isMovable = (event.movability || (event.flexibility === false ? 'immovable' : 'fluid')) !== 'immovable'

  return (
    <div
      className="rounded-lg px-2.5 py-1.5 transition-all duration-150 hover:brightness-110 group overflow-hidden flex flex-col justify-between"
      style={{
        position: 'absolute',
        top: `${topOffset}px`,
        height: `${height}px`,
        left: '3px',
        right: '3px',
        zIndex: 10,
        background: visual.bg,
        border: visual.border,
        borderLeft: visual.borderLeft,
        backdropFilter: 'blur(6px)',
      }}
    >
      <div className="flex items-start justify-between gap-1">
        <div className="min-w-0 flex-1">
          <p className={`text-xs font-semibold truncate ${event.status === 'completed' ? 'line-through opacity-60' : 'text-gray-100'}`}>
            {event.title}
          </p>
          <p className="text-[10px] font-mono text-gray-400">
            {start.format('h:mm A')} – {end.format('h:mm A')}
            {event.location && event.location !== 'home' ? ` · 📍 ${event.location}` : ''}
          </p>
        </div>
        <span
          className="text-[8px] font-bold px-1.5 py-0.5 rounded uppercase tracking-wider flex-shrink-0"
          style={{ background: 'rgba(0,0,0,0.35)', color: visual.badgeColor }}
        >
          {visual.badge}
        </span>
      </div>

      <div className="hidden group-hover:flex items-center gap-1 pt-1">
        {event.status !== 'completed' && (
          <button
            onClick={(e) => { e.stopPropagation(); onComplete(event.id) }}
            className="px-1.5 py-0.5 rounded text-[9px] font-bold bg-emerald-500/20 text-emerald-300 hover:bg-emerald-500/30"
          >
            ✓ Done
          </button>
        )}
        {isMovable && event.status !== 'completed' && (
          <button
            onClick={(e) => { e.stopPropagation(); onNudge(event) }}
            className="px-1.5 py-0.5 rounded text-[9px] font-bold bg-sky-500/20 text-sky-300 hover:bg-sky-500/30"
            title="Re-optimize slot"
          >
            ↻ Re-slot
          </button>
        )}
        <button
          onClick={(e) => { e.stopPropagation(); onExplain(event) }}
          className="px-1.5 py-0.5 rounded text-[9px] font-bold bg-white/10 text-gray-200 hover:bg-white/20"
          title="Why is this here?"
        >
          Why?
        </button>
        <button
          onClick={(e) => { e.stopPropagation(); onDelete(event.id) }}
          className="ml-auto px-1.5 py-0.5 rounded text-[9px] font-bold bg-red-500/15 text-red-400 hover:bg-red-500/30"
        >
          ✕
        </button>
      </div>
    </div>
  )
}

export default function CalendarView() {
  const [events, setEvents] = useState([])
  const [loading, setLoading] = useState(true)
  const [currentWeekStart, setCurrentWeekStart] = useState(dayjs().startOf('isoWeek'))
  const [quickInput, setQuickInput] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [assistantBanner, setAssistantBanner] = useState(null)
  const [explainModal, setExplainModal] = useState(null)
  const gridRef = useRef(null)
  const fileInputRef = useRef(null)

  const fetchSchedule = useCallback(async () => {
    try {
      const data = await api.scheduleItems()
      setEvents(data.items || [])
    } catch (err) {
      console.error('Schedule fetch error:', err)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchSchedule()
  }, [fetchSchedule])

  useEffect(() => {
    if (!loading && gridRef.current) {
      const scrollTo = Math.max(0, (dayjs().hour() - START_HOUR - 1) * HOUR_HEIGHT)
      gridRef.current.scrollTop = scrollTo
    }
  }, [loading])

  const handleQuickPlan = async (e) => {
    e.preventDefault()
    if (!quickInput.trim() || submitting) return
    setSubmitting(true)
    try {
      const res = await api.chat(quickInput.trim())
      setAssistantBanner(res.message)
      setQuickInput('')
      await fetchSchedule()
    } catch (err) {
      setAssistantBanner(`Could not update schedule: ${err.message}`)
    } finally {
      setSubmitting(false)
    }
  }

  const handlePosterUpload = async (e) => {
    const file = e.target.files?.[0]
    if (!file) return
    setSubmitting(true)
    setAssistantBanner(`Analyzing "${file.name}" with Gemini Vision...`)
    try {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('message', 'Extract all events, venues, prep needs, and deadlines from this file.')
      const data = await api.chatUpload(formData)
      setAssistantBanner(data.message)
      await fetchSchedule()
    } catch (err) {
      setAssistantBanner(`Upload error: ${err.message}`)
    } finally {
      setSubmitting(false)
      e.target.value = ''
    }
  }

  const handleComplete = async (id) => {
    await api.completeTask(id)
    fetchSchedule()
  }

  const handleNudge = async (ev) => {
    const res = await api.autoReschedule(ev.id)
    setAssistantBanner(res.message)
    fetchSchedule()
  }

  const handleDelete = async (id) => {
    await api.updateScheduleItem(id, { status: 'cancelled' })
    fetchSchedule()
  }

  const handleExplain = async (ev) => {
    try {
      const res = await api.explainSchedule(ev.id)
      setExplainModal({ title: ev.title, text: res.explanation })
    } catch {
      setExplainModal({
        title: ev.title,
        text: ev.completion_note || 'Placed dynamically based on your location transitions, energy rhythm, and deadline stakes.',
      })
    }
  }

  const weekDays = useMemo(
    () => Array.from({ length: 7 }, (_, i) => currentWeekStart.add(i, 'day')),
    [currentWeekStart]
  )

  const unscheduledTasks = useMemo(
    () => events.filter((ev) => !ev.start_time && ev.status !== 'completed'),
    [events]
  )

  const eventsForDay = useCallback(
    (day) =>
      events
        .filter((ev) => ev.start_time && dayjs(ev.start_time).isSame(day, 'day'))
        .sort((a, b) => dayjs(a.start_time).diff(dayjs(b.start_time))),
    [events]
  )

  return (
    <div className="flex h-full w-full overflow-hidden flex-col" style={{ background: 'var(--ch-bg)', color: 'var(--ch-text)' }}>
      <div className="flex-1 flex flex-col overflow-hidden">
        <div
          className="flex-shrink-0 px-4 sm:px-6 py-2.5 flex items-center justify-between flex-wrap gap-2.5"
          style={{ background: 'var(--ch-surface)', borderBottom: '1px solid var(--ch-border)' }}
        >
          <div className="flex items-center gap-3">
            <h1 className="text-sm sm:text-base font-bold tracking-tight text-white">{currentWeekStart.format('MMMM YYYY')}</h1>
            <div className="flex items-center rounded-lg overflow-hidden border border-white/10 bg-[#0e1422]">
              <button onClick={() => setCurrentWeekStart((w) => w.subtract(1, 'week'))} className="px-2.5 py-1 text-xs text-slate-300 hover:bg-white/5 transition-all">‹</button>
              <button onClick={() => setCurrentWeekStart(dayjs().startOf('isoWeek'))} className="px-3 py-1 text-xs font-semibold text-cyan-400 border-x border-white/10 hover:bg-white/5 transition-all">Today</button>
              <button onClick={() => setCurrentWeekStart((w) => w.add(1, 'week'))} className="px-2.5 py-1 text-xs text-slate-300 hover:bg-white/5 transition-all">›</button>
            </div>
          </div>

          <div className="flex items-center gap-2.5 sm:gap-4 text-[10px] font-mono text-slate-400 flex-wrap">
            <span className="flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-slate-100 shadow-[0_0_8px_rgba(248,250,252,0.6)]" /> Immovable</span>
            <span className="flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-amber-400 shadow-[0_0_8px_rgba(251,191,36,0.6)]" /> Personal Priority</span>
            <span className="flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-cyan-400 shadow-[0_0_8px_rgba(56,189,248,0.6)]" /> Fluid Dynamic</span>
            <span className="flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-amber-500 shadow-[0_0_8px_rgba(245,158,11,0.6)]" /> Travel / Prep</span>
          </div>
        </div>

        {assistantBanner && (
          <div className="px-5 py-2.5 bg-amber-500/10 border-b border-amber-500/25 flex items-center justify-between gap-3 text-xs text-amber-100">
            <span>💡 {assistantBanner}</span>
            <button onClick={() => setAssistantBanner(null)} className="text-amber-300 hover:text-white font-bold">✕</button>
          </div>
        )}

        <div
          className="flex-shrink-0 grid"
          style={{ gridTemplateColumns: '56px repeat(7, 1fr)', background: 'var(--ch-surface)', borderBottom: '1px solid var(--ch-border)' }}
        >
          <div className="h-14 border-r border-white/5" />
          {weekDays.map((day) => {
            const isToday = day.isSame(dayjs(), 'day')
            return (
              <div key={day.format('YYYY-MM-DD')} className="h-14 flex flex-col items-center justify-center border-r border-white/5">
                <span className="text-[10px] uppercase tracking-wider text-gray-400">{day.format('ddd')}</span>
                <span
                  className={`text-sm font-bold mt-0.5 w-7 h-7 flex items-center justify-center rounded-full ${
                    isToday ? 'text-black' : 'text-gray-200'
                  }`}
                  style={isToday ? { background: 'var(--ch-peach)' } : {}}
                >
                  {day.format('D')}
                </span>
              </div>
            )
          })}
        </div>

        <div ref={gridRef} className="flex-1 overflow-y-auto relative">
          <div className="grid relative" style={{ gridTemplateColumns: '56px repeat(7, 1fr)', minHeight: `${HOURS.length * HOUR_HEIGHT}px` }}>
            <div className="relative border-r border-white/5">
              {HOURS.map((hour) => (
                <div key={hour} className="absolute w-full flex justify-end pr-2" style={{ top: `${(hour - START_HOUR) * HOUR_HEIGHT}px` }}>
                  <span className="text-[10px] font-mono text-gray-500 -mt-1.5">
                    {hour < 12 ? `${hour} AM` : hour === 12 ? '12 PM' : `${hour - 12} PM`}
                  </span>
                </div>
              ))}
            </div>

            {weekDays.map((day) => {
              const dayEvents = eventsForDay(day)
              return (
                <div key={day.format('YYYY-MM-DD')} className="relative border-r border-white/5">
                  {HOURS.map((hour) => (
                    <div
                      key={hour}
                      className="absolute w-full border-t border-white/[0.04]"
                      style={{ top: `${(hour - START_HOUR) * HOUR_HEIGHT}px` }}
                    />
                  ))}

                  {dayEvents.map((ev, idx) =>
                    idx < dayEvents.length - 1 ? (
                      <TransitionGapPill key={`gap-${ev.id}`} prevEvent={ev} nextEvent={dayEvents[idx + 1]} />
                    ) : null
                  )}

                  {dayEvents.map((ev) => (
                    <EventChip
                      key={ev.id}
                      event={ev}
                      onComplete={handleComplete}
                      onNudge={handleNudge}
                      onDelete={handleDelete}
                      onExplain={handleExplain}
                    />
                  ))}
                </div>
              )
            })}
          </div>
        </div>
      </div>

      {explainModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
          <div className="max-w-md w-full rounded-2xl p-5 border border-white/15" style={{ background: 'var(--ch-surface-elevated)' }}>
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-bold uppercase tracking-wider text-amber-400">Chrona Reasoning</span>
              <button onClick={() => setExplainModal(null)} className="text-gray-400 hover:text-white">✕</button>
            </div>
            <h3 className="text-base font-bold mb-2">{explainModal.title}</h3>
            <p className="text-xs text-gray-300 leading-relaxed whitespace-pre-wrap">{explainModal.text}</p>
          </div>
        </div>
      )}
    </div>
  )
}
