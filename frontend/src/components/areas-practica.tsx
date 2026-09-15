"use client";

import { useState, useEffect, type CSSProperties } from "react";
import { motion } from "motion/react";
import { Dialog } from "@base-ui/react/dialog";
import {
  Briefcase,
  Users,
  Scales,
  Buildings,
  Bank,
  ShieldCheck,
  ArrowUpRight,
  ArrowRight,
  WhatsappLogo,
  X,
} from "@phosphor-icons/react";
import { areasPractica, type AreaPractica } from "@/lib/content";
import { siteConfig } from "@/lib/site-config";
import { MagneticButton } from "@/components/magnetic-button";

const EASE = [0.16, 1, 0.3, 1] as const;

const iconos = [Briefcase, Users, Scales, Buildings, Bank, ShieldCheck];

// La disciplina cromática de la marca (tinta + oro, variando intensidad) ya
// no vive en el fondo completo de una tarjeta (eso leía como bento de SaaS
// genérico), sino concentrada en la insignia circular de cada fila: mismo
// criterio de color, expresado en una pieza mucho más pequeña y con más
// oficio (degradado real + sombra), no un tinte plano.
const acentos = ["#8a6318", "#d9a925", "#a97c16", "#5c451c", "#c79a2e", "#3d2c18"];
const gradientes = [
  "linear-gradient(150deg, #0a0906 0%, #1c1811 55%, #0a0906 100%)",
  "linear-gradient(135deg, #a97c16 0%, #d9a925 50%, #f1cf6a 100%)",
  "linear-gradient(160deg, #221c12 0%, #3d321c 60%, #221c12 100%)",
  "linear-gradient(135deg, #171410 0%, #2a2317 100%)",
  "linear-gradient(150deg, #4a3a12 0%, #a97c16 55%, #4a3a12 100%)",
  "linear-gradient(200deg, #120f09 0%, #3a2f16 55%, #0c0a07 100%)",
];
const iconoTinta = [
  "text-gold",
  "text-ink-fixed",
  "text-gold-pale",
  "text-gold",
  "text-ink-fixed",
  "text-gold",
];

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

  const indiceSeleccionada = seleccionada
    ? areasPractica.findIndex((a) => a.slug === seleccionada.slug)
    : -1;
  const IconoSeleccionado = indiceSeleccionada >= 0 ? iconos[indiceSeleccionada] : null;

  return (
    <section
      id="areas"
      className="snap-slide section-seam relative py-12 md:py-16 lg:py-20 flex flex-col justify-center"
      style={{
        backgroundImage:
          "radial-gradient(90% 70% at 88% 0%, rgba(217,169,37,0.1), transparent 55%), linear-gradient(175deg, var(--color-surface) 0%, var(--color-paper) 100%)",
      }}
    >
      <div className="mx-auto flex w-full max-w-6xl flex-col px-6">
        <div className="flex shrink-0 flex-wrap items-end justify-between gap-4 sm:gap-6">
          <motion.h2
            initial={{ opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.4 }}
            transition={{ duration: 0.7, ease: EASE }}
            className="max-w-lg font-display text-3xl sm:text-4xl md:text-5xl leading-tight tracking-tight"
          >
            Áreas de práctica
          </motion.h2>
          <motion.p
            initial={{ opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.4 }}
            transition={{ duration: 0.7, delay: 0.1, ease: EASE }}
            className="max-w-xs text-xs sm:text-sm leading-relaxed text-ink-soft sm:text-right"
          >
            Seis frentes de práctica, un mismo criterio: respaldo legal serio
            y cercano.
          </motion.p>
        </div>

        <div className="mt-6 border-t border-line">
          {areasPractica.map((area, i) => {
            const Icono = iconos[i];
            return (
              <motion.div
                key={area.slug}
                initial={{ opacity: 0, y: 16 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, amount: 0.3 }}
                transition={{ duration: 0.5, delay: 0.04 * i, ease: EASE }}
                className="border-b border-line"
              >
                <button
                  type="button"
                  onClick={() => setSeleccionada(area)}
                  style={{ "--area-accent": acentos[i] } as CSSProperties}
                  className="area-row group relative flex w-full cursor-pointer flex-col gap-2 overflow-hidden py-3.5 pl-3 pr-3 sm:pl-4 sm:pr-4 text-left transition-colors md:grid md:grid-cols-12 md:items-center md:gap-x-4 md:py-3 md:pl-5 lg:py-3.5 lg:pl-6"
                >
                  <span
                    aria-hidden="true"
                    className="pointer-events-none absolute -right-2 top-1/2 hidden -translate-y-1/2 select-none font-display text-[4.5rem] leading-none text-ink/[0.03] transition-colors duration-300 group-hover:text-ink/[0.06] md:block"
                  >
                    0{i + 1}
                  </span>

                  <div className="flex items-center justify-between md:contents">
                    <div className="flex items-center gap-3.5 md:contents">
                      <span className="font-display text-xs text-ink-soft/50 md:col-span-1 md:text-sm">
                        0{i + 1}
                      </span>
                      <span
                        style={{ backgroundImage: gradientes[i] }}
                        className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-full shadow-[0_8px_18px_-6px_rgba(20,19,15,0.45)] transition-transform duration-300 group-hover:scale-105 md:col-span-1 ${iconoTinta[i]}`}
                      >
                        <Icono weight="duotone" className="h-5 w-5" />
                      </span>
                    </div>
                    <ArrowUpRight
                      weight="bold"
                      className="h-4 w-4 text-ink-soft transition-transform duration-300 group-hover:translate-x-1 group-hover:-translate-y-1 group-hover:text-gold-deep md:hidden"
                    />
                  </div>

                  <h3 className="relative font-display text-xl leading-snug transition-colors duration-200 group-hover:text-gold-deep md:col-span-4 md:text-2xl">
                    {area.nombre}
                  </h3>

                  <p className="relative text-xs leading-relaxed text-ink-soft md:col-span-5 md:col-start-7 lg:text-sm line-clamp-2 md:line-clamp-1">
                    {area.resumen}
                  </p>

                  <span className="hidden items-center justify-end gap-1.5 md:col-span-1 md:col-start-12 md:flex">
                    <span className="max-w-0 overflow-hidden whitespace-nowrap text-[11px] font-semibold uppercase tracking-[0.08em] text-gold-deep opacity-0 transition-all duration-300 group-hover:max-w-[5rem] group-hover:opacity-100">
                      Detalle
                    </span>
                    <ArrowUpRight
                      weight="bold"
                      className="h-4 w-4 shrink-0 text-ink-soft transition-transform duration-300 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 group-hover:text-gold-deep"
                    />
                  </span>
                </button>
              </motion.div>
            );
          })}
        </div>
      </div>

      {/* Pop up integrado para ver detalles, textos y botones de cada área */}
      <Dialog.Root
        open={seleccionada !== null}
        onOpenChange={(next) => !next && setSeleccionada(null)}
      >
        <Dialog.Portal>
          <Dialog.Backdrop className="fixed inset-0 z-50 bg-night/75 backdrop-blur-md transition-opacity duration-300 data-[ending-style]:opacity-0 data-[starting-style]:opacity-0" />
          <Dialog.Popup className="fixed inset-x-3 sm:inset-x-4 top-1/2 z-50 mx-auto max-h-[88vh] max-w-2xl -translate-y-1/2 overflow-y-auto rounded-3xl sm:rounded-[2rem] bg-paper p-5 sm:p-9 shadow-[0_35px_70px_-15px_rgba(0,0,0,0.55)] ring-1 ring-line transition-all duration-300 data-[ending-style]:scale-95 data-[ending-style]:opacity-0 data-[starting-style]:scale-95 data-[starting-style]:opacity-0">
            <Dialog.Close
              aria-label="Cerrar"
              className="absolute right-4 top-4 sm:right-5 sm:top-5 flex h-9 w-9 items-center justify-center rounded-full text-ink-soft transition-colors hover:bg-ink/5 hover:text-ink"
            >
              <X className="h-5 w-5" />
            </Dialog.Close>

            {seleccionada && (
              <div>
                {/* Cabecera del popup */}
                <div className="flex items-center gap-4 pr-8">
                  {IconoSeleccionado && (
                    <span
                      style={{ backgroundImage: gradientes[indiceSeleccionada] }}
                      className={`flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl shadow-md ${iconoTinta[indiceSeleccionada]}`}
                    >
                      <IconoSeleccionado weight="duotone" className="h-7 w-7" />
                    </span>
                  )}
                  <div>
                    <span className="text-xs font-semibold uppercase tracking-widest text-gold-deep">
                      {`Área 0${indiceSeleccionada + 1} · Especialidad Jurídica`}
                    </span>
                    <Dialog.Title className="mt-1 font-display text-2xl leading-tight text-ink sm:text-3xl">
                      {seleccionada.nombre}
                    </Dialog.Title>
                  </div>
                </div>

                {/* Resumen principal */}
                <Dialog.Description className="mt-5 text-base font-medium leading-relaxed text-ink/85 sm:text-lg">
                  {seleccionada.resumen}
                </Dialog.Description>

                {/* Explicación detallada (párrafos) */}
                <div className="mt-6 space-y-3">
                  {seleccionada.descripcion.map((parrafo, idx) => (
                    <div
                      key={idx}
                      className="flex items-start gap-3.5 rounded-2xl bg-surface p-4 shadow-xs ring-1 ring-line/70"
                    >
                      <div className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-gold" />
                      <p className="text-sm leading-relaxed text-ink-soft sm:text-base">
                        {parrafo}
                      </p>
                    </div>
                  ))}
                </div>

                {/* Botones de acción directa para captación */}
                <div className="mt-8 flex flex-col sm:flex-row items-stretch sm:items-center gap-3 sm:gap-4">
                  <MagneticButton strength={0.35} className="w-full sm:w-auto">
                    <a
                      href={`https://wa.me/573243668845?text=${encodeURIComponent(seleccionada.whatsappMensaje)}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="group inline-flex w-full sm:w-auto items-center justify-center gap-2 rounded-xl bg-[#25D366] px-5 py-3 text-sm font-semibold text-white shadow-sm transition-all duration-300 hover:bg-[#20bd5a] hover:shadow-md active:scale-[0.98]"
                    >
                      <span>Escríbenos por WhatsApp</span>
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

                {/* Selector rápido para cambiar entre áreas sin cerrar */}
                <div className="mt-8 border-t border-line pt-5">
                  <p className="text-xs font-semibold uppercase tracking-wider text-ink-soft/70">
                    Explorar otras áreas de práctica
                  </p>
                  <div className="mt-3 flex flex-wrap gap-2">
                    {areasPractica
                      .filter((a) => a.slug !== seleccionada.slug)
                      .map((otra) => (
                        <button
                          key={otra.slug}
                          type="button"
                          onClick={() => setSeleccionada(otra)}
                          className="cursor-pointer rounded-full bg-ink/5 px-3.5 py-1.5 text-xs font-medium text-ink transition-all hover:bg-gold/15 hover:text-gold-deep"
                        >
                          {otra.nombre}
                        </button>
                      ))}
                  </div>
                </div>
              </div>
            )}
          </Dialog.Popup>
        </Dialog.Portal>
      </Dialog.Root>
    </section>
  );
}
