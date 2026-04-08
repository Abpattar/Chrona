// ============================================================
// RedThreads.jsx — SVG connection lines between mind map nodes
// Uses a viewBox to support percentage-based coordinates
// ============================================================

'use client';

import { useMemo } from 'react';

/**
 * Convert percentage position to viewBox coordinates (0-1000 scale)
 */
function toViewBox(pos) {
  return { x: (pos?.x ?? 50) * 10, y: (pos?.y ?? 50) * 10 };
}

export default function RedThreads({ categories, tasks, mePosition = { x: 50, y: 50 } }) {
  const meViewBox = toViewBox(mePosition);

  const paths = useMemo(() => {
    const lines = [];

    // ME → each category
    categories.forEach((cat) => {
      const to = toViewBox(cat.position);
      lines.push(
        <ThreadPath key={`me-${cat.id}`} from={meViewBox} to={to} />
      );
    });

    // Each category → its tasks
    tasks.forEach((task) => {
      const cat = categories.find((c) => c.id === task.categoryId);
      if (!cat) return;
      const from = toViewBox(cat.position);
      const to = toViewBox(task.position);
      lines.push(
        <ThreadPath
          key={`${cat.id}-${task.id}`}
          from={from}
          to={to}
        />
      );
    });

    return lines;
  }, [categories, tasks, meViewBox]);

  return (
    <svg
      className="absolute inset-0 w-full h-full"
      viewBox="0 0 1000 1000"
      preserveAspectRatio="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      {paths}
    </svg>
  );
}

// ---- Single thread path with hand-drawn-like curve ----
function ThreadPath({ from, to }) {
  // Calculate a control point for a slight hand-drawn curve
  const midX = (from.x + to.x) / 2;
  const midY = (from.y + to.y) / 2;
  // Perpendicular offset for curve
  const dx = to.x - from.x;
  const dy = to.y - from.y;
  const offsetX = midX + dy * 0.08;
  const offsetY = midY - dx * 0.08;

  return (
    <path
      d={`M ${from.x} ${from.y} Q ${offsetX} ${offsetY} ${to.x} ${to.y}`}
      stroke="#DC143C"
      strokeWidth="2.5"
      fill="none"
      strokeDasharray="8 4"
      strokeLinecap="round"
      style={{ filter: 'drop-shadow(2px 2px 2px rgba(0,0,0,0.15))' }}
    />
  );
}
