// ============================================================
// useDragNode.js — Drag-to-place hook for mind map nodes
// Accounts for current canvas zoom when computing movement
// ============================================================

'use client';

import { useRef, useCallback } from 'react';

/**
 * @param {string} id
 * @param {{ x: number, y: number }} position  - current % position
 * @param {(id, newPos) => void} onPositionChange
 * @param {React.RefObject} containerRef  - outer canvas container
 * @param {number} zoom - current canvas zoom
 */
export function useDragNode(id, position, onPositionChange, containerRef, zoom = 1) {
  const dragging = useRef(false);
  const startMouse = useRef({ x: 0, y: 0 });
  const startPos = useRef({ x: 0, y: 0 });

  const handleMouseDown = useCallback(
    (e) => {
      if (e.button !== 0) return;
      // Stop canvas pan from firing
      e.stopPropagation();

      dragging.current = true;
      startMouse.current = { x: e.clientX, y: e.clientY };
      startPos.current = { ...position };

      const handleMouseMove = (ev) => {
        if (!dragging.current) return;

        const container = containerRef?.current;
        const rect = container
          ? container.getBoundingClientRect()
          : { width: window.innerWidth, height: window.innerHeight };

        // Divide pixel delta by zoom so movement is correct regardless of scale
        const dx = ((ev.clientX - startMouse.current.x) / (rect.width * zoom)) * 100;
        const dy = ((ev.clientY - startMouse.current.y) / (rect.height * zoom)) * 100;

        const newX = Math.max(0, Math.min(100, startPos.current.x + dx));
        const newY = Math.max(0, Math.min(100, startPos.current.y + dy));

        onPositionChange(id, { x: newX, y: newY });
      };

      const handleMouseUp = () => {
        dragging.current = false;
        window.removeEventListener('mousemove', handleMouseMove);
        window.removeEventListener('mouseup', handleMouseUp);
      };

      window.addEventListener('mousemove', handleMouseMove);
      window.addEventListener('mouseup', handleMouseUp);
    },
    [id, position, onPositionChange, containerRef, zoom]
  );

  return { handleMouseDown };
}
