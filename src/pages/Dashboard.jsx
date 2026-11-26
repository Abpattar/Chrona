import { useState, useEffect, useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../lib/api'
import CardStack from '../components/CardStack'
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
      await api.autoReschedule(taskId, !taskId)
      showNotif(taskId ? 'Task rescheduled with AI ⚡' : 'Schedule re-balanced ⚡')
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

  const allActiveTasks = useMemo(() => {
    const combined = [...todayItems, ...upcomingItems]
    return combined.filter((t) => t.status !== 'completed')
  }, [todayItems, upcomingItems])

  const completedCount = useMemo(() => {
    const list = [...(ctx?.today || []), ...(ctx?.this_week || [])]
    return list.filter((it) => it.status === 'completed').length
  }, [ctx])

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center h-full">
        <div className="text-center">
          <div className="w-8 h-8 mx-auto rounded-full border-2 border-slate-600/30 border-t-cyan-400 animate-spin mb-2 shadow-[0_0_12px_rgba(56,189,248,0.2)]" />
          <p className="text-[11px] tracking-wider text-slate-400 uppercase font-mono">
            Loading...
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="relative z-10 flex-1 flex flex-col justify-between overflow-hidden p-2.5 sm:p-4 max-w-5xl mx-auto w-full h-full min-h-0">

      {/* Floating Notification */}
      {notification && (
        <div
          className={`fixed top-16 right-4 sm:right-6 z-50 px-3.5 py-2 rounded-xl border text-xs font-mono tracking-wide shadow-2xl backdrop-blur-xl animate-fade-in ${
            notification.type === 'error'
              ? 'bg-rose-950/95 border-rose-500/40 text-rose-200'
              : 'bg-[#0f172a]/95 border-cyan-500/40 text-cyan-200 shadow-[0_0_16px_rgba(56,189,248,0.25)]'
          }`}
        >
          {notification.msg}
        </div>
      )}

      {/* Screen-Fit Top Bar */}
      <div className="flex-none flex items-center justify-between gap-2 pb-2 border-b border-slate-800/80">
        <div className="flex items-center gap-2">
          <h1 className="text-base sm:text-lg font-bold text-slate-100 tracking-tight leading-none">
            Today
          </h1>
          <span className="text-[10px] sm:text-xs font-mono text-slate-400 bg-white/[0.04] px-2 py-0.5 rounded-full border border-white/5">
            {currentTime.format('ddd, MMM D')}
          </span>
        </div>

        {/* View Toggle & Action Buttons */}
        <div className="flex items-center gap-1.5 sm:gap-2">
          {allActiveTasks.length > 0 && (
            <div className="flex items-center bg-[#0d121e] p-0.5 rounded-lg border border-slate-800 text-[11px]">
              <button
                onClick={() => setViewMode('stack')}
                className={`px-2 py-0.5 rounded-md font-medium transition-all ${
                  viewMode === 'stack'
                    ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/30'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                Deck
              </button>
              <button
                onClick={() => setViewMode('grid')}
                className={`px-2 py-0.5 rounded-md font-medium transition-all ${
                  viewMode === 'grid'
                    ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/30'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                Grid
              </button>
            </div>
          )}

          <button
            onClick={() => navigate('/chat')}
            className="px-2.5 sm:px-3 py-1 rounded-lg text-xs font-semibold text-white bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 shadow-[0_0_12px_rgba(56,189,248,0.25)] transition-all flex items-center gap-1 active:scale-95"
          >
            <span>+ Add</span>
          </button>

          {allActiveTasks.length > 0 && (
            <button
              onClick={handleClearSchedule}
              className="px-2 py-1 rounded-lg text-[11px] text-slate-500 hover:text-rose-400 hover:bg-rose-500/10 transition-all"
              title="Clear all tasks"
            >
              Clear
            </button>
          )}
        </div>
      </div>

      {/* Screen-Fit Center Hero: 3D Card Stack (code.zip) or Internal Grid Scroller */}
      <div className="flex-1 flex items-center justify-center min-h-0 relative overflow-hidden py-1">
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
          /* Internal Grid Scroller (page itself doesn't scroll) */
          <div className="w-full h-full overflow-y-auto min-h-0 pr-1 py-1 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2.5">
            {allActiveTasks.map((item) => {
              const stake = getStakeBadge(item)
              const sTime = item.start_time ? dayjs(item.start_time) : null

              return (
                <div
                  key={item.id}
                  className="rounded-xl p-3 bg-[#0e1422]/90 border border-slate-800/80 hover:border-slate-700 backdrop-blur-xl shadow flex flex-col justify-between"
                >
                  <div>
                    <div className="flex items-center justify-between gap-1.5 mb-1.5">
                      <span
                        className="text-[9px] font-semibold px-1.5 py-0.5 rounded uppercase tracking-wider flex items-center gap-1"
                        style={{ background: stake.bg, color: stake.color, border: `1px solid ${stake.border}` }}
                      >
                        <span className="w-1 h-1 rounded-full" style={{ background: stake.dot }} />
                        {stake.label}
                      </span>

                      {sTime && (
                        <span className="text-[10px] font-mono text-slate-400">
                          {sTime.format('h:mm A')} · {item.duration_minutes || 60}m
                        </span>
                      )}
                    </div>

                    <h3 className="text-xs sm:text-sm font-bold text-slate-100 tracking-tight line-clamp-2 mb-1">
                      {item.title}
                    </h3>

                    {item.transition_reason && (
                      <p className="text-[9px] font-mono text-amber-300/80 bg-amber-500/[0.06] p-1 rounded border border-amber-500/20 line-clamp-1 mb-1">
                        ⚡ {item.transition_reason}
                      </p>
                    )}
                  </div>

                  <div className="pt-1.5 border-t border-slate-800/60 flex items-center justify-between gap-1 mt-1">
                    <span className="text-[9px] font-mono text-slate-500">
                      {sTime ? sTime.format('MMM D') : 'Today'}
                    </span>

                    <div className="flex items-center gap-1">
                      <button
                        onClick={() => handleComplete(item.id)}
                        className="px-2 py-0.5 rounded text-[10px] font-semibold text-emerald-300 bg-emerald-500/10 hover:bg-emerald-500/20 border border-emerald-500/30 transition-all"
                      >
                        Done
                      </button>
                      <button
                        onClick={() => handleAutoReschedule(item.id)}
                        className="px-1.5 py-0.5 rounded text-[10px] text-slate-300 bg-white/5 hover:bg-white/10 border border-slate-700/50 transition-all"
                      >
                        ↻
                      </button>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* Screen-Fit Bottom Bar: Compact Stats Strip */}
      <div className="flex-none pt-2 border-t border-slate-800/80">
        <div className="grid grid-cols-4 gap-2">
          <div className="px-2.5 py-1.5 rounded-xl bg-[#0b0f19]/70 border border-slate-800/60 backdrop-blur-md flex flex-col justify-center">
            <span className="text-[9px] font-mono text-slate-400 uppercase tracking-wider leading-none">
              Active
            </span>
            <span className="text-sm sm:text-base font-bold font-mono text-slate-100 mt-0.5 leading-none">
              {allActiveTasks.length}
            </span>
          </div>

          <div className="px-2.5 py-1.5 rounded-xl bg-[#0b0f19]/70 border border-slate-800/60 backdrop-blur-md flex flex-col justify-center">
            <span className="text-[9px] font-mono text-slate-400 uppercase tracking-wider leading-none">
              Today
            </span>
            <span className="text-sm sm:text-base font-bold font-mono text-cyan-300 mt-0.5 leading-none">
              {todayItems.length}
            </span>
          </div>

          <div className="px-2.5 py-1.5 rounded-xl bg-[#0b0f19]/70 border border-slate-800/60 backdrop-blur-md flex flex-col justify-center">
            <span className="text-[9px] font-mono text-slate-400 uppercase tracking-wider leading-none">
              Done
            </span>
            <span className="text-sm sm:text-base font-bold font-mono text-emerald-400 mt-0.5 leading-none">
              {completedCount}
            </span>
          </div>

          <div className="px-2.5 py-1.5 rounded-xl bg-[#0b0f19]/70 border border-slate-800/60 backdrop-blur-md flex flex-col justify-center">
            <span className="text-[9px] font-mono text-slate-400 uppercase tracking-wider leading-none">
              AI Copilot
            </span>
            <span className="text-[10px] sm:text-xs font-mono font-semibold text-emerald-400 mt-0.5 flex items-center gap-1 leading-none">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse flex-shrink-0" />
              <span className="truncate">Active</span>
            </span>
          </div>
        </div>
      </div>

    </div>
  )
}
