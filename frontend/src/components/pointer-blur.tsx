"use client";

import { useEffect, useRef } from "react";

// Efecto de puntero con halo ambiental dorado translúcido (solo blur, sin bordes ni aros):
// - Halo de resplandor áureo que sigue el cursor suavemente con inercia física (lerp).
// - Reacciona suavemente al posarse sobre elementos interactivos (botones, enlaces).
// - Cero bordes, cero aros ni puntos rígidos: únicamente luz difusa cinematográfica.
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

    const LERP_AMB = 0.16;
    const LERP_SCALE = 0.15;
    const LERP_OPACITY = 0.15;

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
        targetScale = esInteractivo ? 1.14 : 1;
      }
    }

    function onPointerDown() {
      targetScale = isHoveringInteractive ? 1.05 : 0.90;
    }

    function onPointerUp() {
      targetScale = isHoveringInteractive ? 1.14 : 1;
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
      {/* Halo ambiental dorado translúcido comprimido y contenido */}
      <div
        ref={ambientRef}
        className="pointer-events-none absolute left-0 top-0 h-[76px] w-[76px] rounded-full will-change-transform"
        style={{
          background:
            "radial-gradient(circle at center, rgba(217,169,37,0.36) 0%, rgba(217,169,37,0.15) 32%, rgba(217,169,37,0.02) 58%, transparent 72%)",
          filter: "blur(9px)",
          transform: "translate3d(-400px, -400px, 0)",
        }}
      />
    </div>
  );
}