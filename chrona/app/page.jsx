// ============================================================
// page.jsx — Main page: state management, view switching,
//            add-item logic, and component assembly
// ============================================================

'use client';

import { useState, useCallback } from 'react';
import { defaultCategories, defaultTasks } from '@/lib/data';
import { generateId, randomStyle } from '@/lib/types';
import PaperGrain from '@/components/PaperGrain';
import Header from '@/components/Header';
import Sidebar from '@/components/Sidebar';
import MindMapCanvas from '@/components/MindMapCanvas';
import FloatingActionButton from '@/components/FloatingActionButton';
import AddItemModal from '@/components/AddItemModal';
import CalendarView from '@/components/CalendarView';

export default function Home() {
  // ---- Core state ----
  const [categories, setCategories] = useState(defaultCategories);
  const [tasks, setTasks] = useState(defaultTasks);
  const [showModal, setShowModal] = useState(false);
  const [activeView, setActiveView] = useState('Today');

  // ---- Add a new category ----
  const handleAddCategory = useCallback((name) => {
    const newCatId = generateId();
    const style = randomStyle();

    // Find a free position near the ME center
    // Spread new categories in a ring around center
    const angle = (categories.length * 72 + 45) * (Math.PI / 180);
    const radius = 22;
    const x = Math.round(50 + radius * Math.cos(angle));
    const y = Math.round(50 + radius * Math.sin(angle));
    const rotation = Math.round(Math.random() * 6 - 3);

    const newCategory = {
      id: newCatId,
      name: name.toUpperCase(),
      style,
      position: { x: Math.max(10, Math.min(90, x)), y: Math.max(15, Math.min(85, y)) },
      rotation,
    };

    setCategories((prev) => [...prev, newCategory]);
    return newCatId;
  }, [categories.length]);

  // ---- Add a new task ----
  const handleAddTask = useCallback((title, date, categoryId) => {
    const taskId = generateId();

    // Position near the parent category with some random scatter
    const parentCat = categories.find((c) => c.id === categoryId);
    let baseX = 50, baseY = 50;
    if (parentCat) {
      // Offset from the category in a random direction
      const angle = Math.random() * Math.PI * 2;
      const dist = 15 + Math.random() * 10;
      baseX = parentCat.position.x + dist * Math.cos(angle);
      baseY = parentCat.position.y + dist * Math.sin(angle);
    }

    const rotation = Math.round(Math.random() * 8 - 4);

    const newTask = {
      id: taskId,
      categoryId,
      title: title.toUpperCase(),
      date,
      position: {
        x: Math.max(5, Math.min(95, Math.round(baseX))),
        y: Math.max(5, Math.min(95, Math.round(baseY))),
      },
      rotation,
    };

    setTasks((prev) => [...prev, newTask]);
  }, [categories]);

  // ---- Handlers ----
  const openModal = () => setShowModal(true);
  const closeModal = () => setShowModal(false);

  return (
    <>
      <PaperGrain />
      <Header activeView={activeView} onViewChange={setActiveView} />
      <Sidebar onAddItem={openModal} />

      {/* View switcher */}
      {activeView === 'Today' && (
        <MindMapCanvas categories={categories} tasks={tasks} />
      )}
      {activeView === 'Calendar' && (
        <CalendarView tasks={tasks} categories={categories} />
      )}
      {activeView === 'Habits' && (
        <PlaceholderView title="Habits" icon="self_improvement" description="Track your daily habits and streaks here." />
      )}
      {activeView === 'Journal' && (
        <PlaceholderView title="Journal" icon="edit_note" description="Write your daily reflections and notes." />
      )}

      <FloatingActionButton onClick={openModal} />

      {/* Add Item Modal */}
      {showModal && (
        <AddItemModal
          categories={categories}
          onClose={closeModal}
          onAddTask={handleAddTask}
          onAddCategory={handleAddCategory}
        />
      )}
    </>
  );
}

// ---- Placeholder for Habits / Journal views ----
function PlaceholderView({ title, icon, description }) {
  return (
    <div className="ml-64 pt-16 min-h-screen flex items-center justify-center">
      <div className="text-center p-12" style={{ maxWidth: '400px' }}>
        <span
          className="material-symbols-outlined mb-4 block"
          style={{ fontSize: '64px', color: '#e6bdbc' }}
        >
          {icon}
        </span>
        <h2
          className="text-2xl font-bold uppercase tracking-widest mb-3"
          style={{ fontFamily: 'Space Grotesk, sans-serif', color: '#1c1c18' }}
        >
          {title}
        </h2>
        <p className="italic text-lg" style={{ fontFamily: 'Newsreader, serif', color: '#916f6e' }}>
          {description}
        </p>
        <p
          className="mt-4 text-xs uppercase tracking-wider"
          style={{ fontFamily: 'Work Sans, sans-serif', color: '#e6bdbc' }}
        >
          Coming Soon
        </p>
      </div>
    </div>
  );
}
