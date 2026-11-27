import { Routes, Route, NavLink, Navigate, useLocation } from 'react-router-dom'
import { useState, useEffect } from 'react'
import Dashboard from './pages/Dashboard'
import Chat from './pages/Chat'
import Calendar from './pages/Calendar'
import { getSkyTheme } from './lib/theme'
import dayjs from 'dayjs'

const navLinks = [
  { to: '/dashboard', label: 'Today', icon: '◈' },
  { to: '/chat', label: 'Chrona AI', icon: '✧' },
  { to: '/calendar', label: 'Mind Map', icon: '❖' },
  { to: '/week', label: 'Week Grid', icon: '▦' },
]

export default function App() {
  const [clock, setClock] = useState(dayjs())
  const location = useLocation()
  const isDashboard = location.pathname === '/dashboard' || location.pathname === '/'
  const isChat = location.pathname === '/chat'
  const isMindmap = location.pathname === '/calendar'
  const skyTheme = getSkyTheme(clock.hour())

  useEffect(() => {
    const timer = setInterval(() => setClock(dayjs()), 10000)
    return () => clearInterval(timer)
  }, [])

  return (
    <div
      className={`relative flex flex-col h-screen h-dvh overflow-hidden select-none transition-colors duration-700 ${
        isDashboard
          ? `${skyTheme.bgClass} sky-transition text-slate-100`
          : isChat
          ? 'bg-copilot-daylight text-slate-800'
          : isMindmap
          ? 'bg-mindmap-canvas text-slate-100'
          : 'bg-week-canvas text-slate-100'
      }`}
    >
      
      {/* ── Sleek Ultra-Glass Header Bar Over Seamless Screen Background ─── */}
      <header className="flex-none z-40 h-13 sm:h-14 bg-black/10 backdrop-blur-md border-b border-white/[0.05] px-4 sm:px-8 shadow-xs">
        <div className="relative w-full h-full flex items-center justify-between gap-2 sm:gap-4">
          
          {/* Brand Crest */}
          <NavLink to="/dashboard" className="flex items-center gap-2 sm:gap-2.5 flex-shrink-0 group z-10">
            <div className="w-8 h-8 rounded-full bg-gradient-to-br from-[#2a2620] to-[#141311] border border-white/15 flex items-center justify-center text-xs sm:text-sm font-bold text-white shadow-md group-hover:scale-105 transition-transform">
              ◈
            </div>
            <span className="text-sm sm:text-base font-semibold tracking-wider text-white uppercase leading-none font-sans">
              CHRONA
            </span>
          </NavLink>

          {/* Clean Menu Bar Links — Truly Centered in all pages to the website */}
          <nav className="sm:absolute sm:left-1/2 sm:-translate-x-1/2 flex items-center bg-[#1e1c18]/85 p-1 rounded-full border border-white/10 shadow-inner backdrop-blur-xl z-20">
            {navLinks.map((link) => (
              <NavLink
                key={link.to}
                to={link.to}
                className={({ isActive }) =>
                  `flex items-center gap-1.5 px-3 sm:px-4 py-1 sm:py-1.5 rounded-full text-xs font-medium tracking-wide transition-all ${
                    isActive
                      ? 'bg-white/15 text-white shadow-sm border border-white/15'
                      : 'text-neutral-400 hover:text-white'
                  }`
                }
              >
                <span className="text-xs text-neutral-300">{link.icon}</span>
                <span className="text-[11px] sm:text-xs leading-none">
                  {link.label}
                </span>
              </NavLink>
            ))}
          </nav>

          {/* Right Live Clock & Online Indicator */}
          <div className="flex items-center gap-2.5 sm:gap-3 flex-shrink-0 z-10">
            {/* Digital Clock Style Widget */}
            <div className="flex items-center gap-2 px-3 py-1 rounded-full bg-[#121419]/90 border border-white/10 shadow-inner backdrop-blur-xl font-mono">
              <div className="hidden md:flex items-center gap-1.5 text-[10px] text-neutral-400 font-semibold tracking-wider uppercase">
                <span className="text-neutral-400 text-xs">▦</span>
                <span>{clock.format('ddd, MMM D')}</span>
                <span className="text-neutral-600 text-xs ml-0.5">|</span>
              </div>
              <div className="flex items-center gap-0.5 text-xs font-bold tracking-widest text-white">
                <span>{clock.format('hh')}</span>
                <span className="animate-pulse text-amber-400 font-bold">:</span>
                <span>{clock.format('mm')}</span>
                <span className="text-[9px] text-amber-300 ml-1 font-medium">{clock.format('A')}</span>
              </div>
            </div>

            <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-[#1b1916]/80 border border-white/10">
              <div className="w-1.5 h-1.5 rounded-full bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.8)] animate-pulse" />
              <span className="text-[10px] font-mono tracking-wider text-emerald-400 uppercase hidden sm:inline font-semibold">
                Online
              </span>
            </div>
          </div>

        </div>
      </header>

      {/* ── Screen-Fit Main Viewport (Zero extra nav rows) ───────────────────── */}
      <main className="flex-1 flex flex-col overflow-hidden relative z-10 w-full min-h-0">
        <Routes>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/chat" element={<Chat />} />
          <Route path="/calendar" element={<Calendar initialView="mindmap" />} />
          <Route path="/week" element={<Calendar initialView="timeline" />} />
          
          {/* Redirect legacy routes */}
          <Route path="/tasks" element={<Navigate to="/dashboard" replace />} />
          <Route path="/priority" element={<Navigate to="/dashboard" replace />} />
          <Route path="/mindmap" element={<Navigate to="/calendar" replace />} />
          <Route path="/upload" element={<Navigate to="/chat" replace />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </main>

    </div>
  )
}
