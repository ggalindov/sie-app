"use client";

import Image from "next/image";
import { motion } from "motion/react";
import { equipo } from "@/lib/content";

const EASE = [0.16, 1, 0.3, 1] as const;

// desvanece el óvalo gris de estudio de las fotos hacia transparente, para
// que se sientan "sin fondo" en vez de recortes rectangulares de un ID
const mascara = {
  maskImage:
    "radial-gradient(circle at 50% 42%, black 52%, rgba(0,0,0,0.65) 68%, transparent 88%)",
  WebkitMaskImage:
    "radial-gradient(circle at 50% 42%, black 52%, rgba(0,0,0,0.65) 68%, transparent 88%)",
};

export function Equipo() {
  return (
    <section id="equipo" className="snap-slide section-seam py-24 md:py-32">
      <div className="mx-auto max-w-7xl px-6">
        <motion.h2
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, amount: 0.4 }}
          transition={{ duration: 0.7, ease: EASE }}
          className="max-w-lg font-display text-3xl leading-tight tracking-tight md:text-4xl"
        >
          Nuestro equipo
        </motion.h2>

        <div className="mt-14 grid grid-cols-2 gap-x-6 gap-y-16 sm:grid-cols-3 lg:grid-cols-4">
          {equipo.map((persona, i) => (
            <motion.div
              key={persona.nombre}
              initial={{ opacity: 0, y: 24 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, amount: 0.3 }}
              transition={{ duration: 0.6, delay: 0.05 * i, ease: EASE }}
              className={`group ${i % 2 === 1 ? "sm:mt-8" : ""}`}
            >
              <motion.div
                animate={{ y: [0, -8, 0] }}
                transition={{ duration: 4.5 + (i % 3) * 0.4, repeat: Infinity, ease: "easeInOut" }}
                className="relative mx-auto aspect-square w-full max-w-[220px]"
              >
                <div
                  aria-hidden="true"
                  className="absolute -inset-2 rounded-full"
                  style={{
                    backgroundImage:
                      "conic-gradient(from 0deg, var(--color-gold-deep), var(--color-gold), var(--color-gold-deep))",
                  }}
                />
                <div className="absolute inset-0 rounded-full bg-paper" />
                <div className="absolute inset-[6px] overflow-hidden rounded-full">
                  <Image
                    src={persona.foto}
                    alt={persona.nombre}
                    fill
                    sizes="(min-width: 1024px) 220px, (min-width: 640px) 30vw, 45vw"
                    style={{
                      ...mascara,
                      objectPosition: persona.posicion ?? "50% 32%",
                      ["--foto-zoom" as string]: persona.zoom ?? 1,
                    }}
                    className="equipo-foto object-cover"
                  />
                </div>
              </motion.div>

              <div className="mt-5 text-center">
                <p className="font-display text-base leading-snug">{persona.nombre}</p>
                <p className="text-sm text-gold-deep">{persona.cargo}</p>
                {persona.bio && (
                  <p className="mx-auto mt-2 max-w-[220px] text-xs leading-relaxed text-ink-soft">
                    {persona.bio}
                  </p>
                )}
              </div>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
}
