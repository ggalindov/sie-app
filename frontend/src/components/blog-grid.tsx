"use client";

import Link from "next/link";
import { motion } from "motion/react";
import type { ArticuloResumen } from "@/lib/api";

const EASE = [0.16, 1, 0.3, 1] as const;

// timeZone explícito: sin esto, toLocaleDateString usa la zona del entorno donde corre el
// código (el navegador de quien mire la página, o el contenedor del servidor en SSR --
// típicamente UTC), no la de Colombia. Bug real reportado por el usuario: un artículo
// publicado de noche en Bogotá (después de las 7pm, medianoche UTC) mostraba la fecha del
// día siguiente.
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
  visible: { transition: { staggerChildren: 0.06 } },
};
const staggerItem = {
  oculto: { opacity: 0, y: 22 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.55, ease: EASE } },
};

// Parte animada de /blog (ver app/blog/page.tsx, que se queda como Server Component para
// el fetch + filtros por searchParams): antes la grilla entera aparecía de golpe en el
// primer render, sin ninguna entrada ni polish de hover más allá del zoom de la imagen --
// la única página del sitio con listados que no tenía ningún whileInView.
export function BlogGrid({ articulos }: { articulos: ArticuloResumen[] }) {
  return (
    <motion.div
      variants={staggerContenedor}
      initial="oculto"
      animate="visible"
      className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3"
    >
      {articulos.map((articulo) => (
        <motion.div key={articulo.slug} variants={staggerItem}>
          <Link
            href={`/blog/${articulo.slug}`}
            className="group flex h-full flex-col overflow-hidden rounded-3xl bg-surface ring-1 ring-line transition-all duration-300 hover:-translate-y-1 hover:shadow-[0_28px_60px_-28px_rgba(20,19,15,0.35)] hover:ring-gold-deep/30"
          >
            <div className="flex aspect-[16/10] items-center justify-center overflow-hidden bg-night">
              {articulo.imagenUrl ? (
                // eslint-disable-next-line @next/next/no-img-element -- enlace externo arbitrario pegado por el admin
                <img
                  src={articulo.imagenUrl}
                  alt={articulo.titulo}
                  className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
                />
              ) : (
                <span className="font-display text-2xl text-gold/40">{articulo.categoria.nombre}</span>
              )}
            </div>
            <div className="flex flex-1 flex-col p-6">
              <p className="flex items-center gap-2 text-xs font-medium uppercase tracking-[0.1em] text-gold-deep">
                {articulo.categoria.nombre}
                {articulo.tipoContenido === "NOTICIA" && (
                  <span className="rounded-full bg-gold/10 px-2 py-0.5 text-[10px] text-gold-deep">Noticia</span>
                )}
              </p>
              <h2 className="mt-2 font-display text-lg leading-snug transition-colors duration-300 group-hover:text-gold-deep">
                {articulo.titulo}
              </h2>
              <p className="mt-2 flex-1 text-sm leading-relaxed text-ink-soft">{articulo.resumen}</p>
              <p className="mt-4 text-xs text-ink-soft">
                {formatearFecha(articulo.fechaPublicacion)}
                {articulo.tiempoLecturaMin && ` · ${articulo.tiempoLecturaMin} min`}
              </p>
            </div>
          </Link>
        </motion.div>
      ))}
    </motion.div>
  );
}
