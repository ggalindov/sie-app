"use client";

import Link from "next/link";
import { motion } from "motion/react";
import {
  ArrowRight,
  ArrowUpRight,
  BookOpenText,
  Clock,
  Scales,
} from "@phosphor-icons/react";
import type { ArticuloResumen } from "@/lib/api";
import { MagneticButton } from "@/components/magnetic-button";

const EASE = [0.16, 1, 0.3, 1] as const;

function formatearFecha(iso: string) {
  try {
    return new Date(iso).toLocaleDateString("es-CO", {
      day: "numeric",
      month: "short",
      year: "numeric",
      timeZone: "America/Bogota",
    });
  } catch {
    return "";
  }
}

const staggerContenedor = {
  oculto: {},
  visible: { transition: { staggerChildren: 0.1, delayChildren: 0.05 } },
};

const staggerItem = {
  oculto: { opacity: 0, y: 22 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.6, ease: EASE } },
};

export function BlogTeaserVacio() {
  return (
    <motion.div
      initial={{ opacity: 0, y: 24 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, amount: 0.4 }}
      transition={{ duration: 0.7, ease: EASE }}
      className="relative overflow-hidden rounded-3xl border border-line bg-surface p-10 text-center shadow-sm md:p-14"
      style={{
        backgroundImage:
          "radial-gradient(70% 60% at 50% 0%, rgba(217,169,37,0.12), transparent 70%)",
      }}
    >
      <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-gold/15 text-gold-deep shadow-sm">
        <BookOpenText weight="duotone" className="h-7 w-7" />
      </div>
      <h3 className="mt-5 font-display text-2xl text-ink md:text-3xl">
        Estamos preparando nuevos análisis jurídicos
      </h3>
      <p className="mx-auto mt-2 max-w-lg text-sm leading-relaxed text-ink-soft md:text-base">
        Próximamente publicaremos artículos sobre precedentes clave en derecho laboral,
        civil, comercial y administrativo. Si tienes una consulta actual, nuestro equipo
        está disponible para atender tu caso hoy mismo.
      </p>
      <div className="mt-8 flex flex-wrap items-center justify-center gap-4">
        <MagneticButton strength={0.35}>
          <Link
            href="#agendar"
            className="cta-boton group inline-flex items-center gap-2 rounded-xl bg-gold px-5 py-2.5 sm:px-7 sm:py-3.5 text-xs sm:text-sm font-semibold text-ink-fixed shadow-sm transition-all duration-300 hover:shadow-md active:scale-[0.98]"
          >
            <span className="relative z-10">Agendar asesoría de mi caso</span>
            <ArrowRight
              weight="bold"
              className="relative z-10 h-3.5 w-3.5 sm:h-4 sm:w-4 transition-transform duration-300 group-hover:translate-x-1"
            />
          </Link>
        </MagneticButton>
      </div>
    </motion.div>
  );
}

export function BlogTeaserContenido({
  articulos,
}: {
  articulos: ArticuloResumen[];
}) {
  if (articulos.length === 0) {
    return <BlogTeaserVacio />;
  }

  const [destacado, ...resto] = articulos;

  return (
    <motion.div
      variants={staggerContenedor}
      initial="oculto"
      whileInView="visible"
      viewport={{ once: true, amount: 0.15 }}
      className="grid gap-6 xl:gap-8 lg:grid-cols-12 lg:items-stretch"
    >
      {/* Tarjeta Principal Hero / Destacada (7 columnas) */}
      <motion.div variants={staggerItem} className="lg:col-span-7">
        <Link
          href={`/blog/${destacado.slug}`}
          className="group relative flex h-[380px] sm:h-[420px] lg:h-[500px] xl:h-[550px] 2xl:h-[580px] flex-col justify-end overflow-hidden rounded-3xl bg-night ring-1 ring-line/50 transition-all duration-500 hover:-translate-y-1 hover:shadow-[0_28px_60px_-24px_rgba(20,19,15,0.45)] hover:ring-gold/60"
        >
          {destacado.imagenUrl ? (
            // eslint-disable-next-line @next/next/no-img-element -- enlace externo arbitrario pegado por el admin
            <img
              src={destacado.imagenUrl}
              alt={destacado.titulo}
              className="absolute inset-0 h-full w-full object-cover transition-transform duration-700 ease-out group-hover:scale-105"
            />
          ) : (
            <div className="absolute inset-0 flex items-center justify-center bg-gradient-to-br from-night via-[#1a1712] to-night">
              <span className="select-none font-display text-7xl xl:text-8xl text-gold/10">
                {destacado.categoria.nombre}
              </span>
            </div>
          )}

          {/* Velo degradado cinematográfico para legibilidad y elegancia */}
          <div className="absolute inset-0 bg-gradient-to-t from-night/95 via-night/60 to-night/15" />

          {/* Contenido de la tarjeta destacada */}
          <div className="relative z-10 flex flex-col justify-end p-5 sm:p-7 md:p-8 xl:p-10">
            <div className="flex flex-wrap items-center gap-2 xl:gap-2.5">
              <span className="rounded-full bg-gold px-3 py-0.5 xl:px-3.5 xl:py-1 text-[11px] xl:text-xs font-semibold uppercase tracking-wider text-ink-fixed shadow-xs">
                {destacado.categoria.nombre}
              </span>
              {destacado.tipoContenido === "NOTICIA" && (
                <span className="rounded-full bg-surface/90 px-2.5 py-0.5 xl:px-3 xl:py-1 text-[11px] xl:text-xs font-medium text-ink backdrop-blur-md">
                  Noticia
                </span>
              )}
              {destacado.tiempoLecturaMin && (
                <span className="inline-flex items-center gap-1 rounded-full bg-night/80 px-2.5 py-0.5 xl:px-3 xl:py-1 text-[11px] xl:text-xs text-night-ink/85 ring-1 ring-night-ink/20 backdrop-blur-md">
                  <Clock weight="bold" className="h-3 w-3 xl:h-3.5 xl:w-3.5 text-gold" />
                  {destacado.tiempoLecturaMin} min de lectura
                </span>
              )}
            </div>

            <h3 className="mt-3 xl:mt-4 font-display text-xl sm:text-2xl lg:text-3xl xl:text-4xl leading-snug text-night-ink transition-colors duration-300 group-hover:text-gold line-clamp-2">
              {destacado.titulo}
            </h3>

            {destacado.resumen && (
              <p className="mt-2 xl:mt-3 line-clamp-2 text-xs sm:text-sm xl:text-base leading-relaxed text-night-ink/75">
                {destacado.resumen}
              </p>
            )}

            <div className="mt-4 xl:mt-5 flex flex-wrap items-center justify-between gap-2 border-t border-night-ink/15 pt-3.5 xl:pt-4 text-xs xl:text-sm text-night-ink/65">
              <div className="flex min-w-0 items-center gap-2">
                <span className="truncate max-w-[130px] font-medium text-night-ink/90 sm:max-w-none">
                  {destacado.autorNombre || "Equipo SIE Jurídicos"}
                </span>
                <span>·</span>
                <span className="shrink-0">{formatearFecha(destacado.fechaPublicacion)}</span>
              </div>

              <span className="inline-flex shrink-0 items-center gap-1.5 font-medium text-gold transition-transform duration-300 group-hover:translate-x-1">
                Leer artículo
                <ArrowRight weight="bold" className="h-3.5 w-3.5 xl:h-4 xl:w-4" />
              </span>
            </div>
          </div>
        </Link>
      </motion.div>

      {/* Columna Lateral (5 columnas): Artículos complementarios y Tarjeta de Conversión */}
      <div className="flex flex-col justify-between gap-3.5 xl:gap-4 lg:col-span-5 lg:h-[500px] xl:h-[550px] 2xl:h-[580px]">
        <div className="flex flex-col gap-3 xl:gap-3.5">
          {resto.map((articulo) => (
            <motion.div key={articulo.slug} variants={staggerItem}>
              <Link
                href={`/blog/${articulo.slug}`}
                className="group flex gap-3.5 xl:gap-4 rounded-2xl xl:rounded-3xl border border-line bg-surface p-3 xl:p-4 transition-all duration-300 hover:-translate-y-0.5 hover:border-gold/40 hover:bg-surface/80 hover:shadow-xs"
              >
                <div className="relative h-16 w-16 sm:h-18 sm:w-18 xl:h-22 xl:w-22 shrink-0 overflow-hidden rounded-xl xl:rounded-2xl bg-night">
                  {articulo.imagenUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element -- enlace externo arbitrario pegado por el admin
                    <img
                      src={articulo.imagenUrl}
                      alt={articulo.titulo}
                      className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
                    />
                  ) : (
                    <div className="flex h-full w-full items-center justify-center bg-gold/10">
                      <span className="font-display text-xs xl:text-sm font-semibold text-gold-deep">
                        {articulo.categoria.nombre.slice(0, 3)}
                      </span>
                    </div>
                  )}
                </div>

                <div className="flex min-w-0 flex-1 flex-col justify-between py-0.5 xl:py-1">
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-[10px] xl:text-xs font-semibold uppercase tracking-wider text-gold-deep">
                        {articulo.categoria.nombre}
                      </span>
                      {articulo.tiempoLecturaMin && (
                        <>
                          <span className="text-ink-soft/40">·</span>
                          <span className="text-[10px] xl:text-xs text-ink-soft">
                            {articulo.tiempoLecturaMin} min
                          </span>
                        </>
                      )}
                    </div>
                    <h4 className="mt-0.5 line-clamp-2 font-display text-sm sm:text-base xl:text-lg leading-snug text-ink transition-colors duration-200 group-hover:text-gold-deep">
                      {articulo.titulo}
                    </h4>
                  </div>

                  <div className="mt-1.5 flex items-center justify-between text-[11px] xl:text-xs text-ink-soft">
                    <span>{formatearFecha(articulo.fechaPublicacion)}</span>
                    <ArrowUpRight
                      weight="bold"
                      className="h-3.5 w-3.5 xl:h-4 xl:w-4 text-gold-deep opacity-0 transition-all duration-200 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 group-hover:opacity-100"
                    />
                  </div>
                </div>
              </Link>
            </motion.div>
          ))}

          {resto.length === 0 && (
            <motion.div variants={staggerItem}>
              <div className="rounded-2xl border border-line bg-surface p-4 xl:p-6 text-sm text-ink-soft">
                <p className="font-display text-base xl:text-lg text-ink">
                  Más análisis y jurisprudencia en camino
                </p>
                <p className="mt-1 text-xs xl:text-sm leading-relaxed text-ink-soft">
                  Monitoreamos permanentemente las sentencias de las altas cortes para
                  mantenerte actualizado.
                </p>
              </div>
            </motion.div>
          )}
        </div>

        {/* Tarjeta Directa de Conversión Legal (Convierte lectores en clientes) */}
        <motion.div variants={staggerItem}>
          <div className="group relative overflow-hidden rounded-2xl xl:rounded-3xl border border-gold/35 bg-gradient-to-br from-gold/15 via-surface to-gold/5 p-4 xl:p-5 shadow-xs ring-1 ring-gold/20 transition-all duration-300 hover:shadow-sm hover:ring-gold/40">
            <div className="flex items-start gap-3.5 xl:gap-4">
              <div className="flex h-10 w-10 xl:h-12 xl:w-12 shrink-0 items-center justify-center rounded-xl xl:rounded-2xl bg-gold text-ink-fixed shadow-xs transition-transform duration-300 group-hover:scale-105">
                <Scales weight="fill" className="h-5 w-5 xl:h-6 xl:w-6" />
              </div>
              <div className="min-w-0 flex-1">
                <span className="text-[10px] xl:text-xs font-semibold uppercase tracking-wider text-gold-deep">
                  Orientación Especializada
                </span>
                <h4 className="mt-0.5 font-display text-sm sm:text-base xl:text-lg leading-snug text-ink">
                  ¿Tu situación legal se relaciona con estos temas?
                </h4>
                <p className="mt-1 line-clamp-2 text-xs xl:text-sm leading-relaxed text-ink-soft">
                  Revisamos los antecedentes de tu caso sin costo inicial de consulta para orientarte con claridad.
                </p>
                <MagneticButton strength={0.3}>
                  <Link
                    href="#agendar"
                    className="cta-boton group/btn mt-2.5 xl:mt-3 inline-flex items-center gap-1.5 rounded-lg bg-gold px-3.5 py-1.5 xl:px-4.5 xl:py-2 text-xs xl:text-sm font-semibold text-ink-fixed transition-all duration-300 hover:bg-gold-deep hover:text-white active:scale-[0.98]"
                  >
                    <span className="relative z-10">Consultar con un abogado</span>
                    <ArrowRight
                      weight="bold"
                      className="relative z-10 h-3 w-3 xl:h-3.5 xl:w-3.5 transition-transform duration-300 group-hover/btn:translate-x-1"
                    />
                  </Link>
                </MagneticButton>
              </div>
            </div>
          </div>
        </motion.div>
      </div>
    </motion.div>
  );
}
