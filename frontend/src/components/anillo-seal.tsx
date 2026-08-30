"use client";

import { useEffect, useId, useRef, type RefObject } from "react";
import { gsap } from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";

gsap.registerPlugin(ScrollTrigger);

// Aislado en su propio archivo cliente (regla dura del proyecto: GSAP y Motion nunca en
// el mismo componente) para que AnilloEstadistica pueda seguir usando Motion para su
// propia entrada y para el conteo del número, sin mezclar ambas librerías en un mismo
// árbol de JSX.
export function AnilloSeal({ trigger }: { trigger: RefObject<HTMLDivElement | null> }) {
  const circuloRef = useRef<SVGCircleElement>(null);
  const brilloRef = useRef<SVGCircleElement>(null);
  const idBase = useId();
  const gradientId = `anillo-gradiente-${idBase}`;
  const glowId = `anillo-glow-${idBase}`;

  useEffect(() => {
    if (!circuloRef.current || !trigger.current) return;
    const ctx = gsap.context(() => {
      const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
      const largo = circuloRef.current!.getTotalLength();

      if (reduce) {
        gsap.set(circuloRef.current, { strokeDashoffset: 0 });
        return;
      }

      gsap.set(circuloRef.current, { strokeDasharray: largo, strokeDashoffset: largo });
      gsap.to(circuloRef.current, {
        strokeDashoffset: 0,
        duration: 1.8,
        ease: "power3.inOut",
        scrollTrigger: { trigger: trigger.current, start: "top 85%" },
        // Una vez trazado el anillo, un resplandor muy suave y continuo lo
        // mantiene "vivo" en vez de quedar como un dibujo terminado y estático.
        onComplete: () => {
          if (!brilloRef.current || reduce) return;
          gsap.to(brilloRef.current, {
            opacity: 0.9,
            duration: 1.8,
            repeat: -1,
            yoyo: true,
            ease: "sine.inOut",
          });
        },
      });
    });
    return () => ctx.revert();
  }, [trigger]);

  return (
    <svg viewBox="0 0 100 100" aria-hidden="true" className="absolute inset-0 h-full w-full -rotate-90">
      <defs>
        <linearGradient id={gradientId} x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="var(--color-gold-deep)" />
          <stop offset="55%" stopColor="var(--color-gold)" />
          <stop offset="100%" stopColor="var(--color-gold-pale)" />
        </linearGradient>
        <filter id={glowId} x="-60%" y="-60%" width="220%" height="220%">
          <feGaussianBlur stdDeviation="2.4" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>
      <circle cx="50" cy="50" r="45" fill="none" stroke="var(--color-line)" strokeWidth="1.5" />
      <circle
        ref={circuloRef}
        cx="50"
        cy="50"
        r="45"
        fill="none"
        stroke={`url(#${gradientId})`}
        strokeWidth="1.75"
        strokeLinecap="round"
      />
      {/* trazo gemelo, invisible hasta que el anterior termina de dibujarse: el
          resplandor continuo vive aquí para no interferir con el
          strokeDashoffset del trazo principal. */}
      <circle
        ref={brilloRef}
        cx="50"
        cy="50"
        r="45"
        fill="none"
        stroke="var(--color-gold-pale)"
        strokeWidth="1.75"
        strokeLinecap="round"
        opacity={0}
        filter={`url(#${glowId})`}
      />
    </svg>
  );
}
