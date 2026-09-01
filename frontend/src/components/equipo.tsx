"use client";

import { useState } from "react";
import Image from "next/image";
import { motion } from "motion/react";
import { Dialog } from "@base-ui/react/dialog";
import { X } from "@phosphor-icons/react";
import { equipo, type MiembroEquipo } from "@/lib/content";

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
  const [seleccionado, setSeleccionado] = useState<MiembroEquipo | null>(null);

  return (
    <section id="equipo" className="snap-slide section-seam frame-fixed py-20">
      <div className="mx-auto flex max-w-7xl flex-col px-6 md:h-full md:justify-center md:py-4">
        <div className="flex shrink-0 flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <motion.h2
            initial={{ opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.4 }}
            transition={{ duration: 0.7, ease: EASE }}
            className="max-w-lg font-display text-4xl leading-tight tracking-tight md:text-5xl"
          >
            Nuestro equipo
          </motion.h2>
          <motion.p
            initial={{ opacity: 0, y: 16 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.4 }}
            transition={{ duration: 0.6, delay: 0.1, ease: EASE }}
            className="max-w-sm text-sm text-ink-soft sm:text-right"
          >
            Un equipo, un mismo criterio: cada caso lo lleva alguien que responde por su nombre.
          </motion.p>
        </div>

        {/* Solo foto y nombre en la grilla: mantiene todas las tarjetas a la
            misma altura sin importar si esa persona ya tiene bio publicada o
            no. El detalle completo (cargo + bio, cuando existe) vive en el
            popup, no en la tarjeta.

            flex-wrap (no CSS grid) a propósito: con 11 personas el conteo no
            es múltiplo exacto de ninguna cantidad de columnas razonable, y un
            grid deja la última fila incompleta pegada a la izquierda (se ve
            como un error, una tarjeta huérfana). justify-center en un flex
            envuelto centra cada fila de forma independiente, así que la
            última fila (3 de 4 en desktop) queda centrada en vez de colgando.
            Ancho fijo por tarjeta = 4 por fila en desktop -> 3 filas exactas
            (4+4+3), 3 por fila en tablet, 2 en móvil. Las tarjetas y fotos se
            achican respecto a la versión de 2 filas para que 3 filas quepan
            cómodas dentro de la altura fija de la sección (frame-fixed) sin
            necesitar scroll interno. */}
        <div className="mt-8 flex flex-wrap justify-center gap-x-5 gap-y-7 sm:gap-x-6 md:mt-1.5 md:min-h-0 md:flex-1 md:content-center md:gap-x-4 md:gap-y-2">
          {equipo.map((persona, i) => (
            <motion.button
              key={persona.nombre}
              type="button"
              onClick={() => setSeleccionado(persona)}
              initial={{ opacity: 0, y: 34, scale: 0.94, filter: "blur(6px)" }}
              whileInView={{ opacity: 1, y: 0, scale: 1, filter: "blur(0px)" }}
              viewport={{ once: true, amount: 0.3 }}
              transition={{ duration: 0.7, delay: 0.06 * i, ease: EASE }}
              whileHover={{ y: -6 }}
              className="card-edged group flex w-[calc(50%-0.625rem)] shrink-0 flex-col items-center px-3 py-5 text-center sm:w-[calc(33.333%-1rem)] md:w-[calc(25%-0.75rem)] md:py-2"
            >
              <div className="relative aspect-square w-full max-w-[116px] md:max-w-[88px]">
                <motion.div
                  aria-hidden="true"
                  className="absolute -inset-2 rounded-full opacity-0 group-hover:opacity-100"
                  animate={{ rotate: 360 }}
                  transition={{ duration: 9, repeat: Infinity, ease: "linear" }}
                  style={{
                    backgroundImage:
                      "conic-gradient(from 0deg, var(--color-gold-deep), var(--color-gold), var(--color-gold-pale), var(--color-gold-deep))",
                    transitionProperty: "opacity",
                    transitionDuration: "400ms",
                  }}
                />
                <div className="absolute inset-0 rounded-full bg-paper" />
                <div className="absolute inset-[6px] overflow-hidden rounded-full">
                  <Image
                    src={persona.foto}
                    alt={`${persona.nombre}, ${persona.cargo}`}
                    fill
                    sizes="(min-width: 768px) 88px, (min-width: 640px) 28vw, 42vw"
                    style={{
                      ...mascara,
                      objectPosition: persona.posicion ?? "50% 32%",
                      ["--foto-zoom" as string]: persona.zoom ?? 1,
                    }}
                    className="equipo-foto object-cover"
                  />
                </div>
              </div>

              <p className="mt-2 font-display text-[0.95rem] leading-snug md:text-[0.85rem]">{persona.nombre}</p>
              <span className="mt-1 inline-flex items-center gap-1.5 text-xs font-medium tracking-wide text-gold-deep">
                <span className="h-1 w-1 shrink-0 rounded-full bg-gold-deep" />
                {persona.cargo}
              </span>
            </motion.button>
          ))}
        </div>
      </div>

      <Dialog.Root
        open={seleccionado !== null}
        onOpenChange={(next) => !next && setSeleccionado(null)}
      >
        <Dialog.Portal>
          <Dialog.Backdrop className="fixed inset-0 z-50 bg-night/70 backdrop-blur-sm transition-opacity duration-300 data-[ending-style]:opacity-0 data-[starting-style]:opacity-0" />
          <Dialog.Popup className="fixed inset-x-4 top-1/2 z-50 mx-auto max-w-md -translate-y-1/2 rounded-[1.75rem] bg-paper p-7 shadow-[0_40px_80px_-20px_rgba(0,0,0,0.5)] ring-1 ring-line transition-all duration-300 data-[ending-style]:scale-95 data-[ending-style]:opacity-0 data-[starting-style]:scale-95 data-[starting-style]:opacity-0 sm:p-9">
            <Dialog.Close
              aria-label="Cerrar"
              className="absolute right-5 top-5 flex h-9 w-9 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5 hover:text-ink"
            >
              <X className="h-4 w-4" />
            </Dialog.Close>

            {seleccionado && (
              <div className="flex flex-col items-center text-center">
                <div className="relative aspect-square w-32">
                  <div className="absolute inset-0 rounded-full bg-paper ring-1 ring-line" />
                  <div className="absolute inset-[6px] overflow-hidden rounded-full">
                    <Image
                      src={seleccionado.foto}
                      alt={`${seleccionado.nombre}, ${seleccionado.cargo}`}
                      fill
                      sizes="128px"
                      style={{
                        ...mascara,
                        objectPosition: seleccionado.posicion ?? "50% 32%",
                        ["--foto-zoom" as string]: seleccionado.zoom ?? 1,
                      }}
                      className="equipo-foto object-cover"
                    />
                  </div>
                </div>

                <Dialog.Title className="mt-5 font-display text-2xl leading-snug">
                  {seleccionado.nombre}
                </Dialog.Title>
                <span className="mt-1.5 inline-block rounded-full bg-gold-pale/60 px-3 py-1 text-xs font-medium text-gold-deep">
                  {seleccionado.cargo}
                </span>

                {seleccionado.bio ? (
                  <Dialog.Description className="mt-4 text-sm leading-relaxed text-ink-soft">
                    {seleccionado.bio}
                  </Dialog.Description>
                ) : (
                  <Dialog.Description className="mt-4 text-sm leading-relaxed text-ink-soft">
                    Parte del equipo jurídico de SIE Jurídicos.
                  </Dialog.Description>
                )}
              </div>
            )}
          </Dialog.Popup>
        </Dialog.Portal>
      </Dialog.Root>
    </section>
  );
}
