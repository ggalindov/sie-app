"use client";

import { useEffect, useRef } from "react";
import { gsap } from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";

gsap.registerPlugin(ScrollTrigger);

// Cortina que sube y descubre la imagen real (el edificio de la firma) al
// entrar en viewport, en vez de aparecer de golpe. Motivo: evita el cambio
// brusco de "nada -> imagen completa" y le da al primer vistazo del edificio
// el peso de un momento, no de un bloque más. Aislado de Motion.
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
    if (!wrapRef.current) return;

    const ctx = gsap.context(() => {
      const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
      if (reduce) return;

      gsap.set(curtainRef.current, { scaleY: 1, transformOrigin: "top" });
      gsap.set(imgRef.current, { scale: 1.12 });

      const tl = gsap.timeline({
        scrollTrigger: {
          trigger: wrapRef.current,
          start: "top 78%",
          once: true,
        },
      });
      tl.to(curtainRef.current, {
        scaleY: 0,
        transformOrigin: "bottom",
        duration: 1,
        ease: "power4.inOut",
      }).to(
        imgRef.current,
        { scale: 1, duration: 1.4, ease: "power3.out" },
        "-=0.7",
      );
    }, wrapRef);

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
