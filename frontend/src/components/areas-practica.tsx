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

// una tarjeta, un color: familia editorial derivada del dorado de marca, cada
// una como degradado que fluye (nunca un color plano), no un arcoíris
// genérico (una sola tarjeta neutra para respirar)
const paletas = [
  {
    gradient: "linear-gradient(135deg, #0c0a08 0%, #211b13 45%, #171310 100%)",
    ink: "text-night-ink",
    icono: "text-gold",
    flecha: "text-night-ink/50",
  },
  {
    gradient: "linear-gradient(135deg, #a97c16 0%, #d9a925 50%, #f1cf6a 100%)",
    ink: "text-ink-fixed",
    icono: "text-ink-fixed",
    flecha: "text-ink-fixed/50",
  },
  {
    gradient: "linear-gradient(135deg, #7a3620 0%, #b1512f 55%, #c96a45 100%)",
    ink: "text-terracotta-ink",
    icono: "text-terracotta-ink",
    flecha: "text-terracotta-ink/60",
  },
  {
    gradient: "linear-gradient(135deg, #263629 0%, #3f5b44 55%, #587a5f 100%)",
    ink: "text-forest-ink",
    icono: "text-forest-ink",
    flecha: "text-forest-ink/60",
  },
  {
    gradient: "linear-gradient(135deg, #16283a 0%, #24425c 55%, #3a6288 100%)",
    ink: "text-navy-ink",
    icono: "text-navy-ink",
    flecha: "text-navy-ink/60",
  },
  {
    gradient: "linear-gradient(135deg, var(--color-surface) 0%, var(--color-paper) 100%)",
    ink: "text-ink",
    icono: "text-gold-deep",
    flecha: "text-ink-soft",
    ring: true,
  },
];

export function AreasPractica() {
  return (
    <section id="areas" className="section-seam bg-surface/90 py-24 md:py-32">
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

        <div className="mt-10 grid grid-cols-1 gap-4 lg:auto-rows-[190px] lg:grid-cols-4">
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
                  className={`group gradient-animate relative flex h-full flex-col justify-between overflow-hidden rounded-3xl p-7 shadow-[0_1px_0_rgba(255,255,255,0.06)_inset] transition-all duration-300 hover:-translate-y-1.5 hover:shadow-[0_24px_50px_-20px_rgba(0,0,0,0.5)] ${p.ink} ${p.ring ? "ring-1 ring-line" : ""}`}
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
                      className={`flex h-11 w-11 items-center justify-center rounded-2xl bg-current/10`}
                    >
                      <Icono weight="light" className={`h-6 w-6 ${p.icono}`} />
                    </motion.span>
                    <ArrowUpRight
                      weight="bold"
                      className={`h-5 w-5 transition-transform duration-300 group-hover:translate-x-1 group-hover:-translate-y-1 ${p.flecha}`}
                    />
                  </div>

                  <div className="relative">
                    <h3 className="font-display text-xl leading-snug">
                      {area.nombre}
                    </h3>
                    <p
                      className={`mt-2 text-sm leading-relaxed opacity-70 transition-all duration-300 ${
                        destacada
                          ? "line-clamp-3"
                          : "line-clamp-0 max-h-0 opacity-0 group-hover:line-clamp-3 group-hover:max-h-24 group-hover:opacity-70"
                      }`}
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
