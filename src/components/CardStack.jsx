import { useState, useRef, useEffect, useCallback, useMemo } from 'react'
import dayjs from 'dayjs'

function getStakeBadge(item) {
  const mov = item.movability || (item.flexibility === false || item.controllability === 'external' ? 'immovable' : 'fluid')
  if (mov === 'immovable') {
    return {
      label: 'Immovable',
      color: '#f8fafc',
      bg: 'rgba(248, 250, 252, 0.12)',
      border: 'rgba(248, 250, 252, 0.28)',
      dot: '#e2e8f0',
    }
  }
  if (mov === 'negotiable') {
    return {
      label: 'Negotiable',
      color: '#fbbf24',
      bg: 'rgba(245, 158, 11, 0.14)',
      border: 'rgba(245, 158, 11, 0.32)',
      dot: '#f59e0b',
    }
  }
  return {
    label: 'Fluid',
    color: '#38bdf8',
    bg: 'rgba(56, 189, 248, 0.14)',
    border: 'rgba(56, 189, 248, 0.3)',
    dot: '#0284c7',
  }
}

// Dynamic sleek task avatar matching the dark warm sunset aesthetic
function CardAvatar({ item, emblem }) {
  const type = (item.task_type || item.category || '').toLowerCase()
  let icon = emblem.icon

  if (type.includes('sprint') || type.includes('focus')) icon = '⚡'
  else if (type.includes('study') || type.includes('read') || type.includes('learn')) icon = '📖'
  else if (type.includes('engineer') || type.includes('dev') || type.includes('code') || type.includes('tech')) icon = '💻'
  else if (type.includes('meet') || type.includes('call') || type.includes('sync')) icon = '👥'
  else if (type.includes('design') || type.includes('art') || type.includes('creative')) icon = '🎨'
  else if (type.includes('health') || type.includes('workout') || type.includes('gym')) icon = '💪'

  return (
    <div
      className={`w-18 h-18 sm:w-20 sm:h-20 rounded-full bg-gradient-to-br ${emblem.bg} ${emblem.border} border-2 flex flex-col items-center justify-center relative shadow-xl overflow-hidden`}
      style={{ boxShadow: `0 0 24px ${emblem.glow}` }}
    >
      <span className="text-2xl sm:text-3xl filter drop-shadow">
        {icon}
      </span>
      <span className="text-[9px] font-mono font-bold tracking-widest text-white/90 uppercase mt-0.5">
        {emblem.initials}
      </span>
    </div>
  )
}

function getCardEmblem(item, index) {
  const title = (item.title || 'Task').toUpperCase()
  const words = title.split(' ').filter(Boolean)
  const initials = words.length > 1 ? (words[0][0] + words[1][0]) : title.slice(0, 2)

  const palettes = [
    { bg: 'from-amber-500/25 to-orange-600/35', border: 'border-amber-400/40', text: 'text-amber-200', glow: 'rgba(245, 158, 11, 0.25)', icon: '⚡' },
    { bg: 'from-cyan-500/25 to-blue-600/35', border: 'border-cyan-400/40', text: 'text-cyan-200', glow: 'rgba(56, 189, 248, 0.25)', icon: '◈' },
    { bg: 'from-emerald-500/25 to-teal-600/35', border: 'border-emerald-400/40', text: 'text-emerald-200', glow: 'rgba(52, 211, 153, 0.25)', icon: '✦' },
    { bg: 'from-purple-500/25 to-indigo-600/35', border: 'border-purple-400/40', text: 'text-purple-200', glow: 'rgba(192, 132, 252, 0.25)', icon: '✧' },
    { bg: 'from-rose-500/25 to-pink-600/35', border: 'border-rose-400/40', text: 'text-rose-200', glow: 'rgba(244, 63, 94, 0.25)', icon: '✴' },
  ]

  const p = palettes[index % palettes.length]
  return { initials, ...p }
}

export default function CardStack({
  items = [],
  onComplete,
  onReschedule,
  onDefer,
  onOpenAI,
  isIntro = false,
  onEndIntro,
}) {
  const [activeIndex, setActiveIndex] = useState(0)
  const [dragOffset, setDragOffset] = useState(0)
  const [isDragging, setIsDragging] = useState(false)
  const [windowWidth, setWindowWidth] = useState(typeof window !== 'undefined' ? window.innerWidth : 1200)
  const startXRef = useRef(0)
  const currentDragRef = useRef(0)
  const containerRef = useRef(null)

  // Track viewport width for fluid responsive turntable bounds
  useEffect(() => {
    const handleResize = () => setWindowWidth(window.innerWidth)
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  // Calculate fluid spacing so cards at all positions fit comfortably inside the viewport width
  const spacing = useMemo(() => {
    if (windowWidth < 640) return Math.min(200, Math.max(150, Math.floor((windowWidth - 32) * 0.44)))
    if (windowWidth < 1024) return Math.min(240, Math.max(180, Math.floor((windowWidth - 64) * 0.24)))
    return Math.min(270, Math.max(210, Math.floor((windowWidth - 80) * 0.21)))
  }, [windowWidth])

  // Ensure activeIndex is valid
  useEffect(() => {
    if (activeIndex >= items.length && items.length > 0) {
      setActiveIndex(items.length - 1)
    }
  }, [items.length, activeIndex])

  const nextCard = useCallback(() => {
    if (items.length <= 1) return
    setActiveIndex((prev) => (prev + 1) % items.length)
  }, [items.length])

  const prevCard = useCallback(() => {
    if (items.length <= 1) return
    setActiveIndex((prev) => (prev - 1 + items.length) % items.length)
  }, [items.length])

  // Keyboard navigation
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'ArrowRight') nextCard()
      else if (e.key === 'ArrowLeft') prevCard()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [nextCard, prevCard])

  // Wheel horizontal navigation
  const handleWheel = useCallback((e) => {
    if (Math.abs(e.deltaX) > 25 || Math.abs(e.deltaY) > 35) {
      if (e.deltaX > 20 || e.deltaY > 30) nextCard()
      else if (e.deltaX < -20 || e.deltaY < -30) prevCard()
    }
  }, [nextCard, prevCard])

  // Mouse & Touch Dragging for physical 3D turntable smoothness
  const handlePointerDown = (e) => {
    setIsDragging(true)
    startXRef.current = e.clientX || (e.touches && e.touches[0].clientX) || 0
    currentDragRef.current = 0
  }

  const handlePointerMove = (e) => {
    if (!isDragging) return
    const currentX = e.clientX || (e.touches && e.touches[0].clientX) || 0
    const delta = currentX - startXRef.current
    currentDragRef.current = delta
    setDragOffset(delta)
  }

  const handlePointerUp = () => {
    if (!isDragging) return
    setIsDragging(false)
    const threshold = 50
    if (currentDragRef.current < -threshold) {
      nextCard()
    } else if (currentDragRef.current > threshold) {
      prevCard()
    }
    setDragOffset(0)
    currentDragRef.current = 0
  }

  // Pristine empty state when no active tasks exist in database
  if (!items.length) {
    return (
      <div className="w-full max-w-md mx-auto px-4 py-8 flex items-center justify-center animate-fade-in">
        <div className="relative rounded-[32px] p-8 sm:p-10 card-sunset-ideas text-center overflow-hidden w-full border border-white/10 shadow-2xl">
          <div className="w-14 h-14 mx-auto mb-4 rounded-full bg-gradient-to-b from-[#2a2620] to-[#171615] border border-amber-400/30 flex items-center justify-center text-xl text-amber-300 shadow-[0_0_20px_rgba(245,158,11,0.25)]">
            ◈
          </div>
          <h3 className="text-xl sm:text-2xl font-light text-white tracking-tight">
            No active tasks scheduled
          </h3>
          <p className="text-xs sm:text-sm text-neutral-400 mt-2 max-w-xs mx-auto leading-relaxed">
            Your schedule is currently clear. You can create a new task or talk to Chrona AI to plan your day.
          </p>
          <div className="mt-6 flex items-center justify-center">
            <button
              onClick={onOpenAI}
              className="px-6 py-2.5 rounded-full text-xs sm:text-sm font-medium text-white bg-white/10 hover:bg-white/20 border border-white/15 shadow-lg transition-all active:scale-95 flex items-center gap-2"
            >
              <span>+</span>
              <span>Schedule with Chrona AI</span>
            </button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div
      ref={containerRef}
      onWheel={handleWheel}
      onMouseDown={handlePointerDown}
      onMouseMove={handlePointerMove}
      onMouseUp={handlePointerUp}
      onMouseLeave={handlePointerUp}
      onTouchStart={handlePointerDown}
      onTouchMove={handlePointerMove}
      onTouchEnd={handlePointerUp}
      className="relative w-full flex flex-col items-center select-none py-1 sm:py-3 cursor-grab active:cursor-grabbing"
    >
      {/* ── 3D Cylindrical Carousel Viewport (Screen-Fit with Cinematic Edge Fading) ── */}
      <div
        className="relative w-full max-w-7xl h-[clamp(290px,46dvh,380px)] flex items-center justify-center my-auto overflow-hidden px-2 sm:px-6"
        style={{
          perspective: '1400px',
          perspectiveOrigin: '50% 50%',
          maskImage: isIntro
            ? 'none'
            : 'linear-gradient(to right, transparent 0%, black 4%, black 96%, transparent 100%)',
          WebkitMaskImage: isIntro
            ? 'none'
            : 'linear-gradient(to right, transparent 0%, black 4%, black 96%, transparent 100%)',
        }}
      >
        <div
          className="relative w-full h-full flex items-center justify-center"
          style={{ transformStyle: 'preserve-3d' }}
        >
          {items.map((item, index) => {
            // Calculate offset relative to active card
            let offset = index - activeIndex

            // Support continuous wrap-around if there are 5+ items
            if (items.length >= 5) {
              if (offset > items.length / 2) offset -= items.length
              if (offset < -items.length / 2) offset += items.length
            }

            const absOffset = Math.abs(offset)
            const isVisible = absOffset <= 2.2

            if (!isVisible) return null

            const isCenter = offset === 0
            const emblem = getCardEmblem(item, index)
            const stake = getStakeBadge(item)
            const sTime = item.start_time ? dayjs(item.start_time) : null
            const eTime = item.end_time ? dayjs(item.end_time) : (sTime ? sTime.add(item.duration_minutes || 60, 'minute') : null)

            const dragShift = isDragging ? dragOffset * 0.45 : 0

            let translateX
            let translateY = 0
            let translateZ
            let rotateY
            let rotateZ = 0
            let scale
            let opacity
            let zIndex

            if (isIntro) {
              // Left-shifted stacked deck position towards the screen
              const leftBaseShift = windowWidth >= 1200
                ? -Math.min(500, windowWidth * 0.33)
                : windowWidth >= 768
                  ? -Math.min(360, windowWidth * 0.30)
                  : -Math.min(140, windowWidth * 0.20)

              const stackStepX = windowWidth >= 768 ? -20 : -10
              const stackStepY = -6
              const stackRotateZ = -offset * 3.5

              translateX = leftBaseShift + (offset * stackStepX) + dragShift
              translateY = offset * stackStepY
              translateZ = -absOffset * 45 + (isCenter ? 35 : 0)
              rotateY = 16 + (offset * 4)
              rotateZ = stackRotateZ
              scale = Math.max(0.72, 1 - absOffset * 0.08)
              opacity = Math.max(0.35, 1 - absOffset * 0.2)
              zIndex = 30 - Math.round(absOffset * 8)
            } else {
              translateX = offset * spacing + dragShift
              translateZ = -Math.pow(absOffset, 1.3) * 75
              rotateY = -offset * 22 + (isDragging ? dragOffset * -0.04 : 0)
              scale = Math.max(0.72, 1 - absOffset * 0.12)
              opacity = Math.max(0.35, 1 - absOffset * 0.28)
              zIndex = 30 - Math.round(absOffset * 10)
            }

            return (
              <div
                key={item.id || `card-${index}`}
                onClick={(e) => {
                  if (isIntro && onEndIntro) onEndIntro()
                  if (Math.abs(dragOffset) > 10) return
                  if (!isCenter) setActiveIndex(index)
                }}
                className={`absolute w-[calc(100vw-48px)] max-w-[290px] sm:max-w-[330px] h-[clamp(280px,44dvh,355px)] rounded-[26px] sm:rounded-[30px] p-4 sm:p-5 flex flex-col justify-between cursor-pointer card-sunset-ideas overflow-hidden ${isCenter
                  ? 'border-white/[0.16] shadow-[0_30px_70px_rgba(0,0,0,0.85)] ring-1 ring-white/10'
                  : 'opacity-70 hover:opacity-90'
                  }`}
                style={{
                  transform: `translateX(${translateX}px) translateY(${translateY}px) translateZ(${translateZ}px) rotateY(${rotateY}deg) rotateZ(${rotateZ}deg) scale(${scale})`,
                  transformStyle: 'preserve-3d',
                  zIndex,
                  opacity,
                  willChange: 'transform, opacity',
                  transition: isDragging ? 'none' : 'transform 1.05s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.8s ease',
                }}
              >
                {/* Ambient Radial Backlight inside card */}
                <div
                  className="absolute inset-0 rounded-[26px] sm:rounded-[30px] pointer-events-none opacity-20"
                  style={{
                    background: `radial-gradient(circle at 50% 15%, ${emblem.glow}, transparent 70%)`,
                  }}
                />

                {/* Card Top Row: Real Movability Badge & Scheduled Time */}
                <div className="relative z-10 flex items-center justify-between text-xs text-neutral-400">
                  <div className="flex items-center gap-1.5">
                    <span
                      className="text-[10px] font-semibold uppercase tracking-wider px-2 py-0.5 rounded-full flex items-center gap-1"
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
                      <span className="text-[10px] font-mono text-neutral-400 bg-white/5 px-2 py-0.5 rounded-full border border-white/10">
                        {item.task_type}
                      </span>
                    )}
                  </div>

                  {sTime ? (
                    <div className="text-right">
                      <span className="text-[11px] font-mono text-neutral-200 font-semibold block">
                        {sTime.format('h:mm A')}
                      </span>
                      <span className="text-[9px] font-mono text-neutral-400">
                        {item.duration_minutes || 60}m
                      </span>
                    </div>
                  ) : (
                    <span className="text-[10px] font-mono text-neutral-400">
                      {item.duration_minutes || 60}m duration
                    </span>
                  )}
                </div>

                {/* Card Center: Dynamic Avatar, Real Task Title, Buffer/Notes */}
                <div className="relative z-10 flex flex-col items-center text-center my-auto px-1">
                  {/* Clean Modern Emblem with Icon & Initials */}
                  <CardAvatar item={item} emblem={emblem} />

                  {/* Clean Bold Title */}
                  <h2 className="mt-3 text-base sm:text-lg font-bold text-white tracking-wide uppercase line-clamp-1">
                    {item.title}
                  </h2>

                  {/* Transition Buffer Note or Description */}
                  {item.transition_reason ? (
                    <div className="mt-1.5 px-2.5 py-1 rounded-xl bg-amber-500/10 border border-amber-500/25 flex items-center gap-1.5 text-left max-w-[270px]">
                      <span className="text-amber-400 text-xs flex-shrink-0">⚡</span>
                      <p className="text-[10px] font-mono text-amber-200 truncate">
                        Buffer: {item.transition_reason}
                      </p>
                    </div>
                  ) : item.description || item.notes ? (
                    <p className="mt-1.5 text-[11px] sm:text-xs text-neutral-300/80 leading-relaxed line-clamp-2 max-w-[270px]">
                      {item.description || item.notes}
                    </p>
                  ) : (
                    <p className="mt-1.5 text-[10px] text-neutral-400 font-mono">
                      {item.location ? `📍 ${item.location}` : 'Scheduled focus block'}
                    </p>
                  )}
                </div>

                {/* Card Bottom Row: Real Productivity Actions */}
                <div
                  className="relative z-20 pt-2.5 border-t border-white/[0.08] flex items-center justify-between gap-1.5"
                  onClick={(e) => e.stopPropagation()}
                >
                  <div className="flex items-center gap-1.5">
                    <button
                      onClick={() => onComplete && onComplete(item.id)}
                      className="px-3 py-1 rounded-full text-[10px] sm:text-[11px] font-medium text-emerald-300 bg-emerald-500/15 hover:bg-emerald-500/25 border border-emerald-500/30 transition-all active:scale-95 flex items-center gap-1"
                    >
                      <span>✓</span>
                      <span>Done</span>
                    </button>

                    <button
                      onClick={() => onReschedule && onReschedule(item.id)}
                      className="px-2.5 py-1 rounded-full text-[10px] sm:text-[11px] font-medium text-neutral-200 bg-white/5 hover:bg-white/10 border border-white/10 transition-all active:scale-95"
                      title="Auto-reschedule with AI"
                    >
                      ↻ Reschedule
                    </button>
                  </div>

                  <button
                    onClick={() => onDefer && onDefer(item.id)}
                    className="px-2.5 py-1 rounded-full text-[10px] sm:text-[11px] text-neutral-400 hover:text-white transition-colors"
                  >
                    Defer
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      </div>

      {/* Navigation Steppers & Indicators */}
      {items.length > 1 && (
        <div
          className="flex items-center gap-3 mt-2 sm:mt-3 z-20 transition-all duration-1000 ease-out"
          style={{
            transform: isIntro 
              ? `translateX(${windowWidth >= 768 ? -Math.min(500, windowWidth * 0.33) : 0}px)` 
              : 'translateX(0)',
          }}
        >
          <button
            onClick={prevCard}
            aria-label="Previous card"
            className="w-7 h-7 rounded-full bg-[#1e1c19]/90 hover:bg-[#2b2723] border border-white/10 flex items-center justify-center text-xs text-neutral-300 hover:text-white transition-all active:scale-90 shadow-md"
          >
            ‹
          </button>

          <div className="flex items-center gap-1.5">
            {items.map((_, i) => (
              <button
                key={`dot-${i}`}
                onClick={() => setActiveIndex(i)}
                aria-label={`Go to card ${i + 1}`}
                className={`h-1.5 rounded-full transition-all duration-300 ${i === activeIndex
                  ? 'w-6 bg-white shadow-[0_0_8px_rgba(255,255,255,0.6)]'
                  : 'w-1.5 bg-neutral-600 hover:bg-neutral-400'
                  }`}
              />
            ))}
          </div>

          <button
            onClick={nextCard}
            aria-label="Next card"
            className="w-7 h-7 rounded-full bg-[#1e1c19]/90 hover:bg-[#2b2723] border border-white/10 flex items-center justify-center text-xs text-neutral-300 hover:text-white transition-all active:scale-90 shadow-md"
          >
            ›
          </button>
        </div>
      )
      }
    </div >
  )
}
