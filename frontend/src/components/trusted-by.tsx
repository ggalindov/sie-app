"use client";

import Image from "next/image";
import { motion } from "motion/react";
import { empresasConfianza } from "@/lib/content";

const EASE = [0.16, 1, 0.3, 1] as const;

const cifras = [
  { valor: "20+", etiqueta: "Años de trayectoria" },
  { valor: "800+", etiqueta: "Casos ganados" },
  { valor: "20+", etiqueta: "Empresas aliadas" },
];

// único marquee de la página (regla: máximo uno por página). Los logos
// originales tienen fondo blanco cuadrado (no hay alfa real, ver CLAUDE.md),
// así que en vez de una tarjeta blanca dura, cada uno se apoya en un halo
// radial blanco que se desvanece sin borde: mix-blend-multiply funde ese
// halo (no el fondo oscuro de la sección) con el logo, así el blanco de
// origen desaparece y el resultado se siente "sin fondo" incluso sobre un
// fondo oscuro. La franja aislada (isolation) evita que el blend se filtre
// hacia el degradado de la sección.
export function TrustedBy() {
  const logos = [...empresasConfianza, ...empresasConfianza];

  return (
    <section
      className="snap-slide section-seam gradient-animate relative overflow-hidden py-20 md:py-24"
      style={{
        backgroundImage:
          "radial-gradient(120% 90% at 12% -10%, rgba(217,169,37,0.18), transparent 55%), radial-gradient(100% 90% at 90% 110%, rgba(217,169,37,0.14), transparent 55%), linear-gradient(160deg, #0a0906 0%, #1c1811 55%, #0a0906 100%)",
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
            <h2 className="font-display text-4xl leading-tight tracking-tight text-night-ink md:text-5xl">
              La confianza de empresas que ya nos eligieron.
            </h2>
            <p className="mt-3 max-w-md text-base leading-relaxed text-night-ink/65">
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
                <p className="font-display text-4xl text-gold md:text-5xl">{c.valor}</p>
                <p className="mt-1 text-xs leading-snug text-night-ink/60">{c.etiqueta}</p>
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
              animate={{ y: [0, -6, 0] }}
              transition={{
                duration: 4 + (i % 4) * 0.4,
                repeat: Infinity,
                ease: "easeInOut",
                delay: (i % 7) * 0.25,
              }}
              style={{ isolation: "isolate" }}
              className="relative flex h-48 w-96 shrink-0 items-center justify-center transition-transform duration-300 hover:scale-[1.05]"
            >
              <div
                aria-hidden="true"
                className="absolute inset-0"
                style={{
                  background:
                    "radial-gradient(60% 65% at 50% 50%, rgba(255,255,255,0.98) 0%, rgba(255,255,255,0.75) 45%, transparent 75%)",
                  filter: "blur(4px)",
                }}
              />
              <Image
                src={empresa.src}
                alt={empresa.alt}
                width={340}
                height={180}
                className="relative h-[72%] w-[80%] object-contain mix-blend-multiply"
              />
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
}
