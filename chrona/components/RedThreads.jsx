// ============================================================
// RedThreads.jsx — SVG threads connecting from pin/edge of cards
// Threads render BEHIND cards (z-0) so card bodies mask overlaps.
// Lines start/end at the pin (top-edge) of each card, not center.
// ============================================================

'use client';

import { useMemo } from 'react';

// Approximate card dimensions in % of the 1000-unit viewBox
const CARD_HALF_W = { me: 70, category: 55, task: 65 };
const CARD_HALF_H = { me: 55, category: 30, task: 50 };

/**
 * Given a center position plus card half-size, compute the edge point
 * along the direction toward `target`. This ensures the thread starts
 * at the card border, not at its center.
 */
function edgePoint(center, halfW, halfH, target) {
  const dx = target.x - center.x;
  const dy = target.y - center.y;
  if (dx === 0 && dy === 0) return center;

  // Scale factor to reach the card edge (simplified ellipse intersection)
  const sx = halfW / Math.abs(dx || 1);
  const sy = halfH / Math.abs(dy || 1);
  const s = Math.min(sx, sy, 1);

  return {
    x: center.x + dx * s,
    y: center.y + dy * s,
  };
}

function toVB(pos) {
  return { x: pos.x * 10, y: pos.y * 10 };
}

export default function RedThreads({ categories, tasks }) {
  const ME = { x: 500, y: 500 };

  const paths = useMemo(() => {
    const lines = [];

    // ME → each category
    categories.forEach((cat) => {
      const catVB = toVB(cat.position);
      const from = edgePoint(ME, CARD_HALF_W.me, CARD_HALF_H.me, catVB);
      const to = edgePoint(catVB, CARD_HALF_W.category, CARD_HALF_H.category, ME);
      lines.push(<ThreadPath key={`me-${cat.id}`} from={from} to={to} />);
    });

    // Category → its tasks
    tasks.forEach((task) => {
      const cat = categories.find((c) => c.id === task.categoryId);
      if (!cat) return;
      const catVB = toVB(cat.position);
      const taskVB = toVB(task.position);
      const from = edgePoint(catVB, CARD_HALF_W.category, CARD_HALF_H.category, taskVB);
      const to = edgePoint(taskVB, CARD_HALF_W.task, CARD_HALF_H.task, catVB);
      lines.push(
        <ThreadPath key={`${cat.id}-${task.id}`} from={from} to={to} />
      );
    });

    return lines;
  }, [categories, tasks]);

  return (
    <svg
      className="absolute inset-0 w-full h-full pointer-events-none"
      style={{ zIndex: 5 }}
      viewBox="0 0 1000 1000"
      preserveAspectRatio="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      {paths}
    </svg>
  );
}

function ThreadPath({ from, to }) {
  const midX = (from.x + to.x) / 2;
  const midY = (from.y + to.y) / 2;
  const dx = to.x - from.x;
  const dy = to.y - from.y;
  // Slight perpendicular offset for hand-drawn feel
  const offsetX = midX + dy * 0.06;
  const offsetY = midY - dx * 0.06;

  return (
    <path
      d={`M ${from.x} ${from.y} Q ${offsetX} ${offsetY} ${to.x} ${to.y}`}
      stroke="#DC143C"
      strokeWidth="2.5"
      fill="none"
      strokeDasharray="8 4"
      strokeLinecap="round"
      style={{ filter: 'drop-shadow(1px 1px 1px rgba(0,0,0,0.1))' }}
    />
  );
}
