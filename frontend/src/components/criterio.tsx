"use client";

import { useRef, type ReactNode } from "react";
import { motion, useScroll, useTransform } from "motion/react";

const EASE = [0.16, 1, 0.3, 1] as const;

// Momento editorial de la home: video real de la firma como fondo (antes una
// foto de stock de una biblioteca genérica), sin ningún objeto 3D flotando
// encima (el martillo se retiró a pedido explícito del usuario, "pierde la
// elegancia del sitio"). El fondo se mueve levemente con el scroll
// (parallax con Motion, no GSAP) para que la sección no se sienta como una
// imagen estática pegada detrás del texto.
export function Criterio({ media }: { media: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  const { scrollYProgress } = useScroll({
    target: ref,
    offset: ["start end", "end start"],
  });
  const y = useTransform(scrollYProgress, [0, 1], ["-8%", "8%"]);

  return (
    <section ref={ref} className="snap-slide relative overflow-hidden py-28 md:py-40">
      <motion.div style={{ y }} className="absolute inset-[-8%]">
        {media}
      </motion.div>
      <div className="absolute inset-0 bg-gradient-to-b from-night/90 via-night/80 to-night/90" />
      <div
        aria-hidden="true"
        className="absolute inset-0"
        style={{
          backgroundImage:
            "radial-gradient(60% 50% at 50% 45%, rgba(217,169,37,0.14), transparent 70%)",
        }}
      />

      <div className="relative mx-auto max-w-5xl px-6 text-center">
        <motion.h2
          initial={{ opacity: 0, y: 28 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, amount: 0.4 }}
          transition={{ duration: 0.9, ease: EASE }}
          className="text-balance leading-[1.04] text-night-ink"
        >
          <span className="font-display text-2xl font-light italic tracking-wide text-night-ink/80 sm:text-3xl md:text-4xl lg:text-5xl block">
            Cada caso se resuelve con el mismo criterio:
          </span>

          <span className="mt-4 sm:mt-6 flex flex-wrap items-baseline justify-center gap-x-4 gap-y-2">
            <span className="font-display text-5xl sm:text-7xl md:text-8xl lg:text-9xl font-medium tracking-tight text-gold drop-shadow-[0_8px_36px_rgba(217,169,37,0.45)]">
              veinte años
            </span>
            <span className="font-display text-4xl sm:text-6xl md:text-7xl lg:text-8xl font-normal tracking-tight text-night-ink">
              de experiencia.
            </span>
          </span>
        </motion.h2>

        {/* Detalle imperial clásico de rigor legal */}
        <motion.div
          initial={{ opacity: 0, scale: 0.9 }}
          whileInView={{ opacity: 1, scale: 1 }}
          viewport={{ once: true, amount: 0.4 }}
          transition={{ duration: 0.8, delay: 0.12, ease: EASE }}
          className="my-7 sm:my-10 flex max-w-full items-center justify-center gap-2 sm:gap-4 overflow-hidden"
        >
          <div className="h-px w-10 sm:w-28 md:w-32 shrink bg-gradient-to-r from-transparent via-gold/60 to-gold/20" />
          <span className="shrink-0 text-[10px] sm:text-xs font-serif uppercase tracking-[0.16em] sm:tracking-[0.25em] text-gold/90 select-none">
            Jurisprudencia · Precedentes · Rigor
          </span>
          <div className="h-px w-10 sm:w-28 md:w-32 shrink bg-gradient-to-l from-transparent via-gold/60 to-gold/20" />
        </motion.div>

        <motion.p
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, amount: 0.4 }}
          transition={{ duration: 0.8, delay: 0.2, ease: EASE }}
          className="mx-auto max-w-2xl text-balance text-lg font-light leading-relaxed text-night-ink/85 sm:text-xl md:text-2xl"
        >
          No improvisamos. Cada estrategia se construye sobre precedentes
          reales y el seguimiento constante de un equipo que conoce el
          sistema.
        </motion.p>
      </div>
    </section>
  );
}
