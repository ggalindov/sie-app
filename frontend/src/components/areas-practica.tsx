"use client";

import Link from "next/link";
import type { CSSProperties } from "react";
import { motion } from "motion/react";
import {
  Briefcase,
  Users,
  Scales,
  Buildings,
  Bank,
  ShieldCheck,
  ArrowUpRight,
} from "@phosphor-icons/react";
import { areasPractica } from "@/lib/content";

const EASE = [0.16, 1, 0.3, 1] as const;

const iconos = [Briefcase, Users, Scales, Buildings, Bank, ShieldCheck];

// La disciplina cromática de la marca (tinta + oro, variando intensidad) ya
// no vive en el fondo completo de una tarjeta (eso leía como bento de SaaS
// genérico), sino concentrada en la insignia circular de cada fila: mismo
// criterio de color, expresado en una pieza mucho más pequeña y con más
// oficio (degradado real + sombra), no un tinte plano.
const acentos = ["#8a6318", "#d9a925", "#a97c16", "#5c451c", "#c79a2e", "#3d2c18"];
const gradientes = [
  "linear-gradient(150deg, #0a0906 0%, #1c1811 55%, #0a0906 100%)",
  "linear-gradient(135deg, #a97c16 0%, #d9a925 50%, #f1cf6a 100%)",
  "linear-gradient(160deg, #221c12 0%, #3d321c 60%, #221c12 100%)",
  "linear-gradient(135deg, #171410 0%, #2a2317 100%)",
  "linear-gradient(150deg, #4a3a12 0%, #a97c16 55%, #4a3a12 100%)",
  "linear-gradient(200deg, #120f09 0%, #3a2f16 55%, #0c0a07 100%)",
];
const iconoTinta = [
  "text-gold",
  "text-ink-fixed",
  "text-gold-pale",
  "text-gold",
  "text-ink-fixed",
  "text-gold",
];

export function AreasPractica() {
  return (
    <section
      id="areas"
      className="snap-slide section-seam frame-fixed relative py-20"
      style={{
        backgroundImage:
          "radial-gradient(90% 70% at 88% 0%, rgba(217,169,37,0.1), transparent 55%), linear-gradient(175deg, var(--color-surface) 0%, var(--color-paper) 100%)",
      }}
    >
      <div className="mx-auto flex max-w-6xl flex-col px-6 md:h-full md:justify-center md:py-10">
        <div className="flex shrink-0 flex-wrap items-end justify-between gap-6">
          <motion.h2
            initial={{ opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.4 }}
            transition={{ duration: 0.7, ease: EASE }}
            className="max-w-lg font-display text-4xl leading-tight tracking-tight md:text-5xl"
          >
            Áreas de práctica
          </motion.h2>
          <motion.p
            initial={{ opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.4 }}
            transition={{ duration: 0.7, delay: 0.1, ease: EASE }}
            className="max-w-xs text-sm leading-relaxed text-ink-soft"
          >
            Seis frentes de práctica, un mismo criterio: respaldo legal serio
            y cercano.
          </motion.p>
        </div>

        <div className="mt-8 border-t border-line md:min-h-0 md:flex-1 md:overflow-y-auto">
          {areasPractica.map((area, i) => {
            const Icono = iconos[i];
            return (
              <motion.div
                key={area.slug}
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, amount: 0.3 }}
                transition={{ duration: 0.6, delay: 0.05 * i, ease: EASE }}
                className="border-b border-line"
              >
                <Link
                  href={`/areas/${area.slug}`}
                  style={{ "--area-accent": acentos[i] } as CSSProperties}
                  className="area-row group relative flex flex-col gap-4 overflow-hidden py-6 pl-5 md:grid md:grid-cols-12 md:items-center md:gap-x-6 md:py-5 md:pl-7"
                >
                  <span
                    aria-hidden="true"
                    className="pointer-events-none absolute -right-2 top-1/2 hidden -translate-y-1/2 select-none font-display text-[7rem] leading-none text-ink/[0.04] transition-colors duration-300 group-hover:text-ink/[0.07] md:block"
                  >
                    0{i + 1}
                  </span>

                  <div className="flex items-center justify-between md:contents">
                    <div className="flex items-center gap-4 md:contents">
                      <span className="font-display text-sm text-ink-soft/50 md:col-span-1 md:text-base">
                        0{i + 1}
                      </span>
                      <span
                        style={{ backgroundImage: gradientes[i] }}
                        className={`flex h-14 w-14 shrink-0 items-center justify-center rounded-full shadow-[0_12px_26px_-10px_rgba(20,19,15,0.5)] transition-transform duration-300 group-hover:scale-110 md:col-span-1 ${iconoTinta[i]}`}
                      >
                        <Icono weight="duotone" className="h-6 w-6" />
                      </span>
                    </div>
                    <ArrowUpRight
                      weight="bold"
                      className="h-5 w-5 text-ink-soft transition-transform duration-300 group-hover:translate-x-1 group-hover:-translate-y-1 group-hover:text-gold-deep md:hidden"
                    />
                  </div>

                  <h3 className="relative font-display text-2xl leading-snug transition-colors duration-300 group-hover:text-gold-deep md:col-span-4 md:text-3xl">
                    {area.nombre}
                  </h3>

                  <p className="relative text-sm leading-relaxed text-ink-soft md:col-span-4 md:col-start-8">
                    {area.resumen}
                  </p>

                  <span className="hidden items-center justify-end gap-2 md:col-span-1 md:col-start-12 md:flex">
                    <span className="max-w-0 overflow-hidden whitespace-nowrap text-xs font-medium uppercase tracking-[0.08em] text-gold-deep opacity-0 transition-all duration-300 group-hover:max-w-[5rem] group-hover:opacity-100">
                      Ver más
                    </span>
                    <ArrowUpRight
                      weight="bold"
                      className="h-5 w-5 shrink-0 text-ink-soft transition-transform duration-300 group-hover:translate-x-1 group-hover:-translate-y-1 group-hover:text-gold-deep"
                    />
                  </span>
                </Link>
              </motion.div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
