"use client";

import { useState } from "react";
import Image from "next/image";
import { motion } from "motion/react";
import { Dialog } from "@base-ui/react/dialog";
import { WhatsappLogo, X } from "@phosphor-icons/react";
import { equipo, type MiembroEquipo } from "@/lib/content";
import { siteConfig } from "@/lib/site-config";

const EASE = [0.16, 1, 0.3, 1] as const;

// desvanece el óvalo gris de estudio de las fotos hacia transparente, para
// que se sientan "sin fondo" en vez de recortes rectangulares de un ID
const mascara = {
  maskImage:
    "radial-gradient(circle at 50% 42%, black 52%, rgba(0,0,0,0.65) 68%, transparent 88%)",
  WebkitMaskImage:
    "radial-gradient(circle at 50% 42%, black 52%, rgba(0,0,0,0.65) 68%, transparent 88%)",
};

// Fondo "de estudio" unificado detrás de la máscara (solo lo usa el popup, ver abajo):
// cada foto real trae su propio gris de estudio (distinto entre sí), así que lo que se
// alcanza a ver en el borde difuso de la máscara no era un fondo consistente entre
// personas. Un degradado radial de marca propio detrás de TODAS las fotos resuelve esto
// sin herramienta de recorte real (no disponible en este entorno).
const fondoEstudio = {
  backgroundImage:
    "radial-gradient(circle at 50% 38%, var(--color-gold-pale) 0%, var(--color-paper) 72%)",
};

export function Equipo() {
  const [seleccionado, setSeleccionado] = useState<MiembroEquipo | null>(null);

  return (
    <section id="equipo" className="snap-slide section-seam frame-fixed overflow-hidden">
      {/* frame-fixed mide la sección a exactamente un viewport bajo el nav (ver
          globals.css): esta capa es la que de verdad se desplaza si la grilla de 10
          personas no cabe completa en una pantalla baja, en vez de estirar la sección
          más allá de un viewport -- así el scroll-snap siempre encaja exacto, "ni más ni
          menos", sin depender de ajustar tamaños a mano por rango de alto de pantalla. */}
      <div className="py-12 sm:py-14 md:h-full md:overflow-hidden md:py-7 lg:py-8 flex flex-col md:justify-center">
        {/* max-w-[1680px], no max-w-7xl (1280px): en monitores anchos sobraba mucho
            margen vacío a los lados mientras las tarjetas se quedaban chicas (pedido
            explícito: "acomodadas perfectamente al espacio"). En pantallas de hasta
            ~1680px de ancho esto no cambia nada (el contenedor ya estaba limitado por el
            propio viewport, no por max-w-7xl), así que el encaje sin scroll ya probado
            en pantallas normales/bajas sigue intacto -- solo crece en monitores
            genuinamente anchos, que sí tienen el espacio de sobra. */}
        <div className="mx-auto flex w-full max-w-[1480px] flex-col px-6 md:justify-center">
          <div className="-mt-3 flex shrink-0 flex-col gap-3 sm:flex-row sm:items-end sm:justify-between md:-mt-5">
            <motion.h2
              initial={{ opacity: 0, y: 24 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, amount: 0.4 }}
              transition={{ duration: 0.7, ease: EASE }}
              className="max-w-xl font-display text-3xl sm:text-4xl md:text-5xl lg:text-6xl leading-tight tracking-tight"
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

          {/* Tarjetas más grandes que la versión original, pero sin pasar de 5 columnas:
              probé 4 columnas (10 personas -> 3 filas) y en pantallas bajas no cabía sin
              scroll interno, algo que esta sección tiene prohibido por pedido explícito
              del usuario ("ninguna seccion puede tener scroll"). 5 columnas mantiene las
              2 filas de siempre -- el tamaño crece recortando el padding y el gap, no
              agregando una fila. */}
          <div className="mt-5 flex flex-wrap justify-center gap-3.5 sm:gap-4 md:mt-3 md:gap-4">
            {equipo.map((persona, i) => (
              <motion.button
                key={persona.nombre}
                type="button"
                onClick={() => setSeleccionado(persona)}
                initial={{ opacity: 0, y: 34, scale: 0.94, filter: "blur(6px)" }}
                whileInView={{ opacity: 1, y: 0, scale: 1, filter: "blur(0px)" }}
                viewport={{ once: true, amount: 0.3 }}
                transition={{ duration: 0.7, delay: 0.05 * i, ease: EASE }}
                whileHover={{ y: -5 }}
                // Marco dorado visible en las 10, sin importar el fondo de cada foto.
                // aspect-[11/12], un poco más alta que ancha -- "un poquito más alargadas"
                // (pedido explícito), sin volver a la proporción 3:4 original que exponía
                // demasiado encuadre de estudio distinto por persona (ver nota más arriba
                // sobre por qué se pasó a cuadrado). El resto de la sección se recortó
                // (padding, margen de la grilla) para que las 2 filas sigan cabiendo
                // completas sin scroll con esta altura extra.
                className="group relative aspect-[4/5] sm:aspect-[11/12] w-[calc(50%-0.4375rem)] shrink-0 overflow-hidden rounded-2xl text-left ring-1 ring-gold/25 shadow-[inset_0_0_0_1px_rgba(0,0,0,0.15),inset_0_0_26px_10px_rgba(0,0,0,0.22)] transition-shadow duration-500 hover:shadow-[inset_0_0_0_1px_rgba(0,0,0,0.15),inset_0_0_26px_10px_rgba(0,0,0,0.22),0_0_38px_-6px_rgba(217,169,37,0.55)] sm:w-[calc(33.333%-0.67rem)] md:w-[calc(19%-0.8rem)]"
              >
                {/* A color siempre, con exposición pareja (contrast+brightness fijos,
                    iguales en las 10). El brillo diagonal al pasar el mouse es lo que
                    unifica el "set". */}
                <Image
                  src={persona.foto}
                  alt={`${persona.nombre}, ${persona.cargo}`}
                  fill
                  sizes="(min-width: 768px) 250px, (min-width: 640px) 30vw, 45vw"
                  style={{
                    objectPosition: persona.posicion ?? "50% 25%",
                    ["--foto-zoom" as string]: persona.zoom ?? 1,
                  }}
                  className="equipo-foto object-cover contrast-[1.06] brightness-[0.97] transition-[filter] duration-500 group-hover:brightness-[1.04]"
                />
                <div className="absolute inset-0 bg-gradient-to-t from-night via-night/20 to-transparent transition-opacity duration-500 group-hover:from-night/88" />
                {/* Viñeta superior simétrica, pareja en las 10. */}
                <div className="absolute inset-0 bg-gradient-to-b from-night/40 via-transparent to-transparent" />
                {/* Brillo diagonal: franja clara que barre la tarjeta de izquierda a
                    derecha al pasar el mouse, como el "shine" de una superficie pulida. */}
                <div className="pointer-events-none absolute inset-y-0 -left-2/3 w-1/3 -skew-x-12 bg-gradient-to-r from-transparent via-white/35 to-transparent opacity-0 transition-all duration-700 ease-out group-hover:left-[130%] group-hover:opacity-100" />
                <div className="absolute inset-x-0 bottom-0 p-2.5 sm:p-3.5">
                  <p className="font-display text-xs sm:text-base leading-snug text-night-ink truncate sm:whitespace-normal">{persona.nombre}</p>
                  <span className="mt-0.5 sm:mt-1 inline-flex items-center gap-1 text-[9px] sm:text-[11px] font-medium tracking-wide text-gold">
                    <span className="h-1 w-1 shrink-0 rounded-full bg-gold" />
                    {persona.cargo}
                  </span>
                  <p className="mt-0.5 sm:mt-1 text-[9px] sm:text-[11px] leading-tight text-night-ink/65 line-clamp-1 sm:line-clamp-2">
                    {persona.descripcionBreve}
                  </p>
                </div>
              </motion.button>
            ))}
          </div>
        </div>
      </div>

      <Dialog.Root
        open={seleccionado !== null}
        onOpenChange={(next) => !next && setSeleccionado(null)}
      >
        <Dialog.Portal>
          <Dialog.Backdrop className="fixed inset-0 z-50 bg-night/75 backdrop-blur-md transition-opacity duration-300 data-[ending-style]:opacity-0 data-[starting-style]:opacity-0" />
          <Dialog.Popup className="fixed inset-x-4 top-1/2 z-50 mx-auto max-w-md -translate-y-1/2 overflow-hidden rounded-[1.75rem] bg-paper shadow-[0_40px_80px_-20px_rgba(0,0,0,0.5)] ring-1 ring-line transition-all duration-300 data-[ending-style]:scale-95 data-[ending-style]:opacity-0 data-[starting-style]:scale-95 data-[starting-style]:opacity-0 sm:rounded-[2rem]">
            {/* Mismo filo superior en degradado dorado que el resto de popups del sitio
                (areas-practica, newsletter-popup) -- un solo lenguaje de "sello" en vez de
                que cada modal invente su propia cabecera. */}
            <span
              aria-hidden="true"
              className="pointer-events-none absolute inset-x-0 top-0 h-[3px] z-10"
              style={{ backgroundImage: "linear-gradient(90deg, var(--color-gold-deep), var(--color-gold) 55%, var(--color-gold-pale))" }}
            />
            <Dialog.Close
              aria-label="Cerrar"
              className="absolute right-4 top-4 z-10 flex h-9 w-9 items-center justify-center rounded-full bg-paper/80 text-ink-soft backdrop-blur-sm hover:bg-ink/5 hover:text-ink"
            >
              <X className="h-4 w-4" />
            </Dialog.Close>

            {seleccionado && (
              <div className="flex flex-col items-center px-7 pb-8 pt-10 text-center sm:px-9 sm:pb-9">
                <div
                  aria-hidden="true"
                  className="pointer-events-none absolute inset-x-0 top-0 h-36"
                  style={{
                    backgroundImage:
                      "radial-gradient(70% 100% at 50% 0%, rgba(217,169,37,0.16), transparent 70%)",
                  }}
                />

                <div className="relative aspect-square w-32">
                  <div className="absolute inset-0 rounded-full ring-1 ring-gold/25" style={fondoEstudio} />
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

                <Dialog.Title className="relative mt-5 font-display text-2xl leading-snug text-ink">
                  {seleccionado.nombre}
                </Dialog.Title>
                <span className="relative mt-1.5 inline-flex items-center gap-1.5 text-xs font-medium tracking-wide text-gold-deep">
                  <span className="h-1 w-1 shrink-0 rounded-full bg-gold-deep" />
                  {seleccionado.cargo}
                </span>

                <Dialog.Description className="relative mt-5 max-w-xs text-sm leading-relaxed text-ink-soft">
                  {seleccionado.bio ?? seleccionado.descripcionBreve}
                </Dialog.Description>

                <a
                  href={`${siteConfig.whatsapp}?text=${encodeURIComponent(`Hola, me gustaría que ${seleccionado.nombre} de SIE Jurídicos me asesore con mi caso.`)}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="relative mt-7 inline-flex items-center gap-2 rounded-xl border border-line bg-surface px-5 py-2.5 text-xs font-semibold text-ink transition-colors hover:border-[#25D366]/50 hover:text-[#1a9c4a]"
                >
                  <WhatsappLogo weight="fill" className="h-4 w-4 text-[#25D366]" />
                  Solicitar asesoría con el equipo
                </a>
              </div>
            )}
          </Dialog.Popup>
        </Dialog.Portal>
      </Dialog.Root>
    </section>
  );
}
