import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react'
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
  Handle,
  Position,
  MarkerType,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import dayjs from 'dayjs'
import { api, TEST_USER_ID } from '../lib/api'

// ── Color Schemes ─────────────────────────────────────────────────────────────
// Pending = Amber/Gold; Completed = Emerald; Critical = Crimson; High = Orange; Normal = Cyan; Low = Purple
export function getNodeTheme(item) {
  const isCompleted = item.status === 'completed'
  const isPending = item.status === 'pending' || !item.start_time
  const isRescheduled = item.status === 'rescheduled' || item.auto_rescheduled
  const isFixed = item.task_type === 'fixed' || item.flexibility === false
  const priority = Number(item.priority || 3)

  if (isCompleted) {
    return {
      border: '#10b981',
      bg: 'rgba(16, 185, 129, 0.12)',
      glow: '0 0 16px rgba(16, 185, 129, 0.35)',
      badgeBg: 'rgba(16, 185, 129, 0.2)',
      badgeText: '#34d399',
      label: '✓ COMPLETED',
      textStyle: 'line-through text-slate-400',
    }
  }

  if (isPending) {
    return {
      border: '#f59e0b',
      bg: 'rgba(245, 158, 11, 0.14)',
      glow: '0 0 18px rgba(245, 158, 11, 0.4)',
      badgeBg: 'rgba(245, 158, 11, 0.22)',
      badgeText: '#fbbf24',
      label: '⏳ PENDING',
      textStyle: 'text-amber-100 font-semibold',
    }
  }

  if (isFixed) {
    return {
      border: '#a855f7',
      bg: 'rgba(168, 85, 247, 0.14)',
      glow: '0 0 18px rgba(168, 85, 247, 0.35)',
      badgeBg: 'rgba(168, 85, 247, 0.22)',
      badgeText: '#c084fc',
      label: '🔒 FIXED',
      textStyle: 'text-white font-semibold',
    }
  }

  // Active items by importance/priority:
  switch (priority) {
    case 1:
      return {
        border: '#ef4444',
        bg: 'rgba(239, 68, 68, 0.16)',
        glow: '0 0 22px rgba(239, 68, 68, 0.45)',
        badgeBg: 'rgba(239, 68, 68, 0.25)',
        badgeText: '#f87171',
        label: isRescheduled ? '↻ AUTO-RESCHEDULED (CRITICAL)' : '⚡ CRITICAL',
        textStyle: 'text-red-100 font-bold',
      }
    case 2:
      return {
        border: '#f97316',
        bg: 'rgba(249, 115, 22, 0.14)',
        glow: '0 0 18px rgba(249, 115, 22, 0.35)',
        badgeBg: 'rgba(249, 115, 22, 0.22)',
        badgeText: '#fb923c',
        label: isRescheduled ? '↻ AUTO-RESCHEDULED (HIGH)' : '★ HIGH PRIORITY',
        textStyle: 'text-orange-100 font-semibold',
      }
    case 3:
      return {
        border: '#06b6d4',
        bg: 'rgba(6, 182, 212, 0.12)',
        glow: '0 0 16px rgba(6, 182, 212, 0.3)',
        badgeBg: 'rgba(6, 182, 212, 0.2)',
        badgeText: '#38bdf8',
        label: isRescheduled ? '↻ AUTO-RESCHEDULED' : '● NORMAL',
        textStyle: 'text-cyan-100 font-semibold',
      }
    default:
      return {
        border: '#8b5cf6',
        bg: 'rgba(139, 92, 246, 0.1)',
        glow: '0 0 12px rgba(139, 92, 246, 0.25)',
        badgeBg: 'rgba(139, 92, 246, 0.18)',
        badgeText: '#a78bfa',
        label: isRescheduled ? '↻ RESCHEDULED' : '○ LOW',
        textStyle: 'text-slate-200',
      }
  }
}

// ── Custom Node: Schedule Item (with date, time, status & importance) ─────────
function ScheduleTaskNode({ data }) {
  const { item, onComplete, onReschedule, onDelete } = data
  const theme = getNodeTheme(item)

  const hasStartTime = !!item.start_time
  const startDay = hasStartTime ? dayjs(item.start_time) : null
  const endDay = item.end_time ? dayjs(item.end_time) : null

  return (
    <div
      className="relative px-3.5 py-3 min-w-[210px] max-w-[260px] rounded-xl transition-all duration-200 group"
      style={{
        background: 'rgba(12, 19, 42, 0.92)',
        backdropFilter: 'blur(10px)',
        border: `1.5px solid ${theme.border}`,
        boxShadow: `${theme.glow}, 0 8px 24px rgba(0,0,0,0.5)`,
      }}
    >
      <Handle type="target" position={Position.Left} className="!bg-cyan-400 !w-2 !h-2" />

      {/* Top row: Priority/Status Badge & Quick Delete */}
      <div className="flex items-center justify-between gap-1 mb-1.5">
        <span
          className="text-[9px] font-bold px-2 py-0.5 rounded uppercase tracking-wider"
          style={{ background: theme.badgeBg, color: theme.badgeText }}
        >
          {theme.label}
        </span>
        <button
          onClick={(e) => {
            e.stopPropagation()
            if (onDelete) onDelete(item.id)
          }}
          className="opacity-60 hover:opacity-100 text-[10px] font-bold text-red-400 hover:text-red-300 px-1"
          title="Remove item"
        >
          ✕
        </button>
      </div>

      {/* Title */}
      <h4 className={`text-xs leading-snug line-clamp-2 ${theme.textStyle}`}>
        {item.title}
      </h4>

      {/* Date and Time block */}
      <div
        className="mt-2 pt-2 flex flex-col gap-1 text-[10px]"
        style={{ borderTop: '1px solid rgba(255,255,255,0.06)' }}
      >
        {hasStartTime ? (
          <>
            <div className="flex items-center gap-1.5 text-cyan-300 font-mono">
              <span>📅</span>
              <span>{startDay.format('ddd, MMM D')}</span>
            </div>
            <div className="flex items-center justify-between font-mono text-[10px] text-slate-300">
              <span className="font-semibold text-white">
                {startDay.format('h:mm A')} – {endDay ? endDay.format('h:mm A') : 'TBD'}
              </span>
              <span className="text-[9px] text-slate-400 px-1 rounded bg-white/5">
                {item.duration_minutes || (endDay ? endDay.diff(startDay, 'minute') : 60)}m
              </span>
            </div>
          </>
        ) : (
          <div className="flex items-center justify-between text-amber-300/90 font-mono">
            <span>⏳ Flexible / Unassigned</span>
            <span className="text-[9px] px-1 rounded bg-amber-500/10">
              {item.duration_minutes || 60}m
            </span>
          </div>
        )}

        {item.deadline && (
          <div className="text-[9px] text-slate-400 font-mono truncate">
            Due: {dayjs(item.deadline).format('MMM D, h:mm A')}
          </div>
        )}
      </div>

      {/* Hover action bar */}
      <div className="mt-2.5 pt-2 flex items-center gap-1.5" style={{ borderTop: '1px solid rgba(255,255,255,0.08)' }}>
        {item.status !== 'completed' && (
          <button
            onClick={(e) => {
              e.stopPropagation()
              if (onComplete) onComplete(item.id)
            }}
            className="flex-1 py-1 rounded text-[10px] font-bold transition-all text-emerald-400 hover:text-emerald-200"
            style={{ background: 'rgba(16, 185, 129, 0.15)', border: '1px solid rgba(16, 185, 129, 0.3)' }}
          >
            ✓ Done
          </button>
        )}
        <button
          onClick={(e) => {
            e.stopPropagation()
            if (onReschedule) onReschedule(item.id)
          }}
          className="flex-1 py-1 rounded text-[10px] font-bold transition-all text-amber-300 hover:text-amber-100"
          style={{ background: 'rgba(245, 158, 11, 0.15)', border: '1px solid rgba(245, 158, 11, 0.3)' }}
          title="Auto-reschedule to next free slot"
        >
          ↻ Reschedule
        </button>
      </div>

      <Handle type="source" position={Position.Right} className="!bg-cyan-400 !w-2 !h-2" />
    </div>
  )
}

// ── Custom Node: Date Branch (Day Hub) ────────────────────────────────────────
function DateBranchNode({ data }) {
  const { dateLabel, dayName, count, totalDuration, isToday, isPending } = data

  const accentColor = isToday ? '#38bdf8' : isPending ? '#f59e0b' : '#a78bfa'

  return (
    <div
      className="relative px-4 py-2.5 rounded-2xl min-w-[150px] text-center transition-all"
      style={{
        background: 'rgba(15, 23, 50, 0.95)',
        backdropFilter: 'blur(10px)',
        border: `2px solid ${accentColor}`,
        boxShadow: `0 0 20px ${accentColor}40, 0 4px 16px rgba(0,0,0,0.4)`,
      }}
    >
      <Handle type="target" position={Position.Left} className="!bg-white !w-2 !h-2" />
      <div className="flex items-center justify-center gap-1.5">
        <span className="text-sm">{isPending ? '⏳' : isToday ? '⚡' : '📅'}</span>
        <span className="text-xs font-bold uppercase tracking-wider text-white">
          {dayName}
        </span>
      </div>
      <p className="text-[10px] font-mono text-slate-300 mt-0.5">{dateLabel}</p>
      <div className="mt-1 inline-flex items-center gap-2 px-2 py-0.5 rounded-full text-[9px] font-mono"
        style={{ background: `${accentColor}15`, color: accentColor }}>
        <span>{count} {count === 1 ? 'task' : 'tasks'}</span>
        {totalDuration > 0 && <span>• {Math.round(totalDuration / 60 * 10) / 10}h</span>}
      </div>
      <Handle type="source" position={Position.Right} className="!bg-white !w-2 !h-2" />
    </div>
  )
}

// ── Custom Node: Center Timeline Core ─────────────────────────────────────────
function CenterTimelineNode({ data }) {
  const { total, completed, pending, onAutoRescheduleAll } = data

  return (
    <div
      className="relative px-6 py-4 rounded-3xl min-w-[200px] text-center transition-all select-none"
      style={{
        background: 'radial-gradient(circle, #1e3a6a, #0b132b)',
        border: '2px solid rgba(77, 163, 255, 0.7)',
        boxShadow: '0 0 40px rgba(77, 163, 255, 0.4), 0 0 80px rgba(99, 102, 241, 0.2)',
      }}
    >
      <div className="flex items-center justify-center gap-2 text-cyan-400 mb-1">
        <span className="text-xl">🧠</span>
        <span className="text-sm font-extrabold tracking-wider uppercase text-white">
          Chrona Timeline
        </span>
      </div>
      <p className="text-[10px] text-cyan-200/80 font-mono">
        {dayjs().format('dddd, MMMM D, YYYY')}
      </p>

      {/* Stats pill */}
      <div className="mt-2.5 flex items-center justify-center gap-1.5 text-[9px] font-mono">
        <span className="px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
          ✓ {completed}
        </span>
        <span className="px-2 py-0.5 rounded-full bg-amber-500/20 text-amber-300 border border-amber-500/30">
          ⏳ {pending}
        </span>
        <span className="px-2 py-0.5 rounded-full bg-cyan-500/20 text-cyan-300 border border-cyan-500/30">
          Σ {total}
        </span>
      </div>

      {pending > 0 && onAutoRescheduleAll && (
        <button
          onClick={(e) => {
            e.stopPropagation()
            onAutoRescheduleAll()
          }}
          className="mt-2.5 text-[9px] font-bold px-2.5 py-1 rounded-lg transition-all animate-pulse text-amber-200 hover:text-white"
          style={{ background: 'rgba(245, 158, 11, 0.25)', border: '1px solid rgba(245, 158, 11, 0.4)' }}
        >
          ✦ Auto-Reschedule Unplaced
        </button>
      )}

      <Handle type="source" position={Position.Right} className="!bg-cyan-400 !w-3 !h-3" />
      <Handle type="source" position={Position.Left} className="!bg-cyan-400 !w-3 !h-3" />
    </div>
  )
}

const nodeTypes = {
  centerNode: CenterTimelineNode,
  dateBranchNode: DateBranchNode,
  scheduleTaskNode: ScheduleTaskNode,
}

// ── Layout Generator ─────────────────────────────────────────────────────────
function buildMindmapLayout(items, { onComplete, onReschedule, onDelete, onAutoRescheduleAll }) {
  const nodes = []
  const edges = []

  const total = items.length
  const completedCount = items.filter(i => i.status === 'completed').length
  const pendingCount = items.filter(i => i.status === 'pending' || !i.start_time).length

  // Center node
  nodes.push({
    id: 'center',
    type: 'centerNode',
    position: { x: 0, y: 0 },
    data: {
      total,
      completed: completedCount,
      pending: pendingCount,
      onAutoRescheduleAll,
    },
    draggable: true,
  })

  // Group items by date: YYYY-MM-DD or 'pending'
  const groups = {}
  items.forEach(item => {
    let key = 'pending'
    if (item.start_time) {
      key = dayjs(item.start_time).format('YYYY-MM-DD')
    }
    if (!groups[key]) groups[key] = []
    groups[key].push(item)
  })

  // Sort dates chronologically, pending at top or end
  const dateKeys = Object.keys(groups).filter(k => k !== 'pending').sort()
  if (groups['pending']?.length) {
    dateKeys.unshift('pending')
  }

  const numBranches = dateKeys.length || 1
  const branchSpacingY = 260
  const totalHeight = (numBranches - 1) * branchSpacingY
  const startY = -totalHeight / 2

  dateKeys.forEach((dateKey, branchIdx) => {
    const isPending = dateKey === 'pending'
    const branchItems = groups[dateKey]
    const branchDate = isPending ? null : dayjs(dateKey)
    const isToday = branchDate ? branchDate.isSame(dayjs(), 'day') : false

    const dateNodeId = `date-${dateKey}`
    const dateY = startY + branchIdx * branchSpacingY
    const dateX = 320 // 320px to the right of center

    let totalDur = 0
    branchItems.forEach(i => {
      totalDur += Number(i.duration_minutes || 60)
    })

    // Add Date Branch Node
    nodes.push({
      id: dateNodeId,
      type: 'dateBranchNode',
      position: { x: dateX, y: dateY },
      data: {
        dateLabel: isPending ? 'Unscheduled items' : branchDate.format('MMM D, YYYY'),
        dayName: isPending ? 'Pending Tasks' : (isToday ? 'Today' : branchDate.format('dddd')),
        count: branchItems.length,
        totalDuration: totalDur,
        isToday,
        isPending,
      },
      draggable: true,
    })

    // Edge from center to Date Branch
    edges.push({
      id: `edge-center-${dateNodeId}`,
      source: 'center',
      target: dateNodeId,
      animated: isToday || isPending,
      style: {
        stroke: isToday ? '#38bdf8' : isPending ? '#f59e0b' : '#a78bfa',
        strokeWidth: 2,
      },
    })

    // Sort items on this date by start_time
    const sortedBranchItems = branchItems.slice().sort((a, b) => {
      if (!a.start_time) return 1
      if (!b.start_time) return -1
      return dayjs(a.start_time).diff(dayjs(b.start_time))
    })

    // Arrange task nodes horizontally / stacked out from the date node
    sortedBranchItems.forEach((task, taskIdx) => {
      const taskId = `task-${task.id}`
      const theme = getNodeTheme(task)
      const taskX = dateX + 260 + (taskIdx * 280)
      const taskY = dateY + (taskIdx % 2 === 0 ? 0 : 30) - 20

      nodes.push({
        id: taskId,
        type: 'scheduleTaskNode',
        position: { x: taskX, y: taskY },
        data: {
          item: task,
          onComplete,
          onReschedule,
          onDelete,
        },
        draggable: true,
      })

      // Link first task to Date Branch, subsequent tasks linked in sequential timeline order!
      const sourceId = taskIdx === 0 ? dateNodeId : `task-${sortedBranchItems[taskIdx - 1].id}`
      edges.push({
        id: `edge-${sourceId}-${taskId}`,
        source: sourceId,
        target: taskId,
        animated: theme.border === '#ef4444' || theme.border === '#f97316',
        markerEnd: {
          type: MarkerType.ArrowClosed,
          color: theme.border,
          width: 14,
          height: 14,
        },
        style: {
          stroke: theme.border,
          strokeWidth: 1.8,
        },
      })
    })
  })

  return { nodes, edges }
}

// ── Main Calendar Mindmap Component ──────────────────────────────────────────
export default function CalendarMindmap({ onOpenRepair }) {
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState('all') // 'all' | 'pending' | 'critical' | 'completed' | 'today'
  const [search, setSearch] = useState('')
  const [nodes, setNodes, onNodesChange] = useNodesState([])
  const [edges, setEdges, onEdgesChange] = useEdgesState([])
  const rfInstance = useRef(null)

  // Fetch schedule items
  const loadSchedule = useCallback(async () => {
    try {
      setLoading(true)
      const res = await api.scheduleItems()
      const raw = res.items || []
      setItems(raw)
    } catch (e) {
      console.error('Failed to load schedule items:', e)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadSchedule()
  }, [loadSchedule])

  // Actions
  const handleComplete = useCallback(async (taskId) => {
    try {
      await api.completeTask(taskId)
      setItems(prev => prev.map(item => item.id === taskId ? { ...item, status: 'completed' } : item))
    } catch (e) {
      alert(`Error completing task: ${e.message}`)
    }
  }, [])

  const handleReschedule = useCallback(async (taskId) => {
    try {
      const res = await api.autoReschedule(taskId)
      alert(res.message || 'Task automatically rescheduled!')
      loadSchedule()
    } catch (e) {
      alert(`Reschedule error: ${e.message}`)
    }
  }, [loadSchedule])

  const handleDelete = useCallback(async (taskId) => {
    if (!window.confirm('Remove this commitment from your schedule?')) return
    try {
      await api.updateScheduleItem(taskId, { status: 'cancelled' })
      setItems(prev => prev.filter(i => i.id !== taskId))
    } catch (e) {
      alert(`Error: ${e.message}`)
    }
  }, [])

  const handleAutoRescheduleAll = useCallback(async () => {
    try {
      const res = await api.autoReschedule(null, true)
      alert(res.message || 'All unplaced tasks have been automatically scheduled!')
      loadSchedule()
    } catch (e) {
      alert(`Auto-reschedule error: ${e.message}`)
    }
  }, [loadSchedule])

  // Filter items based on user selection & search
  const filteredItems = useMemo(() => {
    let result = items
    if (filter === 'pending') {
      result = result.filter(i => i.status === 'pending' || !i.start_time)
    } else if (filter === 'critical') {
      result = result.filter(i => Number(i.priority) <= 2)
    } else if (filter === 'completed') {
      result = result.filter(i => i.status === 'completed')
    } else if (filter === 'today') {
      const today = dayjs().format('YYYY-MM-DD')
      result = result.filter(i => i.start_time && dayjs(i.start_time).format('YYYY-MM-DD') === today)
    }

    if (search.trim()) {
      const q = search.toLowerCase()
      result = result.filter(i => (i.title || '').toLowerCase().includes(q))
    }
    return result
  }, [items, filter, search])

  // Re-build layout whenever filtered items update
  useEffect(() => {
    const { nodes: newNodes, edges: newEdges } = buildMindmapLayout(filteredItems, {
      onComplete: handleComplete,
      onReschedule: handleReschedule,
      onDelete: handleDelete,
      onAutoRescheduleAll: handleAutoRescheduleAll,
    })
    setNodes(newNodes)
    setEdges(newEdges)

    setTimeout(() => {
      rfInstance.current?.fitView({ padding: 0.25, duration: 400 })
    }, 150)
  }, [filteredItems, handleComplete, handleReschedule, handleDelete, handleAutoRescheduleAll, setNodes, setEdges])

  const onInit = useCallback((instance) => {
    rfInstance.current = instance
    setTimeout(() => instance.fitView({ padding: 0.25, duration: 400 }), 300)
  }, [])

  return (
    <div className="flex-1 flex flex-col h-full w-full overflow-hidden relative">
      {/* ── Mind Map Header & Filter Toolbar ────────────────────────────── */}
      <div className="flex-none px-4 py-3 glass-strong border-b border-white/5 flex flex-wrap items-center justify-between gap-3 z-20">
        <div className="flex items-center gap-2">
          <div className="w-2.5 h-2.5 rounded-full bg-cyan-400 animate-pulse shadow-glow" />
          <h2 className="text-sm sm:text-base font-bold text-white tracking-tight">
            Schedule Flow Mind Map
          </h2>
          <span className="text-[10px] text-slate-400 font-mono hidden sm:inline">
            ({filteredItems.length} active nodes)
          </span>
        </div>

        {/* Filters */}
        <div className="flex items-center gap-1.5 flex-wrap">
          {[
            { id: 'all', label: 'All Items' },
            { id: 'today', label: '⚡ Today' },
            { id: 'pending', label: '⏳ Pending' },
            { id: 'critical', label: '🔥 Urgent' },
            { id: 'completed', label: '✓ Done' },
          ].map(tab => (
            <button
              key={tab.id}
              onClick={() => setFilter(tab.id)}
              className={`text-xs px-2.5 py-1 rounded-lg font-semibold transition-all ${filter === tab.id
                ? 'bg-cyan-500/20 text-cyan-300 border border-cyan-500/40 shadow-sm'
                : 'text-slate-400 hover:text-white bg-white/5 border border-transparent'
              }`}
            >
              {tab.label}
            </button>
          ))}

          {/* Search */}
          <input
            type="text"
            placeholder="Search tasks..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="text-xs px-2.5 py-1 rounded-lg bg-black/40 border border-white/10 text-white placeholder-slate-500 focus:outline-none focus:border-cyan-400 w-28 sm:w-36"
          />

          <button
            onClick={() => rfInstance.current?.fitView({ padding: 0.25, duration: 400 })}
            className="text-xs px-2 py-1 rounded-lg bg-white/5 hover:bg-white/10 text-slate-300 border border-white/10"
            title="Reset view zoom"
          >
            Fit View
          </button>
        </div>
      </div>

      {/* ── Legend Bar ─────────────────────────────────────────────────── */}
      <div className="flex-none px-4 py-1.5 bg-black/40 border-b border-white/5 flex items-center justify-between overflow-x-auto text-[10px] font-mono text-slate-400 z-10 scrollbar-none">
        <div className="flex items-center gap-3">
          <span className="flex items-center gap-1">
            <span className="w-2 h-2 rounded-full bg-amber-400 shadow-sm" /> Pending
          </span>
          <span className="flex items-center gap-1">
            <span className="w-2 h-2 rounded-full bg-red-500 shadow-sm" /> Critical (P1)
          </span>
          <span className="flex items-center gap-1">
            <span className="w-2 h-2 rounded-full bg-orange-400 shadow-sm" /> High (P2)
          </span>
          <span className="flex items-center gap-1">
            <span className="w-2 h-2 rounded-full bg-cyan-400 shadow-sm" /> Normal (P3)
          </span>
          <span className="flex items-center gap-1">
            <span className="w-2 h-2 rounded-full bg-purple-400 shadow-sm" /> Fixed/Low
          </span>
          <span className="flex items-center gap-1">
            <span className="w-2 h-2 rounded-full bg-emerald-400 shadow-sm" /> Completed
          </span>
        </div>
        <div className="hidden sm:inline text-slate-500">
          Tip: Drag nodes · Double-click to expand · Use Done / Reschedule
        </div>
      </div>

      {/* ── Interactive Mind Map Canvas ─────────────────────────────────── */}
      <div className="flex-1 w-full h-full relative">
        {loading ? (
          <div className="absolute inset-0 flex items-center justify-center bg-black/40 backdrop-blur-sm z-30">
            <div className="flex flex-col items-center gap-3">
              <div className="w-8 h-8 border-2 border-cyan-400 border-t-transparent rounded-full animate-spin" />
              <p className="text-xs text-cyan-300 font-mono">Building timeline mind map...</p>
            </div>
          </div>
        ) : null}

        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onInit={onInit}
          nodeTypes={nodeTypes}
          panOnDrag={true}
          zoomOnScroll={false}
          panOnScroll={false}
          zoomOnPinch={true}
          preventScrolling={true}
          minZoom={0.1}
          maxZoom={2.5}
          proOptions={{ hideAttribution: true }}
        >
          <Background color="rgba(77, 163, 255, 0.08)" gap={28} size={1.2} variant="dots" />
          <Controls className="!bg-[#0d162c] !border-white/10 !fill-white" />
          <MiniMap
            className="hidden md:block !bg-[#0b1328] !border-white/10"
            nodeColor={(n) => {
              if (n.type === 'centerNode') return '#38bdf8'
              if (n.type === 'dateBranchNode') return '#a78bfa'
              const item = n.data?.item
              if (!item) return '#4da3ff'
              return getNodeTheme(item).border
            }}
          />
        </ReactFlow>
      </div>
    </div>
  )
}
