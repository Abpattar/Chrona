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
      border: 'rgba(52, 211, 153, 0.35)',
      cardBg: 'linear-gradient(160deg, rgba(16, 42, 34, 0.9), rgba(8, 20, 18, 0.95))',
      pinColor: '#34d399',
      glow: '0 0 24px rgba(16, 185, 129, 0.2)',
      badgeBg: 'rgba(16, 185, 129, 0.2)',
      badgeText: '#34d399',
      label: '✓ COMPLETED',
      textStyle: 'line-through text-slate-400',
    }
  }

  if (isPending) {
    return {
      border: 'rgba(192, 132, 252, 0.38)',
      cardBg: 'linear-gradient(160deg, rgba(38, 24, 58, 0.9), rgba(18, 12, 30, 0.95))',
      pinColor: '#c084fc',
      glow: '0 0 24px rgba(168, 85, 247, 0.22)',
      badgeBg: 'rgba(168, 85, 247, 0.22)',
      badgeText: '#d8b4fe',
      label: '⏳ UNASSIGNED',
      textStyle: 'text-purple-100 font-medium',
    }
  }

  if (movability === 'immovable') {
    return {
      border: 'rgba(255, 255, 255, 0.2)',
      cardBg: 'linear-gradient(160deg, rgba(30, 34, 46, 0.92), rgba(15, 18, 28, 0.96))',
      pinColor: '#f8fafc',
      glow: '0 0 24px rgba(248, 250, 252, 0.12)',
      badgeBg: 'rgba(255, 255, 255, 0.14)',
      badgeText: '#f8fafc',
      label: '◈ ANCHOR',
      textStyle: 'text-slate-50 font-semibold',
    }
  }

  if (movability === 'negotiable') {
    return {
      border: 'rgba(245, 158, 11, 0.38)',
      cardBg: 'linear-gradient(160deg, rgba(46, 34, 18, 0.92), rgba(22, 16, 10, 0.96))',
      pinColor: '#fbbf24',
      glow: '0 0 24px rgba(245, 158, 11, 0.2)',
      badgeBg: 'rgba(245, 158, 11, 0.2)',
      badgeText: '#fcd34d',
      label: isRescheduled ? '↻ ALIGNED' : '✧ PRIORITY',
      textStyle: 'text-amber-50 font-semibold',
    }
  }

  return {
    border: 'rgba(56, 189, 248, 0.35)',
    cardBg: 'linear-gradient(160deg, rgba(16, 38, 54, 0.92), rgba(10, 20, 32, 0.96))',
    pinColor: '#38bdf8',
    glow: '0 0 24px rgba(56, 189, 248, 0.18)',
    badgeBg: 'rgba(56, 189, 248, 0.2)',
    badgeText: '#7dd3fc',
    label: isRescheduled ? '✦ RESCHEDULED' : '✦ FLUID',
    textStyle: 'text-cyan-50 font-medium',
  }
}

// ── Realistic 3D Push-Pin Cap ─────────────────────────────────────────────────
function PushPin({ color = '#38bdf8' }) {
  return (
    <div
      className="absolute -top-3 left-1/2 -translate-x-1/2 z-30 flex flex-col items-center pointer-events-none"
      aria-hidden="true"
    >
      <div
        className="w-4 h-4 rounded-full border border-white/50"
        style={{
          background: `radial-gradient(circle at 32% 28%, #ffffff 0%, ${color} 45%, #0f172a 100%)`,
          boxShadow: `0 0 12px ${color}99, 0 5px 8px rgba(0,0,0,0.9)`,
        }}
      />
      <div className="w-[2px] h-2 bg-gradient-to-b from-slate-200 to-slate-600 -mt-0.5 rounded-b-full shadow" />
    </div>
  )
}

// ── Background Pin-Board Column Zone ──────────────────────────────────────────
function BoardZoneNode({ data }) {
  const { width, height, accentColor } = data
  return (
    <div
      className="rounded-3xl pointer-events-none backdrop-blur-sm"
      style={{
        width: `${width}px`,
        height: `${height}px`,
        background: 'linear-gradient(180deg, rgba(255, 255, 255, 0.02) 0%, rgba(20, 16, 35, 0.25) 100%)',
        border: `1px dashed ${accentColor}28`,
        boxShadow: 'inset 0 1px 0 rgba(255,255,255,0.06), 0 20px 48px rgba(0,0,0,0.4)',
      }}
    />
  )
}

// ── Pinned Task Note Card ─────────────────────────────────────────────────────
function ScheduleTaskNode({ data }) {
  const { item, tiltDeg = 0, onComplete, onReschedule, onDelete, onExplain } = data
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
      className="relative px-4 pt-4 pb-3.5 w-[252px] rounded-2xl transition-all duration-200 hover:!rotate-0 hover:scale-[1.03] hover:z-30 group cursor-grab active:cursor-grabbing backdrop-blur-xl"
      style={{
        background: theme.cardBg,
        border: `1px solid ${theme.border}`,
        boxShadow: `${theme.glow}, 0 16px 36px rgba(0,0,0,0.7)`,
        transform: `rotate(${tiltDeg}deg)`,
      }}
    >
      <PushPin color={theme.pinColor} />
      <Handle type="target" position={Position.Top} className="!opacity-0 !w-2 !h-2" />

      {/* Subtle corner paper fold highlight */}
      <div
        className="absolute top-0 right-0 w-4 h-4 pointer-events-none rounded-tr-xl opacity-30"
        style={{
          background: 'linear-gradient(225deg, rgba(255,255,255,0.22) 0%, transparent 55%)',
        }}
      />

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
          className="opacity-40 group-hover:opacity-100 text-[10px] font-bold text-rose-400 hover:text-rose-200 px-1 transition-opacity"
          title="Unpin & remove item"
        >
          ✕
        </button>
      </div>

      {/* Title */}
      <h4 className={`text-[13px] leading-snug line-clamp-2 ${theme.textStyle}`}>
        {item.title}
      </h4>

      {/* Date and Time */}
      <div
        className="mt-2 pt-2 flex flex-col gap-1 text-[10px]"
        style={{ borderTop: '1px solid rgba(255,255,255,0.08)' }}
      >
        {hasStartTime ? (
          <div className="flex items-center justify-between font-mono text-[10px] text-slate-200">
            <span className="font-semibold">
              {startDay.format('h:mm A')} – {endDay ? endDay.format('h:mm A') : 'TBD'}
            </span>
            <span className="text-[9px] text-cyan-300 px-1.5 py-0.5 rounded bg-cyan-500/15 border border-cyan-500/25">
              {durationMin}m
            </span>
          </div>
        ) : (
          <div className="flex items-center justify-between text-amber-300/90 font-mono">
            <span>⏳ Flexible slot</span>
            <span className="text-[9px] px-1.5 py-0.5 rounded bg-amber-500/15">{durationMin}m</span>
          </div>
        )}

        {(item.completion_note || item.reschedule_reason) && (
          <div
            className="text-[9px] text-amber-200/85 font-mono truncate"
            title={item.completion_note || item.reschedule_reason}
          >
            📌 {item.completion_note || item.reschedule_reason}
          </div>
        )}
      </div>

      {/* Action buttons */}
      <div
        className="mt-2.5 pt-2 flex items-center gap-1.5"
        style={{ borderTop: '1px solid rgba(255,255,255,0.08)' }}
      >
        {item.status !== 'completed' && (
          <button
            onClick={(e) => {
              e.stopPropagation()
              if (onComplete) onComplete(item.id)
            }}
            className="flex-1 py-1 rounded-lg text-[10px] font-bold transition-all text-emerald-300 hover:text-emerald-100 hover:bg-emerald-500/25"
            style={{
              background: 'rgba(16, 185, 129, 0.14)',
              border: '1px solid rgba(16, 185, 129, 0.3)',
            }}
          >
            ✓ Done
          </button>
        )}
        <button
          onClick={(e) => {
            e.stopPropagation()
            if (onReschedule) onReschedule(item.id)
          }}
          className="py-1 px-2.5 rounded-lg text-[10px] font-bold text-amber-300 hover:text-amber-100 hover:bg-amber-500/20 transition-all"
          style={{
            background: 'rgba(245, 158, 11, 0.12)',
            border: '1px solid rgba(245, 158, 11, 0.28)',
          }}
          title="Move to next open slot"
        >
          ↻ Move
        </button>
        {onExplain && (
          <button
            onClick={(e) => {
              e.stopPropagation()
              onExplain(item)
            }}
            className="py-1 px-2 rounded-lg text-[10px] font-bold text-cyan-300 hover:text-cyan-100 hover:bg-cyan-500/25 transition-all"
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

// ── Pinned Day Column Tape Header Card ────────────────────────────────────────
function DateBranchNode({ data }) {
  const { dateLabel, dayName, count, totalDuration, isToday, isPending } = data
  const accentColor = isToday ? '#38bdf8' : isPending ? '#c084fc' : '#fbbf24'

  return (
    <div
      className="relative px-4 pt-3.5 pb-2.5 rounded-2xl w-[252px] text-center select-none cursor-grab active:cursor-grabbing overflow-visible backdrop-blur-xl"
      style={{
        background: 'linear-gradient(150deg, rgba(24, 20, 38, 0.94), rgba(14, 12, 24, 0.98))',
        border: `1px solid ${accentColor}45`,
        boxShadow: `0 0 24px ${accentColor}18, 0 12px 28px rgba(0,0,0,0.75)`,
      }}
    >
      {/* Translucent top tape bar */}
      <div
        className="absolute top-0 left-6 right-6 h-1 rounded-b-full opacity-75"
        style={{ background: accentColor }}
      />
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
            background: `${accentColor}1a`,
            color: accentColor,
            border: `1px solid ${accentColor}38`,
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
  boardZoneNode: BoardZoneNode,
  dateBranchNode: DateBranchNode,
  scheduleTaskNode: ScheduleTaskNode,
}

// ── Pin-Board 2D Grid Layout Generator ────────────────────────────────────────
function buildPinBoardLayout(
  items,
  { onComplete, onReschedule, onDelete, onExplain }
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
  const cardWidth = 244
  const zonePadX = 16
  const colSpacingX = cardWidth + zonePadX * 2 + 28 // 304px
  const headerHeight = 96
  const cardSlotY = 188
  const rowGapY = 56

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
      const accentColor = isToday ? '#38bdf8' : isPending ? '#c084fc' : '#fbbf24'

      const sortedBranchItems = branchItems.slice().sort((a, b) => {
        const aTime = a.start_time || a.event_datetime
        const bTime = b.start_time || b.event_datetime
        if (!aTime) return 1
        if (!bTime) return -1
        return dayjs(aTime).diff(dayjs(bTime))
      })

      let totalDur = 0
      sortedBranchItems.forEach((i) => {
        totalDur += Number(i.duration_minutes || 60)
      })

      const colHeight = headerHeight + sortedBranchItems.length * cardSlotY + 18

      // Background zone frame around this day's pinned column
      nodes.push({
        id: `zone-${dateKey}`,
        type: 'boardZoneNode',
        position: { x: colX - zonePadX, y: currentRowY - 18 },
        data: {
          width: cardWidth + zonePadX * 2,
          height: colHeight,
          accentColor,
        },
        draggable: false,
        selectable: false,
        zIndex: -10,
      })

      nodes.push({
        id: dateNodeId,
        type: 'dateBranchNode',
        position: { x: colX, y: currentRowY },
        data: {
          dateLabel: isPending ? 'Flexible' : branchDate.format('MMM D, YYYY'),
          dayName: isPending ? 'Pending' : isToday ? 'Today' : branchDate.format('dddd'),
          count: sortedBranchItems.length,
          totalDuration: totalDur,
          isToday,
          isPending,
        },
        draggable: true,
        zIndex: 5,
      })

      const threadColor = isToday
        ? 'rgba(56, 189, 248, 0.55)'
        : isPending
        ? 'rgba(192, 132, 252, 0.55)'
        : 'rgba(251, 191, 36, 0.45)'

      sortedBranchItems.forEach((task, taskIdx) => {
        const taskId = `task-${task.id || `${dateKey}-${taskIdx}`}`
        const taskY = currentRowY + headerHeight + taskIdx * cardSlotY
        const tilts = [-1.0, 0.85, -0.55, 0.95]
        const tiltDeg = tilts[taskIdx % tilts.length]

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
            onExplain,
          },
          draggable: true,
          zIndex: 10,
        })

        const sourceId =
          taskIdx === 0
            ? dateNodeId
            : `task-${sortedBranchItems[taskIdx - 1].id || `${dateKey}-${taskIdx - 1}`}`

        edges.push({
          id: `pin-thread-${sourceId}-${taskId}`,
          source: sourceId,
          target: taskId,
          type: 'default',
          style: {
            stroke: threadColor,
            strokeWidth: 1.6,
            strokeDasharray: '4 3',
          },
        })
      })

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
  const [toast, setToast] = useState(null)

  const showToast = useCallback((msg, type = 'info') => {
    setToast({ msg, type })
    setTimeout(() => setToast(null), 3500)
  }, [])

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
        showToast('Marked as completed ✓')
        loadSchedule()
      } catch (e) {
        showToast(`Error completing task: ${e.message}`, 'error')
      }
    },
    [loadSchedule, showToast]
  )

  const handleReschedule = useCallback(
    async (taskId) => {
      try {
        const res = await api.autoReschedule(taskId)
        showToast(res.message || 'Pin moved to next open slot ⚡')
        loadSchedule()
      } catch (e) {
        showToast(`Reschedule error: ${e.message}`, 'error')
      }
    },
    [loadSchedule, showToast]
  )

  const handleDelete = useCallback(
    async (taskId) => {
      if (!window.confirm('Remove this pin from your board?')) return
      try {
        await api.updateScheduleItem(taskId, { status: 'cancelled' })
        showToast('Pin removed')
        loadSchedule()
      } catch (e) {
        showToast(`Error: ${e.message}`, 'error')
      }
    },
    [loadSchedule, showToast]
  )

  const handleClearAllPins = useCallback(async () => {
    if (!items.length) return
    if (!window.confirm(`Remove all ${items.length} pins from your Mind Map?`)) return
    try {
      await api.scheduleClear()
      showToast('All pins cleared from board')
      loadSchedule()
    } catch (e) {
      showToast(`Clear error: ${e.message}`, 'error')
    }
  }, [items.length, loadSchedule, showToast])

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
      onExplain: handleExplain,
    })
    setNodes(newNodes)
    setEdges(newEdges)

    setTimeout(() => {
      rfInstance.current?.fitView({ padding: 0.16, maxZoom: 1.0, duration: 350 })
    }, 150)
  }, [
    filteredItems,
    handleComplete,
    handleReschedule,
    handleDelete,
    handleExplain,
    setNodes,
    setEdges,
  ])

  const onInit = useCallback((instance) => {
    rfInstance.current = instance
    setTimeout(() => instance.fitView({ padding: 0.16, maxZoom: 1.0, duration: 350 }), 200)
  }, [])

  return (
    <div className="flex-1 w-full h-full min-h-0 relative overflow-hidden bg-transparent">
      {/* ── Purple Gradient Atmosphere at Bottom ── */}
      <div 
        className="absolute -bottom-20 inset-x-0 h-96 bg-gradient-to-t from-purple-700/40 via-purple-950/25 to-transparent blur-3xl pointer-events-none z-10" 
      />
      <div 
        className="absolute -bottom-28 left-1/4 right-1/4 h-72 bg-radial from-fuchsia-600/30 via-purple-600/15 to-transparent blur-3xl pointer-events-none z-10" 
      />

      {/* Inline Toast Notification */}
      {toast && (
        <div
          className={`fixed top-16 left-1/2 -translate-x-1/2 z-50 px-4 py-2 rounded-full border text-xs font-mono shadow-2xl backdrop-blur-xl animate-fade-in ${
            toast.type === 'error'
              ? 'bg-rose-950/95 border-rose-500/40 text-rose-200'
              : 'bg-[#12141c]/95 border-purple-500/40 text-purple-200 shadow-[0_0_20px_rgba(168,85,247,0.3)]'
          }`}
        >
          {toast.msg}
        </div>
      )}

      {/* Floating Pin Board Summary Badge (Top Left) */}
      <div className="absolute top-4 left-4 sm:left-8 z-20 flex items-center gap-2 pointer-events-auto">
        <div className="flex items-center gap-2.5 bg-[#12141c]/80 px-3.5 py-1.5 rounded-full border border-white/10 backdrop-blur-xl shadow-lg">
          <span className="text-xs font-bold tracking-wide text-white flex items-center gap-1.5">
            <span>🗺️</span>
            <span>Mind Map</span>
          </span>
          <div className="h-3 w-px bg-white/15" />
          <div className="flex items-center gap-2 text-[10px] font-mono">
            <span className="text-cyan-300 font-medium">{stats.total} nodes</span>
            <span className="text-slate-500">·</span>
            <span className="text-emerald-400 font-medium">✓ {stats.completed}</span>
            {stats.pending > 0 && (
              <>
                <span className="text-slate-500">·</span>
                <span className="text-purple-300 font-medium">⏳ {stats.pending}</span>
              </>
            )}
          </div>
        </div>
      </div>

      {/* Floating Canvas Controls (Top Right) */}
      <div className="absolute top-4 right-4 sm:right-8 z-20 flex items-center gap-2 pointer-events-auto">
        <div className="flex items-center bg-[#12141c]/80 p-1.5 rounded-full border border-white/10 backdrop-blur-xl shadow-lg gap-1.5">
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Filter pins..."
            className="hidden sm:block w-28 bg-white/[0.06] text-xs text-white placeholder-slate-400 px-3 py-1 rounded-full border border-white/10 focus:outline-none focus:border-purple-400/50"
          />

          <select
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            className="bg-transparent text-xs text-slate-200 font-medium px-2 py-1 focus:outline-none cursor-pointer rounded-full"
          >
            <option value="all" className="bg-[#12141c] text-white">All Pins ({items.length})</option>
            <option value="today" className="bg-[#12141c] text-white">Today Only</option>
            <option value="pending" className="bg-[#12141c] text-white">Pending</option>
            <option value="critical" className="bg-[#12141c] text-white">Urgent</option>
            <option value="completed" className="bg-[#12141c] text-white">Completed</option>
          </select>

          <div className="h-3 w-px bg-white/15" />

          <button
            onClick={() => rfInstance.current?.zoomIn({ duration: 250 })}
            className="w-7 h-7 rounded-full text-xs text-slate-300 hover:text-white hover:bg-white/10 flex items-center justify-center transition-all"
            title="Zoom In"
          >
            +
          </button>
          <button
            onClick={() => rfInstance.current?.zoomOut({ duration: 250 })}
            className="w-7 h-7 rounded-full text-xs text-slate-300 hover:text-white hover:bg-white/10 flex items-center justify-center transition-all"
            title="Zoom Out"
          >
            −
          </button>
          <button
            onClick={() => rfInstance.current?.fitView({ padding: 0.16, maxZoom: 1.0, duration: 350 })}
            className="px-3 py-1 rounded-full text-xs text-slate-200 hover:text-white bg-white/5 hover:bg-white/15 border border-white/10 transition-all flex items-center gap-1 font-medium"
            title="Reset zoom & fit board"
          >
            <span>⛶</span>
            <span className="hidden sm:inline">Fit</span>
          </button>

          {items.length > 0 && (
            <>
              <div className="h-3 w-px bg-white/15" />
              <button
                onClick={handleClearAllPins}
                className="px-3 py-1 rounded-full text-xs font-medium text-rose-300 hover:text-rose-100 bg-rose-500/15 hover:bg-rose-500/25 border border-rose-500/30 transition-all flex items-center gap-1"
                title="Remove all pins from the Mind Map"
              >
                <span>🗑️</span>
                <span className="hidden sm:inline">Clear</span>
              </button>
            </>
          )}
        </div>
      </div>

      {/* Floating Color Pin Legend (Bottom Left) */}
      {items.length > 0 && (
        <div className="hidden sm:flex absolute bottom-5 left-8 z-20 items-center gap-3.5 bg-[#12141c]/80 px-4 py-2 rounded-full border border-white/10 backdrop-blur-xl text-[10px] font-mono text-slate-300 pointer-events-auto shadow-xl">
          <span className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-slate-100 shadow-[0_0_8px_#f8fafc]" />
            Anchor
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-amber-400 shadow-[0_0_8px_#fbbf24]" />
            Priority
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-cyan-400 shadow-[0_0_8px_#38bdf8]" />
            Fluid
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-emerald-400 shadow-[0_0_8px_#34d399]" />
            Done
          </span>
        </div>
      )}

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
          <Background color="rgba(192, 132, 252, 0.16)" gap={24} size={1.4} variant="dots" />
        </ReactFlow>
      </div>

      {/* Explanation Modal */}
      {explainData && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-md animate-fade-in">
          <div
            className="border border-white/15 rounded-3xl p-6 max-w-md w-full shadow-2xl relative bg-[#141624]/90 backdrop-blur-2xl"
          >
            <button
              onClick={() => setExplainData(null)}
              className="absolute top-4 right-4 text-slate-400 hover:text-white text-base transition-colors"
            >
              ✕
            </button>
            <div className="flex items-center gap-2 mb-2 text-purple-400">
              <span className="text-lg">💡</span>
              <h3 className="font-bold text-xs tracking-wider uppercase font-mono">Chrona Decision Reasoning</h3>
            </div>
            <h4 className="text-base font-semibold text-white mb-2">{explainData.item?.title}</h4>
            <p className="text-xs text-slate-300 leading-relaxed mb-4 whitespace-pre-wrap">
              {explainData.explanation || explainData.item?.completion_note}
            </p>
            {explainData.factors && explainData.factors.length > 0 && (
              <div className="space-y-1.5 mb-5 bg-white/[0.03] p-3 rounded-2xl border border-white/5">
                <p className="text-[10px] uppercase font-bold text-purple-300 font-mono">Decision Factors:</p>
                {explainData.factors.map((f, i) => (
                  <div key={i} className="text-xs text-purple-100/90 flex items-center gap-2">
                    <span className="text-purple-400 text-[10px]">✦</span>
                    <span>{f}</span>
                  </div>
                ))}
              </div>
            )}
            <div className="flex justify-end gap-2 pt-2 border-t border-white/10">
              <button
                onClick={() => setExplainData(null)}
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
