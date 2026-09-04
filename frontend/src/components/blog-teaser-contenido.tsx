"use client";

import Link from "next/link";
import { motion } from "motion/react";
import { ArrowUpRight } from "@phosphor-icons/react";
import type { ArticuloResumen } from "@/lib/api";

const EASE = [0.16, 1, 0.3, 1] as const;

// timeZone explícito: ver el mismo comentario en blog-grid.tsx -- sin esto, la fecha se
// muestra en la zona de quien renderiza (navegador o servidor), no en la de Colombia.
function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", {
    day: "numeric",
    month: "long",
    year: "numeric",
    timeZone: "America/Bogota",
  });
}

const staggerContenedor = {
  oculto: {},
  visible: { transition: { staggerChildren: 0.1, delayChildren: 0.05 } },
};
const staggerItem = {
  oculto: { opacity: 0, y: 22 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.6, ease: EASE } },
};

// La parte visual e interactiva de BlogTeaser vive aparte del fetch (ver blog-teaser.tsx,
// que se queda como Server Component): un componente "use client" no puede ser async, así
// que la entrada animada con Motion se separa aquí. Antes esta sección era la única del
// home sin ninguna animación de aparición -- las tarjetas simplemente estaban ahí desde el
// primer render, mientras el resto de secciones ya usa whileInView en todas partes.
export function BlogTeaserVacio() {
  return (
    <motion.div
      initial={{ opacity: 0, y: 24 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, amount: 0.4 }}
      transition={{ duration: 0.7, ease: EASE }}
      className="mt-10 flex min-h-[220px] flex-col items-center justify-center rounded-3xl bg-surface p-10 text-center ring-1 ring-line"
      style={{
        backgroundImage: "radial-gradient(60% 80% at 50% 0%, rgba(217,169,37,0.1), transparent 70%)",
      }}
    >
      <p className="font-display text-xl">Estamos preparando nuestros primeros artículos.</p>
      <p className="mt-2 max-w-sm text-sm leading-relaxed text-ink-soft">
        Muy pronto encontrarás aquí contenido jurídico útil sobre las áreas en las que te
        podemos ayudar.
      </p>
    </motion.div>
  );
}

export function BlogTeaserLista({
  destacado,
  resto,
}: {
  destacado: ArticuloResumen;
  resto: ArticuloResumen[];
}) {
  return (
    <motion.div
      variants={staggerContenedor}
      initial="oculto"
      whileInView="visible"
      viewport={{ once: true, amount: 0.2 }}
      className="mt-10 grid gap-7 md:grid-cols-2"
    >
      <motion.div variants={staggerItem} className="md:row-span-2">
        <Link
          href={`/blog/${destacado.slug}`}
          className="group flex h-full flex-col overflow-hidden rounded-3xl bg-surface ring-1 ring-line transition-all duration-300 hover:-translate-y-1 hover:shadow-[0_28px_60px_-28px_rgba(20,19,15,0.35)] hover:ring-gold-deep/30"
        >
          <div className="flex aspect-[16/9] items-center justify-center overflow-hidden bg-night md:aspect-auto md:min-h-[240px] md:flex-1">
            {destacado.imagenUrl ? (
              // eslint-disable-next-line @next/next/no-img-element -- enlace externo arbitrario pegado por el admin
              <img
                src={destacado.imagenUrl}
                alt={destacado.titulo}
                className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
              />
            ) : (
              <span className="font-display text-4xl text-gold/40">{destacado.categoria.nombre}</span>
            )}
          </div>
          <div className="p-8">
            <p className="flex items-center gap-2 text-xs font-medium uppercase tracking-[0.1em] text-gold-deep">
              {destacado.categoria.nombre}
              {destacado.tipoContenido === "NOTICIA" && (
                <span className="rounded-full bg-gold/10 px-2 py-0.5 text-[10px] text-gold-deep">Noticia</span>
              )}
            </p>
            <h3 className="mt-3 font-display text-2xl leading-snug transition-colors duration-300 group-hover:text-gold-deep md:text-3xl">
              {destacado.titulo}
            </h3>
            <p className="mt-3 text-sm leading-relaxed text-ink-soft">{destacado.resumen}</p>
            <p className="mt-5 flex items-center gap-1.5 text-xs text-ink-soft">
              {formatearFecha(destacado.fechaPublicacion)}
              {destacado.tiempoLecturaMin && ` · ${destacado.tiempoLecturaMin} min de lectura`}
              <ArrowUpRight
                weight="bold"
                className="h-3.5 w-3.5 shrink-0 text-gold-deep opacity-0 transition-all duration-300 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 group-hover:opacity-100"
              />
            </p>
          </div>
        </Link>
      </motion.div>

      <div className="flex flex-col gap-7">
        {resto.map((articulo) => (
          <motion.div key={articulo.slug} variants={staggerItem}>
            <Link
              href={`/blog/${articulo.slug}`}
              className="group flex items-center gap-5 rounded-3xl bg-surface p-5 ring-1 ring-line transition-all duration-300 hover:-translate-y-0.5 hover:shadow-[0_20px_45px_-24px_rgba(20,19,15,0.3)] hover:ring-gold-deep/30"
            >
              <div className="flex h-24 w-24 shrink-0 items-center justify-center overflow-hidden rounded-2xl bg-night md:h-28 md:w-28">
                {articulo.imagenUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element -- enlace externo arbitrario pegado por el admin
                  <img
                    src={articulo.imagenUrl}
                    alt={articulo.titulo}
                    className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
                  />
                ) : (
                  <span className="font-display text-xs text-gold/40">{articulo.categoria.nombre}</span>
                )}
              </div>
              <div className="min-w-0 flex-1">
                <p className="flex items-center gap-2 text-xs font-medium uppercase tracking-[0.1em] text-gold-deep">
                  {articulo.categoria.nombre}
                  {articulo.tipoContenido === "NOTICIA" && (
                    <span className="rounded-full bg-gold/10 px-2 py-0.5 text-[10px] text-gold-deep">Noticia</span>
                  )}
                </p>
                <h3 className="mt-2 font-display text-lg leading-snug transition-colors duration-300 group-hover:text-gold-deep md:text-xl">
                  {articulo.titulo}
                </h3>
                <p className="mt-2 text-xs text-ink-soft">{formatearFecha(articulo.fechaPublicacion)}</p>
              </div>
              <ArrowUpRight
                weight="bold"
                className="hidden h-5 w-5 shrink-0 text-ink-soft transition-transform duration-300 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 sm:block"
              />
            </Link>
          </motion.div>
        ))}
      </div>
    </motion.div>
  );
}
