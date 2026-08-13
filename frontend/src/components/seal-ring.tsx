"use client";

import { useEffect, useRef } from "react";
import { gsap } from "gsap";

// Anillo que se traza a sí mismo alrededor del logo, como un sello oficial
// cerrándose. Refuerza la idea de "certificado, respaldado" en el primer
// instante del sitio. Aislado de Motion (regla dura del proyecto: GSAP y
// Motion nunca en el mismo componente), se anima una sola vez al montar.
export function SealRing({ delay = 0.6 }: { delay?: number }) {
  const outerRef = useRef<SVGCircleElement>(null);
  const innerRef = useRef<SVGCircleElement>(null);
  const groupRef = useRef<SVGGElement>(null);

  useEffect(() => {
    if (!outerRef.current || !innerRef.current) return;

    const ctx = gsap.context(() => {
      const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

      if (reduce) {
        gsap.set([outerRef.current, innerRef.current], { strokeDashoffset: 0 });
        gsap.set(groupRef.current, { opacity: 1, rotate: 0 });
        return;
      }

      const outerLength = outerRef.current!.getTotalLength();
      const innerLength = innerRef.current!.getTotalLength();
      gsap.set(outerRef.current, { strokeDasharray: outerLength, strokeDashoffset: outerLength });
      gsap.set(innerRef.current, { strokeDasharray: innerLength, strokeDashoffset: innerLength });
      gsap.set(groupRef.current, { opacity: 0, rotate: -14, transformOrigin: "50% 50%" });

      const tl = gsap.timeline({ delay });
      tl.to(groupRef.current, { opacity: 1, duration: 0.3, ease: "power1.out" })
        .to(
          outerRef.current,
          { strokeDashoffset: 0, duration: 1.1, ease: "power3.inOut" },
          "<",
        )
        .to(groupRef.current, { rotate: 0, duration: 1.3, ease: "power3.out" }, "<")
        .to(
          innerRef.current,
          { strokeDashoffset: 0, duration: 0.8, ease: "power2.inOut" },
          "-=0.55",
        );
    });

    return () => ctx.revert();
  }, [delay]);

  return (
    <svg
      viewBox="0 0 200 200"
      aria-hidden="true"
      className="pointer-events-none absolute inset-0 h-full w-full"
    >
      <g ref={groupRef}>
        <circle
          ref={outerRef}
          cx="100"
          cy="100"
          r="92"
          fill="none"
          stroke="var(--color-gold)"
          strokeWidth="0.75"
          strokeLinecap="round"
        />
        <circle
          ref={innerRef}
          cx="100"
          cy="100"
          r="82"
          fill="none"
          stroke="var(--color-gold)"
          strokeOpacity="0.5"
          strokeWidth="0.5"
          strokeLinecap="round"
        />
      </g>
    </svg>
  );
}
