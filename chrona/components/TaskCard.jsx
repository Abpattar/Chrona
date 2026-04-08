// ============================================================
// TaskCard.jsx — Task card inheriting parent category style
// ============================================================

'use client';

export default function TaskCard({ task, categoryStyle, categoryName }) {
  const rotation = task.rotation;

  return (
    <div
      id={`task-${task.id}`}
      data-no-pan="true"
      className="absolute z-20 transition-transform cursor-pointer"
      style={{
        top: `${task.position.y}%`,
        left: `${task.position.x}%`,
        transform: `translate(-50%, -50%) rotate(${rotation}deg)`,
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.transform = `translate(-50%, -50%) rotate(${rotation > 0 ? -1 : 1}deg)`;
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.transform = `translate(-50%, -50%) rotate(${rotation}deg)`;
      }}
    >
      <TaskPin />
      <TaskCardBody
        title={task.title}
        date={task.date}
        categoryName={categoryName}
        categoryStyle={categoryStyle}
      />
    </div>
  );
}

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

function TaskCardBody({ title, date, categoryName, categoryStyle }) {
  return (
    <div className={getTaskClass(categoryStyle)} style={{ width: '13rem' }}>
      <div className="font-bold text-sm uppercase tracking-tight" style={{ fontFamily: 'Space Grotesk, sans-serif' }}>
        {title}
      </div>
      <div className="italic text-xs mt-1" style={{ fontFamily: 'Newsreader, serif', color: 'rgba(28,28,24,0.7)' }}>
        {date}
      </div>
      <div
        className="mt-3 inline-block font-bold uppercase"
        style={{
          fontFamily: 'Work Sans, sans-serif',
          fontSize: '9px',
          padding: '2px 6px',
          backgroundColor: categoryStyle === 'other' ? 'rgba(173,23,12,0.2)' : 'rgba(173,23,12,0.1)',
          color: '#ad170c',
        }}
      >
        {categoryName}
      </div>
    </div>
  );
}

function getTaskClass(style) {
  const base = 'p-5';
  switch (style) {
    case 'hackathon': return `${base} paper-slip`;
    case 'exam':      return `${base} ruled-paper shadow-md bg-[#ebe8e1]`;
    case 'personal':  return `${base} punched-paper pl-10 pr-5 shadow-md bg-white`;
    case 'other':     return `${base} sticky-note torn-edge`;
    default:          return `${base} paper-slip`;
  }
}
