// ============================================================
// Sidebar.jsx — Left navigation panel
// ============================================================

'use client';

import Link from 'next/link';

const MAIN_NAV_ITEMS = [
  { icon: 'task_alt', label: 'Priority Tasks', active: true },
  { icon: 'event', label: 'Upcoming', active: false },
  { icon: 'flag', label: 'Long-term Goals', active: false },
  { icon: 'history', label: 'Archived Logs', active: false },
];

const BOTTOM_NAV_ITEMS = [
  { icon: 'help_outline', label: 'Resources' },
  { icon: 'logout', label: 'Logout' },
];

export default function Sidebar({ onAddItem, activeItem, onNavigate }) {
  return (
    <aside
      id="sidebar"
      className="fixed left-0 top-0 h-full z-40 flex flex-col p-6 overflow-hidden w-64"
      style={{
        backgroundColor: 'rgba(252,249,242,0.95)',
        boxShadow: '10px 0 30px rgba(28,28,24,0.06)',
        backdropFilter: 'blur(8px)',
      }}
    >
      {/* Header Section */}
      <SidebarHeader />

      {/* Main Navigation */}
      <nav
        className="flex flex-col gap-8 italic text-lg"
        style={{ fontFamily: 'Newsreader, serif' }}
      >
        {MAIN_NAV_ITEMS.map((item) => (
          <SidebarNavItem 
            key={item.label} 
            {...item} 
            active={item.label === activeItem}
            onClick={() => onNavigate(item.label)}
          />
        ))}
      </nav>

      {/* Add New Item Button */}
      <button
        id="add-new-item-btn"
        onClick={onAddItem}
        className="mt-12 uppercase px-4 py-3 text-sm tracking-widest active:scale-95 transition-transform font-bold"
        style={{
          fontFamily: 'Space Grotesk, sans-serif',
          backgroundColor: '#ad170c',
          color: '#ffffff',
          border: 'none',
          cursor: 'pointer',
        }}
      >
        Add New Item
      </button>

      {/* Bottom Links */}
      <div
        className="mt-auto flex flex-col gap-4 italic"
        style={{ fontFamily: 'Newsreader, serif', opacity: 0.6, fontSize: '1rem' }}
      >
        {BOTTOM_NAV_ITEMS.map((item) => (
          <SidebarBottomLink key={item.label} {...item} />
        ))}
      </div>
    </aside>
  );
}

// ---- Sub-components ----

function SidebarHeader() {
  return (
    <div className="mt-20 mb-10">
      <div
        className="font-black uppercase text-xl"
        style={{ fontFamily: 'Space Grotesk, sans-serif', color: '#1c1c18' }}
      >
        Personal Board
      </div>
      <div
        className="italic text-lg"
        style={{ fontFamily: 'Newsreader, serif', opacity: 0.8 }}
      >
        Life Design
      </div>
    </div>
  );
}

function SidebarNavItem({ icon, label, active, onClick }) {
  return (
    <button
      id={`sidebar-${label.toLowerCase().replace(/\s+/g, '-')}`}
      onClick={onClick}
      className="flex items-center gap-3 transition-all group appearance-none bg-transparent border-none p-0 text-left outline-none cursor-pointer"
      style={{
        color: active ? '#ad170c' : '#1c1c18',
        fontWeight: active ? 700 : 400,
        fontStyle: 'italic',
        textDecoration: active ? 'underline' : 'none',
        textDecorationStyle: active ? 'wavy' : undefined,
        opacity: active ? 1 : 0.6,
        fontFamily: 'Newsreader, serif',
      }}
      onMouseEnter={(e) => {
        if (!active) {
          e.currentTarget.style.transform = 'translateX(4px)';
          e.currentTarget.style.color = '#ad170c';
        }
      }}
      onMouseLeave={(e) => {
        if (!active) {
          e.currentTarget.style.transform = 'translateX(0)';
          e.currentTarget.style.color = '#1c1c18';
        }
      }}
    >
      <span className="material-symbols-outlined">{icon}</span>
      {label}
    </button>
  );
}

function SidebarBottomLink({ icon, label }) {
  const isLogout = label === 'Logout';
  const Component = isLogout ? Link : 'a';

  return (
    <Component
      href={isLogout ? "/login" : "#"}
      className="flex items-center gap-3 transition-colors"
      style={{ color: 'inherit' }}
      onMouseEnter={(e) => {
        e.currentTarget.style.color = '#ad170c';
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.color = 'inherit';
      }}
    >
      <span className="material-symbols-outlined">{icon}</span>
      {label}
    </Component>
  );
}
