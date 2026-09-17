import { Routes, Route, NavLink } from 'react-router-dom'
import Upload from './pages/Upload'
import Mindmap from './pages/Mindmap'
import Priority from './pages/Priority'
import OpeningAnimation from './components/OpeningAnimation'

const navLinks = [
    { to: '/', label: 'Upload', icon: '⬆️' },
    { to: '/mindmap', label: 'Mindmap', icon: '🧠' },
    { to: '/priority', label: 'Priority', icon: '📊' },
]

export default function App() {
    return (
        <div className="flex flex-col min-h-screen min-h-dvh overflow-x-hidden" style={{ background: 'transparent' }}>
            <OpeningAnimation />

            {/* ── Glass Navigation ────────────────────────── */}
            <nav className="flex-none sticky top-0 z-40 glass-strong" style={{ borderBottom: '1px solid rgba(77, 163, 255, 0.15)' }}>
                {/* Row 1 — Logo + status */}
                <div className="flex items-center justify-between h-10 sm:h-14 px-3 sm:px-6" style={{ borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
                    <div className="flex items-center gap-2">
                        <div className="w-7 h-7 sm:w-8 sm:h-8 flex items-center justify-center rounded-lg text-sm font-bold text-white"
                            style={{ background: 'linear-gradient(135deg, #4da3ff, #6366f1)', boxShadow: '0 0 12px rgba(77, 163, 255, 0.4)' }}>
                            N
                        </div>
                        <span className="text-base sm:text-lg font-bold tracking-tight text-nv-text">
                            Neuravex
                        </span>
                    </div>
                    <div className="flex items-center gap-1.5">
                        <div className="w-2 h-2 rounded-full animate-pulse" style={{ background: '#3ddc97', boxShadow: '0 0 8px rgba(61, 220, 151, 0.5)' }} />
                        <span className="text-[10px] font-medium text-nv-text-dim uppercase tracking-wider">Live</span>
                    </div>
                </div>

                {/* Row 2 — Nav tabs */}
                <div className="flex w-full">
                    {navLinks.map((link) => (
                        <NavLink
                            key={link.to}
                            to={link.to}
                            end={link.to === '/'}
                            className={({ isActive }) =>
                                `flex-1 flex flex-col items-center justify-center gap-0.5 py-2.5 sm:py-3 text-[10px] sm:text-xs font-semibold uppercase tracking-wider transition-all duration-200 relative ${isActive
                                    ? 'text-nv-accent'
                                    : 'text-nv-text-dim hover:text-nv-text'
                                }`
                            }
                            style={({ isActive }) => isActive ? {
                                background: 'rgba(77, 163, 255, 0.08)',
                                borderBottom: '2px solid #4da3ff',
                                boxShadow: '0 2px 12px rgba(77, 163, 255, 0.15)',
                            } : {
                                borderRight: '1px solid rgba(255,255,255,0.04)',
                                borderBottom: '2px solid transparent',
                            }}
                        >
                            <span className="text-lg sm:text-xl leading-none">{link.icon}</span>
                            <span>{link.label}</span>
                        </NavLink>
                    ))}
                </div>
            </nav>

            {/* ── Routes ─────────────────────────────────── */}
            <main className="flex-1 flex flex-col overflow-x-hidden min-h-0">
                <Routes>
                    <Route path="/" element={<Upload />} />
                    <Route path="/mindmap" element={<Mindmap />} />
                    <Route path="/priority" element={<Priority />} />
                </Routes>
            </main>
        </div>
    )
}
