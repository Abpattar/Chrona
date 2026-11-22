import { useEffect, useRef } from 'react'
import { useLocation } from 'react-router-dom'
import gsap from 'gsap'
import { MorphSVGPlugin } from 'gsap/MorphSVGPlugin'

gsap.registerPlugin(MorphSVGPlugin)

const INITIAL_PATH = 'M 0 100 V 100 Q 50 100 100 100 V 100 z'
const ENTER_CURVE = 'M 0 100 V 50 Q 50 0 100 50 V 100 z'
const FILLED_PATH = 'M 0 100 V 0 Q 50 0 100 0 V 100 z'
const LEAVE_CURVE = 'M 0 0 V 50 Q 50 100 100 50 V 0 z'
const UNFILLED_PATH = 'M 0 0 V 0 Q 50 0 100 0 V 0 z'

export default function PageTransition() {
  const location = useLocation()
  const overlayRef = useRef(null)
  const pathRef = useRef(null)
  const isFirstRender = useRef(true)

  useEffect(() => {
    // Skip on initial page load
    if (isFirstRender.current) {
      isFirstRender.current = false
      return
    }

    const overlay = overlayRef.current
    const path = pathRef.current
    if (!overlay || !path) return

    // Animate morphing liquid SVG transition across page navigation
    gsap.killTweensOf(path)

    gsap.set(overlay, {
      display: 'block',
      opacity: 1,
      pointerEvents: 'auto',
    })
    gsap.set(path, { attr: { d: INITIAL_PATH } })

    const tl = gsap.timeline({
      defaults: { ease: 'power2.inOut' },
      onComplete: () => {
        gsap.set(overlay, {
          display: 'none',
          pointerEvents: 'none',
        })
        gsap.set(path, { attr: { d: INITIAL_PATH } })
      },
    })

    // Phase 1: Wave curves up and covers the viewport
    tl.to(path, {
      morphSVG: ENTER_CURVE,
      duration: 0.28,
      ease: 'sine.in',
    })
      .to(path, {
        morphSVG: FILLED_PATH,
        duration: 0.22,
        ease: 'power2.out',
      })
      // Phase 2: Wave curves up and reveals the destination page
      .to(path, {
        morphSVG: LEAVE_CURVE,
        duration: 0.26,
        ease: 'sine.in',
        delay: 0.04,
      })
      .to(path, {
        morphSVG: UNFILLED_PATH,
        duration: 0.22,
        ease: 'power2.out',
      })

    return () => {
      tl.kill()
    }
  }, [location.pathname])

  return (
    <div
      ref={overlayRef}
      className="fixed inset-0 z-50 pointer-events-none hidden overflow-hidden"
      aria-hidden="true"
    >
      <svg
        className="w-full h-full"
        viewBox="0 0 100 100"
        preserveAspectRatio="none"
      >
        <defs>
          <linearGradient id="pageTransitionGrad" x1="0%" y1="100%" x2="0%" y2="0%">
            <stop offset="0%" stopColor="#050811" />
            <stop offset="50%" stopColor="#0b1325" />
            <stop offset="100%" stopColor="#060a14" />
          </linearGradient>
        </defs>
        <path
          ref={pathRef}
          fill="url(#pageTransitionGrad)"
          stroke="rgba(56, 189, 248, 0.25)"
          strokeWidth="0.35"
          vectorEffect="non-scaling-stroke"
          d={INITIAL_PATH}
        />
      </svg>
    </div>
  )
}

