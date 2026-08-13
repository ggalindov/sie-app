"use client";

import Image from "next/image";
import { motion } from "motion/react";
import { empresasConfianza } from "@/lib/content";

const EASE = [0.16, 1, 0.3, 1] as const;

const cifras = [
  { valor: "20+", etiqueta: "Años de trayectoria" },
  { valor: "800+", etiqueta: "Casos ganados" },
  { valor: "7", etiqueta: "Empresas aliadas" },
];

// único marquee de la página (regla: máximo uno por página)
export function TrustedBy() {
  const logos = [...empresasConfianza, ...empresasConfianza];

  return (
    <section className="section-seam bg-surface/90 py-24 md:py-28">
      <div className="mx-auto max-w-7xl px-6">
        <div className="grid gap-10 md:grid-cols-12 md:items-end">
          <motion.div
            initial={{ opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.4 }}
            transition={{ duration: 0.7, ease: EASE }}
            className="md:col-span-7"
          >
            <h2 className="font-display text-3xl leading-tight tracking-tight md:text-4xl">
              La confianza de empresas que ya nos eligieron.
            </h2>
            <p className="mt-3 max-w-md text-base leading-relaxed text-ink-soft">
              Compañías de distintos sectores confían en SIE Jurídicos para
              respaldar sus decisiones legales, año tras año.
            </p>
          </motion.div>

          <div className="grid grid-cols-3 gap-6 md:col-span-5">
            {cifras.map((c, i) => (
              <motion.div
                key={c.etiqueta}
                initial={{ opacity: 0, y: 24 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, amount: 0.4 }}
                transition={{ duration: 0.6, delay: 0.08 * i, ease: EASE }}
                className="text-center md:text-right"
              >
                <p className="font-display text-3xl text-gold-deep md:text-4xl">{c.valor}</p>
                <p className="mt-1 text-xs leading-snug text-ink-soft">{c.etiqueta}</p>
              </motion.div>
            ))}
          </div>
        </div>
      </div>

      <div className="mt-14 overflow-hidden [mask-image:linear-gradient(to_right,transparent,black_8%,black_92%,transparent)]">
        <div className="flex w-max animate-marquee items-center gap-8 hover:[animation-play-state:paused]">
          {logos.map((empresa, i) => (
            <div
              key={`${empresa.src}-${i}`}
              className="flex h-28 w-56 shrink-0 items-center justify-center rounded-3xl bg-white p-6 shadow-[0_20px_45px_-20px_rgba(0,0,0,0.45)] ring-1 ring-black/5 transition-transform duration-300 hover:scale-105"
            >
              <Image
                src={empresa.src}
                alt={empresa.alt}
                width={160}
                height={80}
                className="h-full w-full object-contain"
              />
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
