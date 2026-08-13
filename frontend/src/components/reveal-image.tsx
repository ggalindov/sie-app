"use client";

import { useEffect, useRef } from "react";
import { gsap } from "gsap";

// Cortina que sube y descubre la imagen real (el edificio de la firma) al
// entrar en viewport, en vez de aparecer de golpe. Motivo: evita el cambio
// brusco de "nada -> imagen completa" y le da al primer vistazo del edificio
// el peso de un momento, no de un bloque más. Aislado de Motion.
//
// Usa IntersectionObserver en vez de GSAP ScrollTrigger para decidir CUÁNDO
// disparar: ScrollTrigger calcula posiciones de scroll una sola vez al
// montar, y si el layout cambia después (fuentes, imágenes cercanas
// cargando tarde), el trigger puede quedar mal calculado y no disparar
// nunca. IntersectionObserver no depende de esa matemática, solo mira si el
// elemento está en pantalla.
export function RevealImage({
  children,
  className = "",
}: {
  children: React.ReactNode;
  className?: string;
}) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const curtainRef = useRef<HTMLDivElement>(null);
  const imgRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const wrap = wrapRef.current;
    const curtain = curtainRef.current;
    const img = imgRef.current;
    if (!wrap || !curtain || !img) return;

    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    // sin reducción de movimiento: revela de una vez, sin cortina ni zoom
    if (reduce) {
      gsap.set(curtain, { scaleY: 0 });
      gsap.set(img, { scale: 1 });
      return;
    }

    const ctx = gsap.context(() => {
      gsap.set(curtain, { scaleY: 1, transformOrigin: "top" });
      gsap.set(img, { scale: 1.12 });

      const observer = new IntersectionObserver(
        (entries) => {
          if (!entries[0].isIntersecting) return;
          observer.disconnect();

          const tl = gsap.timeline();
          tl.to(curtain, {
            scaleY: 0,
            transformOrigin: "bottom",
            duration: 1,
            ease: "power4.inOut",
          }).to(img, { scale: 1, duration: 1.4, ease: "power3.out" }, "-=0.7");
        },
        { threshold: 0.25 },
      );
      observer.observe(wrap);

      return () => observer.disconnect();
    }, wrap);

    return () => ctx.revert();
  }, []);

  return (
    <div ref={wrapRef} className={`relative overflow-hidden ${className}`}>
      <div ref={imgRef} className="h-full w-full">
        {children}
      </div>
      <div
        ref={curtainRef}
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 bg-night"
      />
    </div>
  );
}
