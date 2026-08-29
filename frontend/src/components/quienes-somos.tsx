"use client";

import { type ReactNode } from "react";
import { motion } from "motion/react";
import { RevealImage } from "@/components/reveal-image";
import { AnilloEstadistica } from "@/components/anillo-estadistica";

const EASE = [0.16, 1, 0.3, 1] as const;

const stats = [
  { numero: 20, prefijo: "", sufijo: " años", etiqueta: "De trayectoria" },
  { numero: 800, prefijo: "+", sufijo: "", etiqueta: "Casos ganados" },
  { numero: 8, prefijo: "", sufijo: "", etiqueta: "Profesionales en el equipo" },
];

export function QuienesSomos({ media }: { media: ReactNode }) {
  return (
    <section
      id="quienes-somos"
      className="snap-slide section-seam frame-fixed py-20"
    >
      <div className="mx-auto flex max-w-7xl flex-col px-6 md:h-full md:justify-center md:py-16">
        <motion.h2
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, amount: 0.4 }}
          transition={{ duration: 0.7, ease: EASE }}
          className="max-w-3xl shrink-0 text-balance font-display text-4xl leading-[1.05] tracking-tight md:text-6xl"
        >
          Más que abogados: aliados en cada decisión importante.
        </motion.h2>

        {/* en desktop la sección tiene una altura fija (100dvh) para que el
            encaje del scroll-snap sea siempre exacto: el texto largo, en vez
            de estirar la sección, se desplaza con su propio scroll interno */}
        <div className="mt-10 flex flex-col gap-12 md:mt-10 md:min-h-0 md:flex-1 md:flex-row md:gap-16">
          <motion.div
            initial={{ opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.3 }}
            transition={{ duration: 0.7, ease: EASE }}
            className="space-y-6 text-base leading-relaxed text-ink-soft md:h-full md:w-[58%] md:overflow-y-auto md:pr-6 md:text-lg"
          >
            <div>
              <p className="font-display text-xl text-ink md:text-2xl">
                Nuestro propósito
              </p>
              <p className="mt-2">
                Asesoría jurídica seria, transparente y comprometida con la
                justicia. Así empezamos hace más de 20 años, y así seguimos
                hoy.
              </p>
            </div>

            <div>
              <p className="font-display text-xl text-ink md:text-2xl">
                Cómo trabajamos
              </p>
              <p className="mt-2">
                Seguimiento constante, informes periódicos y explicaciones
                sin tecnicismos: nunca pierdes el control de tu proceso, y
                nunca estás solo en él.
              </p>
            </div>

            <div>
              <p className="font-display text-xl text-ink md:text-2xl">
                Por qué confían en nosotros
              </p>
              <p className="mt-2">
                Acompañamos personas en momentos decisivos y empresas en su
                crecimiento. Muchas nos eligen desde hace años, no solo por
                experiencia, sino por la confianza que construimos caso a
                caso.
              </p>
            </div>

            <p className="border-l-2 border-gold py-1 pl-5 font-display text-xl italic leading-snug text-ink md:text-2xl">
              El derecho, con rostro humano.
            </p>
          </motion.div>

          <div className="flex flex-col gap-4 md:h-full md:w-[42%] md:justify-center">
            <motion.div
              initial={{ opacity: 0, y: 24 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, amount: 0.4 }}
              transition={{ duration: 0.7, ease: EASE }}
            >
              <RevealImage className="aspect-[4/3] rounded-2xl ring-1 ring-line md:aspect-[16/10]">
                {media}
              </RevealImage>
            </motion.div>

            {/* justify-center (no justify-around) en móvil a propósito: con
                distribución "around" el primer y último anillo quedaban justo
                debajo de los botones flotantes fijos (chatbot a la izquierda,
                WhatsApp a la derecha), tapando el número. Centrados con gap
                fijo, los tres quedan lejos de ambos bordes sin importar el
                ancho de pantalla. */}
            <div className="flex items-start justify-center gap-5 border-t border-line pt-7 sm:justify-start sm:gap-8">
              {stats.map((stat, i) => (
                <AnilloEstadistica
                  key={stat.etiqueta}
                  numero={stat.numero}
                  prefijo={stat.prefijo}
                  sufijo={stat.sufijo}
                  etiqueta={stat.etiqueta}
                  delay={0.1 * i}
                />
              ))}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
