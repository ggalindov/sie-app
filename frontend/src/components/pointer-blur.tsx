"use client";

import { useEffect, useRef } from "react";

// Puntero de alta gama (Luxury Pointer):
// - Micro-punto áureo de precisión instantánea.
// - Anillo exterior magnético con inercia elástica (lerp) que se expande al posar sobre enlaces y botones.
// - Halo ambiental dorado translúcido que ilumina sutilmente los fondos oscuros.
// - Rendimiento óptimo de 60-120fps garantizado por GPU (translate3d, cero re-renders de React).
// - Totalmente deshabilitado en dispositivos táctiles / móviles (cero consumo de batería o CPU).
export function PointerBlur() {
  const containerRef = useRef<HTMLDivElement>(null);
  const dotRef = useRef<HTMLDivElement>(null);
  const ringRef = useRef<HTMLDivElement>(null);
  const ambientRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (typeof window === "undefined") return;

    // Solo habilitar en dispositivos con mouse real y soporte de hover
    const tieneMouse = window.matchMedia("(hover: hover) and (pointer: fine)").matches;
    if (!tieneMouse) return;

    const container = containerRef.current;
    const dot = dotRef.current;
    const ring = ringRef.current;
    const ambient = ambientRef.current;
    if (!container || !dot || !ring || !ambient) return;

    let targetX = -200;
    let targetY = -200;

    let dotX = -200;
    let dotY = -200;

    let ringX = -200;
    let ringY = -200;

    let ambX = -200;
    let ambY = -200;

    let targetRingScale = 1;
    let currentRingScale = 1;

    let targetDotScale = 1;
    let currentDotScale = 1;

    let targetOpacity = 0;
    let currentOpacity = 0;
    let isHoveringInteractive = false;
    let animId: number;

    const LERP_DOT = 0.55;
    const LERP_RING = 0.16;
    const LERP_AMB = 0.08;
    const LERP_SCALE = 0.16;
    const LERP_OPACITY = 0.12;

    function onPointerMove(e: PointerEvent) {
      targetX = e.clientX;
      targetY = e.clientY;
      targetOpacity = 1;

      const target = e.target as HTMLElement | null;
      const esInteractivo = Boolean(
        target?.closest(
          "a, button, input, textarea, select, [role='button'], .cta-boton, .area-row, [tabindex='0']"
        )
      );

      if (esInteractivo !== isHoveringInteractive) {
        isHoveringInteractive = esInteractivo;
        if (esInteractivo) {
          targetRingScale = 1.6;
          targetDotScale = 0.5;
        } else {
          targetRingScale = 1;
          targetDotScale = 1;
        }
      }
    }

    function onPointerDown() {
      targetRingScale = isHoveringInteractive ? 1.3 : 0.8;
      targetDotScale = 1.3;
    }

    function onPointerUp() {
      targetRingScale = isHoveringInteractive ? 1.6 : 1;
      targetDotScale = isHoveringInteractive ? 0.5 : 1;
    }

    function onMouseLeave() {
      targetOpacity = 0;
    }

    function onMouseEnter() {
      targetOpacity = 1;
    }

    function tick() {
      // Inercias físicas suaves gestionadas en el hilo del compositor GPU
      dotX += (targetX - dotX) * LERP_DOT;
      dotY += (targetY - dotY) * LERP_DOT;

      ringX += (targetX - ringX) * LERP_RING;
      ringY += (targetY - ringY) * LERP_RING;

      ambX += (targetX - ambX) * LERP_AMB;
      ambY += (targetY - ambY) * LERP_AMB;

      currentRingScale += (targetRingScale - currentRingScale) * LERP_SCALE;
      currentDotScale += (targetDotScale - currentDotScale) * LERP_SCALE;
      currentOpacity += (targetOpacity - currentOpacity) * LERP_OPACITY;

      if (container) {
        container.style.opacity = currentOpacity.toFixed(3);
      }

      if (dot) {
        dot.style.transform = `translate3d(${dotX}px, ${dotY}px, 0) translate(-50%, -50%) scale(${currentDotScale.toFixed(3)})`;
      }

      if (ring) {
        ring.style.transform = `translate3d(${ringX}px, ${ringY}px, 0) translate(-50%, -50%) scale(${currentRingScale.toFixed(3)})`;
      }

      if (ambient) {
        ambient.style.transform = `translate3d(${ambX}px, ${ambY}px, 0) translate(-50%, -50%)`;
      }

      animId = requestAnimationFrame(tick);
    }

    window.addEventListener("pointermove", onPointerMove, { passive: true });
    window.addEventListener("pointerdown", onPointerDown, { passive: true });
    window.addEventListener("pointerup", onPointerUp, { passive: true });
    document.documentElement.addEventListener("mouseleave", onMouseLeave);
    document.documentElement.addEventListener("mouseenter", onMouseEnter);

    animId = requestAnimationFrame(tick);

    return () => {
      cancelAnimationFrame(animId);
      window.removeEventListener("pointermove", onPointerMove);
      window.removeEventListener("pointerdown", onPointerDown);
      window.removeEventListener("pointerup", onPointerUp);
      document.documentElement.removeEventListener("mouseleave", onMouseLeave);
      document.documentElement.removeEventListener("mouseenter", onMouseEnter);
    };
  }, []);

  return (
    <div
      ref={containerRef}
      aria-hidden="true"
      style={{ opacity: 0 }}
      className="pointer-events-none fixed inset-0 z-50 overflow-hidden select-none"
    >
      {/* Halo ambiental dorado cálido de fondo */}
      <div
        ref={ambientRef}
        className="pointer-events-none absolute left-0 top-0 h-[260px] w-[260px] rounded-full will-change-transform"
        style={{
          background:
            "radial-gradient(circle at center, rgba(217,169,37,0.12) 0%, rgba(217,169,37,0.035) 45%, transparent 70%)",
          filter: "blur(24px)",
          transform: "translate3d(-400px, -400px, 0)",
        }}
      />

      {/* Anillo magnético exterior que reacciona a los elementos interactivos */}
      <div
        ref={ringRef}
        className="pointer-events-none absolute left-0 top-0 h-[34px] w-[34px] rounded-full border border-gold/65 bg-gold/[0.04] shadow-[0_0_18px_rgba(217,169,37,0.35)] backdrop-blur-[1px] will-change-transform transition-[border-color,background-color] duration-200"
        style={{
          transform: "translate3d(-400px, -400px, 0)",
        }}
      />

      {/* Micro-punto áureo de alta precisión */}
      <div
        ref={dotRef}
        className="pointer-events-none absolute left-0 top-0 h-[6px] w-[6px] rounded-full bg-[#f6d77c] shadow-[0_0_8px_rgba(217,169,37,0.95),0_0_2px_#ffffff] will-change-transform"
        style={{
          transform: "translate3d(-400px, -400px, 0)",
        }}
      />
    </div>
  );
}