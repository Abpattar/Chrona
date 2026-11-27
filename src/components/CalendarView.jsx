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
      border: '1px solid rgba(52, 211, 153, 0.28)',
      borderLeft: '3px solid #10b981',
      bg: 'linear-gradient(145deg, rgba(16, 42, 32, 0.8), rgba(10, 24, 20, 0.9))',
      badge: '✓ Fulfilled',
      badgeColor: '#34d399',
    }
  }
  if (movability === 'immovable') {
    return {
      border: '1px solid rgba(255, 255, 255, 0.18)',
      borderLeft: '3px solid #f8fafc',
      bg: 'linear-gradient(145deg, rgba(30, 36, 50, 0.8), rgba(16, 20, 30, 0.9))',
      badge: '◈ Anchor',
      badgeColor: '#f8fafc',
    }
  }
  if (movability === 'negotiable') {
    return {
      border: '1px solid rgba(245, 158, 11, 0.28)',
      borderLeft: '3px solid #f59e0b',
      bg: 'linear-gradient(145deg, rgba(46, 32, 16, 0.8), rgba(24, 18, 10, 0.9))',
      badge: '✧ Priority',
      badgeColor: '#fbbf24',
    }
  }
  return {
    border: isSuggested ? '1px dashed rgba(56, 189, 248, 0.38)' : '1px solid rgba(56, 189, 248, 0.25)',
    borderLeft: '3px solid #38bdf8',
    bg: 'linear-gradient(145deg, rgba(16, 36, 54, 0.8), rgba(10, 22, 34, 0.9))',
    badge: isSuggested ? '✦ Suggested' : '✦ Fluid',
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
      className="absolute left-1 right-1 rounded-lg px-2 flex items-center gap-1.5 overflow-hidden pointer-events-auto backdrop-blur-sm"
      style={{
        top: `${topOffset}px`,
        height: `${height}px`,
        background: 'repeating-linear-gradient(45deg, rgba(245,158,11,0.08), rgba(245,158,11,0.08) 6px, rgba(245,158,11,0.16) 6px, rgba(245,158,11,0.16) 12px)',
        border: '1px dashed rgba(245, 158, 11, 0.35)',
        zIndex: 5,
      }}
      title={reason}
    >
      <span className="text-[10px]">🧭</span>
      <span className="text-[9px] font-mono text-amber-200/95 truncate">
        {gapMins}m buffer · {reason}
      </span>
    </div>
  )
}

function EventChip({
  event,
  dayIndex = 0,
  isColumnHovered = false,
  onHoverStart,
  onHoverEnd,
  onComplete,
  onNudge,
  onDelete,
  onExplain,
}) {
  const [isHovered, setIsHovered] = useState(false)
  const start = dayjs(event.start_time || event.event_datetime)
  const end = event.end_time ? dayjs(event.end_time) : start.add(event.duration_minutes || 60, 'minute')
  const duration = Math.max(end.diff(start, 'minute'), 30)
  const topOffset = Math.max(0, ((start.hour() - START_HOUR) * HOUR_HEIGHT) + (start.minute() / 60) * HOUR_HEIGHT)
  const baseHeight = Math.max((duration / 60) * HOUR_HEIGHT, 40)
  const visual = getMovabilityStyle(event)
  const isMovable = (event.movability || (event.flexibility === false ? 'immovable' : 'fluid')) !== 'immovable'

  return (
    <div
      onMouseEnter={() => {
        setIsHovered(true)
        if (onHoverStart) onHoverStart(dayIndex)
      }}
      onMouseLeave={() => {
        setIsHovered(false)
        if (onHoverEnd) onHoverEnd()
      }}
      className="rounded-xl px-2.5 py-1.5 transition-all duration-200 flex flex-col justify-between backdrop-blur-2xl shadow-md cursor-pointer select-none"
      style={{
        position: 'absolute',
        top: `${topOffset}px`,
        left: '3px',
        right: '3px',
        height: isHovered ? 'auto' : `${baseHeight}px`,
        minHeight: isHovered ? `${Math.max(baseHeight, 94)}px` : undefined,
        zIndex: isHovered ? 40 : 10,
        background: isHovered ? 'rgba(18, 20, 32, 0.98)' : visual.bg,
        border: visual.border,
        borderLeft: visual.borderLeft,
        boxShadow: isHovered
          ? '0 16px 36px -6px rgba(0, 0, 0, 0.75), 0 0 1px 1px rgba(255, 255, 255, 0.15)'
          : '0 2px 8px rgba(0, 0, 0, 0.3)',
      }}
    >
      <div className="flex items-start justify-between gap-1.5 w-full">
        <div className="min-w-0 flex-1">
          <p className={`text-xs font-semibold ${isHovered ? 'whitespace-normal text-white' : 'truncate text-slate-100'} ${event.status === 'completed' ? 'line-through opacity-60 text-slate-400' : ''}`}>
            {event.title}
          </p>
          <div className="text-[10px] font-mono text-slate-300 mt-0.5 flex items-center gap-1.5 flex-wrap whitespace-nowrap">
            <span>{start.format('h:mm A')} – {end.format('h:mm A')}</span>
            {isHovered && (
              <span className="text-[9px] text-amber-300/90 bg-white/10 px-1.5 py-0.5 rounded-sm border border-white/15">
                {duration}m
              </span>
            )}
            {event.location && event.location !== 'home' ? (
              <span className="text-slate-400">· 📍 {event.location}</span>
            ) : null}
          </div>
        </div>
        <span
          className="text-[8px] font-bold px-1.5 py-0.5 rounded-full uppercase tracking-wider flex-shrink-0"
          style={{ background: 'rgba(0,0,0,0.5)', color: visual.badgeColor, border: `1px solid ${visual.badgeColor}44` }}
        >
          {visual.badge}
        </span>
      </div>

      {isHovered && (
        <div className="flex items-center gap-1.5 pt-2 mt-2 border-t border-white/10 w-full animate-fade-in flex-wrap">
          {event.status !== 'completed' && (
            <button
              onClick={(e) => { e.stopPropagation(); onComplete(event.id) }}
              className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-emerald-500/25 text-emerald-300 hover:bg-emerald-500/40 border border-emerald-500/35 transition-all active:scale-95 whitespace-nowrap shadow-xs"
            >
              ✓ Done
            </button>
          )}
          {isMovable && event.status !== 'completed' && (
            <button
              onClick={(e) => { e.stopPropagation(); onNudge(event) }}
              className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-sky-500/25 text-sky-300 hover:bg-sky-500/40 border border-sky-500/35 transition-all active:scale-95 whitespace-nowrap shadow-xs"
              title="Re-optimize slot"
            >
              ↻ Re-slot
            </button>
          )}
          <button
            onClick={(e) => { e.stopPropagation(); onExplain(event) }}
            className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-white/15 text-slate-100 hover:bg-white/25 border border-white/20 transition-all active:scale-95 whitespace-nowrap shadow-xs"
            title="Why is this here?"
          >
            Why?
          </button>
          <button
            onClick={(e) => { e.stopPropagation(); onDelete(event.id) }}
            className="ml-auto px-2 py-1 rounded-full text-[10px] font-bold bg-rose-500/20 text-rose-300 hover:bg-rose-500/35 border border-rose-500/35 transition-all active:scale-95 whitespace-nowrap shadow-xs"
            title="Delete / cancel task"
          >
            ✕
          </button>
        </div>
      )}
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
  const [hoveredDayIndex, setHoveredDayIndex] = useState(null)
  const hoverTimeoutRef = useRef(null)
  const gridRef = useRef(null)
  const fileInputRef = useRef(null)

  const handleCardHoverStart = useCallback((dayIdx) => {
    if (hoverTimeoutRef.current) clearTimeout(hoverTimeoutRef.current)
    setHoveredDayIndex(dayIdx)
  }, [])

  const handleCardHoverEnd = useCallback(() => {
    if (hoverTimeoutRef.current) clearTimeout(hoverTimeoutRef.current)
    hoverTimeoutRef.current = setTimeout(() => {
      setHoveredDayIndex(null)
    }, 120)
  }, [])

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
    try {
      const res = await api.autoReschedule(ev.id)
      setAssistantBanner(res.message)
      fetchSchedule()
    } catch (err) {
      setAssistantBanner(`Reschedule error: ${err.message}`)
    }
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

  // Fluid responsive column expansion: when hovering a card in any day column,
  // that column expands smoothly to minmax(235px, 1.8fr), and adjacent columns shrink gracefully to minmax(0, 1fr)
  const gridTemplateColumns = useMemo(() => {
    if (hoveredDayIndex === null) {
      return '60px repeat(7, minmax(0, 1fr))'
    }
    const cols = weekDays
      .map((_, i) => (i === hoveredDayIndex ? 'minmax(235px, 1.8fr)' : 'minmax(0, 1fr)'))
      .join(' ')
    return `60px ${cols}`
  }, [hoveredDayIndex, weekDays])

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
    <div className="flex h-full w-full overflow-hidden flex-col bg-transparent text-slate-100">
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Sleek Frosted Top Bar */}
        <div
          className="flex-shrink-0 px-4 sm:px-8 py-3 flex items-center justify-between flex-wrap gap-3 bg-black/20 backdrop-blur-xl border-b border-white/[0.08]"
        >
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2 bg-[#12141c]/80 px-3.5 py-1.5 rounded-full border border-white/10 backdrop-blur-xl shadow-md">
              <span className="text-xs text-amber-400">▦</span>
              <h1 className="text-xs sm:text-sm font-bold tracking-tight text-white">{currentWeekStart.format('MMMM YYYY')}</h1>
            </div>

            <div className="flex items-center rounded-full overflow-hidden border border-white/10 bg-[#12141c]/80 p-0.5 backdrop-blur-xl shadow-md">
              <button 
                onClick={() => setCurrentWeekStart((w) => w.subtract(1, 'week'))} 
                className="w-6 h-6 rounded-full flex items-center justify-center text-xs text-slate-300 hover:text-white hover:bg-white/10 transition-all"
                title="Previous Week"
              >
                ‹
              </button>
              <button 
                onClick={() => setCurrentWeekStart(dayjs().startOf('isoWeek'))} 
                className="px-3 py-0.5 text-xs font-semibold text-cyan-300 hover:text-white hover:bg-white/10 rounded-full transition-all"
              >
                Today
              </button>
              <button 
                onClick={() => setCurrentWeekStart((w) => w.add(1, 'week'))} 
                className="w-6 h-6 rounded-full flex items-center justify-center text-xs text-slate-300 hover:text-white hover:bg-white/10 transition-all"
                title="Next Week"
              >
                ›
              </button>
            </div>
          </div>

          <div className="flex items-center gap-3.5 bg-[#12141c]/80 px-4 py-1.5 rounded-full border border-white/10 backdrop-blur-xl shadow-md text-[10px] font-mono text-slate-300 flex-wrap">
            <span className="flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-slate-100 shadow-[0_0_8px_rgba(248,250,252,0.6)]" /> 
              Anchor
            </span>
            <span className="flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-amber-400 shadow-[0_0_8px_rgba(251,191,36,0.6)]" /> 
              Priority
            </span>
            <span className="flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-cyan-400 shadow-[0_0_8px_rgba(56,189,248,0.6)]" /> 
              Fluid
            </span>
            <span className="flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.6)]" /> 
              Fulfilled
            </span>
          </div>
        </div>

        {assistantBanner && (
          <div className="px-5 py-2.5 bg-amber-500/10 border-b border-amber-500/25 flex items-center justify-between gap-3 text-xs text-amber-100 backdrop-blur-md">
            <span>💡 {assistantBanner}</span>
            <button onClick={() => setAssistantBanner(null)} className="text-amber-300 hover:text-white font-bold">✕</button>
          </div>
        )}

        {/* Day Column Headers (Synchronized column expansion with timeline) */}
        <div
          className="flex-shrink-0 grid bg-white/[0.02] border-b border-white/[0.08] backdrop-blur-sm"
          style={{
            gridTemplateColumns,
            transition: 'grid-template-columns 280ms cubic-bezier(0.16, 1, 0.3, 1)',
          }}
        >
          <div className="h-14 border-r border-white/[0.06] flex items-center justify-center">
            <span className="text-[10px] font-mono text-slate-500 uppercase tracking-wider">TIME</span>
          </div>
          {weekDays.map((day, dayIdx) => {
            const isToday = day.isSame(dayjs(), 'day')
            const isColHovered = hoveredDayIndex === dayIdx
            return (
              <div 
                key={day.format('YYYY-MM-DD')} 
                className={`h-14 flex flex-col items-center justify-center border-r border-white/[0.06] transition-colors ${
                  isToday ? 'bg-amber-400/[0.05]' : isColHovered ? 'bg-white/[0.04]' : ''
                }`}
              >
                <span className={`text-[10px] uppercase tracking-wider font-medium ${isToday ? 'text-amber-300' : isColHovered ? 'text-white' : 'text-slate-400'}`}>
                  {day.format('ddd')}
                </span>
                <span
                  className={`text-xs font-bold mt-0.5 w-7 h-7 flex items-center justify-center rounded-full transition-all ${
                    isToday 
                      ? 'bg-gradient-to-br from-amber-400 to-orange-500 text-black shadow-[0_0_12px_rgba(251,191,36,0.5)]' 
                      : isColHovered
                      ? 'bg-white/10 text-white'
                      : 'text-slate-200 hover:bg-white/5'
                  }`}
                >
                  {day.format('D')}
                </span>
              </div>
            )
          })}
        </div>

        {/* Timeline Grid (Synchronized column expansion with headers) */}
        <div ref={gridRef} className="flex-1 overflow-y-auto relative">
          <div
            className="grid relative"
            style={{
              gridTemplateColumns,
              minHeight: `${HOURS.length * HOUR_HEIGHT}px`,
              transition: 'grid-template-columns 280ms cubic-bezier(0.16, 1, 0.3, 1)',
            }}
          >
            {/* Left Hour Markers */}
            <div className="relative border-r border-white/[0.06]">
              {HOURS.map((hour) => (
                <div key={hour} className="absolute w-full flex justify-end pr-2.5" style={{ top: `${(hour - START_HOUR) * HOUR_HEIGHT}px` }}>
                  <span className="text-[10px] font-mono text-slate-500 -mt-2">
                    {hour < 12 ? `${hour} AM` : hour === 12 ? '12 PM' : `${hour - 12} PM`}
                  </span>
                </div>
              ))}
            </div>

            {/* 7 Days Columns */}
            {weekDays.map((day, dayIdx) => {
              const dayEvents = eventsForDay(day)
              const isToday = day.isSame(dayjs(), 'day')
              const isColHovered = hoveredDayIndex === dayIdx
              return (
                <div 
                  key={day.format('YYYY-MM-DD')} 
                  className={`relative border-r border-white/[0.06] transition-colors duration-200 ${
                    isToday ? 'bg-amber-400/[0.02]' : isColHovered ? 'bg-white/[0.02]' : ''
                  }`}
                >
                  {/* Hour horizontal grid lines */}
                  {HOURS.map((hour) => (
                    <div
                      key={hour}
                      className="absolute w-full border-t border-white/[0.04]"
                      style={{ top: `${(hour - START_HOUR) * HOUR_HEIGHT}px` }}
                    />
                  ))}

                  {/* Current Time Indicator Line (for Today) */}
                  {isToday && (
                    <div 
                      className="absolute left-0 right-0 z-20 pointer-events-none flex items-center"
                      style={{ top: `${((dayjs().hour() - START_HOUR) * HOUR_HEIGHT) + (dayjs().minute() / 60) * HOUR_HEIGHT}px` }}
                    >
                      <div className="w-2 h-2 rounded-full bg-rose-500 -ml-1 shadow-[0_0_8px_rgba(244,63,94,0.9)]" />
                      <div className="flex-1 h-px bg-gradient-to-r from-rose-500 to-rose-400/20 shadow-[0_0_6px_rgba(244,63,94,0.6)]" />
                    </div>
                  )}

                  {/* Transition Buffers */}
                  {dayEvents.map((ev, idx) =>
                    idx < dayEvents.length - 1 ? (
                      <TransitionGapPill key={`gap-${ev.id}`} prevEvent={ev} nextEvent={dayEvents[idx + 1]} />
                    ) : null
                  )}

                  {/* Event Chips */}
                  {dayEvents.map((ev) => (
                    <EventChip
                      key={ev.id}
                      event={ev}
                      dayIndex={dayIdx}
                      isColumnHovered={isColHovered}
                      onHoverStart={handleCardHoverStart}
                      onHoverEnd={handleCardHoverEnd}
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

      {/* Decision Reasoning Modal */}
      {explainModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-md animate-fade-in">
          <div className="max-w-md w-full rounded-3xl p-6 border border-white/15 bg-[#141624]/95 backdrop-blur-2xl shadow-2xl">
            <div className="flex items-center justify-between mb-3">
              <span className="text-xs font-bold uppercase tracking-wider text-amber-400 font-mono">Chrona Reasoning</span>
              <button onClick={() => setExplainModal(null)} className="text-slate-400 hover:text-white transition-colors">✕</button>
            </div>
            <h3 className="text-base font-bold text-white mb-2">{explainModal.title}</h3>
            <p className="text-xs text-slate-300 leading-relaxed whitespace-pre-wrap">{explainModal.text}</p>
            <div className="flex justify-end gap-2 pt-4 mt-3 border-t border-white/10">
              <button
                onClick={() => setExplainModal(null)}
                className="px-4 py-1.5 rounded-full text-xs font-semibold bg-white/10 text-white hover:bg-white/20 transition-all"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
