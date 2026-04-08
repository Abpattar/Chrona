// ============================================================
// AddItemModal.jsx — Modal to add a new task or category
// ============================================================

'use client';

import { useState } from 'react';

const NEW_CATEGORY_VALUE = '__new__';

export default function AddItemModal({ categories, onClose, onAddTask, onAddCategory }) {
  const [title, setTitle] = useState('');
  const [date, setDate] = useState('');
  const [selectedCategory, setSelectedCategory] = useState(
    categories.length > 0 ? categories[0].id : NEW_CATEGORY_VALUE
  );
  const [newCategoryName, setNewCategoryName] = useState('');

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!title.trim()) return;

    const formattedDate = date
      ? new Date(date).toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' })
      : 'TBD';

    if (selectedCategory === NEW_CATEGORY_VALUE) {
      if (!newCategoryName.trim()) return;
      // Create category first, then task
      const newCatId = onAddCategory(newCategoryName.trim().toUpperCase());
      onAddTask(title.trim().toUpperCase(), formattedDate, newCatId);
    } else {
      onAddTask(title.trim().toUpperCase(), formattedDate, selectedCategory);
    }
    onClose();
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-card" onClick={(e) => e.stopPropagation()} data-no-pan="true">
        {/* Pin decoration */}
        <div className="absolute -top-4 left-1/2 -translate-x-1/2 z-40 drop-shadow-md">
          <span
            className="material-symbols-outlined text-3xl"
            style={{ color: '#ad170c', fontVariationSettings: "'FILL' 1" }}
          >
            push_pin
          </span>
        </div>

        {/* Title */}
        <h2
          className="text-xl font-bold uppercase tracking-widest mb-6"
          style={{ fontFamily: 'Space Grotesk, sans-serif', color: '#ad170c' }}
        >
          Pin a New Goal
        </h2>

        <form onSubmit={handleSubmit} className="flex flex-col gap-5">
          {/* Task title */}
          <div>
            <label
              className="text-xs uppercase tracking-wider mb-1 block"
              style={{ fontFamily: 'Work Sans, sans-serif', color: '#916f6e' }}
            >
              Title
            </label>
            <input
              className="form-input-underline"
              placeholder="e.g. Build the landing page..."
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              autoFocus
            />
          </div>

          {/* Date */}
          <div>
            <label
              className="text-xs uppercase tracking-wider mb-1 block"
              style={{ fontFamily: 'Work Sans, sans-serif', color: '#916f6e' }}
            >
              Date (optional)
            </label>
            <input
              type="datetime-local"
              className="form-input-underline"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              style={{ fontFamily: 'Work Sans, sans-serif', fontSize: '0.875rem' }}
            />
          </div>

          {/* Category select */}
          <div>
            <label
              className="text-xs uppercase tracking-wider mb-1 block"
              style={{ fontFamily: 'Work Sans, sans-serif', color: '#916f6e' }}
            >
              Category
            </label>
            <select
              className="form-select"
              value={selectedCategory}
              onChange={(e) => setSelectedCategory(e.target.value)}
            >
              {categories.map((cat) => (
                <option key={cat.id} value={cat.id}>
                  {cat.name}
                </option>
              ))}
              <option value={NEW_CATEGORY_VALUE}>+ Create New Category</option>
            </select>
          </div>

          {/* New category name (conditional) */}
          {selectedCategory === NEW_CATEGORY_VALUE && (
            <div>
              <label
                className="text-xs uppercase tracking-wider mb-1 block"
                style={{ fontFamily: 'Work Sans, sans-serif', color: '#916f6e' }}
              >
                New Category Name
              </label>
              <input
                className="form-input-underline"
                placeholder="e.g. PROJECTS"
                value={newCategoryName}
                onChange={(e) => setNewCategoryName(e.target.value)}
              />
              <p
                className="text-xs italic mt-1"
                style={{ fontFamily: 'Newsreader, serif', color: '#916f6e' }}
              >
                A random page style will be assigned. You can change it later.
              </p>
            </div>
          )}

          {/* Actions */}
          <div className="flex gap-3 mt-4">
            <button
              type="submit"
              className="uppercase px-6 py-3 text-sm tracking-widest active:scale-95 transition-transform flex-1"
              style={{
                fontFamily: 'Space Grotesk, sans-serif',
                backgroundColor: '#ad170c',
                color: '#ffffff',
                border: 'none',
                cursor: 'pointer',
              }}
            >
              Pin It
            </button>
            <button
              type="button"
              onClick={onClose}
              className="uppercase px-6 py-3 text-sm tracking-widest active:scale-95 transition-transform"
              style={{
                fontFamily: 'Space Grotesk, sans-serif',
                backgroundColor: 'transparent',
                color: '#1c1c18',
                border: '1px solid rgba(28,28,24,0.15)',
                cursor: 'pointer',
              }}
            >
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
