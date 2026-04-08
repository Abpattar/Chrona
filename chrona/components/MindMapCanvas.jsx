// ============================================================
// MindMapCanvas.jsx — Pannable canvas with all map nodes
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
      style={{ cursor: isPanning ? 'grabbing' : 'grab' }}
      onMouseDown={handleMouseDown}
    >
      <div
        className="relative w-full h-[1000px]"
        style={{
          transform: `translate(${pan.x}px, ${pan.y}px)`,
          transition: isPanning ? 'none' : 'transform 0.15s ease-out',
        }}
      >
        {/* Red threads — z-5, BELOW cards at z-20 */}
        <RedThreads categories={categories} tasks={tasks} />

        {/* ME node — center */}
        <div className="absolute top-[50%] left-[50%] -translate-x-1/2 -translate-y-1/2">
          <CoreNode />
        </div>

        {/* Categories */}
        {categories.map((cat) => (
          <CategoryNode key={cat.id} category={cat} />
        ))}

        {/* Tasks */}
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
