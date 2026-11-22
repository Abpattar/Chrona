import { Routes, Route, NavLink, Navigate } from 'react-router-dom'
import { useState, useEffect } from 'react'
import Dashboard from './pages/Dashboard'
import Chat from './pages/Chat'
import Calendar from './pages/Calendar'
import ParticleBackground from './components/ParticleBackground'
import dayjs from 'dayjs'

const navLinks = [
  { to: '/dashboard', label: 'Today', icon: '◈' },
  { to: '/chat', label: 'Chrona AI', icon: '✧' },
  { to: '/calendar', label: 'Mind Map', icon: '🗺️' },
  { to: '/week', label: 'Week Grid', icon: '📅' },
]

export default function App() {
  const [clock, setClock] = useState(dayjs())

  useEffect(() => {
    const timer = setInterval(() => setClock(dayjs()), 10000)
    return () => clearInterval(timer)
  }, [])

  return (
    <div className="relative flex flex-col h-screen h-dvh overflow-hidden bg-[#05070c] text-[#cbd5e1] select-none">
      
      {/* ── Glowing Ambient Particle Background Canvas ────────────────────────── */}
      <ParticleBackground />

      {/* ── ONLY SINGLE ROW: Sleek Unified Top Menu Bar ──────────────────────── */}
      <header className="flex-none z-40 h-13 sm:h-14 bg-[#080c16]/90 backdrop-blur-xl border-b border-slate-800/80 px-3 sm:px-6 shadow-[0_4px_20px_rgba(0,0,0,0.5)]">
        <div className="max-w-6xl mx-auto h-full flex items-center justify-between gap-2 sm:gap-4">
          
          {/* Brand Crest */}
          <NavLink to="/dashboard" className="flex items-center gap-2 sm:gap-2.5 flex-shrink-0">
            <div className="w-7 h-7 sm:w-8 sm:h-8 rounded-lg sm:rounded-xl bg-gradient-to-br from-[#121b2d] to-[#0a101b] border border-cyan-500/30 flex items-center justify-center text-xs sm:text-sm font-bold text-cyan-300 shadow-[0_0_12px_rgba(56,189,248,0.25)]">
              ◈
            </div>
            <span className="text-sm sm:text-base font-bold tracking-widest text-white uppercase font-cinzel leading-tight">
              CHRONA
            </span>
          </NavLink>

          {/* Single Compact Menu Bar — Select Whatever is Needed */}
          <nav className="flex items-center bg-[#0b0f1a]/90 p-0.5 sm:p-1 rounded-xl sm:rounded-2xl border border-slate-800 shadow-inner">
            {navLinks.map((link) => (
              <NavLink
                key={link.to}
                to={link.to}
                className={({ isActive }) =>
                  `flex items-center gap-1.5 px-2.5 sm:px-3.5 py-1 sm:py-1.5 rounded-lg sm:rounded-xl text-xs font-semibold tracking-wide transition-all ${
                    isActive
                      ? 'bg-gradient-to-b from-[#182338] to-[#111929] text-white border border-cyan-500/30 shadow-[0_0_12px_rgba(56,189,248,0.2)]'
                      : 'text-slate-400 hover:text-slate-200'
                  }`
                }
              >
                <span className="text-xs text-cyan-400/90">{link.icon}</span>
                <span className="text-[11px] sm:text-xs leading-none">
                  {link.label}
                </span>
              </NavLink>
            ))}
          </nav>

          {/* Right Live Time & Status Indicator */}
          <div className="flex items-center gap-3 flex-shrink-0">
            <span className="hidden md:inline text-xs font-mono text-slate-400">
              {clock.format('ddd · h:mm A')}
            </span>
            <div className="flex items-center gap-1.5">
              <div className="w-1.5 h-1.5 rounded-full bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.8)] animate-pulse" />
              <span className="text-[9px] font-mono tracking-wider text-emerald-400 uppercase hidden sm:inline">
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
