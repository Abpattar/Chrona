'use client';

import { useRef, useState, useCallback, useEffect } from 'react';

export function useCanvasPan() {
  const containerRef = useRef(null);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [isPanning, setIsPanning] = useState(false);
  const startPos = useRef({ x: 0, y: 0 });
  const startPan = useRef({ x: 0, y: 0 });

  const handleMouseDown = useCallback(
    (e) => {
      if (e.button !== 0) return;
      if (e.target.closest('[data-no-pan]')) return;
      setIsPanning(true);
      startPos.current = { x: e.clientX, y: e.clientY };
      startPan.current = { ...pan };
      e.preventDefault();
    },
    [pan]
  );

  useEffect(() => {
    if (!isPanning) return;
    const handleMouseMove = (e) => {
      setPan({
        x: startPan.current.x + (e.clientX - startPos.current.x),
        y: startPan.current.y + (e.clientY - startPos.current.y),
      });
    };
    const handleMouseUp = () => setIsPanning(false);
    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);
    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
  }, [isPanning]);

  return { containerRef, pan, isPanning, handleMouseDown };
}
