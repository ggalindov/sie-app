"use client";

import { useState, useEffect } from "react";
import Image from "next/image";
import { motion } from "motion/react";
import { Dialog } from "@base-ui/react/dialog";
import {
  ArrowUpRight,
  ArrowRight,
  WhatsappLogo,
  X,
} from "@phosphor-icons/react";
import { areasPractica, type AreaPractica } from "@/lib/content";
import { siteConfig } from "@/lib/site-config";
import { MagneticButton } from "@/components/magnetic-button";

const EASE = [0.16, 1, 0.3, 1] as const;

export function AreasPractica() {
  const [seleccionada, setSeleccionada] = useState<AreaPractica | null>(null);

  // Soporte para abrir directo por URL o evento (ej. desde el nav dropdown)
  useEffect(() => {
    function abrirPorSlug(slug: string) {
      const encontrada = areasPractica.find((a) => a.slug === slug);
      if (encontrada) setSeleccionada(encontrada);
    }

    function verificarParametro() {
      try {
        const parametros = new URLSearchParams(window.location.search);
        const slug = parametros.get("area");
        if (slug) abrirPorSlug(slug);
      } catch {
        // no crítico
      }
    }

    verificarParametro();
    window.addEventListener("popstate", verificarParametro);

    function onAbrirArea(e: Event) {
      const custom = e as CustomEvent<string>;
      if (custom.detail) abrirPorSlug(custom.detail);
    }
    window.addEventListener("abrir-area", onAbrirArea);

    return () => {
      window.removeEventListener("popstate", verificarParametro);
      window.removeEventListener("abrir-area", onAbrirArea);
    };
  }, []);



  return (
    <section
      id="areas"
      className="snap-slide section-seam relative py-14 md:py-20 lg:py-24 flex flex-col justify-center"
      style={{
        backgroundImage:
          "radial-gradient(90% 70% at 88% 0%, rgba(217,169,37,0.1), transparent 55%), linear-gradient(175deg, var(--color-surface) 0%, var(--color-paper) 100%)",
      }}
    >
      <div className="mx-auto flex w-full max-w-7xl flex-col px-4 sm:px-6 lg:px-8">
        {/* Cabecera Limpia */}
        <div className="flex shrink-0 flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <motion.h2
            initial={{ opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.4 }}
            transition={{ duration: 0.7, ease: EASE }}
            className="font-display text-3xl sm:text-4xl md:text-5xl lg:text-6xl leading-[1.1] tracking-tight text-ink"
          >
            Áreas de práctica
          </motion.h2>

          <motion.p
            initial={{ opacity: 0, y: 16 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.4 }}
            transition={{ duration: 0.6, delay: 0.1, ease: EASE }}
            className="max-w-md text-xs sm:text-sm md:text-base leading-relaxed text-ink-soft sm:text-right"
          >
            Seis frentes de práctica jurídica, un mismo criterio: respaldo legal serio y cercano.
          </motion.p>
        </div>

        {/* Grilla Limpia: Solo Foto y Título */}
        <div className="mt-8 sm:mt-10 lg:mt-12 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5 sm:gap-6 lg:gap-7">
          {areasPractica.map((area, i) => (
            <motion.button
              key={area.slug}
              type="button"
              onClick={() => setSeleccionada(area)}
              initial={{ opacity: 0, y: 24 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, amount: 0.2 }}
              transition={{ duration: 0.6, delay: 0.05 * i, ease: EASE }}
              whileHover={{ y: -6 }}
              className="group relative flex flex-col rounded-2xl sm:rounded-3xl bg-surface border border-line hover:border-gold/60 p-3 sm:p-3.5 shadow-xs hover:shadow-[0_20px_45px_-12px_rgba(217,169,37,0.22)] transition-all duration-500 text-left cursor-pointer overflow-hidden"
            >
              {/* Portada Fotográfica Limpia */}
              <div className="relative aspect-[16/10] w-full overflow-hidden rounded-xl sm:rounded-2xl bg-night">
                <Image
                  src={area.foto}
                  alt={area.nombre}
                  fill
                  sizes="(max-width: 768px) 100vw, (max-width: 1200px) 50vw, 33vw"
                  className="object-cover transition-transform duration-700 ease-out group-hover:scale-105 filter brightness-[0.96] group-hover:brightness-100"
                />
                {/* Sutil viñeta degradada */}
                <div className="absolute inset-0 bg-gradient-to-t from-night/40 via-transparent to-night/20" />
              </div>

              {/* Título Totalmente Limpio */}
              <div className="pt-4 pb-2 px-1.5 flex items-center justify-between gap-3">
                <h3 className="font-display text-lg sm:text-xl md:text-2xl font-bold tracking-tight text-ink group-hover:text-gold-deep transition-colors leading-snug">
                  {area.nombre}
                </h3>
                <ArrowUpRight
                  weight="bold"
                  className="h-5 w-5 shrink-0 text-ink-soft/40 transition-all duration-300 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 group-hover:text-gold-deep"
                />
              </div>
            </motion.button>
          ))}
        </div>
      </div>

      {/* Pop up Integrado para Detalles, Casos de Referencia y WhatsApp */}
      <Dialog.Root
        open={seleccionada !== null}
        onOpenChange={(next) => !next && setSeleccionada(null)}
      >
        <Dialog.Portal>
          <Dialog.Backdrop className="fixed inset-0 z-50 bg-night/80 backdrop-blur-md transition-opacity duration-300 data-[ending-style]:opacity-0 data-[starting-style]:opacity-0" />
          <Dialog.Popup className="fixed inset-x-3 sm:inset-x-6 top-1/2 z-50 mx-auto max-h-[90vh] max-w-3xl -translate-y-1/2 overflow-hidden rounded-3xl sm:rounded-[2rem] bg-paper shadow-[0_35px_70px_-15px_rgba(0,0,0,0.6)] ring-1 ring-line transition-all duration-300 data-[ending-style]:scale-95 data-[ending-style]:opacity-0 data-[starting-style]:scale-95 data-[starting-style]:opacity-0 flex flex-col">
            {seleccionada && (
              <>
                {/* Cabecera con Fotografía de Referencia */}
                <div className="relative h-44 sm:h-52 w-full shrink-0 overflow-hidden bg-night">
                  <Image
                    src={seleccionada.foto}
                    alt={seleccionada.nombre}
                    fill
                    sizes="(max-width: 768px) 100vw, 800px"
                    className="object-cover filter brightness-[0.88]"
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-paper via-night/70 to-night/40" />

                  {/* Botón Cerrar */}
                  <Dialog.Close
                    aria-label="Cerrar"
                    className="absolute right-3.5 top-3.5 sm:right-5 sm:top-5 z-20 flex h-9 w-9 items-center justify-center rounded-full bg-night/70 backdrop-blur-md text-white/80 hover:text-white hover:bg-night transition-colors border border-white/10"
                  >
                    <X className="h-5 w-5" />
                  </Dialog.Close>

                  {/* Datos del Área en la Cabecera */}
                  <div className="absolute bottom-4 inset-x-5 sm:inset-x-8 z-10">
                    <span className="text-xs font-semibold uppercase tracking-widest text-gold-pale/90 drop-shadow-sm">
                      Especialidad Jurídica
                    </span>
                    <Dialog.Title className="mt-1.5 font-display text-2xl sm:text-3xl lg:text-4xl leading-tight font-bold text-ink drop-shadow-xs">
                      {seleccionada.nombre}
                    </Dialog.Title>
                  </div>
                </div>

                {/* Contenido Desplazable del Modal */}
                <div className="overflow-y-auto px-5 sm:px-8 py-6 space-y-6">
                  {/* Resumen principal */}
                  <Dialog.Description className="text-base sm:text-lg font-medium leading-relaxed text-ink/90 border-l-2 border-gold pl-4">
                    {seleccionada.resumen}
                  </Dialog.Description>

                  {/* Casos de Referencia Atendidos (Casuística) */}
                  <div className="rounded-2xl bg-surface p-5 border border-line/80 shadow-xs">
                    <h4 className="text-xs font-bold uppercase tracking-[0.14em] text-gold-deep mb-3.5">
                      Casos y situaciones de referencia atendidos
                    </h4>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                      {seleccionada.casosReferencia.map((caso, idx) => (
                        <div
                          key={idx}
                          className="rounded-xl bg-paper/80 px-3.5 py-2.5 border border-line/60"
                        >
                          <span className="text-xs sm:text-sm font-medium text-ink leading-snug">
                            {caso}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>

                  {/* Párrafos descriptivos */}
                  <div className="space-y-3">
                    <h4 className="text-xs font-bold uppercase tracking-[0.14em] text-ink-soft/70">
                      Alcance y Acompañamiento
                    </h4>
                    {seleccionada.descripcion.map((parrafo, idx) => (
                      <p
                        key={idx}
                        className="text-xs sm:text-sm leading-relaxed text-ink-soft"
                      >
                        {parrafo}
                      </p>
                    ))}
                  </div>

                  {/* Acciones de Captación Inmediata */}
                  <div className="pt-3 flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
                    <MagneticButton strength={0.35} className="w-full sm:w-auto">
                      <a
                        href={`https://wa.me/573243668845?text=${encodeURIComponent(seleccionada.whatsappMensaje)}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="group inline-flex w-full sm:w-auto items-center justify-center gap-2 rounded-xl bg-[#25D366] px-5 py-3 text-sm font-semibold text-white shadow-sm transition-all duration-300 hover:bg-[#20bd5a] hover:shadow-md active:scale-[0.98]"
                      >
                        <span>Consultar este caso por WhatsApp</span>
                        <WhatsappLogo
                          weight="fill"
                          className="h-4 w-4 shrink-0 transition-transform duration-300 group-hover:translate-x-1"
                        />
                      </a>
                    </MagneticButton>

                    <MagneticButton strength={0.35} className="w-full sm:w-auto">
                      <button
                        type="button"
                        onClick={() => {
                          setSeleccionada(null);
                          setTimeout(() => {
                            const el = document.getElementById("agendar");
                            if (el) el.scrollIntoView({ behavior: "smooth" });
                          }, 150);
                        }}
                        className="cta-boton group inline-flex w-full sm:w-auto items-center justify-center gap-2 rounded-xl bg-gold px-5 py-3 text-sm font-semibold text-ink-fixed active:scale-[0.98]"
                      >
                        <span className="relative z-10">{siteConfig.ctaPrincipal}</span>
                        <ArrowRight
                          weight="bold"
                          className="relative z-10 h-4 w-4 shrink-0 transition-transform duration-300 group-hover:translate-x-1"
                        />
                      </button>
                    </MagneticButton>
                  </div>

                  {/* Selector Rápido entre Áreas */}
                  <div className="border-t border-line pt-4">
                    <p className="text-[11px] font-semibold uppercase tracking-wider text-ink-soft/70">
                      Explorar otras áreas de práctica
                    </p>
                    <div className="mt-2.5 flex flex-wrap gap-2">
                      {areasPractica
                        .filter((a) => a.slug !== seleccionada.slug)
                        .map((otra) => (
                          <button
                            key={otra.slug}
                            type="button"
                            onClick={() => setSeleccionada(otra)}
                            className="cursor-pointer rounded-full bg-ink/5 px-3 py-1 text-xs font-medium text-ink transition-all hover:bg-gold/15 hover:text-gold-deep border border-transparent hover:border-gold/30"
                          >
                            {otra.nombre}
                          </button>
                        ))}
                    </div>
                  </div>
                </div>
              </>
            )}
          </Dialog.Popup>
        </Dialog.Portal>
      </Dialog.Root>
    </section>
  );
}
