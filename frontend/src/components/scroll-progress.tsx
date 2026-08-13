"use client";

import { useEffect, useRef } from "react";
import { gsap } from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";

gsap.registerPlugin(ScrollTrigger);

// Indicador visual de scroll: una línea dorada fija arriba de todo que se
// llena de 0 a 100% con el avance real de la página, aislado de Motion
// (usa GSAP + ScrollTrigger, nunca en el mismo componente que anima Motion).
export function ScrollProgress() {
  const barRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!barRef.current) return;
    const ctx = gsap.context(() => {
      gsap.set(barRef.current, { scaleX: 0 });
      ScrollTrigger.create({
        start: 0,
        end: () => document.documentElement.scrollHeight - window.innerHeight,
        scrub: 0.3,
        onUpdate: (self) => {
          gsap.set(barRef.current, { scaleX: self.progress });
        },
      });
    });
    return () => ctx.revert();
  }, []);

  return (
    <div
      aria-hidden="true"
      className="fixed inset-x-0 top-0 z-50 h-[3px] bg-line/30"
    >
      <div
        ref={barRef}
        className="h-full w-full origin-left"
        style={{
          backgroundImage:
            "linear-gradient(90deg, var(--color-gold-deep), var(--color-gold), var(--color-gold-pale))",
        }}
      />
    </div>
  );
}
