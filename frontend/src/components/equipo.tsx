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
    <section id="equipo" className="snap-slide section-seam frame-fixed py-20">
      <div className="mx-auto flex max-w-7xl flex-col px-6 md:h-full md:justify-center md:py-10">
        <motion.h2
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, amount: 0.4 }}
          transition={{ duration: 0.7, ease: EASE }}
          className="max-w-lg shrink-0 font-display text-4xl leading-tight tracking-tight md:text-5xl"
        >
          Nuestro equipo
        </motion.h2>

        <div className="mt-8 grid grid-cols-2 gap-4 sm:grid-cols-3 md:min-h-0 md:flex-1 md:content-center md:overflow-y-auto lg:grid-cols-4">
          {equipo.map((persona, i) => (
            <motion.div
              key={persona.nombre}
              initial={{ opacity: 0, y: 24 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, amount: 0.3 }}
              transition={{ duration: 0.6, delay: 0.05 * i, ease: EASE }}
              className="card-edged group flex flex-col items-center px-4 py-5 text-center transition-transform duration-300 hover:-translate-y-1.5"
            >
              <div className="relative aspect-square w-full max-w-[128px]">
                <div
                  aria-hidden="true"
                  className="absolute -inset-2 rounded-full opacity-0 transition-opacity duration-300 group-hover:opacity-100"
                  style={{
                    backgroundImage:
                      "conic-gradient(from 0deg, var(--color-gold-deep), var(--color-gold), var(--color-gold-deep))",
                  }}
                />
                <div className="absolute inset-0 rounded-full bg-paper" />
                <div className="absolute inset-[6px] overflow-hidden rounded-full">
                  <Image
                    src={persona.foto}
                    alt={`${persona.nombre}, ${persona.cargo}`}
                    fill
                    sizes="(min-width: 1024px) 168px, (min-width: 640px) 25vw, 40vw"
                    style={{
                      ...mascara,
                      objectPosition: persona.posicion ?? "50% 32%",
                      ["--foto-zoom" as string]: persona.zoom ?? 1,
                    }}
                    className="equipo-foto object-cover"
                  />
                </div>
              </div>

              <p className="mt-3 font-display text-base leading-snug">{persona.nombre}</p>
              <span className="mt-1.5 inline-block rounded-full bg-gold-pale/60 px-3 py-1 text-xs font-medium text-gold-deep">
                {persona.cargo}
              </span>
              {persona.bio && (
                <p className="mx-auto mt-2 max-w-[220px] text-xs leading-relaxed text-ink-soft">
                  {persona.bio}
                </p>
              )}
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
}
