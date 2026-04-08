// ============================================================
// CoreNode.jsx — The central "ME" node
// ============================================================

import { useDragNode } from '@/hooks/useDragNode';

export default function CoreNode({ position, onPositionChange, containerRef, zoom }) {
  const { handleMouseDown } = useDragNode(
    'me',
    position,
    onPositionChange,
    containerRef,
    zoom
  );

  return (
    <div
      id="core-node-me"
      data-no-pan="true"
      className="relative z-30 select-none"
      style={{ transform: 'rotate(1deg)', cursor: 'grab' }}
      onMouseDown={handleMouseDown}
      onMouseEnter={(e) => {
        if (!e.buttons) {
          e.currentTarget.style.transform = 'rotate(0deg)';
        }
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.transform = 'rotate(1deg)';
      }}
    >
      {/* Push Pin */}
      <PushPin size="text-4xl" />

      {/* Card */}
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

// ---- Push Pin Icon ----
function PushPin({ size = 'text-2xl' }) {
  return (
    <div className="absolute -top-4 left-1/2 -translate-x-1/2 z-40 drop-shadow-md">
      <span
        className={`material-symbols-outlined ${size}`}
        style={{
          color: '#ad170c',
          fontVariationSettings: "'FILL' 1",
        }}
      >
        push_pin
      </span>
    </div>
  );
}
