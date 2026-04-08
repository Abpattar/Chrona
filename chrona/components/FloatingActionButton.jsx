// ============================================================
// FloatingActionButton.jsx — Bottom-right FAB for new goals
// ============================================================

'use client';

export default function FloatingActionButton({ onClick }) {
  return (
    <button
      id="fab-new-goal"
      onClick={onClick}
      className="fixed bottom-8 right-8 z-50 flex items-center gap-2 group active:scale-95 transition-all"
      style={{
        backgroundColor: '#ad170c',
        color: '#ffffff',
        padding: '16px',
        boxShadow: '0 10px 25px rgba(173,23,12,0.3)',
        border: 'none',
        cursor: 'pointer',
      }}
    >
      <span className="material-symbols-outlined">add</span>
      <span
        className="uppercase text-sm tracking-widest hidden group-hover:inline"
        style={{ fontFamily: 'Space Grotesk, sans-serif' }}
      >
        New Goal
      </span>
    </button>
  );
}
