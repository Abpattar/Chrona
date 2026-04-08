// ============================================================
// CoreNode.jsx — Central "ME" node (opaque background)
// ============================================================

'use client';

export default function CoreNode() {
  return (
    <div
      id="core-node-me"
      data-no-pan="true"
      className="relative z-20 transition-transform cursor-move"
      style={{ transform: 'rotate(1deg)' }}
      onMouseEnter={(e) => { e.currentTarget.style.transform = 'rotate(0deg)'; }}
      onMouseLeave={(e) => { e.currentTarget.style.transform = 'rotate(1deg)'; }}
    >
      <PushPin size="text-4xl" />
      <div
        className="p-10 flex flex-col items-center"
        style={{
          backgroundColor: '#ffffff',
          boxShadow: '10px 15px 40px rgba(28,28,24,0.1)',
          borderBottom: '4px solid rgba(173,23,12,0.2)',
        }}
      >
        <div
          className="text-7xl uppercase tracking-widest"
          style={{ fontFamily: 'Permanent Marker, cursive', color: '#1c1c18' }}
        >
          ME
        </div>
      </div>
    </div>
  );
}

function PushPin({ size = 'text-2xl' }) {
  return (
    <div className="absolute -top-4 left-1/2 -translate-x-1/2 z-40 drop-shadow-md">
      <span
        className={`material-symbols-outlined ${size}`}
        style={{ color: '#ad170c', fontVariationSettings: "'FILL' 1" }}
      >
        push_pin
      </span>
    </div>
  );
}
