import React, { useState, useEffect, useLayoutEffect, useCallback, useMemo, useRef } from 'react'
import {
  ReactFlow,
  Background,
  Controls,
  useNodesState,
  useEdgesState,
  Handle,
  Position,
  MarkerType,
  ReactFlowProvider,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import dayjs from 'dayjs'
import { api, TEST_USER_ID } from '../lib/api'

// ── Hollow Knight / Hallownest Cartography Themes ─────────────────────────────
// Ancient Anchor (Immovable): #f8fafc (Soul Pale Silver)
// Sacred Vow (Personal Priority): #fbbf24 (Lumafly Gold)
// Wandering Thought (Dynamic): #38bdf8 (City of Tears Azure)
// Unassigned / Pending: #c084fc (Monarch Purple)
// Fulfilled: #34d399 (Soul Green)
export function getNodeTheme(item) {
  const isCompleted = item.status === 'completed'
  const isPending = item.status === 'pending' || !item.start_time
  const isRescheduled = item.status === 'rescheduled' || !!item.auto_rescheduled || !!item.completion_note?.includes('Auto-rescheduled')
  const movability = item.movability || (
    item.flexibility === false || item.controllability === 'external' || item.task_type === 'fixed' ||
    /exam|midterm|final|quiz|test|lecture|class|lab|movie|cinema|film|theater|theatre|concert|show|doctor|dentist|appointment|clinic|therapy|hospital|surgery|interview|court|family|wedding|ceremony/i.test(item.title || '')
      ? 'immovable'
      : (Number(item.priority || 3) <= 2 ? 'negotiable' : 'fluid')
  )

  if (isCompleted) {
    return {
      border: 'rgba(52, 211, 153, 0.4)',
      bg: 'rgba(16, 185, 129, 0.1)',
      glow: '0 0 16px rgba(16, 185, 129, 0.25)',
      badgeBg: 'rgba(16, 185, 129, 0.2)',
      badgeText: '#34d399',
      label: '✓ FULFILLED',
      textStyle: 'line-through text-slate-400',
    }
  }

  if (isPending) {
    return {
      border: 'rgba(192, 132, 252, 0.4)',
      bg: 'rgba(157, 78, 221, 0.1)',
      glow: '0 0 16px rgba(157, 78, 221, 0.25)',
      badgeBg: 'rgba(157, 78, 221, 0.2)',
      badgeText: '#c084fc',
      label: '⏳ UNASSIGNED',
      textStyle: 'text-purple-200 font-medium',
    }
  }

  if (movability === 'immovable') {
    return {
      border: 'rgba(248, 250, 252, 0.35)',
      bg: 'rgba(248, 250, 252, 0.08)',
      glow: '0 0 18px rgba(248, 250, 252, 0.15)',
      badgeBg: 'rgba(248, 250, 252, 0.14)',
      badgeText: '#f8fafc',
      label: '◈ ANCIENT ANCHOR',
      textStyle: 'text-slate-100 font-cinzel font-bold tracking-wide',
    }
  }

  if (movability === 'negotiable') {
    return {
      border: 'rgba(245, 158, 11, 0.4)',
      bg: 'rgba(245, 158, 11, 0.08)',
      glow: '0 0 18px rgba(245, 158, 11, 0.25)',
      badgeBg: 'rgba(245, 158, 11, 0.18)',
      badgeText: '#fbbf24',
      label: isRescheduled ? '↻ AUTO-ALIGNED' : '✧ SACRED VOW',
      textStyle: 'text-amber-100 font-cinzel font-semibold',
    }
  }

  // Fluid / Dynamic
  return {
    border: 'rgba(56, 189, 248, 0.35)',
    bg: 'rgba(56, 189, 248, 0.08)',
    glow: '0 0 18px rgba(56, 189, 248, 0.2)',
    badgeBg: 'rgba(56, 189, 248, 0.18)',
    badgeText: '#38bdf8',
    label: isRescheduled ? '✦ SUGGESTED' : '✦ WANDERING THOUGHT',
    textStyle: 'text-cyan-100 font-medium',
  }
}

// ── Custom Node: Schedule Item (with date, time, status & importance) ─────────
function ScheduleTaskNode({ data }) {
  const { item, onComplete, onReschedule, onDelete, onOverrideBreak, onExplain } = data
  const theme = getNodeTheme(item)

  const hasStartTime = !!item.start_time
  const startDay = hasStartTime ? dayjs(item.start_time) : null
  const rawEndDay = item.end_time ? dayjs(item.end_time) : null
  const durationMin = Number(item.duration_minutes || 60)
  // Defensive guard: Guarantee endDay is strictly after startDay so invalid inverted times can never render
  const endDay = (startDay && rawEndDay && rawEndDay.isAfter(startDay))
    ? rawEndDay
    : (startDay ? startDay.add(durationMin, 'minute') : null)

  return (
    <div
      className="relative px-3.5 py-3 min-w-[210px] max-w-[260px] rounded-xl transition-all duration-200 group"
      style={{
        background: '#10141e',
        border: `1px solid ${theme.border}`,
        boxShadow: `${theme.glow}, 0 4px 20px rgba(0,0,0,0.7)`,
      }}
    >
      <Handle type="target" position={Position.Left} className="!bg-slate-300 !w-2 !h-2" />

      {/* Top row: Priority/Status Badge & Quick Delete */}
      <div className="flex items-center justify-between gap-1 mb-1.5">
        <span
          className="text-[9px] font-cinzel font-bold px-2 py-0.5 rounded uppercase tracking-wider"
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

        {(item.completion_note || item.reschedule_reason) && (
          <div className="text-[9px] text-amber-300/90 font-mono truncate" title={item.completion_note || item.reschedule_reason}>
            ↻ {item.completion_note || item.reschedule_reason}
          </div>
        )}
      </div>

      {/* Action buttons */}
      <div className="mt-2.5 pt-2 flex items-center gap-1.5 flex-wrap" style={{ borderTop: '1px solid rgba(255,255,255,0.08)' }}>
        {item.status !== 'completed' && (
          <button
            onClick={(e) => {
              e.stopPropagation()
              if (onComplete) onComplete(item.id)
            }}
            className="flex-1 min-w-[50px] py-1 rounded text-[10px] font-bold transition-all text-emerald-400 hover:text-emerald-200"
            style={{ background: 'rgba(16, 185, 129, 0.15)', border: '1px solid rgba(16, 185, 129, 0.3)' }}
          >
            ✓ Done
          </button>
        )}
        {item.auto_rescheduled && onOverrideBreak && (
          <button
            onClick={(e) => {
              e.stopPropagation()
              onOverrideBreak(item.id, true)
            }}
            className="flex-1 min-w-[65px] py-1 rounded text-[10px] font-bold transition-all text-amber-300 hover:text-amber-100"
            style={{ background: 'rgba(245, 158, 11, 0.2)', border: '1px solid rgba(245, 158, 11, 0.4)' }}
            title="Place strictly with zero decompression break"
          >
            ⚡ Skip Break
          </button>
        )}
        {(item.strict_time || item.completion_note?.includes('Strict')) && onOverrideBreak && (
          <button
            onClick={(e) => {
              e.stopPropagation()
              onOverrideBreak(item.id, false)
            }}
            className="flex-1 min-w-[65px] py-1 rounded text-[10px] font-bold transition-all text-sky-300 hover:text-sky-100"
            style={{ background: 'rgba(56, 189, 248, 0.2)', border: '1px solid rgba(56, 189, 248, 0.4)' }}
            title="Restore 35m cognitive decompression break"
          >
            🧘 Add Break
          </button>
        )}
        <button
          onClick={(e) => {
            e.stopPropagation()
            if (onReschedule) onReschedule(item.id)
          }}
          className="py-1 px-2 rounded text-[10px] font-bold transition-all text-amber-300 hover:text-amber-100"
          style={{ background: 'rgba(245, 158, 11, 0.12)', border: '1px solid rgba(245, 158, 11, 0.25)' }}
          title="Auto-reschedule to next free slot"
        >
          ↻
        </button>
        {onExplain && (
          <button
            onClick={(e) => {
              e.stopPropagation()
              onExplain(item)
            }}
            className="py-1 px-2 rounded text-[10px] font-bold transition-all text-cyan-300 hover:text-cyan-100"
            style={{ background: 'rgba(6, 182, 212, 0.15)', border: '1px solid rgba(6, 182, 212, 0.3)' }}
            title="Explain schedule decision"
          >
            💡
          </button>
        )}
      </div>

      <Handle type="source" position={Position.Right} className="!bg-cyan-400 !w-2 !h-2" />
    </div>
  )
}

// ── Custom Node: Date Branch (Day Milestone Hub) ──────────────────────────────
function DateBranchNode({ data }) {
  const { dateLabel, dayName, count, totalDuration, isToday, isPending } = data
  const accentColor = isToday ? '#38bdf8' : isPending ? '#c084fc' : '#f8fafc'

  return (
    <div
      className="relative px-4 py-2.5 rounded-2xl min-w-[160px] text-center transition-all select-none"
      style={{
        background: '#0e121c',
        border: `1px solid ${accentColor}45`,
        boxShadow: `0 0 16px ${accentColor}18, 0 4px 16px rgba(0,0,0,0.6)`,
      }}
    >
      <Handle type="target" position={Position.Left} className="!bg-slate-300 !w-2.5 !h-2.5" />
      <div className="flex items-center justify-center gap-1.5">
        <span className="text-xs text-slate-400">{isPending ? '⏳' : isToday ? '✧' : '◈'}</span>
        <span className="font-cinzel text-xs font-bold uppercase tracking-wider text-slate-100">
          {dayName}
        </span>
      </div>
      <p className="text-[10px] font-mono text-slate-400 mt-0.5">{dateLabel}</p>
      <div
        className="mt-1 inline-flex items-center gap-2 px-2.5 py-0.5 rounded-full text-[9px] font-mono font-medium"
        style={{ background: 'rgba(255, 255, 255, 0.05)', color: accentColor, border: `1px solid ${accentColor}30` }}
      >
        <span>{count} {count === 1 ? 'vow' : 'vows'}</span>
        {totalDuration > 0 && <span>• {Math.round(totalDuration / 60 * 10) / 10}h</span>}
      </div>
      <Handle type="source" position={Position.Right} className="!bg-slate-300 !w-2.5 !h-2.5" />
    </div>
  )
}

// ── Custom Node: Center Timeline Core ─────────────────────────────────────────
function CenterTimelineNode({ data }) {
  const { total, completed, pending, onAutoRescheduleAll, onDeploySample } = data

  return (
    <div
      className="relative px-7 py-5 rounded-3xl min-w-[240px] text-center transition-all select-none"
      style={{
        background: 'radial-gradient(circle at 50% 30%, #151a27 0%, #07090e 100%)',
        border: '1.5px solid rgba(203, 213, 225, 0.3)',
        boxShadow: '0 0 30px rgba(248, 250, 252, 0.08), 0 8px 32px rgba(0, 0, 0, 0.8)',
      }}
    >
      <div className="flex items-center justify-center gap-2 mb-1">
        <span className="text-xl text-slate-200">◈</span>
        <span className="font-cinzel text-sm font-bold tracking-widest uppercase text-slate-100">
          CHRONA TIMELINE FLOW
        </span>
      </div>
      <p className="text-[10px] text-cyan-300 font-mono tracking-wider">
        {dayjs().format('dddd, MMMM D, YYYY')}
      </p>

      {/* Stats pill */}
      <div className="mt-3 flex items-center justify-center gap-1.5 text-[9px] font-mono">
        <span className="px-2.5 py-0.5 rounded-full bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
          ✓ {completed}
        </span>
        <span className="px-2.5 py-0.5 rounded-full bg-purple-500/15 text-purple-300 border border-purple-500/30">
          ⏳ {pending}
        </span>
        <span className="px-2.5 py-0.5 rounded-full bg-cyan-500/15 text-cyan-300 border border-cyan-500/30">
          Σ {total}
        </span>
      </div>

      {total === 0 && (
        <div className="mt-3">
          <p className="text-[10px] text-slate-400 font-mono">
            No scheduled tasks on the timeline yet
          </p>
          <a
            href="/chat"
            className="inline-block mt-2 text-[10px] font-semibold tracking-wider px-3 py-1.5 rounded-xl text-white bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 shadow-sm transition-all"
          >
            + Plan in Chrona AI
          </a>
        </div>
      )}

      {pending > 0 && onAutoRescheduleAll && (
        <button
          onClick={(e) => {
            e.stopPropagation()
            onAutoRescheduleAll()
          }}
          className="mt-3 text-[9px] font-semibold tracking-wider uppercase px-3 py-1 rounded-xl transition-all text-amber-200 hover:text-white bg-amber-500/20 border border-amber-500/35"
        >
          ⚡ Place Flexible Tasks
        </button>
      )}

      <Handle type="source" position={Position.Right} className="!bg-slate-300 !w-3 !h-3" />
      <Handle type="source" position={Position.Left} className="!bg-slate-300 !w-3 !h-3" />
    </div>
  )
}

const nodeTypes = {
  centerNode: CenterTimelineNode,
  dateBranchNode: DateBranchNode,
  scheduleTaskNode: ScheduleTaskNode,
}

// ── Layout Generator ─────────────────────────────────────────────────────────
function buildMindmapLayout(items, { onComplete, onReschedule, onDelete, onAutoRescheduleAll, onOverrideBreak, onExplain }) {
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
    const sTime = item.start_time || item.event_datetime
    if (sTime) {
      key = dayjs(sTime).format('YYYY-MM-DD')
    }
    if (!groups[key]) groups[key] = []
    groups[key].push(item)
  })

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
        dateLabel: isPending ? 'Flexible Tasks' : branchDate.format('MMM D, YYYY'),
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
        stroke: isToday ? '#38bdf8' : isPending ? '#f59e0b' : '#a855f7',
        strokeWidth: 2,
      },
    })

    // Sort items on this date by start_time
    const sortedBranchItems = branchItems.slice().sort((a, b) => {
      const aTime = a.start_time || a.event_datetime
      const bTime = b.start_time || b.event_datetime
      if (!aTime) return 1
      if (!bTime) return -1
      return dayjs(aTime).diff(dayjs(bTime))
    })

    // Arrange task nodes horizontally / stacked out from the date node
    sortedBranchItems.forEach((task, taskIdx) => {
      const taskId = `task-${task.id || taskIdx}`
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
          onOverrideBreak,
          onExplain,
        },
        draggable: true,
      })

      // Link first task to Date Branch, subsequent tasks linked in sequential timeline order!
      const sourceId = taskIdx === 0 ? dateNodeId : `task-${sortedBranchItems[taskIdx - 1].id || (taskIdx - 1)}`
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

  // Add explicit task dependency edges for decomposed goals and subtasks
  items.forEach(task => {
    const deps = task.dependencies || task.depends_on || []
    if (Array.isArray(deps)) {
      deps.forEach(depId => {
        const depTaskId = `task-${depId}`
        const targetTaskId = `task-${task.id}`
        if (nodes.some(n => n.id === depTaskId) && nodes.some(n => n.id === targetTaskId)) {
          // Avoid duplicate edges
          const edgeId = `dep-${depTaskId}-${targetTaskId}`
          if (!edges.some(e => e.id === edgeId)) {
            edges.push({
              id: edgeId,
              source: depTaskId,
              target: targetTaskId,
              animated: true,
              label: 'depends on',
              labelStyle: { fill: '#38bdf8', fontSize: 9, fontFamily: 'monospace', fontWeight: 600 },
              labelBgStyle: { fill: '#14161b', fillOpacity: 0.9 },
              markerEnd: {
                type: MarkerType.ArrowClosed,
                color: '#38bdf8',
                width: 12,
                height: 12,
              },
              style: {
                stroke: '#38bdf8',
                strokeWidth: 2,
                strokeDasharray: '4 4',
              },
            })
          }
        }
      })
    }
  })

  return { nodes, edges }
}

// ── Inner Flow Canvas Component ───────────────────────────────────────────────
function MindmapFlow({ items, loadSchedule }) {
  const [filter, setFilter] = useState('all')
  const [search, setSearch] = useState('')
  const [canvasHeight, setCanvasHeight] = useState(0)
  const [nodes, setNodes, onNodesChange] = useNodesState([])
  const [edges, setEdges, onEdgesChange] = useEdgesState([])
  const rfInstance = useRef(null)

  // Measure parent container height dynamically so ReactFlow never collapses
  useLayoutEffect(() => {
    function measure() {
      const navEl = document.querySelector('nav')
      const navH = navEl ? navEl.getBoundingClientRect().height : 48
      const calculated = window.innerHeight - navH - 52
      setCanvasHeight(Math.max(calculated, 500))
    }
    measure()
    window.addEventListener('resize', measure)
    return () => window.removeEventListener('resize', measure)
  }, [])

  // Actions
  const [explainData, setExplainData] = useState(null)

  const handleOverrideBreak = useCallback(async (taskId, skipBreak) => {
    try {
      await api.overrideBreak({ task_id: taskId, skip_break: skipBreak })
      loadSchedule()
    } catch (e) {
      alert(`Error adjusting break: ${e.message}`)
    }
  }, [loadSchedule])

  const handleExplain = useCallback(async (item) => {
    try {
      const res = await api.explainSchedule(item.id)
      setExplainData({ item, ...res })
    } catch (err) {
      setExplainData({
        item,
        explanation: item.completion_note || 'Scheduled using constraint satisfaction, cognitive buffer analysis, and available focus windows.',
        factors: ['Energy & exhaustion buffer', 'Priority constraint matching', 'Collision avoidance']
      })
    }
  }, [])

  const handleComplete = useCallback(async (taskId) => {
    try {
      await api.completeTask(taskId)
      loadSchedule()
    } catch (e) {
      alert(`Error completing task: ${e.message}`)
    }
  }, [loadSchedule])

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
      loadSchedule()
    } catch (e) {
      alert(`Error: ${e.message}`)
    }
  }, [loadSchedule])

  const handleAutoRescheduleAll = useCallback(async () => {
    try {
      const res = await api.autoReschedule(null, true)
      alert(res.message || 'All unplaced tasks have been automatically scheduled!')
      loadSchedule()
    } catch (e) {
      alert(`Auto-reschedule error: ${e.message}`)
    }
  }, [loadSchedule])

  // Filter items
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
      result = result.filter(i => {
        const sTime = i.start_time || i.event_datetime
        return sTime && dayjs(sTime).format('YYYY-MM-DD') === today
      })
    }

    if (search.trim()) {
      const q = search.toLowerCase()
      result = result.filter(i => (i.title || '').toLowerCase().includes(q))
    }
    return result
  }, [items, filter, search])

  // Re-build layout
  useEffect(() => {
    const { nodes: newNodes, edges: newEdges } = buildMindmapLayout(filteredItems, {
      onComplete: handleComplete,
      onReschedule: handleReschedule,
      onDelete: handleDelete,
      onAutoRescheduleAll: handleAutoRescheduleAll,
      onOverrideBreak: handleOverrideBreak,
      onExplain: handleExplain,
    })
    setNodes(newNodes)
    setEdges(newEdges)

    setTimeout(() => {
      rfInstance.current?.fitView({ padding: 0.25, duration: 400 })
    }, 200)
  }, [filteredItems, handleComplete, handleReschedule, handleDelete, handleAutoRescheduleAll, handleOverrideBreak, handleExplain, setNodes, setEdges])

  const onInit = useCallback((instance) => {
    rfInstance.current = instance
    setTimeout(() => instance.fitView({ padding: 0.25, duration: 400 }), 300)
  }, [])

  return (
    <div className="flex-1 w-full h-full min-h-0 relative" style={{ background: '#07090e' }}>
      {/* Floating Minimal Canvas Menu Controls */}
      <div className="absolute top-3 right-3 sm:right-6 z-20 flex items-center gap-2 pointer-events-auto">
        <div className="flex items-center bg-[#0d121e]/90 p-1 rounded-xl border border-slate-800/80 backdrop-blur-xl shadow-lg gap-1">
          <select
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            className="bg-transparent text-xs text-slate-300 font-medium px-2 py-0.5 focus:outline-none cursor-pointer"
          >
            <option value="all" className="bg-[#0f1422]">All Tasks ({items.length})</option>
            <option value="today" className="bg-[#0f1422]">Today Only</option>
            <option value="pending" className="bg-[#0f1422]">Pending</option>
            <option value="critical" className="bg-[#0f1422]">Urgent</option>
            <option value="completed" className="bg-[#0f1422]">Completed</option>
          </select>

          <div className="h-3 w-px bg-slate-800" />

          <button
            onClick={() => rfInstance.current?.zoomIn({ duration: 300 })}
            className="w-6 h-6 rounded-lg text-xs text-slate-400 hover:text-white hover:bg-white/5 flex items-center justify-center transition-all"
            title="Zoom In"
          >
            +
          </button>
          <button
            onClick={() => rfInstance.current?.zoomOut({ duration: 300 })}
            className="w-6 h-6 rounded-lg text-xs text-slate-400 hover:text-white hover:bg-white/5 flex items-center justify-center transition-all"
            title="Zoom Out"
          >
            −
          </button>
          <button
            onClick={() => rfInstance.current?.fitView({ padding: 0.25, duration: 400 })}
            className="px-2 py-0.5 rounded-lg text-xs text-slate-300 hover:text-white bg-white/5 hover:bg-white/10 border border-white/5 transition-all flex items-center gap-1"
            title="Reset zoom & fit view"
          >
            <span>⛶</span>
            <span className="hidden sm:inline">Fit</span>
          </button>
        </div>
      </div>

      {/* ── Pure Full-Height Canvas ─────────────────────────────────────────── */}
      <div className="absolute inset-0 w-full h-full">
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
          <Background color="rgba(255, 255, 255, 0.05)" gap={28} size={1.2} variant="dots" />
        </ReactFlow>
      </div>

      {/* ── Explanation Modal ───────────────────────────────────────── */}
      {explainData && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fade-in">
          <div className="border border-white/10 rounded-2xl p-5 max-w-md w-full shadow-2xl relative" style={{ background: '#14161b' }}>
            <button
              onClick={() => setExplainData(null)}
              className="absolute top-3.5 right-3.5 text-slate-400 hover:text-white text-base"
            >
              ✕
            </button>
            <div className="flex items-center gap-2 mb-2 text-cyan-400">
              <span className="text-lg">💡</span>
              <h3 className="font-bold text-sm tracking-wide uppercase">Chrona Decision Reasoning</h3>
            </div>
            <h4 className="text-base font-semibold text-white mb-2">{explainData.item?.title}</h4>
            <p className="text-xs text-slate-300 leading-relaxed mb-3 whitespace-pre-wrap">
              {explainData.explanation || explainData.item?.completion_note}
            </p>
            {explainData.factors && explainData.factors.length > 0 && (
              <div className="space-y-1 mb-4">
                <p className="text-[10px] uppercase font-bold text-slate-400">Decision Factors:</p>
                {explainData.factors.map((f, i) => (
                  <div key={i} className="text-xs text-cyan-200/90 flex items-center gap-1.5">
                    <span>✦</span>
                    <span>{f}</span>
                  </div>
                ))}
              </div>
            )}
            <div className="flex justify-end gap-2 pt-2 border-t border-white/10">
              {explainData.item?.auto_rescheduled && (
                <button
                  onClick={() => {
                    handleOverrideBreak(explainData.item.id, true)
                    setExplainData(null)
                  }}
                  className="px-3 py-1.5 rounded-lg text-xs font-bold bg-amber-500/20 text-amber-300 border border-amber-500/40 hover:brightness-110"
                >
                  ⚡ Place Strictly (Skip Break)
                </button>
              )}
              <button
                onClick={() => setExplainData(null)}
                className="px-4 py-1.5 rounded-lg text-xs font-semibold bg-white/10 text-white hover:bg-white/15"
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

// ── Export Wrapped in ReactFlowProvider ───────────────────────────────────────
export default function CalendarMindmap({ onOpenRepair }) {
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)

  const loadSchedule = useCallback(async () => {
    try {
      setLoading(true)
      // Fetch from both schedule_items and events to ensure nothing is missed!
      const [resSched, resEvt] = await Promise.all([
        api.scheduleItems().catch(() => ({ items: [] })),
        api.events().catch(() => ({ events: [] })),
      ])

      const schedItems = resSched.items || []
      const evtItems = (resEvt.events || []).map(e => ({
        id: e.id,
        title: e.title,
        task_type: e.task_type || e.category || 'task',
        priority: e.priority || 3,
        duration_minutes: e.duration_minutes || (e.prep_hours ? e.prep_hours * 60 : 60),
        start_time: e.event_datetime || e.fixed_start,
        end_time: e.fixed_end,
        deadline: e.deadline,
        status: e.status || 'scheduled',
      }))

      // Deduplicate by title/id and filter phantom dummy tasks
      const combined = []
      const existingTitles = new Set()
      for (const it of [...schedItems, ...evtItems]) {
        const t = (it.title || '').trim().toLowerCase()
        if (!t || ['pending task', 'untitled', 'task', 'undefined', 'null'].includes(t)) continue
        if (!existingTitles.has(t)) {
          existingTitles.add(t)
          combined.push(it)
        }
      }

      setItems(combined)
    } catch (e) {
      console.error('Failed to load schedule items:', e)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadSchedule()
  }, [loadSchedule])

  if (loading && items.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center backdrop-blur-sm" style={{ background: '#0c0d10' }}>
        <div className="flex flex-col items-center gap-3">
          <div className="w-8 h-8 border-2 border-cyan-400 border-t-transparent rounded-full animate-spin" />
          <p className="text-xs text-cyan-300 font-mono">Loading schedule mind map...</p>
        </div>
      </div>
    )
  }

  return (
    <ReactFlowProvider>
      <MindmapFlow
        items={items}
        loadSchedule={loadSchedule}
      />
    </ReactFlowProvider>
  )
}
