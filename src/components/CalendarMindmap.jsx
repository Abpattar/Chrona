import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react'
import {
  ReactFlow,
  Background,
  useNodesState,
  useEdgesState,
  Handle,
  Position,
  MarkerType,
  ReactFlowProvider,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import dayjs from 'dayjs'
import { api } from '../lib/api'

// ── Pin Board Themes ──────────────────────────────────────────────────────────
export function getNodeTheme(item) {
  const isCompleted = item.status === 'completed'
  const isPending = item.status === 'pending' || !item.start_time
  const isRescheduled =
    item.status === 'rescheduled' ||
    !!item.auto_rescheduled ||
    !!item.completion_note?.includes('Auto-rescheduled')
  const movability =
    item.movability ||
    (item.flexibility === false ||
    item.controllability === 'external' ||
    item.task_type === 'fixed' ||
    /exam|midterm|final|quiz|test|lecture|class|lab|movie|cinema|film|theater|theatre|concert|show|doctor|dentist|appointment|clinic|therapy|hospital|surgery|interview|court|family|wedding|ceremony/i.test(
      item.title || ''
    )
      ? 'immovable'
      : Number(item.priority || 3) <= 2
      ? 'negotiable'
      : 'fluid')

  if (isCompleted) {
    return {
      border: 'rgba(52, 211, 153, 0.45)',
      pinColor: '#34d399',
      glow: '0 0 18px rgba(16, 185, 129, 0.18)',
      badgeBg: 'rgba(16, 185, 129, 0.18)',
      badgeText: '#34d399',
      label: '✓ FULFILLED',
      textStyle: 'line-through text-slate-400',
    }
  }

  if (isPending) {
    return {
      border: 'rgba(192, 132, 252, 0.45)',
      pinColor: '#c084fc',
      glow: '0 0 18px rgba(157, 78, 221, 0.2)',
      badgeBg: 'rgba(157, 78, 221, 0.18)',
      badgeText: '#c084fc',
      label: '⏳ UNASSIGNED',
      textStyle: 'text-purple-100 font-medium',
    }
  }

  if (movability === 'immovable') {
    return {
      border: 'rgba(226, 232, 240, 0.38)',
      pinColor: '#f8fafc',
      glow: '0 0 18px rgba(248, 250, 252, 0.12)',
      badgeBg: 'rgba(248, 250, 252, 0.12)',
      badgeText: '#f8fafc',
      label: '◈ ANCHOR',
      textStyle: 'text-slate-100 font-semibold',
    }
  }

  if (movability === 'negotiable') {
    return {
      border: 'rgba(245, 158, 11, 0.45)',
      pinColor: '#fbbf24',
      glow: '0 0 18px rgba(245, 158, 11, 0.18)',
      badgeBg: 'rgba(245, 158, 11, 0.18)',
      badgeText: '#fbbf24',
      label: isRescheduled ? '↻ ALIGNED' : '✧ PRIORITY',
      textStyle: 'text-amber-100 font-semibold',
    }
  }

  return {
    border: 'rgba(56, 189, 248, 0.4)',
    pinColor: '#38bdf8',
    glow: '0 0 18px rgba(56, 189, 248, 0.18)',
    badgeBg: 'rgba(56, 189, 248, 0.16)',
    badgeText: '#38bdf8',
    label: isRescheduled ? '✦ SUGGESTED' : '✦ FLUID',
    textStyle: 'text-cyan-100 font-medium',
  }
}

// ── Realistic Push-Pin Cap ────────────────────────────────────────────────────
function PushPin({ color = '#38bdf8' }) {
  return (
    <div
      className="absolute -top-2.5 left-1/2 -translate-x-1/2 z-20 flex flex-col items-center pointer-events-none"
      aria-hidden="true"
    >
      <div
        className="w-4 h-4 rounded-full border border-white/40 shadow-[0_3px_8px_rgba(0,0,0,0.85)]"
        style={{
          background: `radial-gradient(circle at 35% 30%, #ffffff 0%, ${color} 45%, #0f172a 100%)`,
          boxShadow: `0 0 10px ${color}88, 0 3px 6px rgba(0,0,0,0.85)`,
        }}
      />
      <div className="w-0.5 h-1.5 bg-slate-400/70 -mt-0.5 rounded-b-full" />
    </div>
  )
}

// ── Pinned Task Note Card ─────────────────────────────────────────────────────
function ScheduleTaskNode({ data }) {
  const { item, tiltDeg = 0, onComplete, onReschedule, onDelete, onOverrideBreak, onExplain } = data
  const theme = getNodeTheme(item)

  const hasStartTime = !!item.start_time
  const startDay = hasStartTime ? dayjs(item.start_time) : null
  const rawEndDay = item.end_time ? dayjs(item.end_time) : null
  const durationMin = Number(item.duration_minutes || 60)
  const endDay =
    startDay && rawEndDay && rawEndDay.isAfter(startDay)
      ? rawEndDay
      : startDay
      ? startDay.add(durationMin, 'minute')
      : null

  return (
    <div
      className="relative px-3.5 pt-4 pb-3 w-[238px] rounded-xl transition-transform duration-200 hover:!rotate-0 hover:scale-[1.02] group cursor-grab active:cursor-grabbing"
      style={{
        background: 'linear-gradient(155deg, rgba(18, 24, 38, 0.96), rgba(10, 14, 23, 0.96))',
        border: `1px solid ${theme.border}`,
        boxShadow: `${theme.glow}, 0 10px 26px rgba(0,0,0,0.72)`,
        transform: `rotate(${tiltDeg}deg)`,
      }}
    >
      <PushPin color={theme.pinColor} />
      <Handle type="target" position={Position.Top} className="!opacity-0 !w-2 !h-2" />

      {/* Top row: Badge & Delete */}
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
          className="opacity-50 group-hover:opacity-100 text-[10px] font-bold text-rose-400 hover:text-rose-300 px-1"
          title="Remove item"
        >
          ✕
        </button>
      </div>

      {/* Title */}
      <h4 className={`text-xs leading-snug line-clamp-2 ${theme.textStyle}`}>
        {item.title}
      </h4>

      {/* Date and Time */}
      <div
        className="mt-2 pt-2 flex flex-col gap-1 text-[10px]"
        style={{ borderTop: '1px solid rgba(255,255,255,0.06)' }}
      >
        {hasStartTime ? (
          <div className="flex items-center justify-between font-mono text-[10px] text-slate-300">
            <span className="font-semibold text-white">
              {startDay.format('h:mm A')} – {endDay ? endDay.format('h:mm A') : 'TBD'}
            </span>
            <span className="text-[9px] text-cyan-300 px-1.5 py-0.2 rounded bg-cyan-500/10 border border-cyan-500/20">
              {durationMin}m
            </span>
          </div>
        ) : (
          <div className="flex items-center justify-between text-amber-300/90 font-mono">
            <span>⏳ Flexible</span>
            <span className="text-[9px] px-1 rounded bg-amber-500/10">{durationMin}m</span>
          </div>
        )}

        {(item.completion_note || item.reschedule_reason) && (
          <div
            className="text-[9px] text-amber-300/85 font-mono truncate"
            title={item.completion_note || item.reschedule_reason}
          >
            📌 {item.completion_note || item.reschedule_reason}
          </div>
        )}
      </div>

      {/* Action buttons */}
      <div
        className="mt-2.5 pt-2 flex items-center gap-1.5 flex-wrap"
        style={{ borderTop: '1px solid rgba(255,255,255,0.08)' }}
      >
        {item.status !== 'completed' && (
          <button
            onClick={(e) => {
              e.stopPropagation()
              if (onComplete) onComplete(item.id)
            }}
            className="flex-1 min-w-[50px] py-1 rounded text-[10px] font-bold transition-all text-emerald-300 hover:text-emerald-100"
            style={{
              background: 'rgba(16, 185, 129, 0.14)',
              border: '1px solid rgba(16, 185, 129, 0.3)',
            }}
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
            className="py-1 px-2 rounded text-[10px] font-bold text-amber-300 bg-amber-500/20 border border-amber-500/40"
            title="Skip break"
          >
            ⚡
          </button>
        )}
        <button
          onClick={(e) => {
            e.stopPropagation()
            if (onReschedule) onReschedule(item.id)
          }}
          className="py-1 px-2 rounded text-[10px] font-bold text-amber-300 hover:text-amber-100"
          style={{
            background: 'rgba(245, 158, 11, 0.12)',
            border: '1px solid rgba(245, 158, 11, 0.25)',
          }}
          title="Auto-reschedule"
        >
          ↻
        </button>
        {onExplain && (
          <button
            onClick={(e) => {
              e.stopPropagation()
              onExplain(item)
            }}
            className="py-1 px-2 rounded text-[10px] font-bold text-cyan-300 hover:text-cyan-100"
            style={{
              background: 'rgba(6, 182, 212, 0.14)',
              border: '1px solid rgba(6, 182, 212, 0.3)',
            }}
            title="Explain decision"
          >
            💡
          </button>
        )}
      </div>

      <Handle type="source" position={Position.Bottom} className="!opacity-0 !w-2 !h-2" />
    </div>
  )
}

// ── Pinned Day Column Header Card ─────────────────────────────────────────────
function DateBranchNode({ data }) {
  const { dateLabel, dayName, count, totalDuration, isToday, isPending } = data
  const accentColor = isToday ? '#38bdf8' : isPending ? '#c084fc' : '#fbbf24'

  return (
    <div
      className="relative px-4 pt-3.5 pb-2.5 rounded-2xl w-[238px] text-center select-none cursor-grab active:cursor-grabbing"
      style={{
        background: 'linear-gradient(145deg, rgba(20, 27, 45, 0.96), rgba(11, 15, 25, 0.96))',
        border: `1px solid ${accentColor}55`,
        boxShadow: `0 0 22px ${accentColor}20, 0 8px 20px rgba(0,0,0,0.7)`,
      }}
    >
      <PushPin color={accentColor} />
      <div className="flex items-center justify-center gap-1.5">
        <span className="text-xs" style={{ color: accentColor }}>
          {isPending ? '⏳' : isToday ? '✧' : '◈'}
        </span>
        <span className="font-cinzel text-xs font-bold uppercase tracking-widest text-slate-100">
          {dayName}
        </span>
      </div>
      <div className="mt-1 flex items-center justify-center gap-2 text-[10px] font-mono">
        <span className="text-slate-300">{dateLabel}</span>
        <span
          className="px-2 py-0.5 rounded-full text-[9px] font-semibold"
          style={{
            background: `${accentColor}18`,
            color: accentColor,
            border: `1px solid ${accentColor}35`,
          }}
        >
          {count} {count === 1 ? 'pin' : 'pins'}
          {totalDuration > 0 ? ` · ${Math.round((totalDuration / 60) * 10) / 10}h` : ''}
        </span>
      </div>
      <Handle type="source" position={Position.Bottom} className="!opacity-0 !w-2 !h-2" />
    </div>
  )
}

const nodeTypes = {
  dateBranchNode: DateBranchNode,
  scheduleTaskNode: ScheduleTaskNode,
}

// ── Pin-Board 2D Grid Layout Generator ────────────────────────────────────────
function buildPinBoardLayout(
  items,
  { onComplete, onReschedule, onDelete, onOverrideBreak, onExplain }
) {
  const nodes = []
  const edges = []

  if (!items.length) return { nodes, edges }

  // Group items by local YYYY-MM-DD or 'pending'
  const groups = {}
  items.forEach((item) => {
    let key = 'pending'
    const sTime = item.start_time || item.event_datetime
    if (sTime) {
      key = dayjs(sTime).format('YYYY-MM-DD')
    }
    if (!groups[key]) groups[key] = []
    groups[key].push(item)
  })

  const dateKeys = Object.keys(groups).filter((k) => k !== 'pending').sort()
  if (groups.pending?.length) dateKeys.unshift('pending')

  const numGroups = dateKeys.length
  const colsPerRow = numGroups <= 2 ? numGroups : numGroups <= 6 ? 3 : 4
  const colSpacingX = 285
  const headerHeight = 96
  const cardSlotY = 186
  const rowGapY = 64

  // Split dateKeys into visual rows so the board stays compact & balanced
  let currentRowY = 0
  for (let rowStart = 0; rowStart < numGroups; rowStart += colsPerRow) {
    const rowKeys = dateKeys.slice(rowStart, rowStart + colsPerRow)
    let maxRowHeight = headerHeight

    rowKeys.forEach((dateKey, colIdx) => {
      const isPending = dateKey === 'pending'
      const branchItems = groups[dateKey]
      const branchDate = isPending ? null : dayjs(dateKey)
      const isToday = branchDate ? branchDate.isSame(dayjs(), 'day') : false

      const colX = colIdx * colSpacingX
      const dateNodeId = `date-${dateKey}`

      let totalDur = 0
      branchItems.forEach((i) => {
        totalDur += Number(i.duration_minutes || 60)
      })

      nodes.push({
        id: dateNodeId,
        type: 'dateBranchNode',
        position: { x: colX, y: currentRowY },
        data: {
          dateLabel: isPending ? 'Flexible' : branchDate.format('MMM D, YYYY'),
          dayName: isPending ? 'Pending' : isToday ? 'Today' : branchDate.format('dddd'),
          count: branchItems.length,
          totalDuration: totalDur,
          isToday,
          isPending,
        },
        draggable: true,
      })

      const sortedBranchItems = branchItems.slice().sort((a, b) => {
        const aTime = a.start_time || a.event_datetime
        const bTime = b.start_time || b.event_datetime
        if (!aTime) return 1
        if (!bTime) return -1
        return dayjs(aTime).diff(dayjs(bTime))
      })

      const threadColor = isToday ? '#38bdf8' : isPending ? '#c084fc' : 'rgba(251, 191, 36, 0.55)'

      sortedBranchItems.forEach((task, taskIdx) => {
        const taskId = `task-${task.id || `${dateKey}-${taskIdx}`}`
        const taskY = currentRowY + headerHeight + taskIdx * cardSlotY
        const tiltDeg = taskIdx % 2 === 0 ? -0.6 : 0.6

        nodes.push({
          id: taskId,
          type: 'scheduleTaskNode',
          position: { x: colX, y: taskY },
          data: {
            item: task,
            tiltDeg,
            onComplete,
            onReschedule,
            onDelete,
            onOverrideBreak,
            onExplain,
          },
          draggable: true,
        })

        // Taut pin-board string connecting header -> 1st card -> 2nd card...
        const sourceId =
          taskIdx === 0
            ? dateNodeId
            : `task-${sortedBranchItems[taskIdx - 1].id || `${dateKey}-${taskIdx - 1}`}`

        edges.push({
          id: `pin-thread-${sourceId}-${taskId}`,
          source: sourceId,
          target: taskId,
          type: 'straight',
          style: {
            stroke: threadColor,
            strokeWidth: 1.5,
            strokeDasharray: '4 3',
          },
        })
      })

      const colHeight = headerHeight + sortedBranchItems.length * cardSlotY
      if (colHeight > maxRowHeight) maxRowHeight = colHeight
    })

    currentRowY += maxRowHeight + rowGapY
  }

  // Explicit task dependency strings
  items.forEach((task) => {
    const deps = task.dependencies || task.depends_on || []
    if (Array.isArray(deps)) {
      deps.forEach((depId) => {
        const depTaskId = `task-${depId}`
        const targetTaskId = `task-${task.id}`
        if (nodes.some((n) => n.id === depTaskId) && nodes.some((n) => n.id === targetTaskId)) {
          const edgeId = `dep-${depTaskId}-${targetTaskId}`
          if (!edges.some((e) => e.id === edgeId)) {
            edges.push({
              id: edgeId,
              source: depTaskId,
              target: targetTaskId,
              animated: true,
              markerEnd: {
                type: MarkerType.ArrowClosed,
                color: '#38bdf8',
                width: 12,
                height: 12,
              },
              style: {
                stroke: '#38bdf8',
                strokeWidth: 1.8,
                strokeDasharray: '5 4',
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
  const [nodes, setNodes, onNodesChange] = useNodesState([])
  const [edges, setEdges, onEdgesChange] = useEdgesState([])
  const rfInstance = useRef(null)
  const [explainData, setExplainData] = useState(null)

  const handleOverrideBreak = useCallback(
    async (taskId, skipBreak) => {
      try {
        await api.overrideBreak({ task_id: taskId, skip_break: skipBreak })
        loadSchedule()
      } catch (e) {
        alert(`Error adjusting break: ${e.message}`)
      }
    },
    [loadSchedule]
  )

  const handleExplain = useCallback(async (item) => {
    try {
      const res = await api.explainSchedule(item.id)
      setExplainData({ item, ...res })
    } catch {
      setExplainData({
        item,
        explanation:
          item.completion_note ||
          'Scheduled using constraint satisfaction, cognitive buffer analysis, and available focus windows.',
        factors: [
          'Energy & exhaustion buffer',
          'Priority constraint matching',
          'Collision avoidance',
        ],
      })
    }
  }, [])

  const handleComplete = useCallback(
    async (taskId) => {
      try {
        await api.completeTask(taskId)
        loadSchedule()
      } catch (e) {
        alert(`Error completing task: ${e.message}`)
      }
    },
    [loadSchedule]
  )

  const handleReschedule = useCallback(
    async (taskId) => {
      try {
        const res = await api.autoReschedule(taskId)
        alert(res.message || 'Task automatically rescheduled!')
        loadSchedule()
      } catch (e) {
        alert(`Reschedule error: ${e.message}`)
      }
    },
    [loadSchedule]
  )

  const handleDelete = useCallback(
    async (taskId) => {
      if (!window.confirm('Remove this commitment from your pin board?')) return
      try {
        await api.updateScheduleItem(taskId, { status: 'cancelled' })
        loadSchedule()
      } catch (e) {
        alert(`Error: ${e.message}`)
      }
    },
    [loadSchedule]
  )

  const filteredItems = useMemo(() => {
    let result = items
    if (filter === 'pending') {
      result = result.filter((i) => i.status === 'pending' || !i.start_time)
    } else if (filter === 'critical') {
      result = result.filter((i) => Number(i.priority) <= 2)
    } else if (filter === 'completed') {
      result = result.filter((i) => i.status === 'completed')
    } else if (filter === 'today') {
      const today = dayjs().format('YYYY-MM-DD')
      result = result.filter((i) => {
        const sTime = i.start_time || i.event_datetime
        return sTime && dayjs(sTime).format('YYYY-MM-DD') === today
      })
    }

    if (search.trim()) {
      const q = search.toLowerCase()
      result = result.filter((i) => (i.title || '').toLowerCase().includes(q))
    }
    return result
  }, [items, filter, search])

  const stats = useMemo(
    () => ({
      total: items.length,
      completed: items.filter((i) => i.status === 'completed').length,
      pending: items.filter((i) => i.status === 'pending' || !i.start_time).length,
    }),
    [items]
  )

  useEffect(() => {
    const { nodes: newNodes, edges: newEdges } = buildPinBoardLayout(filteredItems, {
      onComplete: handleComplete,
      onReschedule: handleReschedule,
      onDelete: handleDelete,
      onOverrideBreak: handleOverrideBreak,
      onExplain: handleExplain,
    })
    setNodes(newNodes)
    setEdges(newEdges)

    setTimeout(() => {
      rfInstance.current?.fitView({ padding: 0.18, maxZoom: 1.05, duration: 350 })
    }, 150)
  }, [
    filteredItems,
    handleComplete,
    handleReschedule,
    handleDelete,
    handleOverrideBreak,
    handleExplain,
    setNodes,
    setEdges,
  ])

  const onInit = useCallback((instance) => {
    rfInstance.current = instance
    setTimeout(() => instance.fitView({ padding: 0.18, maxZoom: 1.05, duration: 350 }), 200)
  }, [])

  return (
    <div
      className="flex-1 w-full h-full min-h-0 relative"
      style={{
        background:
          'radial-gradient(circle at 50% 25%, #0e1422 0%, #070a11 65%, #05070c 100%)',
      }}
    >
      {/* Floating Pin Board Summary Badge (Top Left) */}
      <div className="absolute top-3 left-3 sm:left-6 z-20 flex items-center gap-2 pointer-events-auto">
        <div className="flex items-center gap-2.5 bg-[#0d121e]/90 px-3 py-1.5 rounded-xl border border-slate-800/80 backdrop-blur-xl shadow-lg">
          <span className="text-xs font-cinzel font-bold tracking-wider text-slate-100 flex items-center gap-1.5">
            <span>📌</span>
            <span>Pin Board</span>
          </span>
          <div className="h-3 w-px bg-slate-800" />
          <div className="flex items-center gap-1.5 text-[10px] font-mono">
            <span className="text-cyan-300">{stats.total} pins</span>
            <span className="text-slate-600">·</span>
            <span className="text-emerald-400">✓ {stats.completed}</span>
            {stats.pending > 0 && (
              <>
                <span className="text-slate-600">·</span>
                <span className="text-purple-300">⏳ {stats.pending}</span>
              </>
            )}
          </div>
        </div>
      </div>

      {/* Floating Canvas Controls (Top Right) */}
      <div className="absolute top-3 right-3 sm:right-6 z-20 flex items-center gap-2 pointer-events-auto">
        <div className="flex items-center bg-[#0d121e]/90 p-1 rounded-xl border border-slate-800/80 backdrop-blur-xl shadow-lg gap-1">
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Filter pins..."
            className="hidden sm:block w-28 bg-white/[0.04] text-xs text-slate-200 placeholder-slate-500 px-2 py-0.5 rounded-lg border border-slate-800 focus:outline-none focus:border-cyan-500/40"
          />

          <select
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            className="bg-transparent text-xs text-slate-300 font-medium px-2 py-0.5 focus:outline-none cursor-pointer"
          >
            <option value="all" className="bg-[#0f1422]">All Pins ({items.length})</option>
            <option value="today" className="bg-[#0f1422]">Today Only</option>
            <option value="pending" className="bg-[#0f1422]">Pending</option>
            <option value="critical" className="bg-[#0f1422]">Urgent</option>
            <option value="completed" className="bg-[#0f1422]">Completed</option>
          </select>

          <div className="h-3 w-px bg-slate-800" />

          <button
            onClick={() => rfInstance.current?.zoomIn({ duration: 250 })}
            className="w-6 h-6 rounded-lg text-xs text-slate-400 hover:text-white hover:bg-white/5 flex items-center justify-center transition-all"
            title="Zoom In"
          >
            +
          </button>
          <button
            onClick={() => rfInstance.current?.zoomOut({ duration: 250 })}
            className="w-6 h-6 rounded-lg text-xs text-slate-400 hover:text-white hover:bg-white/5 flex items-center justify-center transition-all"
            title="Zoom Out"
          >
            −
          </button>
          <button
            onClick={() => rfInstance.current?.fitView({ padding: 0.18, maxZoom: 1.05, duration: 350 })}
            className="px-2 py-0.5 rounded-lg text-xs text-slate-300 hover:text-white bg-white/5 hover:bg-white/10 border border-white/5 transition-all flex items-center gap-1"
            title="Reset zoom & fit board"
          >
            <span>⛶</span>
            <span className="hidden sm:inline">Fit</span>
          </button>
        </div>
      </div>

      {/* Empty State */}
      {filteredItems.length === 0 && (
        <div className="absolute inset-0 z-10 flex items-center justify-center pointer-events-none">
          <div className="pointer-events-auto rounded-2xl p-6 bg-[#0e1320]/90 border border-slate-700/40 backdrop-blur-xl shadow-2xl text-center max-w-xs">
            <div className="text-2xl mb-2">📌</div>
            <h3 className="text-sm font-bold text-slate-100">Pin Board is Empty</h3>
            <p className="text-xs text-slate-400 mt-1">
              Add tasks or upload a timetable in Chrona AI to pin them onto your board.
            </p>
            <a
              href="/chat"
              className="inline-block mt-3 text-xs font-semibold px-3.5 py-1.5 rounded-xl text-white bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 shadow-sm"
            >
              + Open Chrona AI
            </a>
          </div>
        </div>
      )}

      {/* Full-Height Pin Board Canvas */}
      <div className="absolute inset-0 w-full h-full">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onInit={onInit}
          nodeTypes={nodeTypes}
          panOnDrag={true}
          zoomOnScroll={true}
          panOnScroll={false}
          zoomOnPinch={true}
          preventScrolling={true}
          minZoom={0.2}
          maxZoom={2.0}
          proOptions={{ hideAttribution: true }}
        >
          <Background color="rgba(148, 163, 184, 0.14)" gap={24} size={1.4} variant="dots" />
        </ReactFlow>
      </div>

      {/* Explanation Modal */}
      {explainData && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fade-in">
          <div
            className="border border-white/10 rounded-2xl p-5 max-w-md w-full shadow-2xl relative"
            style={{ background: '#14161b' }}
          >
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
export default function CalendarMindmap() {
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)

  const loadSchedule = useCallback(async () => {
    try {
      setLoading(true)
      const [resSched, resEvt] = await Promise.all([
        api.scheduleItems().catch(() => ({ items: [] })),
        api.events().catch(() => ({ events: [] })),
      ])

      const schedItems = resSched.items || []
      const evtItems = (resEvt.events || []).map((e) => ({
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

      const combined = []
      const existingKeys = new Set()
      for (const it of [...schedItems, ...evtItems]) {
        const t = (it.title || '').trim().toLowerCase()
        if (!t || ['pending task', 'untitled', 'task', 'undefined', 'null'].includes(t)) continue
        if (it.status === 'cancelled') continue
        const key = it.id || `${t}-${it.start_time || ''}`
        if (!existingKeys.has(key)) {
          existingKeys.add(key)
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
      <div
        className="flex-1 flex items-center justify-center backdrop-blur-sm"
        style={{ background: '#0c0d10' }}
      >
        <div className="flex flex-col items-center gap-3">
          <div className="w-8 h-8 border-2 border-cyan-400 border-t-transparent rounded-full animate-spin" />
          <p className="text-xs text-cyan-300 font-mono">Loading pin board...</p>
        </div>
      </div>
    )
  }

  return (
    <ReactFlowProvider>
      <MindmapFlow items={items} loadSchedule={loadSchedule} />
    </ReactFlowProvider>
  )
}
