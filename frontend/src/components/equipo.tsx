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
      <div className="mx-auto flex max-w-7xl flex-col px-6 md:h-full md:justify-center md:py-6">
        <div className="flex shrink-0 flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <motion.h2
            initial={{ opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.4 }}
            transition={{ duration: 0.7, ease: EASE }}
            className="max-w-xl font-display text-4xl leading-tight tracking-tight md:text-5xl md:[@media(min-height:900px)]:text-6xl"
          >
            Nuestro equipo
          </motion.h2>
          <motion.p
            initial={{ opacity: 0, y: 16 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.4 }}
            transition={{ duration: 0.6, delay: 0.1, ease: EASE }}
            className="max-w-sm text-sm text-ink-soft sm:text-right md:text-base"
          >
            Un equipo, un mismo criterio: cada caso lo lleva alguien que responde por su nombre.
          </motion.p>
        </div>

        {/* Solo foto y nombre en la grilla: mantiene todas las tarjetas a la
            misma altura sin importar si esa persona ya tiene bio publicada o
            no. El detalle completo (cargo + bio, cuando existe) vive en el
            popup, no en la tarjeta.

            flex-wrap (no CSS grid) a propósito: con 10 personas el desktop
            (5 por fila) sí cae en dos filas exactas, pero en tablet (3 por
            fila) sobra una persona en una tercera fila -- un grid deja esa
            tarjeta huérfana pegada a la izquierda; justify-center en un flex
            envuelto la centra en vez de dejarla colgando. Las fotos y el
            texto son más grandes que en la versión de 3 filas porque ahora
            solo hay 2 filas en desktop, así que sobra más alto disponible
            dentro de la altura fija de la sección (frame-fixed) para
            aprovecharlo sin necesitar scroll interno. */}
        <div className="mt-8 flex flex-wrap justify-center gap-x-5 gap-y-8 sm:gap-x-6 md:mt-3 md:min-h-0 md:flex-1 md:content-center md:gap-x-5 md:gap-y-3 md:[@media(min-height:760px)_and_(max-height:899px)]:mt-5 md:[@media(min-height:760px)_and_(max-height:899px)]:gap-x-6 md:[@media(min-height:760px)_and_(max-height:899px)]:gap-y-5 md:[@media(min-height:900px)_and_(max-height:1019px)]:mt-6 md:[@media(min-height:900px)_and_(max-height:1019px)]:gap-x-7 md:[@media(min-height:900px)_and_(max-height:1019px)]:gap-y-5 md:[@media(min-height:1020px)]:mt-8 md:[@media(min-height:1020px)]:gap-x-8 md:[@media(min-height:1020px)]:gap-y-7">
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
              className="card-edged group flex w-[calc(50%-0.625rem)] shrink-0 flex-col items-center px-3 py-5 text-center sm:w-[calc(33.333%-1rem)] md:w-[calc(20%-1rem)] md:py-3 md:[@media(min-height:760px)_and_(max-height:899px)]:w-[calc(20%-1.2rem)] md:[@media(min-height:760px)_and_(max-height:899px)]:py-4 md:[@media(min-height:900px)_and_(max-height:1019px)]:w-[calc(20%-1.4rem)] md:[@media(min-height:900px)_and_(max-height:1019px)]:py-5 md:[@media(min-height:1020px)]:w-[calc(20%-1.6rem)] md:[@media(min-height:1020px)]:py-7"
            >
              <div className="relative aspect-square w-full max-w-[128px] md:max-w-[132px] md:[@media(min-height:760px)_and_(max-height:899px)]:max-w-[140px] md:[@media(min-height:900px)_and_(max-height:1019px)]:max-w-[160px] md:[@media(min-height:1020px)]:max-w-[192px]">
                <motion.div
                  aria-hidden="true"
                  className="absolute -inset-2.5 rounded-full opacity-0 group-hover:opacity-100"
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
                    sizes="(min-width: 768px) 192px, (min-width: 640px) 30vw, 45vw"
                    style={{
                      ...mascara,
                      objectPosition: persona.posicion ?? "50% 32%",
                      ["--foto-zoom" as string]: persona.zoom ?? 1,
                    }}
                    className="equipo-foto object-cover"
                  />
                </div>
              </div>

              <p className="mt-3 font-display text-base leading-snug md:text-[1.05rem] md:[@media(min-height:760px)_and_(max-height:899px)]:text-lg md:[@media(min-height:900px)]:text-xl">{persona.nombre}</p>
              <span className="mt-1.5 inline-flex items-center gap-1.5 text-xs font-medium tracking-wide text-gold-deep md:[@media(min-height:900px)]:text-sm md:[@media(min-height:1020px)]:text-[0.95rem]">
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
