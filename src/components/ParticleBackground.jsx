import { useEffect, useRef } from 'react'

export default function ParticleBackground() {
  const canvasRef = useRef(null)

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    let animationFrameId
    let width = (canvas.width = window.innerWidth)
    let height = (canvas.height = window.innerHeight)

    const handleResize = () => {
      if (!canvas) return
      width = canvas.width = window.innerWidth
      height = canvas.height = window.innerHeight
    }
    window.addEventListener('resize', handleResize)

    // Mouse tracking for subtle ambient parallax
    let mouse = { x: width / 2, y: height / 2, targetX: width / 2, targetY: height / 2 }
    const handleMouseMove = (e) => {
      mouse.targetX = e.clientX
      mouse.targetY = e.clientY
    }
    window.addEventListener('mousemove', handleMouseMove)

    // Particle pool: Glowing luminous motes / soul fireflies
    const PARTICLE_COUNT = Math.min(75, Math.floor((width * height) / 18000))
    const particles = []

    const colors = [
      { r: 254, g: 243, b: 199 },  // Warm starlight
      { r: 245, g: 158, b: 11 },   // Golden sunset amber
      { r: 234, g: 88, b: 12 },    // Sunset ember orange
      { r: 255, g: 255, b: 255 },  // Pure white spark
    ]

    for (let i = 0; i < PARTICLE_COUNT; i++) {
      const color = colors[Math.floor(Math.random() * colors.length)]
      particles.push({
        x: Math.random() * width,
        y: Math.random() * height,
        radius: Math.random() * 2 + 0.8,
        baseRadius: Math.random() * 2 + 0.8,
        vx: (Math.random() - 0.5) * 0.45,
        vy: -Math.random() * 0.5 - 0.15, // Gentle upward drift
        color,
        alpha: Math.random() * 0.65 + 0.25,
        baseAlpha: Math.random() * 0.65 + 0.25,
        pulseSpeed: Math.random() * 0.025 + 0.01,
        pulseAngle: Math.random() * Math.PI * 2,
        wobbleSpeed: Math.random() * 0.02 + 0.005,
        wobbleAngle: Math.random() * Math.PI * 2,
        wobbleAmp: Math.random() * 0.8 + 0.3,
      })
    }

    let lastTime = performance.now()

    const render = (time) => {
      const dt = Math.min(32, time - lastTime) / 16.67
      lastTime = time

      // Smooth mouse easing
      mouse.x += (mouse.targetX - mouse.x) * 0.05
      mouse.y += (mouse.targetY - mouse.y) * 0.05

      // Clear canvas with transparent frame so daylight background shines through
      ctx.clearRect(0, 0, width, height)

      // Render constellation connections between close particles
      for (let i = 0; i < particles.length; i++) {
        const p1 = particles[i]
        for (let j = i + 1; j < particles.length; j++) {
          const p2 = particles[j]
          const dx = p1.x - p2.x
          const dy = p1.y - p2.y
          const dist = Math.sqrt(dx * dx + dy * dy)
          if (dist < 85) {
            const lineAlpha = (1 - dist / 85) * 0.12 * Math.min(p1.alpha, p2.alpha)
            ctx.beginPath()
            ctx.strokeStyle = `rgba(56, 189, 248, ${lineAlpha})`
            ctx.lineWidth = 0.6
            ctx.moveTo(p1.x, p1.y)
            ctx.lineTo(p2.x, p2.y)
            ctx.stroke()
          }
        }
      }

      // Update and draw glowing particles
      for (let i = 0; i < particles.length; i++) {
        const p = particles[i]

        // Drift motion + sinusoidal wobble
        p.wobbleAngle += p.wobbleSpeed * dt
        p.pulseAngle += p.pulseSpeed * dt
        p.x += (p.vx + Math.sin(p.wobbleAngle) * p.wobbleAmp) * dt
        p.y += p.vy * dt

        // Wrap around edges gracefully
        if (p.y < -20) {
          p.y = height + 10
          p.x = Math.random() * width
        }
        if (p.x < -20) p.x = width + 10
        if (p.x > width + 20) p.x = -10

        // Pulsing glow
        const pulse = Math.sin(p.pulseAngle)
        p.alpha = Math.max(0.1, p.baseAlpha + pulse * 0.22)
        p.radius = Math.max(0.6, p.baseRadius + pulse * 0.4)

        // Mouse gentle repel/attract effect
        const mdx = mouse.x - p.x
        const mdy = mouse.y - p.y
        const mdist = Math.sqrt(mdx * mdx + mdy * mdy)
        if (mdist < 130) {
          const force = (1 - mdist / 130) * 0.6
          p.x -= (mdx / mdist) * force * 1.5
          p.y -= (mdy / mdist) * force * 1.5
        }

        // Draw outer glow aura
        const glowGrad = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, p.radius * 4.5)
        glowGrad.addColorStop(0, `rgba(${p.color.r}, ${p.color.g}, ${p.color.b}, ${p.alpha * 0.9})`)
        glowGrad.addColorStop(0.35, `rgba(${p.color.r}, ${p.color.g}, ${p.color.b}, ${p.alpha * 0.35})`)
        glowGrad.addColorStop(1, `rgba(${p.color.r}, ${p.color.g}, ${p.color.b}, 0)`)

        ctx.beginPath()
        ctx.arc(p.x, p.y, p.radius * 4.5, 0, Math.PI * 2)
        ctx.fillStyle = glowGrad
        ctx.fill()

        // Draw solid core
        ctx.beginPath()
        ctx.arc(p.x, p.y, p.radius, 0, Math.PI * 2)
        ctx.fillStyle = `rgba(255, 255, 255, ${Math.min(1, p.alpha * 1.3)})`
        ctx.fill()
      }

      animationFrameId = requestAnimationFrame(render)
    }

    animationFrameId = requestAnimationFrame(render)

    return () => {
      cancelAnimationFrame(animationFrameId)
      window.removeEventListener('resize', handleResize)
      window.removeEventListener('mousemove', handleMouseMove)
    }
  }, [])

  return (
    <canvas
      ref={canvasRef}
      className="fixed inset-0 pointer-events-none z-0 w-full h-full"
      style={{ background: '#05070c' }}
    />
  )
}

