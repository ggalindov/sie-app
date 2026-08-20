"use client";

import { useEffect, useRef } from "react";
import { gsap } from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { motion, useInView, useMotionValue } from "motion/react";
import { animate } from "motion";

gsap.registerPlugin(ScrollTrigger);

// Reemplaza las tres barras oscuras apiladas (idénticas entre sí, el patrón de
// "stat card" más visto en cualquier plantilla genérica) por un sello que se traza
// solo al entrar en pantalla, como el que ya usa el logo del Hero (SealRing) pero
// aislado en su propio componente para poder disparar cada uno con su propio
// ScrollTrigger en vez de un delay fijo al montar. El número sigue contando con
// Motion (igual que antes); el trazo del anillo es GSAP puro, nunca en el mismo
// bloque de JSX que anima con Motion (regla dura del proyecto).
function Anillo({ trigger }: { trigger: React.RefObject<HTMLDivElement | null> }) {
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

function Contador({ numero, prefijo, sufijo }: { numero: number; prefijo: string; sufijo: string }) {
  const ref = useRef<HTMLSpanElement>(null);
  const enVista = useInView(ref, { once: true, amount: 0.6 });
  const valor = useMotionValue(0);

  useEffect(() => {
    if (!enVista) return;
    const controles = animate(valor, numero, {
      duration: 1.6,
      delay: 0.3,
      ease: [0.16, 1, 0.3, 1],
      onUpdate: (v) => {
        if (ref.current) ref.current.textContent = `${prefijo}${Math.round(v)}${sufijo}`;
      },
    });
    return () => controles.stop();
  }, [enVista, numero, prefijo, sufijo, valor]);

  return (
    <span ref={ref}>
      {prefijo}0{sufijo}
    </span>
  );
}

export function AnilloEstadistica({
  numero,
  prefijo = "",
  sufijo = "",
  etiqueta,
  delay = 0,
}: {
  numero: number;
  prefijo?: string;
  sufijo?: string;
  etiqueta: string;
  delay?: number;
}) {
  const contenedorRef = useRef<HTMLDivElement>(null);

  return (
    <motion.div
      ref={contenedorRef}
      initial={{ opacity: 0, y: 16 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, amount: 0.5 }}
      transition={{ duration: 0.6, delay, ease: [0.16, 1, 0.3, 1] }}
      className="flex flex-col items-center text-center"
    >
      <div className="relative flex h-[5.5rem] w-[5.5rem] shrink-0 items-center justify-center sm:h-24 sm:w-24">
        <Anillo trigger={contenedorRef} />
        <p className="relative font-display text-xl leading-none text-ink sm:text-2xl">
          <Contador numero={numero} prefijo={prefijo} sufijo={sufijo} />
        </p>
      </div>
      <p className="mt-3 max-w-[6.5rem] text-[11px] font-medium uppercase leading-tight tracking-[0.08em] text-ink-soft">
        {etiqueta}
      </p>
    </motion.div>
  );
}
