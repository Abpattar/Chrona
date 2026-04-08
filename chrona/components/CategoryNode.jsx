// ============================================================
// CategoryNode.jsx — Category label with style-specific visuals
// ============================================================

'use client';

export default function CategoryNode({ category }) {
  const rotation = category.rotation;

  return (
    <div
      id={`category-${category.id}`}
      data-no-pan="true"
      className="absolute z-20 transition-transform"
      style={{
        top: `${category.position.y}%`,
        left: `${category.position.x}%`,
        transform: `translate(-50%, -50%) rotate(${rotation}deg)`,
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.transform = 'translate(-50%, -50%) rotate(0deg)';
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.transform = `translate(-50%, -50%) rotate(${rotation}deg)`;
      }}
    >
      <CategoryPin />
      <div className={getCategoryClass(category.style)}>
        <div
          className="font-bold text-xl uppercase tracking-widest"
          style={{ fontFamily: 'Space Grotesk, sans-serif', color: '#ad170c' }}
        >
          {category.name}
        </div>
      </div>
    </div>
  );
}

function CategoryPin() {
  return (
    <div className="absolute -top-4 left-1/2 -translate-x-1/2 z-40 drop-shadow-sm">
      <span
        className="material-symbols-outlined text-2xl"
        style={{ color: '#ad170c', fontVariationSettings: "'FILL' 1" }}
      >
        push_pin
      </span>
    </div>
  );
}

function getCategoryClass(style) {
  const base = 'px-8 py-5 min-w-[140px] text-center';
  switch (style) {
    case 'hackathon': return `${base} sticky-note torn-edge`;
    case 'exam':      return `${base} ruled-paper shadow-lg bg-[#ebe8e1]`;
    case 'personal':  return `${base} punched-paper pl-10 pr-6 py-6 shadow-lg bg-white`;
    case 'other':     return `${base} sticky-note`;
    default:          return `${base} bg-white shadow-md`;
  }
}
