// ============================================================
// Header.jsx — Top app bar with nav tabs and action icons
// ============================================================

'use client';

import { useState } from 'react';

const NAV_TABS = ['Today', 'Calendar', 'Habits', 'Journal'];

export default function Header() {
  const [activeTab, setActiveTab] = useState('Today');

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
      {/* Left: Title + Nav */}
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
              isActive={tab === activeTab}
              onClick={() => setActiveTab(tab)}
            />
          ))}
        </nav>
      </div>

      {/* Right: Action Icons */}
      <div className="flex items-center gap-4">
        <HeaderIcon icon="push_pin" />
        <HeaderIcon icon="settings" />
      </div>
    </header>
  );
}

// ---- Nav tab button ----
function NavTab({ label, isActive, onClick }) {
  return (
    <button
      id={`nav-tab-${label.toLowerCase()}`}
      onClick={onClick}
      className="transition-transform hover:rotate-1 pb-1"
      style={{
        color: isActive ? '#ad170c' : '#1c1c18',
        opacity: isActive ? 1 : 0.7,
        borderBottom: isActive ? '2px solid #ad170c' : '2px solid transparent',
        fontFamily: 'Space Grotesk, sans-serif',
        background: 'none',
        cursor: 'pointer',
        border: 'none',
        borderBottomWidth: '2px',
        borderBottomStyle: 'solid',
        borderBottomColor: isActive ? '#ad170c' : 'transparent',
        paddingBottom: '4px',
      }}
    >
      {label}
    </button>
  );
}

// ---- Header icon button ----
function HeaderIcon({ icon }) {
  return (
    <button
      className="material-symbols-outlined transition-transform hover:rotate-1"
      style={{
        color: '#1c1c18',
        opacity: 0.7,
        background: 'none',
        cursor: 'pointer',
        border: 'none',
        fontSize: '24px',
      }}
    >
      {icon}
    </button>
  );
}
