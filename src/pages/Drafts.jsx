import { useState, useEffect, useCallback } from 'react'
import { supabase } from '../lib/supabase'
import { enrichAndSort, generateAction, getPriorityColor, getSeverityColor } from '../lib/priorityEngine'
import dayjs from 'dayjs'

export default function Drafts() {
    const [drafts, setDrafts] = useState([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState(null)
    const [selectedDrafts, setSelectedDrafts] = useState([])

    const fetchDrafts = useCallback(async () => {
        try {
            const { data, error: fetchError } = await supabase.from('drafts').select('*').order('archived_at', { ascending: false })
            if (fetchError) throw fetchError
            setDrafts(data || [])
        } catch (err) {
            setError(err.message)
        } finally {
            setLoading(false)
        }
    }, [])

    useEffect(() => { fetchDrafts() }, [fetchDrafts])

    const handleRestoreDraft = useCallback(async (draftId) => {
        try {
            const { data: draftData, error: fetchError } = await supabase
                .from('drafts').select('*').eq('id', draftId).single()
            if (fetchError) throw fetchError

            const { error: insertError } = await supabase
                .from('events')
                .insert([{
                    title: draftData.title, category: draftData.category, venue: draftData.venue,
                    event_datetime: draftData.event_datetime, severity_level: draftData.severity_level,
                    complexity_score: draftData.complexity_score, estimated_prep_hours: draftData.estimated_prep_hours,
                    status: 'pending'
                }])
            if (insertError) throw insertError
            await supabase.from('drafts').delete().eq('id', draftId)
            fetchDrafts()
        } catch (err) {
            console.error('Restore draft error:', err)
            alert(`Failed to restore draft: ${err.message}`)
        }
    }, [fetchDrafts])

    const handleBulkRestore = useCallback(async () => {
        if (selectedDrafts.length === 0) return
        for (const draftId of selectedDrafts) { await handleRestoreDraft(draftId) }
        setSelectedDrafts([])
    }, [selectedDrafts, handleRestoreDraft])

    const handleDeleteDraft = useCallback(async (draftId) => {
        if (window.confirm('Are you sure you want to permanently delete this draft?')) {
            try {
                const { error: delError } = await supabase.from('drafts').delete().eq('id', draftId)
                if (delError) throw delError
                fetchDrafts()
            } catch (err) {
                console.error('Delete draft error:', err)
                alert(`Failed to delete draft: ${err.message}`)
            }
        }
    }, [fetchDrafts])

    const handleBulkDelete = useCallback(async () => {
        if (selectedDrafts.length === 0) return
        if (window.confirm(`Are you sure you want to permanently delete ${selectedDrafts.length} draft(s)?`)) {
            try {
                const { error: delError } = await supabase.from('drafts').delete().in('id', selectedDrafts)
                if (delError) throw delError
                setSelectedDrafts([])
                fetchDrafts()
            } catch (err) {
                console.error('Bulk delete error:', err)
                alert(`Failed to delete drafts: ${err.message}`)
            }
        }
    }, [selectedDrafts, fetchDrafts])

    const toggleSelectAll = () => {
        if (selectedDrafts.length === drafts.length) setSelectedDrafts([])
        else setSelectedDrafts(drafts.map(d => d.id))
    }

    const toggleSelectDraft = (draftId) => {
        setSelectedDrafts(prev =>
            prev.includes(draftId) ? prev.filter(id => id !== draftId) : [...prev, draftId]
        )
    }

    if (loading) {
        return (
            <div className="flex-1 flex items-center justify-center p-4">
                <div className="text-center rounded-xl p-6 shadow-warm" style={{ background: 'var(--ch-surface)', border: '1px solid rgba(180,140,110,0.12)' }}>
                    <div className="w-10 h-10 mx-auto rounded-full border-2 border-ch-peach-soft border-t-ch-peach animate-spin mb-3" />
                    <p className="text-xs font-semibold uppercase tracking-wider" style={{ color: 'var(--ch-text-dim)' }}>Loading drafts...</p>
                </div>
            </div>
        )
    }

    if (error) {
        return (
            <div className="flex-1 flex items-center justify-center p-4">
                <div className="rounded-xl p-5 max-w-sm w-full text-center shadow-warm" style={{ background: 'var(--ch-surface)', border: '1px solid rgba(217,79,79,0.2)' }}>
                    <h2 className="font-bold text-lg mb-2" style={{ color: 'var(--ch-critical)' }}>System Error</h2>
                    <p className="text-xs font-mono" style={{ color: 'var(--ch-text-dim)' }}>{error}</p>
                </div>
            </div>
        )
    }

    return (
        <div className="flex-1 overflow-y-auto overflow-x-hidden w-full px-3 sm:px-6 py-5 sm:py-8 max-w-4xl mx-auto animate-fade-in">

            {/* Header */}
            <div className="text-center mb-6 sm:mb-10">
                <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] sm:text-xs font-semibold uppercase tracking-wider mb-4"
                    style={{ background: 'rgba(232,146,124,0.08)', color: 'var(--ch-peach)', border: '1px solid rgba(232,146,124,0.15)' }}>
                    <div className="w-1.5 h-1.5 rounded-full animate-pulse" style={{ background: 'var(--ch-peach)' }} />
                    Archived Events
                </div>
                <h1 className="text-2xl sm:text-4xl font-bold tracking-tight leading-none" style={{ color: 'var(--ch-text)' }}>
                    Drafts & Expired Events
                </h1>
                <p className="text-sm max-w-md mx-auto mt-3 leading-relaxed" style={{ color: 'var(--ch-text-dim)' }}>
                    Previously scheduled events that were missed or archived. Restore them to reschedule or delete permanently.
                </p>
            </div>

            {/* Bulk actions */}
            {drafts.length > 0 && (
                <div className="flex flex-wrap gap-2 mb-6">
                    <button
                        onClick={handleBulkRestore}
                        disabled={selectedDrafts.length === 0}
                        className={`rounded-lg px-4 py-2 text-xs font-semibold transition-all ${selectedDrafts.length > 0
                            ? 'hover:-translate-y-0.5'
                            : 'opacity-40 cursor-not-allowed'}`}
                        style={selectedDrafts.length > 0 ? {
                            background: 'rgba(76,175,130,0.08)', color: 'var(--ch-success)', border: '1px solid rgba(76,175,130,0.2)',
                        } : {
                            background: 'rgba(180,140,110,0.04)', color: 'var(--ch-text-muted)', border: '1px solid rgba(180,140,110,0.1)',
                        }}
                    >
                        Restore Selected ({selectedDrafts.length})
                    </button>
                    <button
                        onClick={handleBulkDelete}
                        disabled={selectedDrafts.length === 0}
                        className={`rounded-lg px-4 py-2 text-xs font-semibold transition-all ${selectedDrafts.length > 0
                            ? 'hover:-translate-y-0.5'
                            : 'opacity-40 cursor-not-allowed'}`}
                        style={selectedDrafts.length > 0 ? {
                            background: 'rgba(217,79,79,0.06)', color: 'var(--ch-critical)', border: '1px solid rgba(217,79,79,0.15)',
                        } : {
                            background: 'rgba(180,140,110,0.04)', color: 'var(--ch-text-muted)', border: '1px solid rgba(180,140,110,0.1)',
                        }}
                    >
                        Delete Selected ({selectedDrafts.length})
                    </button>
                </div>
            )}

            {/* Drafts list */}
            {drafts.length === 0 ? (
                <div className="text-center py-14 rounded-xl shadow-warm"
                    style={{ background: 'var(--ch-surface)', border: '1px solid rgba(180,140,110,0.1)' }}>
                    <div className="w-14 h-14 mx-auto rounded-xl flex items-center justify-center mb-5" style={{ background: 'rgba(232,146,124,0.08)' }}>
                        <span className="text-3xl">📋</span>
                    </div>
                    <p className="font-semibold text-sm" style={{ color: 'var(--ch-text)' }}>No archived events.</p>
                    <p className="text-xs mt-1" style={{ color: 'var(--ch-text-muted)' }}>Expired events will appear here.</p>
                </div>
            ) : (
                <div className="space-y-3 sm:space-y-4">
                    {drafts.map((draft, i) => {
                        const priorityScore = draft.complexity_score || 5
                        const color = getPriorityColor(priorityScore)
                        const severityColor = getSeverityColor(draft.severity_level)
                        const { action, recommendation } = generateAction(draft)

                        return (
                            <div
                                key={draft.id}
                                className="relative rounded-xl p-4 sm:p-5 transition-all duration-200 hover:-translate-y-0.5 overflow-hidden hover:shadow-card-hover"
                                style={{
                                    background: 'var(--ch-surface)',
                                    border: '1px solid rgba(180,140,110,0.12)',
                                    boxShadow: '0 1px 3px rgba(180,140,110,0.06)',
                                    opacity: 0.85,
                                }}
                            >
                                <div className="absolute top-0 left-0 w-1 h-full rounded-l-xl" style={{ background: color.border, opacity: 0.6 }} />

                                <div className="absolute top-3 right-3">
                                    <input
                                        type="checkbox"
                                        checked={selectedDrafts.includes(draft.id)}
                                        onChange={() => toggleSelectDraft(draft.id)}
                                        className="w-4 h-4 rounded"
                                        style={{ accentColor: 'var(--ch-peach)' }}
                                    />
                                </div>

                                <div className="flex flex-col gap-3 pl-2">
                                    <div className="flex items-start gap-3">
                                        <div className="flex-1 min-w-0">
                                            <div className="flex flex-wrap items-center gap-1.5 mb-1">
                                                <span className="px-2 py-0.5 rounded-full text-[8px] sm:text-[10px] font-semibold uppercase tracking-wider"
                                                    style={{ background: `${color.border}08`, color: color.border, border: `1px solid ${color.border}15` }}>
                                                    {draft.category}
                                                </span>
                                                <span className="px-2 py-0.5 rounded-full text-[8px] sm:text-[10px] font-semibold uppercase tracking-wider"
                                                    style={{ background: `${severityColor.bg}`, color: severityColor.text, border: `1px solid ${severityColor.border}15` }}>
                                                    {draft.severity_level} severity
                                                </span>
                                                <div className="ml-auto flex items-center gap-1.5">
                                                    <span className="text-xs font-mono" style={{ color: 'var(--ch-text-muted)' }}>
                                                        Archived {dayjs(draft.archived_at).format('MMM D')}
                                                    </span>
                                                    <button
                                                        onClick={() => handleRestoreDraft(draft.id)}
                                                        className="w-7 h-7 flex items-center justify-center rounded-lg text-xs font-bold transition-all duration-150 hover:scale-110"
                                                        style={{ background: 'rgba(76,175,130,0.08)', color: 'var(--ch-success)', border: '1px solid rgba(76,175,130,0.15)' }}
                                                        title="Restore event"
                                                    >
                                                        ↻
                                                    </button>
                                                    <button
                                                        onClick={() => handleDeleteDraft(draft.id)}
                                                        className="w-7 h-7 flex items-center justify-center rounded-lg text-xs font-bold transition-all duration-150 hover:scale-110"
                                                        style={{ background: 'rgba(217,79,79,0.06)', color: 'var(--ch-critical)', border: '1px solid rgba(217,79,79,0.15)' }}
                                                        title="Delete event"
                                                    >
                                                        ✕
                                                    </button>
                                                </div>
                                            </div>
                                            <h3 className="text-sm sm:text-lg font-semibold tracking-tight break-words whitespace-normal" style={{ color: 'var(--ch-text)' }}>
                                                {draft.title}
                                            </h3>
                                            <p className="text-[10px] font-mono mt-0.5" style={{ color: 'var(--ch-text-muted)' }}>
                                                Original date: {draft.event_datetime ? dayjs(draft.event_datetime).format('MMM D · h:mm A') : 'Not specified'}
                                            </p>
                                        </div>
                                    </div>

                                    <div className="rounded-lg p-3 relative overflow-hidden"
                                        style={{ background: 'var(--ch-bg-subtle)', border: '1px solid rgba(180,140,110,0.08)' }}>
                                        <p className="text-xs sm:text-sm font-semibold tracking-tight" style={{ color: color.border }}>
                                            {action}
                                        </p>
                                        <p className="text-xs font-mono mt-1" style={{ color: 'var(--ch-text-dim)' }}>
                                            {recommendation}
                                        </p>
                                        {draft.notes && (
                                            <div className="mt-1.5 p-2 rounded text-[10px]"
                                                style={{ background: 'rgba(180,140,110,0.04)', color: 'var(--ch-text-dim)' }}>
                                                <span className="font-semibold">Note:</span> {draft.notes}
                                            </div>
                                        )}
                                    </div>
                                </div>
                            </div>
                        )
                    })}
                </div>
            )}
        </div>
    )
}