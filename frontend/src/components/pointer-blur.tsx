"use client";

import { useEffect, useRef } from "react";

// Efecto de puntero: un punto dorado pequeño y nítido (ya no un halo grande difuso) que
// sigue el cursor casi sin retraso:
// - Puntito compacto de 10px con un blur mínimo (apenas suaviza el borde, no lo difumina).
// - Reacciona con un pop rápido al posarse sobre elementos interactivos (botones, enlaces).
// - Cero bordes, cero aros: un punto de luz concentrado, no una nube.
// - Rendimiento de 60-120fps en GPU con translate3d (sin re-renders de React).
// - Deshabilitado en móviles / pantallas táctiles.
export function PointerBlur() {
  const containerRef = useRef<HTMLDivElement>(null);
  const ambientRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (typeof window === "undefined") return;

    // Solo habilitar en dispositivos con mouse real y soporte de hover
    const tieneMouse = window.matchMedia("(hover: hover) and (pointer: fine)").matches;
    if (!tieneMouse) return;

    const container = containerRef.current;
    const ambient = ambientRef.current;
    if (!container || !ambient) return;

    let targetX = -300;
    let targetY = -300;
    let ambX = -300;
    let ambY = -300;

    let targetScale = 1;
    let currentScale = 1;

    let targetOpacity = 0;
    let currentOpacity = 0;
    let isHoveringInteractive = false;
    let animId: number;

    // Más altos que antes (0.16/0.15/0.15): un puntito pequeño necesita seguir el cursor
    // casi 1:1 para no sentirse "atrasado" -- esa inercia funcionaba en un halo grande y
    // difuso, no en un punto nítido, donde el ojo nota el desfase de inmediato.
    const LERP_AMB = 0.35;
    const LERP_SCALE = 0.28;
    const LERP_OPACITY = 0.22;

    function onPointerMove(e: PointerEvent) {
      targetX = e.clientX;
      targetY = e.clientY;
      targetOpacity = 1;

      const target = e.target as HTMLElement | null;
      const esInteractivo = Boolean(
        target?.closest(
          "a, button, input, textarea, select, [role='button'], [role='tab'], .cta-boton, .area-row, [tabindex='0'], .cursor-pointer, label[for], [data-state], tr[onClick]"
        )
      );

      if (esInteractivo !== isHoveringInteractive) {
        isHoveringInteractive = esInteractivo;
        // Pop más marcado que antes (1.14): un punto de 10px necesita crecer bastante más,
        // en proporción, para que el feedback de "esto es interactivo" siga siendo legible.
        targetScale = esInteractivo ? 2.2 : 1;
      }
    }

    function onPointerDown() {
      targetScale = isHoveringInteractive ? 1.6 : 0.6;
    }

    function onPointerUp() {
      targetScale = isHoveringInteractive ? 2.2 : 1;
    }

    function onMouseLeave() {
      targetOpacity = 0;
    }

    function onMouseEnter() {
      targetOpacity = 1;
    }

    function tick() {
      ambX += (targetX - ambX) * LERP_AMB;
      ambY += (targetY - ambY) * LERP_AMB;
      currentScale += (targetScale - currentScale) * LERP_SCALE;
      currentOpacity += (targetOpacity - currentOpacity) * LERP_OPACITY;

      if (container) {
        container.style.opacity = currentOpacity.toFixed(3);
      }

      if (ambient) {
        ambient.style.transform = `translate3d(${ambX}px, ${ambY}px, 0) translate(-50%, -50%) scale(${currentScale.toFixed(3)})`;
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
      className="pointer-events-none fixed inset-0 z-[99999] overflow-hidden select-none"
    >
      {/* Puntito dorado compacto: antes un halo de 76px con blur(9px), ahora un punto de
          10px con blur(2px) -- apenas lo justo para suavizar el borde duro de un círculo
          sólido, sin volver a convertirlo en una nube difusa. */}
      <div
        ref={ambientRef}
        className="pointer-events-none absolute left-0 top-0 h-[10px] w-[10px] rounded-full will-change-transform"
        style={{
          background:
            "radial-gradient(circle at center, rgba(217,169,37,0.95) 0%, rgba(217,169,37,0.85) 55%, rgba(217,169,37,0.5) 78%, transparent 100%)",
          filter: "blur(2px)",
          transform: "translate3d(-400px, -400px, 0)",
        }}
      />
    </div>
  );
}