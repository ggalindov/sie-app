"use client";

import { useEffect, useRef } from "react";
import { motion, useInView, useMotionValue } from "motion/react";
import { animate } from "motion";
import { AnilloSeal } from "@/components/anillo-seal";

// Reemplaza las tres barras oscuras apiladas (idénticas entre sí, el patrón de
// "stat card" más visto en cualquier plantilla genérica) por un sello que se traza
// solo al entrar en pantalla, como el que ya usa el logo del Hero (SealRing). El trazo
// del anillo vive aislado en anillo-seal.tsx (GSAP puro, con su propio ScrollTrigger
// por instancia); aquí solo se compone como hijo, nunca en el mismo bloque de JSX que
// anima con Motion (regla dura del proyecto: GSAP y Motion nunca en el mismo
// componente). El número sigue contando con Motion.
function Contador({ numero, prefijo, sufijo }: { numero: number; prefijo: string; sufijo: string }) {
  const ref = useRef<HTMLSpanElement>(null);
  const enVista = useInView(ref, { once: true, amount: 0.6 });
  const valor = useMotionValue(0);

  useEffect(() => {
    if (!enVista) return;
    const controles = animate(valor, numero, {
      duration: 2.6,
      delay: 0.3,
      ease: [0.22, 0.68, 0, 1],
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
      <div className="relative flex h-24 w-24 shrink-0 items-center justify-center sm:h-28 sm:w-28">
        <div
          aria-hidden="true"
          className="absolute inset-[6px] rounded-full opacity-70"
          style={{ background: "radial-gradient(circle, var(--color-gold-pale) 0%, transparent 72%)" }}
        />
        <AnilloSeal trigger={contenedorRef} />
        <p className="relative font-display text-2xl leading-none text-ink sm:text-[1.75rem]">
          <Contador numero={numero} prefijo={prefijo} sufijo={sufijo} />
        </p>
      </div>
      <p className="mt-3.5 max-w-[7rem] text-[11px] font-medium uppercase leading-tight tracking-[0.1em] text-ink-soft">
        {etiqueta}
      </p>
    </motion.div>
  );
}
