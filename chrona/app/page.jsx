// ============================================================
// page.jsx — Main page: assembles all mind map components
// ============================================================

'use client';

import { useState } from 'react';
import { defaultCategories, defaultTasks } from '@/lib/data';
import PaperGrain from '@/components/PaperGrain';
import Header from '@/components/Header';
import Sidebar from '@/components/Sidebar';
import MindMapCanvas from '@/components/MindMapCanvas';
import FloatingActionButton from '@/components/FloatingActionButton';

export default function Home() {
  // ---- State: categories & tasks ----
  const [categories] = useState(defaultCategories);
  const [tasks] = useState(defaultTasks);

  // ---- Handlers ----
  const handleAddItem = () => {
    console.log('Add new item clicked');
  };

  const handleNewGoal = () => {
    console.log('New goal FAB clicked');
  };

  return (
    <>
      {/* Paper texture overlay */}
      <PaperGrain />

      {/* Fixed header */}
      <Header />

      {/* Fixed sidebar */}
      <Sidebar onAddItem={handleAddItem} />

      {/* Pannable mind map canvas */}
      <MindMapCanvas categories={categories} tasks={tasks} />

      {/* Floating action button */}
      <FloatingActionButton onClick={handleNewGoal} />
    </>
  );
}
