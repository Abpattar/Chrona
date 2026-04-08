// ============================================================
// TaskCard.jsx — Individual task card styled per category
// Supports drag-to-place via onPositionChange callback
// ============================================================

'use client';

import { useDragNode } from '@/hooks/useDragNode';

export default function TaskCard({ task, categoryStyle, categoryName, onPositionChange, containerRef, zoom }) {
  const rotation = task.rotation;

  const { handleMouseDown } = useDragNode(
    task.id,
    task.position,
    onPositionChange,
    containerRef,
    zoom
  );

  return (
    <div
      id={`task-${task.id}`}
      data-no-pan="true"
      className="absolute z-20 select-none"
      style={{
        top: `${task.position.y}%`,
        left: `${task.position.x}%`,
        transform: `translate(-50%, -50%) rotate(${rotation}deg)`,
        cursor: 'grab',
        transition: 'transform 0.15s ease',
      }}
      onMouseDown={handleMouseDown}
      onMouseEnter={(e) => {
        if (!e.buttons) {
          e.currentTarget.style.transform = `translate(-50%, -50%) rotate(${rotation > 0 ? -1 : 1}deg)`;
        }
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.transform = `translate(-50%, -50%) rotate(${rotation}deg)`;
      }}
    >
      {/* Push Pin */}
      <TaskPin />

      {/* Card — styled by parent category */}
      <TaskCardBody
        title={task.title}
        date={task.date}
        categoryName={categoryName}
        categoryStyle={categoryStyle}
      />
    </div>
  );
}

// ---- Pin ----
function TaskPin() {
  return (
    <div className="absolute -top-3 left-1/2 -translate-x-1/2 z-40">
      <span
        className="material-symbols-outlined"
        style={{ color: '#ad170c', fontVariationSettings: "'FILL' 1" }}
      >
        push_pin
      </span>
    </div>
  );
}

// ---- Card body ----
function TaskCardBody({ title, date, categoryName, categoryStyle }) {
  const cardClass = getTaskCardClass(categoryStyle);

  return (
    <div className={cardClass} style={{ width: '13rem' }}>
      {/* Title */}
      <div
        className="font-bold text-sm uppercase tracking-tight"
        style={{ fontFamily: 'Space Grotesk, sans-serif' }}
      >
        {title}
      </div>

      {/* Date */}
      <div
        className="italic text-xs mt-1"
        style={{ fontFamily: 'Newsreader, serif', color: 'rgba(28,28,24,0.7)' }}
      >
        {date}
      </div>

      {/* Category Badge */}
      <CategoryBadge name={categoryName} style={categoryStyle} />
    </div>
  );
}

// ---- Category badge ----
function CategoryBadge({ name, style }) {
  const isOther = style === 'other';
  return (
    <div
      className="mt-3 inline-block font-bold uppercase"
      style={{
        fontFamily: 'Work Sans, sans-serif',
        fontSize: '9px',
        padding: '2px 6px',
        backgroundColor: isOther ? 'rgba(173,23,12,0.2)' : 'rgba(173,23,12,0.1)',
        color: '#ad170c',
      }}
    >
      {name}
    </div>
  );
}

// ---- Map category style → task card CSS classes ----
function getTaskCardClass(style) {
  const base = 'p-5';

  switch (style) {
    case 'hackathon':
      return `${base} paper-slip`;
    case 'exam':
      return `${base} ruled-paper shadow-md`;
    case 'personal':
      return `${base} punched-paper pl-10 pr-5 shadow-md`;
    case 'other':
      return `${base} sticky-note torn-edge`;
    default:
      return `${base} paper-slip`;
  }
}
