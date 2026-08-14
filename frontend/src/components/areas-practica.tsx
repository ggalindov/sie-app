"use client";

import Link from "next/link";
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

const spans = [
  "lg:col-span-2 lg:row-span-2",
  "lg:col-span-2 lg:row-span-1",
  "lg:col-span-1 lg:row-span-1",
  "lg:col-span-1 lg:row-span-1",
  "lg:col-span-2 lg:row-span-1",
  "lg:col-span-2 lg:row-span-1",
];

// un solo acento (tinta + oro), variando intensidad y ángulo por tarjeta en
// vez de un color distinto por tarjeta: más disciplinado, más "firma legal
// seria" que el bento arcoíris genérico. Nunca un color plano.
const paletas = [
  {
    gradient: "linear-gradient(150deg, #0a0906 0%, #1c1811 55%, #0a0906 100%)",
    ink: "text-night-ink",
    icono: "text-gold",
    flecha: "text-night-ink/50",
    texto: "text-night-ink/85",
  },
  {
    gradient: "linear-gradient(135deg, #a97c16 0%, #d9a925 50%, #f1cf6a 100%)",
    ink: "text-ink-fixed",
    icono: "text-ink-fixed",
    flecha: "text-ink-fixed/50",
    texto: "text-ink-fixed/80",
  },
  {
    gradient: "linear-gradient(160deg, #221c12 0%, #3d321c 60%, #221c12 100%)",
    ink: "text-gold-pale",
    icono: "text-gold-pale",
    flecha: "text-gold-pale/60",
    texto: "text-gold-pale/90",
  },
  {
    gradient: "linear-gradient(135deg, #171410 0%, #2a2317 100%)",
    ink: "text-gold-pale",
    icono: "text-gold",
    flecha: "text-gold-pale/60",
    texto: "text-gold-pale/90",
  },
  {
    gradient: "linear-gradient(150deg, #4a3a12 0%, #a97c16 55%, #4a3a12 100%)",
    ink: "text-ink-fixed",
    icono: "text-ink-fixed",
    flecha: "text-ink-fixed/60",
    texto: "text-ink-fixed/80",
  },
  {
    gradient: "linear-gradient(200deg, #120f09 0%, #3a2f16 55%, #0c0a07 100%)",
    ink: "text-night-ink",
    icono: "text-gold",
    flecha: "text-night-ink/50",
    texto: "text-night-ink/85",
  },
];

export function AreasPractica() {
  return (
    <section
      id="areas"
      className="section-seam gradient-animate relative py-24 md:py-32"
      style={{
        backgroundImage:
          "radial-gradient(110% 80% at 85% 0%, rgba(217,169,37,0.12), transparent 55%), radial-gradient(90% 80% at 5% 100%, rgba(217,169,37,0.09), transparent 55%), linear-gradient(175deg, var(--color-surface) 0%, var(--color-paper) 100%)",
      }}
    >
      <div className="mx-auto max-w-7xl px-6">
        <motion.h2
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, amount: 0.4 }}
          transition={{ duration: 0.7, ease: EASE }}
          className="max-w-lg font-display text-3xl leading-tight tracking-tight md:text-4xl"
        >
          Áreas de práctica
        </motion.h2>

        <div className="mt-10 grid grid-cols-1 gap-4 lg:auto-rows-[220px] lg:grid-cols-4">
          {areasPractica.map((area, i) => {
            const Icono = iconos[i];
            const destacada = i === 0;
            const p = paletas[i];
            return (
              <motion.div
                key={area.slug}
                initial={{ opacity: 0, y: 24 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, amount: 0.3 }}
                transition={{ duration: 0.6, delay: 0.06 * i, ease: EASE }}
                className={spans[i]}
              >
                <Link
                  href={`/areas/${area.slug}`}
                  style={{ backgroundImage: p.gradient }}
                  className={`group gradient-animate relative flex h-full flex-col justify-between overflow-hidden rounded-3xl p-7 shadow-[0_1px_0_rgba(255,255,255,0.08)_inset] ring-1 ring-white/10 transition-all duration-300 hover:-translate-y-1.5 hover:shadow-[0_28px_55px_-20px_rgba(0,0,0,0.55)] ${p.ink}`}
                >
                  <div
                    aria-hidden="true"
                    className="pointer-events-none absolute -inset-x-10 -top-24 h-40 rotate-[8deg] bg-white/10 blur-2xl transition-opacity duration-500 group-hover:opacity-70"
                  />

                  <div className="relative flex items-start justify-between">
                    <motion.span
                      animate={{ y: [0, -6, 0] }}
                      transition={{
                        duration: 3.4 + i * 0.35,
                        repeat: Infinity,
                        ease: "easeInOut",
                      }}
                      className="flex h-12 w-12 items-center justify-center rounded-2xl bg-current/10 ring-1 ring-current/20"
                    >
                      <Icono weight="light" className={`h-6 w-6 ${p.icono}`} />
                    </motion.span>
                    <ArrowUpRight
                      weight="bold"
                      className={`h-5 w-5 shrink-0 transition-transform duration-300 group-hover:translate-x-1 group-hover:-translate-y-1 ${p.flecha}`}
                    />
                  </div>

                  <div className="relative">
                    <h3 className="font-display text-xl font-semibold leading-snug">
                      {area.nombre}
                    </h3>
                    <p
                      className={`mt-2 text-sm leading-relaxed ${
                        destacada ? "line-clamp-3" : "line-clamp-2"
                      } ${p.texto}`}
                    >
                      {area.resumen}
                    </p>
                  </div>
                </Link>
              </motion.div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
