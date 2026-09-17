import { getPriorityColor } from '../lib/priorityEngine'

export default function PriorityCard({ item, index, onDelete }) {
    const color = getPriorityColor(item.priority)
    const isBreak = item.type === 'break'
    const alert = item.alert

    const handleDelete = (e) => {
        e.stopPropagation()
        if (onDelete && item.id) onDelete(item.id)
    }

    return (
        <div
            className="relative animate-slide-up group"
            style={{
                animationDelay: `${index * 50}ms`,
                animationFillMode: 'backwards',
            }}
        >
            <div
                className="relative glass rounded-xl p-3.5 sm:p-5 transition-all duration-200 hover:-translate-y-0.5 overflow-hidden"
                style={{
                    borderColor: isBreak ? 'rgba(255,255,255,0.05)' : `${color.border}25`,
                    boxShadow: isBreak ? 'none' : `0 0 20px ${color.border}10`,
                    opacity: isBreak ? 0.7 : 1,
                }}
            >
                {/* Left glow bar — pulses when there's an active alert */}
                <div
                    className={`absolute top-0 left-0 w-1 h-full rounded-l-xl ${alert && alert.type !== 'past' ? 'animate-pulse' : ''}`}
                    style={{
                        background: isBreak ? 'rgba(148, 163, 184, 0.3)' : color.border,
                        boxShadow: isBreak ? 'none' : `0 0 8px ${color.border}60`,
                    }}
                />

                {/* Alert banner inside card */}
                {alert && alert.type !== 'past' && !isBreak && (
                    <div
                        className="mb-3 rounded-lg p-2 sm:p-2.5 flex items-center gap-2 animate-pulse ml-2"
                        style={{
                            background: `${alert.color}12`,
                            border: `1px solid ${alert.color}30`,
                        }}
                    >
                        <span className="text-base sm:text-lg flex-shrink-0">{alert.icon}</span>
                        <div className="min-w-0">
                            <p className="text-[10px] sm:text-xs font-bold tracking-tight" style={{ color: alert.color }}>
                                {alert.title}
                            </p>
                            <p className="text-[9px] sm:text-[10px] text-nv-text-dim font-mono truncate">
                                {alert.message}
                            </p>
                        </div>
                    </div>
                )}

                {/* Top info row: time + delete + priority badge */}
                <div className="flex items-center justify-between mb-2.5 sm:mb-3 pb-2.5" style={{ borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
                    {/* Time */}
                    <div className="flex items-center gap-1.5 pl-2">
                        <span className="text-sm sm:text-base font-semibold text-nv-text tracking-tight">{item.startTime}</span>
                        <span className="text-[9px] font-medium text-nv-text-muted">→</span>
                        <span className="text-xs sm:text-sm font-medium text-nv-text-dim">{item.endTime}</span>
                    </div>

                    <div className="flex items-center gap-2">
                        {/* Delete button — red X */}
                        {!isBreak && (
                            <button
                                onClick={handleDelete}
                                className="w-7 h-7 sm:w-8 sm:h-8 flex items-center justify-center rounded-lg text-xs font-bold transition-all duration-150 hover:scale-110"
                                style={{
                                    background: 'rgba(255, 77, 77, 0.15)',
                                    color: '#ff4d4d',
                                    border: '1px solid rgba(255, 77, 77, 0.3)',
                                }}
                                title="Delete event"
                            >
                                ✕
                            </button>
                        )}

                        {/* Priority score */}
                        {!isBreak && (
                            <div
                                className="w-8 h-8 sm:w-9 sm:h-9 flex flex-col items-center justify-center font-bold flex-shrink-0 rounded-lg"
                                style={{ background: `${color.border}18`, color: color.border, border: `1px solid ${color.border}30` }}
                            >
                                <span className="text-xs sm:text-sm leading-none">{item.priority}</span>
                                <span className="text-[5px] sm:text-[6px] uppercase leading-none tracking-widest opacity-70">pts</span>
                            </div>
                        )}
                    </div>
                </div>

                {/* Main content */}
                <div className="flex items-start gap-2.5 sm:gap-3 pl-2">
                    {/* Icon */}
                    <span className="text-lg sm:text-2xl flex-shrink-0 mt-0.5">
                        {item.actionIcon || (isBreak ? '☕' : '📅')}
                    </span>

                    <div className="flex-1 min-w-0">
                        {/* Type badge + title */}
                        <span
                            className="text-[8px] sm:text-[10px] font-semibold uppercase tracking-wider px-2 py-0.5 rounded-full inline-block mb-1.5"
                            style={isBreak ? {
                                background: 'rgba(148, 163, 184, 0.1)',
                                color: '#94a3b8',
                                border: '1px solid rgba(148, 163, 184, 0.2)',
                            } : {
                                background: `${color.border}15`,
                                color: color.border,
                                border: `1px solid ${color.border}25`,
                            }}
                        >
                            {isBreak ? 'break' : item.type || 'event'}
                        </span>
                        <h3 className={`text-sm sm:text-lg font-semibold tracking-tight break-words whitespace-normal leading-tight ${isBreak ? 'text-nv-text-muted' : 'text-nv-text'}`}>
                            {item.title}
                        </h3>

                        {/* Action clause */}
                        {item.action && !isBreak && (
                            <div className="mt-2.5 rounded-lg p-2.5 sm:p-3 relative overflow-hidden"
                                style={{ background: 'rgba(10, 17, 40, 0.6)', border: '1px solid rgba(255,255,255,0.05)' }}>
                                <p className="text-[10px] sm:text-sm font-semibold tracking-tight" style={{ color: color.border }}>
                                    {item.action}
                                </p>
                                {item.recommendation && (
                                    <p className="text-[10px] sm:text-xs text-nv-text-dim mt-1 font-mono">
                                        {item.recommendation}
                                    </p>
                                )}
                            </div>
                        )}

                        {/* Break recommendation */}
                        {isBreak && item.recommendation && (
                            <p className="text-xs text-nv-text-muted font-mono mt-1.5">
                                {item.recommendation}
                            </p>
                        )}

                        {/* Tags */}
                        <div className="flex flex-wrap items-center gap-1.5 mt-2.5 sm:mt-3">
                            {item.category && (
                                <span
                                    className="px-2 py-0.5 rounded-full text-[8px] sm:text-[10px] font-semibold uppercase tracking-wider"
                                    style={{ background: `${color.border}15`, color: color.border, border: `1px solid ${color.border}25` }}
                                >
                                    {item.category}
                                </span>
                            )}
                            <span className="text-[8px] sm:text-[10px] font-medium text-nv-text-dim px-2 py-0.5 rounded-full" style={{ background: 'rgba(255,255,255,0.05)' }}>
                                ⏱ {item.duration} min
                            </span>
                            {item.venue && (
                                <span className="text-[8px] sm:text-[10px] font-medium text-nv-text-muted">
                                    📍 {item.venue}
                                </span>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    )
}
