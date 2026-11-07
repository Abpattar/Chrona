import { useState, useEffect } from 'react'
import { api } from '../lib/api'

export default function RepairPanel({ task, onClose, onRepair, onRepaired }) {
    const [proposals, setProposals] = useState([])
    const [loading, setLoading] = useState(true)
    const [applying, setApplying] = useState(null)
    const [error, setError] = useState(null)

    useEffect(() => {
        if (!task) return
        setLoading(true)
        setError(null)

        api.repairPropose(task.id, 'user_requested')
            .then(data => {
                const mapped = (data.proposals || []).map((p, i) => ({
                    id: p.strategy || `proposal-${i}`,
                    title: p.label || p.title || 'Repair option',
                    risk: p.risk || 'medium',
                    description: p.note || p.warning || (p.urgency_after ? `Urgency after: ${Math.round(p.urgency_after * 100)}%` : ''),
                    new_start: p.new_start,
                    new_end: p.new_end,
                    task_id: task.id,
                    ...p,
                }))
                setProposals(mapped)
                setLoading(false)
            })
            .catch(e => {
                setError(e.message)
                // Provide fallback proposals
                setProposals([
                    { id: 'move_today', title: 'Next available slot', risk: 'low',
                      description: 'Move to the nearest open time slot.', task_id: task.id },
                    { id: 'defer', title: 'Defer to tomorrow', risk: 'medium',
                      description: 'Push to tomorrow morning.', task_id: task.id },
                ])
                setLoading(false)
            })
    }, [task])

    const handleApply = async (proposal) => {
        setApplying(proposal.id)
        try {
            await api.repairApply({ ...proposal, task_id: task.id })
            onRepaired?.()
            onClose()
        } catch (e) {
            setError(e.message)
        } finally {
            setApplying(null)
        }
    }

    const riskStyles = {
        low:      { bg: 'rgba(76,175,130,0.06)', border: '1px solid rgba(76,175,130,0.15)', color: 'var(--ch-success)',  label: '● Low Risk' },
        medium:   { bg: 'rgba(232,168,76,0.06)', border: '1px solid rgba(232,168,76,0.15)', color: 'var(--ch-warning)',  label: '● Medium Risk' },
        high:     { bg: 'rgba(217,79,79,0.06)',  border: '1px solid rgba(217,79,79,0.15)',  color: 'var(--ch-critical)', label: '● High Risk' },
        critical: { bg: 'rgba(217,79,79,0.06)',  border: '1px solid rgba(217,79,79,0.15)',  color: 'var(--ch-critical)', label: '● Critical' },
    }

    if (!task) return null

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in"
            style={{ background: 'rgba(0, 0, 0, 0.5)', backdropFilter: 'blur(8px)' }}
            onClick={onClose}
        >
            <div
                className="w-full max-w-md rounded-2xl p-5 sm:p-7 animate-scale-in relative"
                style={{
                    background: 'var(--ch-surface)',
                    border: '1px solid rgba(180,140,110,0.15)',
                    boxShadow: '0 12px 48px rgba(180,140,110,0.15), 0 4px 16px rgba(0,0,0,0.08)',
                }}
                onClick={(e) => e.stopPropagation()}
            >
                {/* Top accent line */}
                <div className="absolute top-0 left-6 right-6 h-px" style={{ background: 'linear-gradient(90deg, transparent, rgba(232,146,124,0.4), transparent)' }} />

                {/* Close button */}
                <button
                    onClick={onClose}
                    className="absolute top-4 right-4 w-8 h-8 flex items-center justify-center rounded-lg transition-all hover:scale-110"
                    style={{ background: 'rgba(180,140,110,0.06)', color: 'var(--ch-text-dim)' }}
                >
                    ✕
                </button>

                {/* Header */}
                <div className="mb-6">
                    <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-semibold uppercase tracking-wider mb-3"
                        style={{ background: 'rgba(232,168,76,0.08)', color: 'var(--ch-warning)', border: '1px solid rgba(232,168,76,0.15)' }}>
                        🔧 Conflict Repair
                    </div>
                    <h2 className="text-xl sm:text-2xl font-bold tracking-tight" style={{ color: 'var(--ch-text)' }}>
                        {task.title}
                    </h2>
                    <p className="text-xs font-mono mt-1" style={{ color: 'var(--ch-text-muted)' }}>
                        Choose a repair strategy below
                    </p>
                </div>

                {/* Proposals */}
                {loading ? (
                    <div className="text-center py-8">
                        <div className="w-10 h-10 mx-auto rounded-full border-2 border-ch-peach-soft border-t-ch-peach animate-spin mb-3" />
                        <p className="text-xs font-semibold" style={{ color: 'var(--ch-text-dim)' }}>Analyzing conflicts...</p>
                    </div>
                ) : (
                    <div className="space-y-2.5">
                        {proposals.map((proposal) => {
                            const risk = riskStyles[proposal.risk] || riskStyles.medium
                            return (
                                <button
                                    key={proposal.id}
                                    onClick={() => handleApply(proposal)}
                                    disabled={applying !== null}
                                    className="w-full text-left rounded-xl p-3.5 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-card-hover group disabled:opacity-50"
                                    style={{
                                        background: risk.bg,
                                        border: risk.border,
                                    }}
                                >
                                    <div className="flex items-start justify-between gap-2">
                                        <div className="min-w-0">
                                            <p className="text-sm font-semibold" style={{ color: 'var(--ch-text)' }}>
                                                {proposal.title}
                                            </p>
                                            <p className="text-xs mt-0.5" style={{ color: 'var(--ch-text-dim)' }}>
                                                {proposal.description}
                                            </p>
                                        </div>
                                        <div className="flex-shrink-0">
                                            {applying === proposal.id ? (
                                                <div className="w-5 h-5 rounded-full border-2 border-current animate-spin" style={{ borderTopColor: 'transparent', color: risk.color }} />
                                            ) : (
                                                <span className="text-[10px] font-bold tracking-wider whitespace-nowrap" style={{ color: risk.color }}>
                                                    {risk.label}
                                                </span>
                                            )}
                                        </div>
                                    </div>
                                </button>
                            )
                        })}
                    </div>
                )}

                {/* Cancel */}
                <button
                    onClick={onClose}
                    className="w-full mt-4 py-2.5 rounded-xl text-xs font-semibold transition-all hover:-translate-y-0.5"
                    style={{ background: 'rgba(180,140,110,0.04)', color: 'var(--ch-text-dim)', border: '1px solid rgba(180,140,110,0.1)' }}
                >
                    Cancel
                </button>
            </div>
        </div>
    )
}
