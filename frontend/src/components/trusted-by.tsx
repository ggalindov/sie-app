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
    <section
      className="section-seam gradient-animate relative py-24 md:py-28"
      style={{
        backgroundImage:
          "radial-gradient(120% 90% at 12% -10%, rgba(217,169,37,0.14), transparent 55%), radial-gradient(100% 90% at 90% 110%, rgba(217,169,37,0.1), transparent 55%), linear-gradient(175deg, var(--color-surface) 0%, var(--color-paper) 100%)",
      }}
    >
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

      <div className="mt-16 overflow-hidden [mask-image:linear-gradient(to_right,transparent,black_8%,black_92%,transparent)]">
        <div className="flex w-max animate-marquee items-center gap-8">
          {logos.map((empresa, i) => (
            <motion.div
              key={`${empresa.src}-${i}`}
              animate={{ y: [0, -8, 0] }}
              transition={{
                duration: 4 + (i % 4) * 0.4,
                repeat: Infinity,
                ease: "easeInOut",
                delay: (i % 7) * 0.25,
              }}
              className="flex h-44 w-80 shrink-0 items-center justify-center rounded-[1.75rem] bg-white p-9 shadow-[0_24px_55px_-20px_rgba(0,0,0,0.35)] ring-1 ring-black/5 transition-transform duration-300 hover:-translate-y-1 hover:scale-105"
            >
              <Image
                src={empresa.src}
                alt={empresa.alt}
                width={240}
                height={130}
                className="h-full w-full object-contain"
              />
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
}
