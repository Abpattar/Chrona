// ============================================================
// Header.jsx — Top bar with nav tabs and view switching
// ============================================================

'use client';

const NAV_TABS = ['Today', 'Calendar', 'Habits', 'Journal'];

export default function Header({ activeView, onViewChange }) {
  return (
    <header
      id="header-bar"
      className="flex justify-between items-center px-8 w-full top-0 left-0 z-50 fixed h-16"
      style={{
        backgroundColor: '#fcf9f2',
        backgroundImage: 'linear-gradient(to bottom, rgba(28,28,24,0.05), transparent)',
        borderBottom: '1px solid rgba(0,0,0,0.05)',
      }}
    >
      <div className="flex items-center gap-8">
        <h1
          className="text-2xl font-bold uppercase tracking-tighter"
          style={{ fontFamily: 'Space Grotesk, sans-serif', color: '#1c1c18' }}
        >
          My Schedule
        </h1>
        <nav
          className="hidden md:flex gap-6 uppercase tracking-tighter text-sm"
          style={{ fontFamily: 'Space Grotesk, sans-serif' }}
        >
          {NAV_TABS.map((tab) => (
            <NavTab
              key={tab}
              label={tab}
              isActive={tab === activeView}
              onClick={() => onViewChange(tab)}
            />
          ))}
        </nav>
      </div>
      <div className="flex items-center gap-4">
        <HeaderIcon icon="push_pin" />
        <HeaderIcon icon="settings" />
      </div>
    </header>
  );
}

function NavTab({ label, isActive, onClick }) {
  return (
    <button
      id={`nav-tab-${label.toLowerCase()}`}
      onClick={onClick}
      className="transition-transform hover:rotate-1 pb-1"
      style={{
        color: isActive ? '#ad170c' : '#1c1c18',
        opacity: isActive ? 1 : 0.7,
        border: 'none',
        borderBottom: isActive ? '2px solid #ad170c' : '2px solid transparent',
        background: 'none',
        cursor: 'pointer',
        fontFamily: 'inherit',
        fontSize: 'inherit',
      }}
    >
      {label}
    </button>
  );
}

function HeaderIcon({ icon }) {
  return (
    <button
      className="material-symbols-outlined transition-transform hover:rotate-1"
      style={{ color: '#1c1c18', opacity: 0.7, background: 'none', cursor: 'pointer', border: 'none' }}
    >
      {icon}
    </button>
  );
}
