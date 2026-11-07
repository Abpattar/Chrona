import { useState, useEffect, useCallback } from 'react'
import { api, TEST_USER_ID } from '../lib/api'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'

dayjs.extend(relativeTime)

const PRIORITY_COLORS = {
  1: { text: '#ff4d4d', bg: 'rgba(255,77,77,0.08)', border: 'rgba(255,77,77,0.3)', label: 'Critical' },
  2: { text: '#ff9f43', bg: 'rgba(255,159,67,0.08)', border: 'rgba(255,159,67,0.3)', label: 'High' },
  3: { text: '#4da3ff', bg: 'rgba(77,163,255,0.08)', border: 'rgba(77,163,255,0.3)', label: 'Normal' },
  4: { text: '#3ddc97', bg: 'rgba(61,220,151,0.08)', border: 'rgba(61,220,151,0.3)', label: 'Low' },
}

function TaskCard({ item, onComplete, onSkip, onRepair }) {
  const pc = PRIORITY_COLORS[item.priority] || PRIORITY_COLORS[3]
  const start = item.start_time ? dayjs(item.start_time) : null
  const end = item.end_time ? dayjs(item.end_time) : null
  const isPast = end && end.isBefore(dayjs())
  const isNow = start && end && start.isBefore(dayjs()) && end.isAfter(dayjs())

  return (
    <div
      className="rounded-xl p-3 flex gap-3 group transition-all hover:-translate-y-0.5"
      style={{
        background: pc.bg,
        border: `1px solid ${pc.border}`,
        opacity: isPast ? 0.6 : 1,
      }}
    >
      {/* Priority bar */}
      <div className="w-1 rounded-full flex-shrink-0" style={{ background: pc.text }} />

      <div className="flex-1 min-w-0">
        <div className="flex items-start justify-between gap-2">
          <div className="min-w-0">
            <p className="text-sm font-semibold truncate" style={{ color: 'var(--ch-text)' }}>
              {item.title}
            </p>
            {start && (
              <p className="text-[11px] font-mono mt-0.5" style={{ color: 'var(--ch-text-dim)' }}>
                {start.format('h:mm A')} {end ? `→ ${end.format('h:mm A')}` : ''}
                {isNow && <span className="ml-2 text-green-400 font-bold animate-pulse">● NOW</span>}
              </p>
            )}
            {item.deadline && !item.start_time && (
              <p className="text-[11px] mt-0.5" style={{ color: 'var(--ch-text-muted)' }}>
                Due {dayjs(item.deadline).fromNow()}
              </p>
            )}
          </div>
          <span className="text-[9px] font-bold px-1.5 py-0.5 rounded flex-shrink-0"
            style={{ background: pc.border, color: pc.text }}>
            {pc.label}
          </span>
        </div>

        {/* Actions */}
        <div className="hidden group-hover:flex gap-1.5 mt-2">
          {onComplete && (
            <button onClick={() => onComplete(item.id)}
              className="text-[10px] font-semibold px-2 py-0.5 rounded transition-colors"
              style={{ background: 'rgba(61,220,151,0.12)', color: '#3ddc97' }}>
              ✓ Done
            </button>
          )}
          {onSkip && (
            <button onClick={() => onSkip(item.id)}
              className="text-[10px] font-semibold px-2 py-0.5 rounded transition-colors"
              style={{ background: 'rgba(255,159,67,0.12)', color: '#ff9f43' }}>
              Skip
            </button>
          )}
          {onRepair && (
            <button onClick={() => onRepair(item)}
              className="text-[10px] font-semibold px-2 py-0.5 rounded transition-colors"
              style={{ background: 'rgba(77,163,255,0.12)', color: '#4da3ff' }}>
              Repair
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

function StatCard({ label, value, color = '#4da3ff', sub = null }) {
  return (
    <div className="glass rounded-xl p-4 flex flex-col gap-1" style={{ borderColor: `${color}20` }}>
      <p className="text-[10px] uppercase tracking-wider font-semibold" style={{ color: 'var(--ch-text-muted)' }}>{label}</p>
      <p className="text-3xl font-bold" style={{ color }}>{value}</p>
      {sub && <p className="text-[11px]" style={{ color: 'var(--ch-text-dim)' }}>{sub}</p>}
    </div>
  )
}

export default function Dashboard() {
  const [ctx, setCtx] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [generating, setGenerating] = useState(false)
  const [genResult, setGenResult] = useState(null)
  const [notification, setNotification] = useState(null)

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
      showNotif('Task completed! Great work 🎉')
      fetchContext()
    } catch (e) {
      showNotif(e.message, 'error')
    }
  }

  const handleSkip = async (taskId) => {
    try {
      await api.skipTask(taskId)
      showNotif('Task skipped — will reschedule automatically')
      fetchContext()
    } catch (e) {
      showNotif(e.message, 'error')
    }
  }

  const handleGenerate = async () => {
    setGenerating(true)
    setGenResult(null)
    try {
      const today = dayjs().format('YYYY-MM-DD')
      const nextWeek = dayjs().add(7, 'day').format('YYYY-MM-DD')
      const result = await api.scheduleGenerate(today, nextWeek)
      setGenResult(result)
    } catch (e) {
      showNotif(e.message, 'error')
    } finally {
      setGenerating(false)
    }
  }

  const handleAcceptSchedule = async () => {
    if (!genResult) return
    try {
      await api.scheduleAccept(genResult.version, genResult.proposed)
      showNotif(`Schedule saved! ${genResult.proposed.length} tasks scheduled ✨`)
      setGenResult(null)
      fetchContext()
    } catch (e) {
      showNotif(e.message, 'error')
    }
  }

  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <div className="text-center">
          <div className="w-10 h-10 mx-auto rounded-full border-2 border-ch-accent/30 border-t-ch-accent animate-spin mb-3" />
          <p className="text-sm font-medium" style={{ color: 'var(--ch-text-dim)' }}>Loading your schedule...</p>
        </div>
      </div>
    )
  }

  const stats = ctx?.stats || {}
  const today = ctx?.today || []
  const pending = ctx?.pending_tasks || []
  const overdue = ctx?.overdue || []
  const deadlines = ctx?.upcoming_deadlines || []

  const now = dayjs()
  const upcoming = today.filter(i => dayjs(i.start_time).isAfter(now)).slice(0, 3)
  const inProgress = today.filter(i => {
    const s = dayjs(i.start_time), e = dayjs(i.end_time)
    return s.isBefore(now) && e.isAfter(now)
  })

  return (
    <div className="flex-1 overflow-y-auto px-4 py-6 sm:px-6 max-w-5xl mx-auto w-full">
      {/* Notification */}
      {notification && (
        <div className="fixed top-4 right-4 z-50 animate-fade-in">
          <div className="glass rounded-xl px-4 py-3 text-sm font-semibold shadow-glass"
            style={{
              borderColor: notification.type === 'error' ? 'rgba(255,77,77,0.3)' : 'rgba(61,220,151,0.3)',
              color: notification.type === 'error' ? '#ff4d4d' : '#3ddc97'
            }}>
            {notification.msg}
          </div>
        </div>
      )}

      {/* Header */}
      <div className="mb-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight" style={{ color: 'var(--ch-text)' }}>
              {now.format('dddd')}
            </h1>
            <p className="text-sm mt-0.5" style={{ color: 'var(--ch-text-dim)' }}>
              {now.format('MMMM D, YYYY')}
            </p>
          </div>
          <button
            onClick={handleGenerate}
            disabled={generating}
            className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold transition-all hover:-translate-y-0.5 disabled:opacity-50"
            style={{ background: 'rgba(77,163,255,0.12)', color: '#4da3ff', border: '1px solid rgba(77,163,255,0.3)' }}
          >
            {generating ? (
              <>
                <div className="w-4 h-4 rounded-full border-2 border-current border-t-transparent animate-spin" />
                Generating...
              </>
            ) : '✦ Generate Schedule'}
          </button>
        </div>

        {error && (
          <div className="mt-3 p-3 rounded-xl text-xs font-mono" style={{ background: 'rgba(255,77,77,0.08)', color: '#ff4d4d', border: '1px solid rgba(255,77,77,0.2)' }}>
            Backend error: {error} — Make sure the backend is running (uvicorn server:app --port 8000)
          </div>
        )}
      </div>

      {/* Schedule Generation Result */}
      {genResult && (
        <div className="mb-6 glass rounded-2xl p-5 animate-fade-in" style={{ borderColor: 'rgba(77,163,255,0.2)' }}>
          <div className="flex items-center justify-between mb-3">
            <h3 className="font-bold text-base" style={{ color: 'var(--ch-text)' }}>
              Proposed Schedule ({genResult.proposed?.length || 0} tasks)
            </h3>
            <div className="flex gap-2">
              <button onClick={() => setGenResult(null)}
                className="text-xs px-3 py-1.5 rounded-lg font-semibold"
                style={{ background: 'rgba(255,255,255,0.05)', color: 'var(--ch-text-dim)' }}>
                Discard
              </button>
              <button onClick={handleAcceptSchedule}
                className="text-xs px-3 py-1.5 rounded-lg font-semibold"
                style={{ background: 'rgba(61,220,151,0.12)', color: '#3ddc97', border: '1px solid rgba(61,220,151,0.25)' }}>
                Accept & Save
              </button>
            </div>
          </div>
          <div className="space-y-1.5 max-h-48 overflow-y-auto">
            {genResult.proposed?.slice(0, 10).map((item, i) => (
              <div key={i} className="flex items-center gap-3 text-xs py-1">
                <span className="font-mono" style={{ color: 'var(--ch-text-muted)' }}>
                  {dayjs(item.start_time).format('ddd h:mm')}
                </span>
                <span style={{ color: 'var(--ch-text)' }} className="font-medium">{item.title}</span>
                <span className="ml-auto font-mono" style={{ color: 'var(--ch-text-dim)' }}>
                  {Math.round((new Date(item.end_time) - new Date(item.start_time)) / 60000)}m
                </span>
              </div>
            ))}
            {genResult.proposed?.length > 10 && (
              <p className="text-xs text-center pt-1" style={{ color: 'var(--ch-text-muted)' }}>
                +{genResult.proposed.length - 10} more
              </p>
            )}
          </div>
          {genResult.impossible?.length > 0 && (
            <p className="text-xs mt-2" style={{ color: '#ff9f43' }}>
              ⚠ {genResult.impossible.length} task(s) couldn't fit before their deadlines
            </p>
          )}
        </div>
      )}

      {/* Stats */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-6">
        <StatCard label="Today" value={stats.today_count ?? today.length} color="#4da3ff" sub="scheduled" />
        <StatCard label="Pending" value={stats.pending_count ?? pending.length} color="#ff9f43" sub="need scheduling" />
        <StatCard label="Overdue" value={stats.overdue_count ?? overdue.length} color="#ff4d4d" sub="missed" />
        <StatCard label="This Week" value={stats.week_count ?? 0} color="#3ddc97" sub="scheduled" />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
        {/* In Progress / Upcoming */}
        <div className="glass rounded-2xl p-4">
          <h2 className="text-xs uppercase tracking-wider font-semibold mb-3" style={{ color: 'var(--ch-text-muted)' }}>
            {inProgress.length > 0 ? '🟢 In Progress' : '⏰ Up Next'}
          </h2>
          <div className="space-y-2">
            {(inProgress.length > 0 ? inProgress : upcoming).length === 0 ? (
              <p className="text-sm" style={{ color: 'var(--ch-text-dim)' }}>
                {today.length === 0 ? 'No tasks scheduled today. Generate a schedule to get started!' : 'All done for now!'}
              </p>
            ) : (
              (inProgress.length > 0 ? inProgress : upcoming).map(item => (
                <TaskCard key={item.id} item={item}
                  onComplete={handleComplete}
                  onSkip={handleSkip}
                  onRepair={null}
                />
              ))
            )}
          </div>
        </div>

        {/* Overdue */}
        {overdue.length > 0 && (
          <div className="glass rounded-2xl p-4" style={{ borderColor: 'rgba(255,77,77,0.2)' }}>
            <h2 className="text-xs uppercase tracking-wider font-semibold mb-3" style={{ color: '#ff4d4d' }}>
              ⚠ Overdue ({overdue.length})
            </h2>
            <div className="space-y-2">
              {overdue.slice(0, 4).map(item => (
                <TaskCard key={item.id} item={item}
                  onComplete={handleComplete}
                  onSkip={handleSkip}
                  onRepair={null}
                />
              ))}
            </div>
          </div>
        )}

        {/* Upcoming Deadlines */}
        {deadlines.length > 0 && (
          <div className="glass rounded-2xl p-4">
            <h2 className="text-xs uppercase tracking-wider font-semibold mb-3" style={{ color: 'var(--ch-text-muted)' }}>
              📅 Upcoming Deadlines
            </h2>
            <div className="space-y-2">
              {deadlines.slice(0, 4).map(item => (
                <div key={item.id} className="flex items-center justify-between gap-3 py-2"
                  style={{ borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                  <span className="text-sm font-medium truncate" style={{ color: 'var(--ch-text)' }}>
                    {item.title}
                  </span>
                  <span className="text-[11px] font-mono flex-shrink-0" style={{
                    color: dayjs(item.deadline).diff(dayjs(), 'hour') < 24 ? '#ff4d4d' : '#ff9f43'
                  }}>
                    {dayjs(item.deadline).fromNow()}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Pending Tasks */}
        {pending.length > 0 && (
          <div className="glass rounded-2xl p-4">
            <h2 className="text-xs uppercase tracking-wider font-semibold mb-3" style={{ color: 'var(--ch-text-muted)' }}>
              📋 Pending ({pending.length})
            </h2>
            <div className="space-y-2">
              {pending.slice(0, 5).map(item => (
                <TaskCard key={item.id} item={item}
                  onComplete={null}
                  onSkip={null}
                  onRepair={null}
                />
              ))}
              {pending.length > 5 && (
                <p className="text-xs text-center pt-1" style={{ color: 'var(--ch-text-muted)' }}>
                  +{pending.length - 5} more tasks — generate a schedule to place them
                </p>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

