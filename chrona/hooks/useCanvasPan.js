// ============================================================
// useCanvasPan.js — Pan + pinch/wheel zoom for the mind map
// ============================================================

'use client';

import { useRef, useState, useCallback, useEffect } from 'react';

const MIN_ZOOM = 0.3;
const MAX_ZOOM = 2.5;

export function useCanvasPan() {
  const containerRef = useRef(null);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [isPanning, setIsPanning] = useState(false);
  const startPos = useRef({ x: 0, y: 0 });
  const startPan = useRef({ x: 0, y: 0 });

  // ---- Start panning (left-click on canvas bg) ----
  const handleMouseDown = useCallback(
    (e) => {
      if (e.button !== 0) return;
      const target = e.target;
      if (target.closest('[data-no-pan]')) return;

      setIsPanning(true);
      startPos.current = { x: e.clientX, y: e.clientY };
      startPan.current = { ...pan };
      e.preventDefault();
    },
    [pan]
  );

  // ---- Pan move / up ----
  useEffect(() => {
    if (!isPanning) return;

    const handleMouseMove = (e) => {
      const dx = e.clientX - startPos.current.x;
      const dy = e.clientY - startPos.current.y;
      setPan({ x: startPan.current.x + dx, y: startPan.current.y + dy });
    };

    const handleMouseUp = () => setIsPanning(false);

    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);

    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
  }, [isPanning]);

  // ---- Wheel zoom (zoom toward cursor) ----
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const handleWheel = (e) => {
      e.preventDefault();

      const rect = container.getBoundingClientRect();
      const mouseX = e.clientX - rect.left;
      const mouseY = e.clientY - rect.top;

      // Detect pinch gesture (ctrlKey is true for pinch-to-zoom on trackpads)
      // Map it to a more aggressive or subtle delta depending on user intent
      let delta = e.ctrlKey ? -e.deltaY * 0.01 : -e.deltaY * 0.001;
      
      // Use a multiplier concept for smoother scaling
      const zoomFactor = Math.pow(1.1, delta);

      setZoom((prevZoom) => {
        const nextZoom = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, prevZoom * zoomFactor));
        const scaleChange = nextZoom / prevZoom;

        setPan((prevPan) => ({
          x: mouseX - scaleChange * (mouseX - prevPan.x),
          y: mouseY - scaleChange * (mouseY - prevPan.y),
        }));

        return nextZoom;
      });
    };

    container.addEventListener('wheel', handleWheel, { passive: false });
    return () => container.removeEventListener('wheel', handleWheel);
  }, []);

  return { containerRef, pan, zoom, isPanning, handleMouseDown };
}
