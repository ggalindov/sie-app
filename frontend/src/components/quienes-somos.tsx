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
    <section id="quienes-somos" className="snap-slide section-seam frame-fixed overflow-hidden">
      {/* frame-fixed: la sección mide exactamente un viewport bajo el nav, con scroll
          interno propio si el contenido no cabe -- antes esta sección (texto + imagen +
          anillos de estadísticas) se salía del viewport en pantallas bajas y los anillos
          quedaban apretados contra el borde inferior (ver captura de referencia). */}
      <div className="py-12 sm:py-16 md:h-full md:overflow-hidden md:py-20 flex flex-col md:justify-center">
      <div className="mx-auto flex w-full max-w-7xl flex-col px-6 md:justify-center">
        <motion.h2
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, amount: 0.4 }}
          transition={{ duration: 0.7, ease: EASE }}
          className="max-w-3xl shrink-0 text-balance font-display text-2xl sm:text-4xl leading-[1.12] tracking-tight md:text-5xl lg:text-6xl"
        >
          Más que abogados: aliados en cada decisión importante.
        </motion.h2>

        <div className="mt-8 flex flex-col gap-10 md:mt-10 md:flex-row md:items-center md:gap-16">
          <motion.div
            initial={{ opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.3 }}
            transition={{ duration: 0.7, ease: EASE }}
            className="space-y-6 text-base leading-relaxed text-ink-soft md:w-[58%] md:pr-6 md:text-lg"
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

          <div className="flex flex-col gap-4 md:w-[42%] md:justify-center">
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
            <div className="flex items-start justify-center gap-2.5 sm:justify-start sm:gap-8 border-t border-line pt-6 sm:pt-7">
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
      </div>
    </section>
  );
}
