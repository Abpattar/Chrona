import { useEffect, useRef, useState } from 'react'
import { gsap } from 'gsap'

export default function OpeningAnimation({ onComplete }) {
    const containerRef = useRef(null)
    const blocksRef = useRef([])
    const textRef = useRef(null)
    const [animationDone, setAnimationDone] = useState(false)

    useEffect(() => {
        document.body.classList.add('no-scroll')

        const tl = gsap.timeline({
            onComplete: () => {
                document.body.classList.remove('no-scroll')
                setAnimationDone(true)
                if (onComplete) onComplete()
            }
        })

        tl.fromTo(
            textRef.current,
            { opacity: 0, y: 20 },
            { opacity: 1, y: 0, duration: 1, ease: 'power3.out' }
        )

        tl.to(textRef.current, { opacity: 0, duration: 0.5, delay: 0.5 })

        tl.to(
            blocksRef.current,
            {
                duration: 1,
                clipPath: 'polygon(0% 0%, 100% 0%, 100% 0%, 0% 0%)',
                stagger: 0.075,
                ease: 'power3.inOut'
            },
            '-=0.2'
        )

        return () => { tl.kill() }
    }, [onComplete])

    if (animationDone) return null

    const blocks = Array.from({ length: 8 })

    return (
        <div
            ref={containerRef}
            className="fixed inset-0 z-[9999] pointer-events-none flex"
        >
            {/* The Text overlay */}
            <div className="absolute inset-0 z-20 flex items-center justify-center pointer-events-none">
                <h1
                    ref={textRef}
                    className="text-5xl md:text-7xl lg:text-9xl font-bold tracking-widest uppercase"
                    style={{
                        fontFamily: '"Space Grotesk", sans-serif',
                        opacity: 0,
                        color: '#4da3ff',
                        textShadow: '0 0 40px rgba(77, 163, 255, 0.5)',
                    }}
                >
                    Neuravex
                </h1>
            </div>

            {/* The animated blocks layer — deep navy */}
            <div className="absolute inset-0 z-10 flex w-full h-full pointer-events-none">
                {blocks.map((_, i) => (
                    <div
                        key={i}
                        ref={(el) => (blocksRef.current[i] = el)}
                        className="flex-1 h-full"
                        style={{
                            backgroundColor: '#070d1f',
                            marginRight: '-1px',
                            clipPath: 'polygon(0 0, 100% 0, 100% 100%, 0 100%)'
                        }}
                    />
                ))}
            </div>
        </div>
    )
}
