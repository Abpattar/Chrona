import { useState, useRef, useEffect, useCallback } from 'react'
import gsap from 'gsap'
import { CustomEase } from 'gsap/CustomEase'
import dayjs from 'dayjs'

try {
  gsap.registerPlugin(CustomEase)
  CustomEase.create('stackEase', '0.83, 0, 0.17, 1')
} catch (e) {
  // Ignore if already registered
}

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

export default function CardStack({
  items = [],
  onComplete,
  onReschedule,
  onDefer,
  onOpenAI,
}) {
  const sliderRef = useRef(null)
  const [deck, setDeck] = useState(() => items)
  const [isAnimating, setIsAnimating] = useState(false)
  const isAnimatingRef = useRef(false)

  // Sync internal deck when items prop updates
  useEffect(() => {
    setDeck(items)
  }, [items])

  const positionCards = useCallback(() => {
    if (!sliderRef.current) return
    const cardElements = Array.from(sliderRef.current.querySelectorAll('.deck-card'))
    if (!cardElements.length) return

    cardElements.forEach((el, index) => {
      gsap.to(el, {
        y: `${-12 + 12 * index}%`,
        z: 14 * index,
        scale: 1 - (cardElements.length - 1 - index) * 0.035,
        opacity: index < cardElements.length - 3 ? 0.3 : 1,
        duration: 0.7,
        ease: 'stackEase',
        overwrite: 'auto',
      })
    })
  }, [])

  useEffect(() => {
    positionCards()
  }, [deck, positionCards])

  const cycleCard = () => {
    if (isAnimatingRef.current || !sliderRef.current || deck.length <= 1) return
    isAnimatingRef.current = true
    setIsAnimating(true)

    const slider = sliderRef.current
    const cards = Array.from(slider.querySelectorAll('.deck-card'))
    const lastCard = cards[cards.length - 1]

    if (!lastCard) {
      isAnimatingRef.current = false
      setIsAnimating(false)
      return
    }

    gsap.to(lastCard, {
      y: '+=140%',
      opacity: 0,
      scale: 0.9,
      duration: 0.55,
      ease: 'stackEase',
      onComplete: () => {
        setDeck((prev) => {
          if (prev.length <= 1) return prev
          const nextDeck = [...prev]
          const top = nextDeck.pop()
          nextDeck.unshift(top)
          return nextDeck
        })

        setTimeout(() => {
          isAnimatingRef.current = false
          setIsAnimating(false)
        }, 120)
      },
    })
  }

  // Screen-Fit Empty State
  if (!deck.length) {
    return (
      <div className="w-full max-w-md mx-auto px-4 py-2 flex items-center justify-center">
        <div className="relative rounded-2xl p-5 sm:p-7 bg-[#0e1320]/85 border border-slate-700/40 backdrop-blur-xl shadow-2xl text-center overflow-hidden w-full">
          <div className="absolute -top-12 -right-12 w-28 h-28 rounded-full bg-cyan-500/10 blur-xl pointer-events-none" />
          <div className="absolute -bottom-12 -left-12 w-28 h-28 rounded-full bg-amber-500/10 blur-xl pointer-events-none" />

          <div className="w-11 h-11 mx-auto mb-3 rounded-xl bg-gradient-to-b from-[#1c2438] to-[#111726] border border-cyan-400/30 flex items-center justify-center text-lg text-cyan-300 shadow-[0_0_15px_rgba(56,189,248,0.2)]">
            ◈
          </div>

          <h3 className="text-base sm:text-lg font-bold text-slate-100 tracking-tight">
            Schedule is Clear
          </h3>
          <p className="text-xs text-slate-400 mt-1.5 max-w-xs mx-auto leading-relaxed">
            No commitments scheduled yet. Talk to Chrona AI to plan classes, meetings, or study blocks.
          </p>

          <div className="mt-4 flex items-center justify-center">
            <button
              onClick={onOpenAI}
              className="px-4 py-2 rounded-xl text-xs font-semibold text-white bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 shadow-[0_0_15px_rgba(56,189,248,0.3)] transition-all flex items-center gap-1.5 active:scale-95"
            >
              <span>+ Plan with Chrona AI</span>
              <span>→</span>
            </button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="w-full max-w-xl mx-auto flex flex-col items-center">

      {/* Stack Deck Controller Sub-header */}
      <div className="w-full flex items-center justify-between px-3 mb-1 text-xs text-slate-400">
        <div className="flex items-center gap-1.5">
          <span className="w-1.5 h-1.5 rounded-full bg-cyan-400 animate-pulse shadow-[0_0_6px_rgba(56,189,248,0.8)]" />
          <span className="font-medium text-[11px] sm:text-xs text-slate-300">Active Deck</span>
          <span className="px-1.5 py-0.2 rounded-full bg-white/5 border border-white/10 font-mono text-[10px] text-slate-300">
            {deck.length} {deck.length === 1 ? 'task' : 'tasks'}
          </span>
        </div>

        {deck.length > 1 && (
          <button
            onClick={cycleCard}
            disabled={isAnimating}
            className="flex items-center gap-1 px-2.5 py-0.5 rounded-lg bg-white/5 hover:bg-white/10 border border-slate-700/50 text-[10px] font-medium text-slate-300 transition-all cursor-pointer active:scale-95"
          >
            <span>Next Card</span>
            <span className="text-slate-400">›</span>
          </button>
        )}
      </div>

      {/* Responsive 3D Stack Container from code.zip */}
      <div
        className="relative w-full h-[250px] sm:h-[285px] overflow-hidden"
        style={{
          perspective: '550px',
          perspectiveOrigin: '50% 50%',
        }}
      >
        <div
          ref={sliderRef}
          className="relative w-full h-full flex items-center justify-center"
          style={{ transformStyle: 'preserve-3d' }}
        >
          {deck.map((item, index) => {
            const isTop = index === deck.length - 1
            const stake = getStakeBadge(item)
            const sTime = item.start_time ? dayjs(item.start_time) : null
            const eTime = item.end_time ? dayjs(item.end_time) : null

            return (
              <article
                key={item.id || `card-${index}`}
                onClick={isTop && deck.length > 1 ? cycleCard : undefined}
                className={`deck-card absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[calc(100vw-36px)] max-w-[480px] h-[225px] sm:h-[250px] rounded-2xl p-4 sm:p-5 flex flex-col justify-between select-none cursor-pointer border transition-colors ${
                  isTop
                    ? 'bg-[#0f1422]/95 border-slate-600/40 shadow-[0_18px_40px_rgba(0,0,0,0.7)] backdrop-blur-xl'
                    : 'bg-[#0b0e18]/90 border-slate-800/40 shadow-[0_8px_20px_rgba(0,0,0,0.5)] pointer-events-none'
                }`}
                style={{
                  transformStyle: 'preserve-3d',
                  willChange: 'transform, opacity',
                }}
              >
                {/* Background Ambient Glow */}
                <div
                  className="absolute inset-0 rounded-2xl pointer-events-none opacity-20"
                  style={{
                    background: `radial-gradient(circle at 80% 20%, ${stake.color}33, transparent 70%)`,
                  }}
                />

                {/* Card Top: Time, Stake Badge & Type */}
                <div className="relative z-10 flex items-start justify-between gap-2">
                  <div className="flex items-center gap-1.5 flex-wrap">
                    <span
                      className="text-[9px] sm:text-[10px] font-semibold px-2 py-0.5 rounded-md uppercase tracking-wider flex items-center gap-1"
                      style={{
                        background: stake.bg,
                        color: stake.color,
                        border: `1px solid ${stake.border}`,
                      }}
                    >
                      <span className="w-1.5 h-1.5 rounded-full" style={{ background: stake.dot }} />
                      {stake.label}
                    </span>

                    {item.task_type && (
                      <span className="text-[9px] font-mono uppercase tracking-wider text-slate-400 bg-white/[0.04] px-1.5 py-0.5 rounded border border-white/5">
                        {item.task_type}
                      </span>
                    )}
                  </div>

                  {sTime && (
                    <div className="text-right flex flex-col items-end">
                      <span className="text-xs sm:text-sm font-semibold font-mono text-slate-200">
                        {sTime.format('h:mm A')}
                        {eTime && ` – ${eTime.format('h:mm A')}`}
                      </span>
                      <span className="text-[9px] sm:text-[10px] font-mono text-slate-400">
                        {sTime.format('ddd, MMM D')} · {item.duration_minutes || 60}m
                      </span>
                    </div>
                  )}
                </div>

                {/* Card Middle: Title & Transition Buffer Note */}
                <div className="relative z-10 my-auto py-1">
                  <h2 className="text-base sm:text-lg font-bold text-slate-100 tracking-tight leading-snug line-clamp-2">
                    {item.title}
                  </h2>

                  {item.transition_reason && (
                    <div className="mt-1.5 p-1.5 sm:p-2 rounded-lg bg-amber-500/[0.07] border border-amber-500/25 flex items-start gap-1.5 text-left">
                      <span className="text-amber-400 text-[10px] sm:text-xs mt-0.5 flex-shrink-0">⚡</span>
                      <p className="text-[10px] sm:text-[11px] leading-tight text-amber-200/90 font-mono line-clamp-2">
                        {item.transition_reason}
                      </p>
                    </div>
                  )}

                  {item.location && (
                    <p className="text-[10px] font-mono text-slate-400 mt-1 flex items-center gap-1">
                      <span>📍</span>
                      <span className="capitalize">{item.location.replace('_', ' ')}</span>
                    </p>
                  )}
                </div>

                {/* Card Bottom: Tactile Action Controls */}
                {isTop ? (
                  <div
                    className="relative z-10 pt-2 border-t border-slate-800/80 flex items-center justify-between gap-2"
                    onClick={(e) => e.stopPropagation()}
                  >
                    <div className="flex items-center gap-1.5 sm:gap-2">
                      <button
                        onClick={() => onComplete && onComplete(item.id)}
                        className="px-2.5 sm:px-3 py-1 rounded-lg text-[11px] font-semibold text-emerald-300 bg-emerald-500/10 hover:bg-emerald-500/20 border border-emerald-500/30 transition-all flex items-center gap-1 active:scale-95"
                      >
                        <span>✓</span>
                        <span>Complete</span>
                      </button>

                      <button
                        onClick={() => onReschedule && onReschedule(item.id)}
                        className="px-2.5 sm:px-3 py-1 rounded-lg text-[11px] font-medium text-slate-300 bg-white/5 hover:bg-white/10 border border-slate-700/60 transition-all active:scale-95"
                      >
                        ↻ Reschedule
                      </button>

                      <button
                        onClick={() => onDefer && onDefer(item.id)}
                        className="px-2 py-1 rounded-lg text-[11px] font-medium text-slate-400 hover:text-slate-200 transition-all"
                      >
                        Defer
                      </button>
                    </div>

                    {deck.length > 1 && (
                      <span className="text-[9px] font-mono text-slate-500 hidden xs:inline">
                        Tap card to cycle ›
                      </span>
                    )}
                  </div>
                ) : (
                  <div className="pt-1.5 border-t border-slate-800/40 text-[9px] font-mono text-slate-500 text-center">
                    Upcoming in stack
                  </div>
                )}
              </article>
            )
          })}
        </div>
      </div>

      {/* Minimal Stack Dots */}
      {deck.length > 1 && (
        <div className="flex items-center gap-1 mt-1">
          {deck.map((_, i) => (
            <span
              key={`dot-${i}`}
              className={`h-1 rounded-full transition-all duration-300 ${
                i === deck.length - 1
                  ? 'w-5 bg-cyan-400 shadow-[0_0_6px_rgba(56,189,248,0.6)]'
                  : 'w-1 bg-slate-700'
              }`}
            />
          ))}
        </div>
      )}

    </div>
  )
}
