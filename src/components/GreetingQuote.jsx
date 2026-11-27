import { useState, useEffect, useMemo } from 'react'
import dayjs from 'dayjs'

const MOTIVATIONAL_QUOTES = [
  'Get these things done.',
  'Time and tide wait for no one.',
  'Focus on what moves the needle.',
  'Small steps compound into greatness.',
  'Master your momentum.',
  'Energy flows where focus goes.',
  'Action turns thought into reality.',
  'Every minute shapes your destiny.',
  'Own your hours, conquer the day.',
]

export default function GreetingQuote({ hour = dayjs().hour(), isLight = null }) {
  const [quoteIndex, setQuoteIndex] = useState(0)
  const [fade, setFade] = useState(true)

  // Smart readability: automatically detect whether background is light or dark
  const lightMode = isLight !== null ? isLight : (hour >= 11 && hour < 17)

  const greeting = useMemo(() => {
    if (hour >= 5 && hour < 12) return { text: 'Good morning', icon: '🌅' }
    if (hour >= 12 && hour < 17) return { text: 'Good afternoon', icon: '☀️' }
    if (hour >= 17 && hour < 21) return { text: 'Good evening', icon: '🌇' }
    return { text: 'Good night', icon: '🌙' }
  }, [hour])

  useEffect(() => {
    const interval = setInterval(() => {
      setFade(false)
      setTimeout(() => {
        setQuoteIndex((prev) => (prev + 1) % MOTIVATIONAL_QUOTES.length)
        setFade(true)
      }, 700)
    }, 6500)

    return () => clearInterval(interval)
  }, [])

  return (
    <div className="flex flex-col items-end text-right select-none font-sans leading-tight">
      {/* Top line: Right-aligned Time Greeting */}
      <div className="flex items-center gap-1.5">
        <span
          className={`text-xs sm:text-sm font-bold tracking-tight transition-colors duration-500 ${
            lightMode
              ? 'text-neutral-950 drop-shadow-[0_1px_2px_rgba(255,255,255,0.85)]'
              : 'text-white drop-shadow-[0_2px_8px_rgba(0,0,0,0.85)]'
          }`}
        >
          {greeting.text}
        </span>
        <span className="text-xs drop-shadow-[0_2px_4px_rgba(0,0,0,0.4)]">{greeting.icon}</span>
      </div>

      {/* Bottom line: Rotating Motivational Quote */}
      <div className="mt-0.5 overflow-hidden">
        <p
          className={`text-[11px] sm:text-xs tracking-normal font-medium italic transition-all duration-700 ease-in-out whitespace-nowrap truncate max-w-[190px] sm:max-w-[280px] md:max-w-[360px] ${
            lightMode
              ? 'text-neutral-900 drop-shadow-[0_1px_2px_rgba(255,255,255,0.8)]'
              : 'text-white/95 drop-shadow-[0_2px_8px_rgba(0,0,0,0.85)]'
          } ${
            fade ? 'opacity-100 translate-y-0 blur-0' : 'opacity-0 translate-y-1.5 blur-[2px]'
          }`}
        >
          "{MOTIVATIONAL_QUOTES[quoteIndex]}"
        </p>
      </div>
    </div>
  )
}
