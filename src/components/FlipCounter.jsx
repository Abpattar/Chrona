import React, { useState, useEffect, useRef } from 'react'

/**
 * Authentic Mechanical Split-Flap Single Unit with White Frosted Leaves & Deep Black Numerals
 */
function SplitFlapDigit({ digit }) {
  const [curr, setCurr] = useState(digit)
  const [next, setNext] = useState(digit)
  const [isFlipping, setIsFlipping] = useState(false)
  const prevRef = useRef(digit)

  useEffect(() => {
    if (digit !== prevRef.current) {
      setNext(digit)
      setIsFlipping(true)
      const timer = setTimeout(() => {
        setCurr(digit)
        setIsFlipping(false)
        prevRef.current = digit
      }, 310)
      return () => clearTimeout(timer)
    }
  }, [digit])

  return (
    <div
      className="relative w-5 sm:w-5.5 h-7 select-none"
      style={{ perspective: '300px' }}
    >
      {/* ── Stationary Base Layer ── */}
      {/* Upper Back Flap (shows NEXT top half while flipping) */}
      <div className="absolute top-0 inset-x-0 h-3.5 overflow-hidden rounded-t-[3px] bg-gradient-to-b from-[#ffffff] to-[#eeeeee] border-t border-x border-black/15 shadow-2xs">
        <div className="absolute inset-x-0 top-0 h-7 flex items-center justify-center font-mono font-black text-sm tracking-tighter leading-none text-black">
          {isFlipping ? next : curr}
        </div>
      </div>

      {/* Lower Back Flap (shows CURRENT bottom half while flipping) */}
      <div className="absolute bottom-0 inset-x-0 h-3.5 overflow-hidden rounded-b-[3px] bg-gradient-to-b from-[#e8e8e8] to-[#dedede] border-b border-x border-black/15 shadow-2xs">
        <div className="absolute inset-x-0 -top-3.5 h-7 flex items-center justify-center font-mono font-black text-sm tracking-tighter leading-none text-black">
          {isFlipping ? next : curr}
        </div>
      </div>

      {/* ── Dynamic 3D Flipping Flaps ── */}
      {isFlipping && (
        <>
          {/* Upper Front Flap: folds DOWN (-90deg) */}
          <div
            className="absolute top-0 inset-x-0 h-3.5 overflow-hidden rounded-t-[3px] bg-gradient-to-b from-[#ffffff] to-[#eaeaea] border-t border-x border-black/20 split-flap-top-anim z-20"
          >
            <div className="absolute inset-x-0 top-0 h-7 flex items-center justify-center font-mono font-black text-sm tracking-tighter leading-none text-black">
              {curr}
            </div>
            {/* Dynamic shade overlay as it folds down */}
            <div className="absolute inset-0 bg-black/15 pointer-events-none" />
          </div>

          {/* Lower Front Flap: flips down from top into place (90deg -> 0deg) */}
          <div
            className="absolute bottom-0 inset-x-0 h-3.5 overflow-hidden rounded-b-[3px] bg-gradient-to-b from-[#e6e6e6] to-[#dedede] border-b border-x border-black/20 split-flap-bottom-anim z-20"
          >
            <div className="absolute inset-x-0 -top-3.5 h-7 flex items-center justify-center font-mono font-black text-sm tracking-tighter leading-none text-black">
              {next}
            </div>
          </div>
        </>
      )}

      {/* ── Mechanical Center Seam & Axle Pivot Notch Pins ── */}
      <div className="absolute inset-x-0 top-1/2 -translate-y-1/2 h-[1px] bg-black/35 shadow-[0_1px_0_rgba(255,255,255,0.7)] z-30 pointer-events-none" />
      {/* Left axle pin notch */}
      <div className="absolute -left-[2px] top-1/2 -translate-y-1/2 w-[3px] h-[3px] rounded-full bg-neutral-400 border border-neutral-600/50 z-40 pointer-events-none" />
      {/* Right axle pin notch */}
      <div className="absolute -right-[2px] top-1/2 -translate-y-1/2 w-[3px] h-[3px] rounded-full bg-neutral-400 border border-neutral-600/50 z-40 pointer-events-none" />
    </div>
  )
}

/**
 * 2-Digit Split-Flap Module (Tens + Ones) with Clean White Glassy Housing
 */
function SplitFlapUnit({ value = 0 }) {
  const safeVal = Math.max(0, Number(value) || 0)
  const tens = Math.floor(safeVal / 10) % 10
  const ones = safeVal % 10

  return (
    <div className="inline-flex items-center gap-[3px] p-[2px] rounded-[5px] bg-white/70 border border-black/10 shadow-[inset_0_1px_3px_rgba(0,0,0,0.12)]">
      <SplitFlapDigit digit={tens} />
      <SplitFlapDigit digit={ones} />
    </div>
  )
}

export default function FlipCounter({ activeCount = 0, completedCount = 0 }) {
  return (
    <div className="flex items-center gap-2.5 sm:gap-3 px-3.5 sm:px-4 py-1.5 rounded-full bg-white/80 hover:bg-white/90 backdrop-blur-2xl border border-white/60 shadow-[0_8px_32px_rgba(0,0,0,0.14)] font-mono select-none transition-all">
      {/* Active Section with Black Typography */}
      <div className="flex items-center gap-1.5">
        <span className="w-1.5 h-1.5 rounded-full bg-amber-500 shadow-[0_0_6px_rgba(245,158,11,0.7)] animate-pulse" />
        <span className="text-[10px] font-bold tracking-wider text-black uppercase">
          Active
        </span>
        <SplitFlapUnit value={activeCount} />
      </div>

      {/* Divider */}
      <span className="text-black/30 text-xs font-sans leading-none">|</span>

      {/* Done Section with Black Typography */}
      <div className="flex items-center gap-1.5">
        <span className="w-1.5 h-1.5 rounded-full bg-emerald-600 shadow-[0_0_6px_rgba(16,185,129,0.7)]" />
        <span className="text-[10px] font-bold tracking-wider text-black uppercase">
          Done
        </span>
        <SplitFlapUnit value={completedCount} />
      </div>
    </div>
  )
}
