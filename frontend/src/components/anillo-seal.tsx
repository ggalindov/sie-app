"use client";

import { useEffect, useRef, type RefObject } from "react";
import { gsap } from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";

gsap.registerPlugin(ScrollTrigger);

// Aislado en su propio archivo cliente (regla dura del proyecto: GSAP y Motion nunca en
// el mismo componente) para que AnilloEstadistica pueda seguir usando Motion para su
// propia entrada y para el conteo del número, sin mezclar ambas librerías en un mismo
// árbol de JSX.
export function AnilloSeal({ trigger }: { trigger: RefObject<HTMLDivElement | null> }) {
  const circuloRef = useRef<SVGCircleElement>(null);

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
        duration: 1.4,
        ease: "power3.inOut",
        scrollTrigger: { trigger: trigger.current, start: "top 85%" },
      });
    });
    return () => ctx.revert();
  }, [trigger]);

  return (
    <svg viewBox="0 0 100 100" aria-hidden="true" className="absolute inset-0 h-full w-full -rotate-90">
      <circle cx="50" cy="50" r="45" fill="none" stroke="var(--color-line)" strokeWidth="1.25" />
      <circle
        ref={circuloRef}
        cx="50"
        cy="50"
        r="45"
        fill="none"
        stroke="var(--color-gold)"
        strokeWidth="1.25"
        strokeLinecap="round"
      />
    </svg>
  );
}
