// ============================================================
// page.jsx — Main page: assembles all mind map components
// ============================================================

'use client';

import { useState, useCallback } from 'react';
import { defaultCategories, defaultTasks } from '@/lib/data';
import PaperGrain from '@/components/PaperGrain';
import Header from '@/components/Header';
import Sidebar from '@/components/Sidebar';
import MindMapCanvas from '@/components/MindMapCanvas';
import FloatingActionButton from '@/components/FloatingActionButton';
import AddItemModal from '@/components/AddItemModal';
import UpcomingView from '@/components/UpcomingView';

export default function Home() {
  // ---- View state: controls what the user sees ----
  const [currentView, setCurrentView] = useState('Priority Tasks'); // or 'Upcoming'

  // ---- Mutable state for positions (enables drag-to-place + live wires) ----
  const [categories, setCategories] = useState(defaultCategories);
  const [tasks, setTasks] = useState(defaultTasks);
  const [mePosition, setMePosition] = useState({ x: 50, y: 50 });
  const [isModalOpen, setIsModalOpen] = useState(false);

  const handleMePositionChange = useCallback((_, newPos) => {
    setMePosition(newPos);
  }, []);

  // ---- Update a single category's position ----
  const handleCategoryPositionChange = useCallback((id, newPos) => {
    setCategories((prev) =>
      prev.map((cat) => (cat.id === id ? { ...cat, position: newPos } : cat))
    );
  }, []);

  // ---- Update a single task's position ----
  const handleTaskPositionChange = useCallback((id, newPos) => {
    setTasks((prev) =>
      prev.map((task) => (task.id === id ? { ...task, position: newPos } : task))
    );
  }, []);

  // ---- Handlers ----
  const handleAddItem = () => setIsModalOpen(true);
  const handleNewGoal = () => setIsModalOpen(true);
  
  const handleProcessEvent = (description) => {
    console.log('Processing event:', description);
    // Future logic: AI extraction or adding a new task/category
  };

  return (
    <>
      {/* Paper texture overlay */}
      <PaperGrain />

      {/* Fixed header */}
      <Header />

      {/* Fixed sidebar */}
      <Sidebar 
        onAddItem={handleAddItem} 
        activeItem={currentView}
        onNavigate={setCurrentView}
      />

      {/* Main Content: Conditionally rendered views */}
      {currentView === 'Upcoming' ? (
        <UpcomingView />
      ) : (
        <MindMapCanvas
          categories={categories}
          tasks={tasks}
          mePosition={mePosition}
          onCategoryPositionChange={handleCategoryPositionChange}
          onTaskPositionChange={handleTaskPositionChange}
          onMePositionChange={handleMePositionChange}
        />
      )}

      {/* Floating action button */}
      <FloatingActionButton onClick={handleNewGoal} />

      {/* Add Item Modal Overlay */}
      <AddItemModal 
        isOpen={isModalOpen} 
        onClose={() => setIsModalOpen(false)} 
        onProcess={handleProcessEvent}
      />
    </>
  );
}
