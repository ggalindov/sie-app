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

// Único marquee de la página (regla: máximo uno por página). Todos los logos
// cuentan con transparencia alfa limpia, dimensiones normalizadas y contraste
// equilibrado para flotar con sutileza y elegancia sobre el fondo oscuro luxury.
export function TrustedBy() {
  const logos = [...empresasConfianza, ...empresasConfianza];

  return (
    <section
      className="snap-slide section-seam gradient-animate relative overflow-hidden py-24 md:py-32 lg:py-36"
      style={{
        backgroundImage:
          "radial-gradient(120% 90% at 12% -10%, rgba(217,169,37,0.22), transparent 55%), radial-gradient(100% 90% at 90% 110%, rgba(217,169,37,0.18), transparent 55%), linear-gradient(160deg, #0a0906 0%, #1c1811 55%, #0a0906 100%)",
      }}
    >
      <div className="mx-auto max-w-7xl px-6">
        <div className="grid gap-10 md:grid-cols-12 md:items-end">
          <motion.div
            initial={{ opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.4 }}
            transition={{ duration: 0.7, ease: EASE }}
            className="md:col-span-7 lg:col-span-7"
          >
            {/* Titular monumental con 'confianza' más grande y animada */}
            <h2 className="font-display text-3xl min-[400px]:text-4xl leading-[1.08] tracking-tight text-night-ink sm:text-5xl md:text-5xl lg:text-6xl xl:text-7xl">
              La{" "}
              <motion.span
                className="relative inline-block font-serif italic text-gold lg:text-[1.12em] tracking-normal"
                animate={{
                  textShadow: [
                    "0 0 20px rgba(217,169,37,0.25)",
                    "0 0 45px rgba(217,169,37,0.7)",
                    "0 0 20px rgba(217,169,37,0.25)",
                  ],
                }}
                transition={{ duration: 3.5, repeat: Infinity, ease: "easeInOut" }}
              >
                confianza
                {/* Línea dorada animada en expansión */}
                <motion.span
                  className="absolute -bottom-1 sm:-bottom-2 left-0 h-[3px] sm:h-[4px] rounded-full bg-gradient-to-r from-gold/30 via-gold to-gold/30 shadow-[0_0_14px_rgba(217,169,37,0.85)]"
                  initial={{ width: "0%" }}
                  whileInView={{ width: "100%" }}
                  viewport={{ once: true }}
                  transition={{ duration: 1.2, delay: 0.3, ease: EASE }}
                />
              </motion.span>{" "}
              de empresas que ya nos eligieron.
            </h2>
            <p className="mt-4 sm:mt-5 max-w-xl text-base sm:text-lg leading-relaxed text-night-ink/70">
              Compañías de distintos sectores confían en SIE Jurídicos para
              respaldar sus decisiones legales, año tras año.
            </p>
          </motion.div>

          <div className="grid grid-cols-3 gap-2 min-[400px]:gap-4 sm:gap-6 md:gap-8 md:col-span-5 lg:col-span-5">
            {cifras.map((c, i) => (
              <motion.div
                key={c.etiqueta}
                initial={{ opacity: 0, y: 24 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, amount: 0.4 }}
                transition={{ duration: 0.6, delay: 0.08 * i, ease: EASE }}
                className="text-center md:text-right"
              >
                <p className="font-display text-3xl min-[400px]:text-4xl sm:text-5xl md:text-5xl lg:text-6xl xl:text-7xl text-gold drop-shadow-[0_2px_16px_rgba(217,169,37,0.3)]">
                  {c.valor}
                </p>
                <p className="mt-1.5 sm:mt-2 text-[11px] min-[400px]:text-xs sm:text-sm lg:text-base leading-snug text-night-ink/65 font-medium">
                  {c.etiqueta}
                </p>
              </motion.div>
            ))}
          </div>
        </div>
      </div>

      {/* Marquee de logotipos sustancialmente más grande para PC y móvil */}
      <div className="mt-12 sm:mt-20 md:mt-24 lg:mt-28 overflow-hidden [mask-image:linear-gradient(to_right,transparent,black_8%,black_92%,transparent)]">
        <div className="flex w-max animate-marquee items-center gap-6 min-[400px]:gap-10 sm:gap-14 md:gap-16 lg:gap-20 hover:[animation-play-state:paused]">
          {logos.map((empresa, i) => (
            <motion.div
              key={`${empresa.src}-${i}`}
              animate={{ y: [0, -6, 0] }}
              transition={{
                duration: 4.2 + (i % 4) * 0.5,
                repeat: Infinity,
                ease: "easeInOut",
                delay: (i % 7) * 0.3,
              }}
              className="group relative flex h-20 min-[400px]:h-24 sm:h-28 md:h-36 lg:h-44 min-w-[130px] min-[400px]:min-w-[150px] sm:min-w-[190px] md:min-w-[240px] lg:min-w-[290px] px-4 min-[400px]:px-6 sm:px-8 md:px-10 shrink-0 items-center justify-center transition-all duration-300"
            >
              <Image
                src={empresa.src}
                alt={empresa.alt}
                width={400}
                height={160}
                className="h-10 min-[400px]:h-12 sm:h-14 md:h-20 lg:h-24 xl:h-28 w-auto max-w-[170px] min-[400px]:max-w-[210px] sm:max-w-[250px] md:max-w-[320px] lg:max-w-[380px] object-contain opacity-80 group-hover:opacity-100 group-hover:scale-110 transition-all duration-300 drop-shadow-[0_4px_16px_rgba(0,0,0,0.6)] group-hover:drop-shadow-[0_0_24px_rgba(217,169,37,0.35)]"
              />
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
}
