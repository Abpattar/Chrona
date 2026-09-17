import { useState, useEffect, useCallback, useRef, useLayoutEffect, useMemo } from 'react'
import {
    ReactFlow,
    Background,
    Controls,
    MiniMap,
    useNodesState,
    useEdgesState,
    ReactFlowProvider,
} from '@xyflow/react'

// Real-time refresh interval (ms)
const TICK_INTERVAL = 60_000
import '@xyflow/react/dist/style.css'
import { supabase } from '../lib/supabase'
import {
    getPriorityScore,
    getPriorityColor,
    getNodeDistance,
    sortByPriority,
} from '../lib/priorityEngine'
import EventNode from '../components/EventNode'
import CategoryNode from '../components/CategoryNode'

const nodeTypes = { eventNode: EventNode, categoryNode: CategoryNode }

const CATEGORY_COLORS = {
    exam: { border: '#ff4d4d', bg: '#ff4d4d20' },
    hackathon: { border: '#4da3ff', bg: '#4da3ff20' },
    assignment: { border: '#ff9f43', bg: '#ff9f4320' },
    meeting: { border: '#a78bfa', bg: '#a78bfa20' },
    personal: { border: '#3ddc97', bg: '#3ddc9720' },
    reminder: { border: '#38bdf8', bg: '#38bdf820' },
    other: { border: '#94a3b8', bg: '#94a3b820' },
}

function getCategoryColor(cat) {
    return CATEGORY_COLORS[(cat || 'other').toLowerCase()] || CATEGORY_COLORS.other
}

const STORAGE_KEY = 'neuravex_mindmap_positions'

// ── Inner ReactFlow component ────────────────────────────────────────────────
function MindmapCanvas({ events, canvasHeight, onDeleteEvent }) {
    const [nodes, setNodes, onNodesChange] = useNodesState([])
    const [edges, setEdges, onEdgesChange] = useEdgesState([])
    const rfInstance = useRef(null)

    const buildLayout = useCallback((eventsData) => {
        const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
        const sorted = sortByPriority(eventsData)
        const newNodes = []
        const newEdges = []

        const centerPos = saved['center'] || { x: 0, y: 0 }
        newNodes.push({
            id: 'center',
            type: 'default',
            position: centerPos,
            data: { label: '🧠 You' },
            style: {
                background: 'radial-gradient(circle, #1e3a5f, #0a1128)',
                color: '#4da3ff',
                border: '2px solid rgba(77, 163, 255, 0.5)',
                borderRadius: '50%',
                width: 76,
                height: 76,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '14px',
                fontWeight: 700,
                boxShadow: '0 0 30px rgba(77, 163, 255, 0.35), 0 0 60px rgba(77, 163, 255, 0.1)',
            },
            draggable: true,
        })

        const byCategory = {}
        sorted.forEach((e) => {
            const cat = e.category || 'other'
            if (!byCategory[cat]) byCategory[cat] = []
            byCategory[cat].push(e)
        })

        const catKeys = Object.keys(byCategory)
        catKeys.forEach((cat, ci) => {
            const evts = byCategory[cat]
            const color = getCategoryColor(cat)
            const catId = `cat-${cat}`
            const angle = (2 * Math.PI * ci) / catKeys.length - Math.PI / 2
            const cx = Math.cos(angle) * 200
            const cy = Math.sin(angle) * 200
            const catPos = saved[catId] || { x: cx - 55, y: cy - 15 }

            newNodes.push({ id: catId, type: 'categoryNode', position: catPos, data: { label: cat, color }, draggable: true })
            newEdges.push({
                id: `e-c-${cat}`, source: 'center', target: catId,
                style: { stroke: color.border, strokeWidth: 2 },
                animated: true,
            })

            const spread = Math.PI / 3
            const startAngle = angle - spread / 2
            evts.forEach((ev, ei) => {
                const eAngle = evts.length === 1 ? angle : startAngle + (spread * ei) / (evts.length - 1)
                const score = getPriorityScore(ev)
                const dist = Math.min(getNodeDistance(score) + 60, 130)
                const ex = cx + Math.cos(eAngle) * dist
                const ey = cy + Math.sin(eAngle) * dist
                const ePos = saved[ev.id] || { x: ex - 60, y: ey - 20 }
                const eColor = getPriorityColor(score)
                newNodes.push({
                    id: ev.id,
                    type: 'eventNode',
                    position: ePos,
                    data: { event: ev, onDelete: onDeleteEvent },
                    draggable: true,
                })
                newEdges.push({
                    id: `e-${cat}-${ev.id}`, source: catId, target: ev.id,
                    animated: score > 15,
                    style: { stroke: eColor.border, strokeWidth: 1.5 },
                })
            })
        })

        setNodes(newNodes)
        setEdges(newEdges)
    }, [setNodes, setEdges, onDeleteEvent])

    useEffect(() => { buildLayout(events) }, [events, buildLayout])

    // When ReactFlow initializes, do a delayed fitView as extra assurance
    const onInit = useCallback((instance) => {
        rfInstance.current = instance
        // After init, wait for nodes to be painted then fit
        setTimeout(() => instance.fitView({ padding: 0.2, duration: 400 }), 300)
        setTimeout(() => instance.fitView({ padding: 0.2, duration: 400 }), 800)
    }, [])

    const onNodeDragStop = useCallback((_e, node) => {
        const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
        saved[node.id] = node.position
        localStorage.setItem(STORAGE_KEY, JSON.stringify(saved))
    }, [])

    return (
        <div style={{ width: '100%', height: canvasHeight || '100%' }}>
            <ReactFlow
                nodes={nodes}
                edges={edges}
                onNodesChange={onNodesChange}
                onEdgesChange={onEdgesChange}
                onNodeDragStop={onNodeDragStop}
                onInit={onInit}
                nodeTypes={nodeTypes}
                panOnScroll={false}
                panOnDrag={true}
                zoomOnPinch={true}
                zoomOnScroll={false}
                preventScrolling={true}
                fitView
                fitViewOptions={{ padding: 0.2 }}
                minZoom={0.05}
                maxZoom={3}
                proOptions={{ hideAttribution: true }}
            >
                <Background color="rgba(77, 163, 255, 0.06)" gap={28} size={1} variant="dots" />
                <Controls />
                <MiniMap
                    className="hidden md:block"
                    nodeBorderRadius={4}
                    nodeColor={() => 'rgba(77, 163, 255, 0.3)'}
                    nodeStrokeColor={() => 'rgba(77, 163, 255, 0.5)'}
                />
            </ReactFlow>
        </div>
    )
}

// ── Page outer wrapper ───────────────────────────────────────────────────────
export default function Mindmap() {
    const [events, setEvents] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [canvasHeight, setCanvasHeight] = useState(0)
    const [resetKey, setResetKey] = useState(0)
    const [tick, setTick] = useState(0)

    // Real-time tick: re-render every 60s so node colors update live
    // Also auto-deletes past events (1h grace period)
    useEffect(() => {
        const cleanup = () => {
            setTick((t) => t + 1)
            const now = dayjs()
            setEvents((prev) => {
                const expired = prev.filter((e) => now.diff(dayjs(e.event_datetime), 'hour', true) >= 1)
                const remaining = prev.filter((e) => now.diff(dayjs(e.event_datetime), 'hour', true) < 1)
                if (expired.length > 0) {
                    const ids = expired.map((e) => e.id)
                    supabase.from('events').delete().in('id', ids).then(({ error: delErr }) => {
                        if (delErr) console.error('Auto-cleanup failed:', delErr.message)
                    })
                }
                return remaining
            })
        }
        const id = setInterval(cleanup, TICK_INTERVAL)
        return () => clearInterval(id)
    }, [])

    useLayoutEffect(() => {
        function measure() {
            const navEl = document.querySelector('nav')
            const navH = navEl ? navEl.getBoundingClientRect().height : 0
            setCanvasHeight(window.innerHeight - navH)
        }
        measure()
        window.addEventListener('resize', measure)
        return () => window.removeEventListener('resize', measure)
    }, [])

    const fetchEvents = useCallback(async () => {
        const { data, error: e } = await supabase.from('events').select('*').order('event_datetime', { ascending: true })
        if (e) setError(e.message)
        else setEvents(data || [])
        setLoading(false)
    }, [])

    useEffect(() => { fetchEvents() }, [fetchEvents])

    const handleReset = () => {
        localStorage.removeItem(STORAGE_KEY)
        setResetKey((k) => k + 1)
    }

    const handleDeleteEvent = useCallback(async (eventId) => {
        // Optimistically remove from UI
        setEvents((prev) => prev.filter((e) => e.id !== eventId))
        // Remove saved position
        const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')
        delete saved[eventId]
        localStorage.setItem(STORAGE_KEY, JSON.stringify(saved))
        // Delete from database
        const { error: delError } = await supabase.from('events').delete().eq('id', eventId)
        if (delError) {
            console.error('Delete failed:', delError.message)
            fetchEvents() // Re-fetch on error
        }
    }, [fetchEvents])

    const canvasStyle = {
        width: '100%',
        height: canvasHeight > 0 ? `${canvasHeight}px` : '100vh',
        position: 'relative',
    }

    return (
        <div style={canvasStyle}>
            {/* Top bar */}
            <div style={{ position: 'absolute', top: 12, left: 0, right: 0, zIndex: 10, display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 12px', pointerEvents: 'none' }}>
                <div style={{ pointerEvents: 'auto' }} className="glass rounded-lg px-3 py-1.5 flex items-center gap-2">
                    <div className="w-2 h-2 rounded-full animate-pulse" style={{ background: '#4da3ff', boxShadow: '0 0 8px rgba(77, 163, 255, 0.5)' }} />
                    <span className="text-[10px] sm:text-xs font-mono font-semibold text-nv-text tracking-wider">{events.length} events</span>
                </div>
                <button
                    onClick={handleReset}
                    style={{ pointerEvents: 'auto' }}
                    className="glass rounded-lg font-semibold text-[10px] sm:text-xs px-3 py-1.5 transition-all hover:-translate-y-0.5 text-nv-critical"
                >
                    ↺ Reset
                </button>
            </div>

            {/* Priority legend — bottom-right; on md+ pushed up to avoid minimap overlap */}
            <div className="absolute bottom-3 right-3 z-20 pointer-events-none md:bottom-[140px]">
                <div className="glass rounded-lg px-3 py-2.5 flex flex-col gap-1.5">
                    <p className="text-[8px] font-semibold uppercase tracking-widest text-nv-text-muted">Priority</p>
                    {[
                        { label: 'Critical', color: '#ff4d4d' },
                        { label: 'High', color: '#ff9f43' },
                        { label: 'Medium', color: '#4da3ff' },
                        { label: 'Low', color: '#3ddc97' },
                    ].map((item) => (
                        <div key={item.label} className="flex items-center gap-2">
                            <div className="w-2.5 h-2.5 rounded-sm" style={{ background: item.color, boxShadow: `0 0 6px ${item.color}60` }} />
                            <span className="text-[9px] font-medium" style={{ color: item.color }}>{item.label}</span>
                        </div>
                    ))}
                </div>
            </div>

            {/* Canvas content */}
            {loading ? (
                <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <div className="text-center glass rounded-xl p-6 shadow-glass">
                        <div className="w-8 h-8 mx-auto rounded-full border-2 border-nv-accent/30 border-t-nv-accent animate-spin mb-3" />
                        <p className="text-nv-text-dim text-xs font-medium tracking-wider uppercase">Loading Space...</p>
                    </div>
                </div>
            ) : error ? (
                <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px' }}>
                    <div className="glass rounded-xl p-5 max-w-sm w-full text-center shadow-glass" style={{ borderColor: 'rgba(255, 77, 77, 0.3)' }}>
                        <h2 className="text-nv-critical font-bold text-lg mb-2">Error</h2>
                        <p className="text-nv-text-dim text-xs font-mono">{error}</p>
                    </div>
                </div>
            ) : (
                <ReactFlowProvider key={`${resetKey}-${tick}`}>
                    <MindmapCanvas events={events} canvasHeight={canvasHeight} onDeleteEvent={handleDeleteEvent} />
                </ReactFlowProvider>
            )}
        </div>
    )
}
