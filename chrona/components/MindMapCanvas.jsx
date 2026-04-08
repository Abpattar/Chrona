// ============================================================
// MindMapCanvas.jsx — Pannable + zoomable canvas
// Two-layer architecture: wires SVG renders BELOW cards layer
// ============================================================

'use client';

import { useCanvasPan } from '@/hooks/useCanvasPan';
import CoreNode from './CoreNode';
import CategoryNode from './CategoryNode';
import TaskCard from './TaskCard';
import RedThreads from './RedThreads';

export default function MindMapCanvas({
  categories,
  tasks,
  mePosition,
  onCategoryPositionChange,
  onTaskPositionChange,
  onMePositionChange,
}) {
  const { containerRef, pan, zoom, isPanning, handleMouseDown } = useCanvasPan();

  // Combined CSS transform applied to both layers so they move together
  const canvasTransform = `translate(${pan.x}px, ${pan.y}px) scale(${zoom})`;
  const canvasStyle = {
    transform: canvasTransform,
    transformOrigin: '0 0',
    width: '100%',
    height: '1000px',
    position: 'absolute',
    top: 0,
    left: 0,
  };

  return (
    <main
      id="mind-map-canvas"
      ref={containerRef}
      className="ml-64 pt-16 min-h-screen relative overflow-hidden"
      style={{ cursor: isPanning ? 'grabbing' : 'grab' }}
      onMouseDown={handleMouseDown}
    >
      {/*
        ─────────────────────────────────────────────────────
        LAYER 1 — SVG wires (rendered first = painted first)
        No z-index needed: DOM order puts this behind layer 2.
        ─────────────────────────────────────────────────────
      */}
      <div style={{ ...canvasStyle, pointerEvents: 'none' }}>
        <RedThreads categories={categories} tasks={tasks} mePosition={mePosition} />
      </div>

      {/*
        ─────────────────────────────────────────────────────
        LAYER 2 — All interactive nodes (painted on top)
        ─────────────────────────────────────────────────────
      */}
      <div style={{ ...canvasStyle, position: 'absolute' }}>
        {/* Center "ME" node */}
        <div style={{ position: 'absolute', top: `${mePosition.y}%`, left: `${mePosition.x}%`, transform: 'translate(-50%, -50%)' }}>
          <CoreNode 
            position={mePosition} 
            onPositionChange={onMePositionChange} 
            containerRef={containerRef} 
            zoom={zoom} 
          />
        </div>

        {/* Category nodes */}
        {categories.map((cat) => (
          <CategoryNode
            key={cat.id}
            category={cat}
            onPositionChange={onCategoryPositionChange}
            containerRef={containerRef}
            zoom={zoom}
          />
        ))}

        {/* Task cards */}
        {tasks.map((task) => {
          const parentCat = categories.find((c) => c.id === task.categoryId);
          if (!parentCat) return null;
          return (
            <TaskCard
              key={task.id}
              task={task}
              categoryStyle={parentCat.style}
              categoryName={parentCat.name}
              onPositionChange={onTaskPositionChange}
              containerRef={containerRef}
              zoom={zoom}
            />
          );
        })}
      </div>

      {/* Zoom indicator */}
      <ZoomIndicator zoom={zoom} />
    </main>
  );
}

// ---- Small zoom % badge in bottom-right ----
function ZoomIndicator({ zoom }) {
  return (
    <div
      style={{
        position: 'fixed',
        bottom: '1.5rem',
        right: '5rem',
        background: 'rgba(252,249,242,0.85)',
        border: '1px solid rgba(173,23,12,0.2)',
        backdropFilter: 'blur(4px)',
        padding: '4px 10px',
        fontFamily: 'Work Sans, sans-serif',
        fontSize: '11px',
        fontWeight: '600',
        color: '#ad170c',
        letterSpacing: '0.05em',
        pointerEvents: 'none',
        userSelect: 'none',
      }}
    >
      {Math.round(zoom * 100)}%
    </div>
  );
}
