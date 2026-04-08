// ============================================================
// useCanvasPan.js — Custom hook for mouse-drag panning
// ============================================================

'use client';

import { useRef, useState, useCallback, useEffect } from 'react';

export function useCanvasPan() {
  const containerRef = useRef(null);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [isPanning, setIsPanning] = useState(false);
  const startPos = useRef({ x: 0, y: 0 });
  const startPan = useRef({ x: 0, y: 0 });

  // ---- Start dragging ----
  const handleMouseDown = useCallback(
    (e) => {
      if (e.button !== 0) return;
      const target = e.target;
      // Don't pan if clicking on a card or interactive element
      if (target.closest('[data-no-pan]')) return;

      setIsPanning(true);
      startPos.current = { x: e.clientX, y: e.clientY };
      startPan.current = { ...pan };
      e.preventDefault();
    },
    [pan]
  );

  // ---- Move while dragging ----
  useEffect(() => {
    if (!isPanning) return;

    const handleMouseMove = (e) => {
      const dx = e.clientX - startPos.current.x;
      const dy = e.clientY - startPos.current.y;
      setPan({
        x: startPan.current.x + dx,
        y: startPan.current.y + dy,
      });
    };

    const handleMouseUp = () => {
      setIsPanning(false);
    };

    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);

    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
  }, [isPanning]);

  return { containerRef, pan, isPanning, handleMouseDown };
}
