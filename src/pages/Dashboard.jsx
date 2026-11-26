import { useState, useEffect, useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../lib/api'
import CardStack from '../components/CardStack'
import { getSkyTheme } from '../lib/theme'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'

dayjs.extend(relativeTime)

function getStakeBadge(item) {
  const mov = item.movability || (item.flexibility === false || item.controllability === 'external' ? 'immovable' : 'fluid')
  if (mov === 'immovable') {
    return {
      label: 'Immovable',
      color: '#f8fafc',
      bg: 'rgba(248, 250, 252, 0.08)',
      border: 'rgba(248, 250, 252, 0.25)',
      dot: '#e2e8f0',
    }
  }
  if (mov === 'negotiable') {
    return {
      label: 'Negotiable',
      color: '#fbbf24',
      bg: 'rgba(245, 158, 11, 0.1)',
      border: 'rgba(245, 158, 11, 0.3)',
      dot: '#f59e0b',
    }
  }
  return {
    label: 'Fluid',
    color: '#38bdf8',
    bg: 'rgba(56, 189, 248, 0.1)',
    border: 'rgba(56, 189, 248, 0.28)',
    dot: '#0284c7',
  }
}

export default function Dashboard() {
  const navigate = useNavigate()
  const [ctx, setCtx] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [notification, setNotification] = useState(null)
  const [currentTime, setCurrentTime] = useState(dayjs())
  const [viewMode, setViewMode] = useState('stack') // 'stack' | 'grid'

  useEffect(() => {
    const timer = setInterval(() => setCurrentTime(dayjs()), 10000)
    return () => clearInterval(timer)
  }, [])

  const showNotif = (msg, type = 'success') => {
    setNotification({ msg, type })
    setTimeout(() => setNotification(null), 3200)
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

  useEffect(() => {
    fetchContext()
  }, [fetchContext])

  const handleComplete = async (taskId) => {
    try {
      await api.completeTask(taskId)
      showNotif('Task completed successfully ✓')
      fetchContext()
    } catch (e) {
      showNotif(e.message, 'error')
    }
  }

  const handleSkip = async (taskId) => {
    try {
      await api.skipTask(taskId)
      showNotif('Task deferred to next window')
      fetchContext()
    } catch (e) {
      showNotif(e.message, 'error')
    }
  }

  const handleAutoReschedule = async (taskId = null) => {
    try {
      const res = await api.autoReschedule(taskId, !taskId)
      showNotif(res?.message || (taskId ? 'Task rescheduled with AI ⚡' : 'Schedule re-balanced ⚡'))
      fetchContext()
    } catch (e) {
      showNotif(e.message, 'error')
    }
  }

  const handleClearSchedule = async () => {
    if (!window.confirm('Clear all tasks from your schedule?')) return
    try {
      await api.scheduleClear()
      showNotif('All scheduled items cleared.')
      fetchContext()
    } catch (e) {
      showNotif(e.message, 'error')
    }
  }

  const todayItems = useMemo(() => {
    const list = ctx?.today || []
    return [...list].sort((a, b) => new Date(a.start_time || 0) - new Date(b.start_time || 0))
  }, [ctx])

  const upcomingItems = useMemo(() => {
    const list = ctx?.this_week || []
    const todayStr = currentTime.format('YYYY-MM-DD')
    return list
      .filter((it) => {
        if (!it.start_time) return false
        const itDay = dayjs(it.start_time).format('YYYY-MM-DD')
        return itDay !== todayStr
      })
      .sort((a, b) => new Date(a.start_time || 0) - new Date(b.start_time || 0))
  }, [ctx, currentTime])

  // Real backend tasks only — demo crypto data removed
  const allActiveTasks = useMemo(() => {
    const backendTasks = []
    const seenIds = new Set()

    for (const t of [...todayItems, ...upcomingItems]) {
      if (t.status === 'completed' || t.status === 'cancelled') continue
      const key = t.id || `${(t.title || '').toLowerCase()}-${t.start_time || ''}`
      if (!seenIds.has(key)) {
        seenIds.add(key)
        backendTasks.push(t)
      }
    }

    return backendTasks
  }, [todayItems, upcomingItems])

  const completedCount = useMemo(() => {
    const list = [...(ctx?.today || []), ...(ctx?.this_week || [])]
    return list.filter((it) => it.status === 'completed').length
  }, [ctx])

  const skyTheme = getSkyTheme(currentTime.hour())

  if (loading) {
    return (
      <div className={`flex-1 flex items-center justify-center h-full ${skyTheme.bgClass} sky-transition`}>
        <div className="text-center">
          <div className="w-9 h-9 mx-auto rounded-full border-2 border-amber-500/30 border-t-amber-400 animate-spin mb-3 shadow-[0_0_20px_rgba(245,158,11,0.4)]" />
          <p className="text-xs tracking-widest text-neutral-300 uppercase font-mono">
            Loading Great Ideas...
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className={`relative z-10 flex-1 flex flex-col justify-between overflow-hidden px-4 sm:px-8 md:px-12 py-2 sm:py-3.5 w-full h-full min-h-0 ${skyTheme.bgClass} sky-transition select-none`}>

      {/* ── Dynamic Sky Horizon Atmosphere (Morphs from Morning Orange to Afternoon Yellow to Sunset Pink to Night Blue) ── */}
      <div className="absolute inset-0 pointer-events-none -z-10 overflow-hidden">
        {/* Soft sunlit clouds across the lower horizon */}
        <div className={`absolute -bottom-20 inset-x-0 h-[450px] bg-gradient-to-t ${skyTheme.horizonGlow} blur-3xl animate-warm-breath`} />
        <div className={`absolute bottom-6 left-1/4 w-[600px] h-[350px] rounded-full ${skyTheme.orbLeft} blur-3xl transition-colors duration-1000`} />
        <div className={`absolute bottom-4 right-1/4 w-[700px] h-[360px] rounded-full ${skyTheme.orbRight} blur-3xl transition-colors duration-1000`} />
      </div>

      {/* Floating Notification */}
      {notification && (
        <div
          className={`fixed top-16 right-4 sm:right-6 z-50 px-4 py-2.5 rounded-2xl border text-xs font-mono tracking-wide shadow-2xl backdrop-blur-2xl animate-fade-in ${
            notification.type === 'error'
              ? 'bg-rose-950/95 border-rose-500/40 text-rose-200'
              : 'bg-[#1e1c19]/95 border-amber-500/40 text-amber-200 shadow-[0_0_20px_rgba(245,158,11,0.3)]'
          }`}
        >
          {notification.msg}
        </div>
      )}

      {/* ── Header: Exact match to Image ("Great ideas live here") ────────────── */}
      <div className="flex-none text-center pt-1 sm:pt-2 pb-1 relative z-10">
        <h1 className="text-2xl sm:text-4xl md:text-5xl font-light tracking-tight text-white/95 drop-shadow-[0_4px_16px_rgba(0,0,0,0.5)]">
          Great ideas live here
        </h1>
      </div>

      {/* ── Center Stage: 3D Cylindrical Carousel (Image 1/3) or Grid View ────── */}
      <div className="flex-1 flex items-center justify-center min-h-0 relative overflow-hidden py-0.5 my-auto">
        {viewMode === 'stack' ? (
          <div className="w-full flex items-center justify-center">
            <CardStack
              items={allActiveTasks}
              onComplete={handleComplete}
              onReschedule={handleAutoReschedule}
              onDefer={handleSkip}
              onOpenAI={() => navigate('/chat')}
            />
          </div>
        ) : (
          /* Internal Grid Scroller matching dark warm matte glass */
          <div className="w-full h-full overflow-y-auto min-h-0 pr-1 py-1 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3.5 animate-fade-in scrollbar-none">
            {allActiveTasks.map((item, idx) => {
              const stake = getStakeBadge(item)
              const sTime = item.start_time ? dayjs(item.start_time) : null
              const eTime = item.end_time ? dayjs(item.end_time) : (sTime ? sTime.add(item.duration_minutes || 60, 'minute') : null)

              return (
                <div
                  key={item.id || idx}
                  className="rounded-[28px] p-5 card-sunset-ideas flex flex-col justify-between transition-all hover:scale-[1.01]"
                >
                  <div>
                    {/* Top row: Movability & Time */}
                    <div className="flex items-center justify-between gap-1.5 mb-2.5">
                      <div className="flex items-center gap-1.5">
                        <span
                          className="text-[10px] font-semibold uppercase tracking-wider px-2 py-0.5 rounded-full flex items-center gap-1"
                          style={{
                            background: stake.bg,
                            color: stake.color,
                            border: `1px solid ${stake.border}`,
                          }}
                        >
                          <span className="w-1.5 h-1.5 rounded-full" style={{ background: stake.dot }} />
                          {stake.label}
                        </span>
                        {item.task_type && (
                          <span className="text-[10px] font-mono text-neutral-400 bg-white/5 px-2 py-0.5 rounded-full border border-white/10">
                            {item.task_type}
                          </span>
                        )}
                      </div>

                      {sTime ? (
                        <span className="text-[11px] font-mono text-neutral-300">
                          {sTime.format('h:mm A')}
                          {eTime ? ` – ${eTime.format('h:mm A')}` : ''}
                        </span>
                      ) : (
                        <span className="text-[11px] font-mono text-neutral-400">
                          {item.duration_minutes || 60}m duration
                        </span>
                      )}
                    </div>

                    {/* Middle details */}
                    <h3 className="text-base sm:text-lg font-bold text-white tracking-wide uppercase line-clamp-1 mb-1">
                      {item.title}
                    </h3>

                    {item.transition_reason ? (
                      <div className="mt-2 p-2 rounded-xl bg-amber-500/10 border border-amber-500/25 flex items-start gap-1.5 text-left">
                        <span className="text-amber-400 text-xs mt-0.5">⚡</span>
                        <p className="text-[11px] leading-relaxed text-amber-200/90 font-mono line-clamp-2">
                          Buffer: {item.transition_reason}
                        </p>
                      </div>
                    ) : item.description || item.notes ? (
                      <p className="text-xs text-neutral-300/80 line-clamp-2 leading-relaxed mt-1">
                        {item.description || item.notes}
                      </p>
                    ) : (
                      <p className="text-xs text-neutral-400 font-mono mt-1">
                        {item.location ? `📍 ${item.location}` : 'Scheduled focus session'}
                      </p>
                    )}
                  </div>

                  {/* Bottom actions */}
                  <div className="pt-3 border-t border-white/[0.08] flex items-center justify-between gap-2 mt-3">
                    <span className="text-xs font-mono text-neutral-400 flex items-center gap-1.5">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                      <span>{item.duration_minutes || 60}m</span>
                    </span>

                    <div className="flex items-center gap-1.5">
                      <button
                        onClick={() => handleComplete(item.id)}
                        className="px-3 py-1 rounded-full text-[10px] font-medium text-emerald-300 bg-emerald-500/15 hover:bg-emerald-500/25 border border-emerald-500/30 transition-all active:scale-95"
                      >
                        ✓ Done
                      </button>
                      <button
                        onClick={() => handleAutoReschedule(item.id)}
                        className="px-2.5 py-1 rounded-full text-[10px] text-neutral-300 bg-white/5 hover:bg-white/10 border border-white/10 transition-all active:scale-95"
                        title="Auto-reschedule with AI"
                      >
                        ↻
                      </button>
                      <button
                        onClick={() => handleSkip(item.id)}
                        className="px-2 py-1 rounded-full text-[10px] text-neutral-400 hover:text-white transition-all active:scale-95"
                        title="Defer task"
                      >
                        Defer
                      </button>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* ── Bottom Bar: Truly Page-Centered Down Buttons ("+ Create" & "See all ideas") ── */}
      <div className="flex-none pt-2 sm:pt-3 pb-1 border-t border-white/[0.08] relative z-20 w-full">
        <div className="relative w-full flex flex-col sm:flex-row items-center justify-between gap-2 min-h-[44px]">
          
          {/* Left mini status indicators */}
          <div className="flex items-center gap-2.5 text-xs font-mono text-neutral-300 flex-none">
            <span className="px-3 py-1 rounded-full bg-white/[0.05] border border-white/10 shadow-xs backdrop-blur-md">
              Active: <strong className="text-white ml-1">{allActiveTasks.length}</strong>
            </span>
            <span className="px-3 py-1 rounded-full bg-white/[0.05] border border-white/10 shadow-xs backdrop-blur-md">
              Done: <strong className="text-emerald-400 ml-1">{completedCount}</strong>
            </span>
          </div>

          {/* Truly Centered to Page Horizontal Axis */}
          <div className="sm:absolute sm:left-1/2 sm:-translate-x-1/2 flex items-center gap-3 z-10 my-1 sm:my-0">
            <button
              onClick={() => navigate('/chat')}
              className="px-7 sm:px-8 py-2.5 sm:py-3 rounded-full text-xs sm:text-sm font-medium text-white bg-[#1e1c19]/90 hover:bg-[#2b2723] border border-white/10 shadow-[0_10px_30px_rgba(0,0,0,0.5)] transition-all flex items-center gap-2 active:scale-95 backdrop-blur-xl"
            >
              <span>+</span>
              <span>Create</span>
            </button>

            <button
              onClick={() => setViewMode((prev) => (prev === 'stack' ? 'grid' : 'stack'))}
              className="px-7 sm:px-8 py-2.5 sm:py-3 rounded-full text-xs sm:text-sm font-medium text-white bg-[#1e1c19]/90 hover:bg-[#2b2723] border border-white/10 shadow-[0_10px_30px_rgba(0,0,0,0.5)] transition-all flex items-center gap-2 active:scale-95 backdrop-blur-xl"
            >
              <span className="text-sm">⠿</span>
              <span>{viewMode === 'stack' ? 'See all ideas' : '3D Deck'}</span>
            </button>
          </div>

          {/* Right Clear Schedule Action */}
          <div className="flex items-center gap-2 flex-none">
            {allActiveTasks.length > 0 && (
              <button
                onClick={handleClearSchedule}
                className="px-3.5 py-1 rounded-full text-[11px] font-mono text-neutral-400 hover:text-rose-400 hover:bg-rose-500/10 transition-all"
                title="Clear all tasks"
              >
                Clear all
              </button>
            )}
          </div>

        </div>
      </div>

    </div>
  )
}
