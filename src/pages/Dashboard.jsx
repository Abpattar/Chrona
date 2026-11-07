import { useState, useEffect, useCallback, useMemo } from 'react'
import { api, TEST_USER_ID } from '../lib/api'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'

dayjs.extend(relativeTime)

const PRIORITY_THEMES = {
  1: { text: '#ff4d4d', bg: 'rgba(255,77,77,0.08)', border: 'rgba(255,77,77,0.3)', badge: 'rgba(255,77,77,0.18)', label: 'Critical' },
  2: { text: '#ff9f43', bg: 'rgba(255,159,67,0.08)', border: 'rgba(255,159,67,0.3)', badge: 'rgba(255,159,67,0.18)', label: 'High' },
  3: { text: '#4da3ff', bg: 'rgba(77,163,255,0.08)', border: 'rgba(77,163,255,0.3)', badge: 'rgba(77,163,255,0.18)', label: 'Normal' },
  4: { text: '#3ddc97', bg: 'rgba(61,220,151,0.08)', border: 'rgba(61,220,151,0.3)', badge: 'rgba(61,220,151,0.18)', label: 'Low' },
}

export default function Dashboard() {
  const [ctx, setCtx] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [generating, setGenerating] = useState(false)
  const [quickInput, setQuickInput] = useState('')
  const [quickAdding, setQuickAdding] = useState(false)
  const [notification, setNotification] = useState(null)
  const [currentTime, setCurrentTime] = useState(dayjs())

  // Update clock every 30 seconds
  useEffect(() => {
    const timer = setInterval(() => setCurrentTime(dayjs()), 30000)
    return () => clearInterval(timer)
  }, [])

  const showNotif = (msg, type = 'success') => {
    setNotification({ msg, type })
    setTimeout(() => setNotification(null), 3500)
  }

  const fetchContext = useCallback(async () => {
    try {
      const data = await api.context()
      setCtx(data)
      setError(null)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetchContext() }, [fetchContext])

  const handleComplete = async (taskId) => {
    try {
      await api.completeTask(taskId)
      showNotif('Task completed! Great job 🎉')
      fetchContext()
    } catch (e) {
      showNotif(e.message, 'error')
    }
  }

  const handleSkip = async (taskId) => {
    try {
      await api.skipTask(taskId)
      showNotif('Task skipped — rescheduling automatically')
      fetchContext()
    } catch (e) {
      showNotif(e.message, 'error')
    }
  }

  const handleAutoReschedule = async (taskId = null) => {
    try {
      await api.autoReschedule(taskId, !taskId)
      showNotif(taskId ? 'Task rescheduled to next open focus slot ✨' : 'Unplaced tasks organized into free slots ✨')
      fetchContext()
    } catch (e) {
      showNotif(e.message, 'error')
    }
  }

  const handleQuickAdd = async (e) => {
    e.preventDefault()
    const text = quickInput.trim()
    if (!text || quickAdding) return
    setQuickAdding(true)
    try {
      const res = await api.chat(text)
      setQuickInput('')
      showNotif(res.message || 'Task scheduled successfully! ✨')
      fetchContext()
    } catch (err) {
      showNotif(err.message, 'error')
    } finally {
      setQuickAdding(false)
    }
  }

  const todayItems = useMemo(() => {
    const list = ctx?.today || []
    return [...list].sort((a, b) => new Date(a.start_time || 0) - new Date(b.start_time || 0))
  }, [ctx])

  const pending = ctx?.pending_tasks || []
  const overdue = ctx?.overdue || []

  // Determine current active task and next upcoming task
  const { currentTask, nextTask, completedCount } = useMemo(() => {
    const now = currentTime
    let active = null
    let next = null
    let done = 0

    for (const it of todayItems) {
      if (it.status === 'completed') {
        done++
        continue
      }
      if (!it.start_time) continue
      const s = dayjs(it.start_time)
      const e = it.end_time ? dayjs(it.end_time) : s.add(it.duration_minutes || 60, 'minute')

      if (s.isBefore(now) && e.isAfter(now)) {
        active = { ...it, endObj: e }
      } else if (s.isAfter(now) && !next) {
        next = { ...it, startObj: s }
      }
    }
    return { currentTask: active, nextTask: next, completedCount: done }
  }, [todayItems, currentTime])

  const dayProgressPct = todayItems.length > 0
    ? Math.round((completedCount / todayItems.length) * 100)
    : 0

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center min-h-[50vh]">
        <div className="text-center">
          <div className="w-10 h-10 mx-auto rounded-full border-2 border-ch-accent/30 border-t-ch-accent animate-spin mb-3" />
          <p className="text-sm font-medium" style={{ color: 'var(--ch-text-dim)' }}>Loading today's schedule...</p>
        </div>
      </div>
    )
  }

  const hour = currentTime.hour()
  const greeting = hour < 12 ? 'Good morning' : (hour < 17 ? 'Good afternoon' : 'Good evening')

  return (
    <div className="flex-1 overflow-y-auto px-4 py-6 sm:px-6 max-w-4xl mx-auto w-full">
      {/* Toast Notification */}
      {notification && (
        <div className="fixed top-4 right-4 z-50 animate-fade-in">
          <div className="glass rounded-xl px-4 py-3 text-sm font-semibold shadow-glass"
            style={{
              borderColor: notification.type === 'error' ? 'rgba(255,77,77,0.4)' : 'rgba(61,220,151,0.4)',
              color: notification.type === 'error' ? '#ff4d4d' : '#3ddc97',
              background: 'rgba(10, 15, 30, 0.95)'
            }}>
            {notification.msg}
          </div>
        </div>
      )}

      {/* Header & Day Summary */}
      <div className="mb-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs uppercase font-mono font-semibold tracking-wider text-nv-accent">
                {currentTime.format('dddd, MMMM D')}
              </span>
              <span className="text-xs text-white/30">•</span>
              <span className="text-xs font-mono font-bold text-white/70">
                {currentTime.format('h:mm A')}
              </span>
            </div>
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-white mt-1">
              {greeting}
            </h1>
          </div>

          <div className="flex items-center gap-2">
            {pending.length > 0 && (
              <button
                onClick={() => handleAutoReschedule(null)}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold transition-all hover:scale-105"
                style={{ background: 'rgba(77,163,255,0.12)', color: '#4da3ff', border: '1px solid rgba(77,163,255,0.3)' }}
              >
                ✦ Place Unscheduled ({pending.length})
              </button>
            )}
            <button
              onClick={fetchContext}
              title="Refresh Schedule"
              className="px-2.5 py-1.5 rounded-xl text-xs font-semibold text-white/70 hover:text-white glass transition-all"
            >
              ↻ Refresh
            </button>
          </div>
        </div>

        {/* Day Progress Bar */}
        <div className="mt-4 p-3 rounded-2xl glass flex flex-col gap-2">
          <div className="flex items-center justify-between text-xs">
            <span className="text-white/70 font-medium">
              Today's Progress: <span className="text-white font-bold">{completedCount} of {todayItems.length}</span> tasks completed
            </span>
            <span className="font-mono font-bold text-nv-accent">{dayProgressPct}%</span>
          </div>
          <div className="w-full bg-white/5 rounded-full h-2 overflow-hidden">
            <div
              className="h-full rounded-full transition-all duration-500 ease-out"
              style={{
                width: `${dayProgressPct}%`,
                background: 'linear-gradient(90deg, #4da3ff, #3ddc97)',
                boxShadow: '0 0 12px rgba(61,220,151,0.5)'
              }}
            />
          </div>
        </div>

        {error && (
          <div className="mt-3 p-3 rounded-xl text-xs font-mono bg-red-500/10 text-red-400 border border-red-500/20">
            Backend notification: {error}
          </div>
        )}
      </div>

      {/* Hero Focus Card: Happening Now OR Up Next */}
      <div className="mb-6">
        {currentTask ? (
          <div className="glass-strong rounded-2xl p-5 border border-green-500/30 relative overflow-hidden shadow-lg animate-fade-in"
            style={{ background: 'linear-gradient(135deg, rgba(61,220,151,0.08), rgba(15,23,42,0.95))' }}>
            <div className="flex items-center justify-between mb-2">
              <span className="inline-flex items-center gap-2 px-2.5 py-0.5 rounded-full text-xs font-bold uppercase tracking-wider text-green-400 bg-green-500/20 border border-green-500/30">
                <span className="w-2 h-2 rounded-full bg-green-400 animate-ping" />
                Happening Right Now
              </span>
              <span className="text-xs font-mono text-white/60">
                {currentTask.endObj.diff(currentTime, 'minute')}m remaining
              </span>
            </div>
            <h2 className="text-xl sm:text-2xl font-bold text-white mb-1">
              {currentTask.title}
            </h2>
            <p className="text-xs font-mono text-white/60 mb-4">
              {dayjs(currentTask.start_time).format('h:mm A')} → {dayjs(currentTask.end_time).format('h:mm A')} ({currentTask.duration_minutes || 60} min)
            </p>
            <div className="flex items-center gap-2">
              <button
                onClick={() => handleComplete(currentTask.id)}
                className="px-4 py-2 rounded-xl text-xs font-bold bg-green-500/20 text-green-300 border border-green-500/40 hover:bg-green-500/30 transition-all flex items-center gap-1.5"
              >
                ✓ Mark Completed
              </button>
              <button
                onClick={() => handleSkip(currentTask.id)}
                className="px-3 py-2 rounded-xl text-xs font-semibold text-white/60 hover:text-white hover:bg-white/5 transition-all"
              >
                Skip Task
              </button>
            </div>
          </div>
        ) : nextTask ? (
          <div className="glass rounded-2xl p-4 border border-blue-500/20 animate-fade-in"
            style={{ background: 'linear-gradient(135deg, rgba(77,163,255,0.06), rgba(15,23,42,0.8))' }}>
            <div className="flex items-center justify-between mb-1.5">
              <span className="text-xs font-bold uppercase tracking-wider text-blue-400 flex items-center gap-1.5">
                ⏰ Up Next (Starts {nextTask.startObj.fromNow()})
              </span>
              <span className="text-xs font-mono text-white/50">
                {nextTask.startObj.format('h:mm A')}
              </span>
            </div>
            <h3 className="text-lg font-bold text-white">
              {nextTask.title}
            </h3>
            <p className="text-xs text-white/60 font-mono mt-0.5">
              {nextTask.startObj.format('h:mm A')} → {dayjs(nextTask.end_time).format('h:mm A')} ({nextTask.duration_minutes || 60}m)
            </p>
          </div>
        ) : todayItems.length > 0 && completedCount === todayItems.length ? (
          <div className="glass rounded-2xl p-4 border border-green-500/20 text-center animate-fade-in">
            <span className="text-2xl">🎉</span>
            <h3 className="text-base font-bold text-white mt-1">All caught up for today!</h3>
            <p className="text-xs text-white/60">You have completed all scheduled commitments. Relax or plan ahead.</p>
          </div>
        ) : null}
      </div>

      {/* Missed / Overdue Section (ONLY shown if there are real past uncompleted tasks!) */}
      {overdue.length > 0 && (
        <div className="mb-6 p-4 rounded-2xl border border-red-500/30 bg-red-500/5 animate-fade-in">
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2">
              <span className="text-red-400 font-bold text-sm">⚠ Missed Past Commitments ({overdue.length})</span>
            </div>
            <button
              onClick={() => handleAutoReschedule(overdue[0].id)}
              className="text-xs font-bold px-3 py-1.5 rounded-lg bg-red-500/20 text-red-300 border border-red-500/30 hover:bg-red-500/30 transition-all"
            >
              ⚡ Auto-Reschedule Missed
            </button>
          </div>
          <div className="space-y-2">
            {overdue.map(item => (
              <div key={item.id} className="p-3 rounded-xl bg-black/40 border border-white/5 flex items-center justify-between gap-3">
                <div>
                  <p className="text-sm font-semibold text-white/90">{item.title}</p>
                  <p className="text-[11px] font-mono text-red-400/80">
                    Scheduled: {item.start_time ? dayjs(item.start_time).format('h:mm A') : 'Unplaced'} • Ended {item.end_time ? dayjs(item.end_time).fromNow() : ''}
                  </p>
                </div>
                <div className="flex items-center gap-1.5 flex-shrink-0">
                  <button
                    onClick={() => handleComplete(item.id)}
                    className="text-xs px-2.5 py-1 rounded-lg bg-green-500/10 text-green-400 hover:bg-green-500/20 transition-all"
                  >
                    Done
                  </button>
                  <button
                    onClick={() => handleAutoReschedule(item.id)}
                    className="text-xs px-2.5 py-1 rounded-lg bg-blue-500/10 text-blue-400 hover:bg-blue-500/20 transition-all"
                  >
                    Reschedule
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Today's Chronological Timeline */}
      <div className="mb-6">
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-sm font-bold uppercase tracking-wider text-white/70 flex items-center gap-2">
            <span>📅 Today's Schedule</span>
            <span className="text-xs font-normal font-mono text-white/40">({todayItems.length} items)</span>
          </h2>
        </div>

        {todayItems.length === 0 ? (
          <div className="glass rounded-2xl p-8 text-center border border-white/5">
            <p className="text-3xl mb-2">🏖️</p>
            <h3 className="text-base font-bold text-white">No tasks scheduled for today</h3>
            <p className="text-xs text-white/50 max-w-sm mx-auto mt-1 mb-4">
              Your day is wide open! Add commitments using the quick input below or let Chrona plan your study and tasks.
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {todayItems.map((item, idx) => {
              const pc = PRIORITY_THEMES[item.priority] || PRIORITY_THEMES[3]
              const start = item.start_time ? dayjs(item.start_time) : null
              const end = item.end_time ? dayjs(item.end_time) : (start ? start.add(item.duration_minutes || 60, 'minute') : null)
              const isDone = item.status === 'completed'
              const isCurrent = start && end && start.isBefore(currentTime) && end.isAfter(currentTime)

              // Check if decompression break follows this task
              const hasBreakNote = (item.completion_note || '').toLowerCase().includes('decompression')

              return (
                <div key={item.id} className="relative">
                  <div
                    className={`rounded-2xl p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 transition-all ${
                      isDone ? 'opacity-50' : 'hover:-translate-y-0.5'
                    }`}
                    style={{
                      background: isCurrent ? 'rgba(77,163,255,0.08)' : pc.bg,
                      border: `1px solid ${isCurrent ? '#4da3ff' : pc.border}`,
                      boxShadow: isCurrent ? '0 0 16px rgba(77,163,255,0.2)' : 'none',
                    }}
                  >
                    <div className="flex items-start gap-3 min-w-0">
                      {/* Priority strip */}
                      <div className="w-1.5 h-10 rounded-full flex-shrink-0 mt-0.5" style={{ background: pc.text }} />

                      <div className="min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <h4 className={`text-base font-bold truncate ${isDone ? 'line-through text-white/50' : 'text-white'}`}>
                            {item.title}
                          </h4>
                          <span
                            className="text-[10px] font-bold px-2 py-0.5 rounded-full"
                            style={{ background: pc.badge, color: pc.text }}
                          >
                            {pc.label}
                          </span>
                          {item.auto_rescheduled && (
                            <span className="text-[10px] font-medium px-2 py-0.5 rounded-full bg-cyan-500/10 text-cyan-300 border border-cyan-500/20">
                              Auto-Rescheduled
                            </span>
                          )}
                          {isCurrent && (
                            <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-green-500/20 text-green-400 animate-pulse border border-green-500/30">
                              ● NOW
                            </span>
                          )}
                        </div>

                        {start && (
                          <p className="text-xs font-mono text-white/60 mt-1 flex items-center gap-2">
                            <span>{start.format('h:mm A')} → {end ? end.format('h:mm A') : ''}</span>
                            <span className="text-white/20">•</span>
                            <span>{item.duration_minutes || 60} mins</span>
                          </p>
                        )}

                        {item.completion_note && (
                          <p className="text-[11px] text-cyan-300/80 mt-1 italic">
                            ✦ {item.completion_note}
                          </p>
                        )}
                      </div>
                    </div>

                    {/* Action buttons */}
                    <div className="flex items-center gap-2 self-end sm:self-center flex-shrink-0">
                      {!isDone ? (
                        <>
                          <button
                            onClick={() => handleComplete(item.id)}
                            className="text-xs font-semibold px-3 py-1.5 rounded-xl bg-green-500/15 text-green-300 border border-green-500/30 hover:bg-green-500/25 transition-all flex items-center gap-1"
                          >
                            ✓ Done
                          </button>
                          <button
                            onClick={() => handleSkip(item.id)}
                            className="text-xs font-semibold px-2.5 py-1.5 rounded-xl text-white/50 hover:text-white hover:bg-white/5 transition-all"
                          >
                            Skip
                          </button>
                          <button
                            onClick={() => handleAutoReschedule(item.id)}
                            title="Auto-reschedule to another free slot"
                            className="text-xs font-semibold px-2.5 py-1.5 rounded-xl text-blue-400 hover:bg-blue-500/10 transition-all"
                          >
                            Reschedule
                          </button>
                        </>
                      ) : (
                        <span className="text-xs font-semibold text-green-400/80 px-2.5 py-1 rounded-lg bg-green-500/10">
                          ✓ Completed
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Decompression break chip between items */}
                  {hasBreakNote && (
                    <div className="my-1.5 flex items-center justify-center">
                      <span className="text-[11px] px-3 py-1 rounded-full bg-indigo-500/10 text-indigo-300 border border-indigo-500/20 font-medium flex items-center gap-1.5">
                        🧘 35m Decompression Break
                      </span>
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* Unscheduled / Flexible Tasks Drawer (Only shown if pending tasks exist) */}
      {pending.length > 0 && (
        <div className="mb-6 p-4 rounded-2xl glass border border-white/10 animate-fade-in">
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-xs uppercase tracking-wider font-semibold text-white/70">
              📋 Unscheduled Tasks ({pending.length})
            </h3>
            <button
              onClick={() => handleAutoReschedule(null)}
              className="text-xs font-bold px-3 py-1 rounded-lg bg-blue-500/15 text-blue-400 hover:bg-blue-500/25 transition-all"
            >
              ✦ Place in Schedule
            </button>
          </div>
          <div className="space-y-2">
            {pending.map(p => (
              <div key={p.id} className="p-3 rounded-xl bg-black/30 border border-white/5 flex items-center justify-between gap-3">
                <span className="text-sm font-medium text-white truncate">{p.title}</span>
                <span className="text-xs font-mono text-white/40">{p.duration_minutes || 60}m</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Quick Add Bar: Direct text scheduling right on Today page */}
      <form onSubmit={handleQuickAdd} className="mt-8 sticky bottom-4 z-20">
        <div className="glass-strong rounded-2xl p-2 flex items-center gap-2 shadow-2xl border border-ch-accent/20"
          style={{ background: 'rgba(10, 15, 30, 0.95)' }}>
          <input
            type="text"
            value={quickInput}
            onChange={(e) => setQuickInput(e.target.value)}
            placeholder="Quick add: e.g. 'Dinner with friends at 8 PM', 'Math revision for 2 hours'..."
            className="flex-1 bg-transparent px-3 py-2 text-sm text-white placeholder-white/40 focus:outline-none"
          />
          <button
            type="submit"
            disabled={!quickInput.trim() || quickAdding}
            className="px-4 py-2 rounded-xl text-xs font-bold transition-all disabled:opacity-40"
            style={{
              background: 'linear-gradient(135deg, #4da3ff, #6366f1)',
              color: '#ffffff',
              boxShadow: '0 0 12px rgba(77,163,255,0.3)',
            }}
          >
            {quickAdding ? 'Adding...' : '+ Add'}
          </button>
        </div>
      </form>
    </div>
  )
}
