import { Routes, Route, NavLink, Navigate } from 'react-router-dom'
import { useState, useEffect } from 'react'
import Dashboard from './pages/Dashboard'
import Chat from './pages/Chat'
import Calendar from './pages/Calendar'
import Onboarding from './pages/Onboarding'
import Priority from './pages/Priority'
import Upload from './pages/Upload'

// Nav links for the Chrona app
const navLinks = [
  { to: '/dashboard', label: 'Today', icon: '⚡' },
  { to: '/chat', label: 'Chrona', icon: '✦' },
  { to: '/calendar', label: 'Calendar', icon: '📅' },
  { to: '/tasks', label: 'Tasks', icon: '📋' },
  { to: '/upload', label: 'Upload', icon: '⬆️' },
]

export default function App() {
  const [onboarded, setOnboarded] = useState(() => {
    return localStorage.getItem('chrona_onboarded') === 'true'
  })

  if (!onboarded) {
    return (
      <div className="flex flex-col min-h-screen min-h-dvh" style={{ background: 'transparent' }}>
        <Onboarding onComplete={() => setOnboarded(true)} />
      </div>
    )
  }

  return (
    <div className="flex flex-col min-h-screen min-h-dvh overflow-x-hidden" style={{ background: 'transparent' }}>

      {/* ── Header ─────────────────────────────────── */}
      <nav className="flex-none sticky top-0 z-40 glass-strong" style={{ borderBottom: '1px solid rgba(77, 163, 255, 0.12)' }}>
        {/* Logo row */}
        <div className="flex items-center justify-between h-12 px-4 sm:px-6"
          style={{ borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
          <div className="flex items-center gap-2.5">
            <div className="w-7 h-7 flex items-center justify-center rounded-lg text-sm font-bold text-white"
              style={{ background: 'linear-gradient(135deg, #4da3ff, #6366f1)', boxShadow: '0 0 12px rgba(77,163,255,0.4)' }}>
              C
            </div>
            <span className="text-base font-bold tracking-tight" style={{ color: 'var(--ch-text)' }}>
              Chrona
            </span>
          </div>
          <div className="flex items-center gap-1.5">
            <div className="w-1.5 h-1.5 rounded-full animate-pulse" style={{ background: '#3ddc97', boxShadow: '0 0 8px rgba(61,220,151,0.5)' }} />
            <span className="text-[10px] font-medium uppercase tracking-wider" style={{ color: 'var(--ch-text-dim)' }}>
              AI Active
            </span>
          </div>
        </div>

        {/* Nav tabs */}
        <div className="flex w-full">
          {navLinks.map(link => (
            <NavLink
              key={link.to}
              to={link.to}
              className={({ isActive }) =>
                `flex-1 flex flex-col items-center justify-center gap-0.5 py-2.5 text-[10px] sm:text-xs font-semibold uppercase tracking-wider transition-all duration-200 ${isActive
                  ? 'text-nv-accent'
                  : 'text-nv-text-dim hover:text-nv-text'
                }`
              }
              style={({ isActive }) => isActive ? {
                background: 'rgba(77,163,255,0.08)',
                borderBottom: '2px solid #4da3ff',
                boxShadow: '0 2px 12px rgba(77,163,255,0.15)',
              } : {
                borderRight: '1px solid rgba(255,255,255,0.04)',
                borderBottom: '2px solid transparent',
              }}
            >
              <span className="text-base sm:text-lg leading-none">{link.icon}</span>
              <span>{link.label}</span>
            </NavLink>
          ))}
        </div>
      </nav>

      {/* ── Main Content ────────────────────────────── */}
      <main className="flex-1 flex flex-col overflow-hidden">
        <Routes>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/chat" element={<Chat />} />
          <Route path="/calendar" element={<Calendar />} />
          <Route path="/tasks" element={<Priority />} />
          <Route path="/upload" element={<Upload />} />
          {/* Legacy routes */}
          <Route path="/mindmap" element={<Navigate to="/calendar" replace />} />
          <Route path="/priority" element={<Navigate to="/tasks" replace />} />
        </Routes>
      </main>
    </div>
  )
}
