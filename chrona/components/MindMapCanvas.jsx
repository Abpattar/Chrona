// ============================================================
// MindMapCanvas.jsx — Pannable canvas for the mind map
// ============================================================

'use client';

import { useCanvasPan } from '@/hooks/useCanvasPan';
import CoreNode from './CoreNode';
import CategoryNode from './CategoryNode';
import TaskCard from './TaskCard';
import RedThreads from './RedThreads';

export default function MindMapCanvas({ categories, tasks }) {
  const { containerRef, pan, isPanning, handleMouseDown } = useCanvasPan();

  return (
    <main
      id="mind-map-canvas"
      ref={containerRef}
      className="ml-64 pt-16 min-h-screen relative overflow-hidden"
      style={{
        cursor: isPanning ? 'grabbing' : 'grab',
      }}
      onMouseDown={handleMouseDown}
    >
      {/* Inner container that moves with pan */}
      <div
        className="relative w-full h-[1000px]"
        style={{
          transform: `translate(${pan.x}px, ${pan.y}px)`,
          transition: isPanning ? 'none' : 'transform 0.15s ease-out',
        }}
      >
        {/* SVG Red Threads Layer */}
        <RedThreads categories={categories} tasks={tasks} />

        {/* Center: "ME" Node */}
        <div className="absolute top-[50%] left-[50%] -translate-x-1/2 -translate-y-1/2">
          <CoreNode />
        </div>

        {/* Category Nodes */}
        {categories.map((cat) => (
          <CategoryNode key={cat.id} category={cat} />
        ))}

        {/* Task Cards */}
        {tasks.map((task) => {
          const parentCat = categories.find((c) => c.id === task.categoryId);
          if (!parentCat) return null;
          return (
            <TaskCard
              key={task.id}
              task={task}
              categoryStyle={parentCat.style}
              categoryName={parentCat.name}
            />
          );
        })}
      </div>
    </main>
  );
}
