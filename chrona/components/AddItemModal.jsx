// ============================================================
// AddItemModal.jsx — Modal to add new tasks/events
// Reimagined in Analog style with Post-it and Clipboard motifs
// ============================================================

'use client';

import { useEffect, useState } from 'react';

export default function AddItemModal({ isOpen, onClose, onProcess }) {
  const [description, setDescription] = useState('');

  // Close on Escape
  useEffect(() => {
    const handleEsc = (e) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleEsc);
    return () => window.removeEventListener('keydown', handleEsc);
  }, [onClose]);

  if (!isOpen) return null;

  return (
    <div 
      className="fixed inset-0 z-[100] flex items-center justify-center p-6 animate-in fade-in duration-300"
      style={{ backgroundColor: 'rgba(28,28,24,0.3)', backdropFilter: 'blur(4px)' }}
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      {/* Modal Container: Post-it / Sticky Note style */}
      <div 
        className="relative w-full max-w-lg animate-in slide-in-from-bottom-8 duration-500"
        style={{ transform: 'rotate(0.5deg)' }}
      >
        {/* The "Paper" */}
        <div 
          className="paper-slip shadow-2xl overflow-hidden bg-white"
          style={{ minHeight: '500px' }}
        >
          {/* Subtle horizontal lines background */}
          <div className="absolute inset-0 ruled-paper opacity-20 pointer-events-none" />

          {/* Close Button: A small "X" that looks hand-drawn */}
          <button 
            onClick={onClose}
            className="absolute top-4 right-4 z-20 material-symbols-outlined text-gray-400 hover:text-primary transition-colors"
          >
            close
          </button>

          {/* Header */}
          <header className="px-10 pt-10 pb-6 relative z-10">
            <h2 
              className="text-4xl text-primary mb-2" 
              style={{ fontFamily: 'Permanent Marker, cursive' }}
            >
              Add Event
            </h2>
            <p 
              className="text-sm italic opacity-70"
              style={{ fontFamily: 'Newsreader, serif' }}
            >
              Paste text, drop a PDF, or upload an image to extract event data.
            </p>
          </header>

          {/* Form Content */}
          <div className="px-10 pb-10 space-y-8 relative z-10 flex flex-col h-full">
            
            {/* Description Section */}
            <div className="flex flex-col gap-2">
              <label 
                className="text-[10px] font-bold uppercase tracking-[0.2em] opacity-40 px-1"
                style={{ fontFamily: 'Work Sans, sans-serif' }}
              >
                Description
              </label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder='e.g. "Math exam tomorrow 4 PM"'
                className="w-full h-32 bg-surface-container rounded-sm p-4 focus:ring-2 focus:ring-primary/20 outline-none transition-all resize-none italic"
                style={{ 
                  fontFamily: 'Newsreader, serif', 
                  fontSize: '1.2rem',
                  border: '1px solid rgba(0,0,0,0.05)',
                  boxShadow: 'inset 0 2px 4px rgba(0,0,0,0.02)'
                }}
              />
            </div>

            {/* File Upload / Dropzone */}
            <div className="flex flex-col gap-2">
              <label 
                className="text-[10px] font-bold uppercase tracking-[0.2em] opacity-40 px-1"
                style={{ fontFamily: 'Work Sans, sans-serif' }}
              >
                File Upload
              </label>
              <div 
                className="group border-2 border-dashed border-black/10 rounded-sm p-10 flex flex-col items-center justify-center gap-3 hover:border-primary/30 transition-colors cursor-pointer"
                style={{ backgroundColor: 'rgba(28,28,24,0.02)' }}
              >
                <div 
                  className="w-12 h-12 rounded-full border border-black/5 flex items-center justify-center group-hover:bg-primary group-hover:text-white transition-all shadow-sm"
                  style={{ backgroundColor: '#fff' }}
                >
                  <span className="material-symbols-outlined text-center leading-none">upload</span>
                </div>
                <div className="text-center font-bold uppercase text-[9px] tracking-widest opacity-60">
                  Drop <span className="text-primary">PDF</span> or <span className="text-primary">Image</span> here
                  <div className="font-normal normal-case italic mt-1 text-xs opacity-60">
                    or click to browse
                  </div>
                </div>
              </div>
            </div>

            {/* Action Button */}
            <button
              onClick={() => { onProcess(description); onClose(); }}
              className="w-full bg-primary hover:bg-primary-container text-white py-5 font-bold uppercase tracking-[0.3em] transition-all shadow-lg active:translate-y-0.5 active:shadow-md mt-auto"
              style={{ fontFamily: 'Space Grotesk, sans-serif' }}
            >
              Process Event
            </button>
          </div>
        </div>

        {/* Decorative Tape effect at the top */}
        <div 
          className="absolute -top-3 left-1/4 w-1/2 h-8 bg-white/40 backdrop-blur-md opacity-50 z-20 pointer-events-none"
          style={{ 
            clipPath: 'polygon(0% 0%, 100% 10%, 95% 90%, 5% 100%)',
            borderLeft: '1px solid rgba(0,0,0,0.05)',
            borderRight: '1px solid rgba(0,0,0,0.05)'
          }}
        />
      </div>
    </div>
  );
}
