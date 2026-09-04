"use client";

import Link from "next/link";
import { motion } from "motion/react";
import { ArrowLeft } from "@phosphor-icons/react";
import type { ReactNode } from "react";

const EASE = [0.16, 1, 0.3, 1] as const;

// Entrada única y sobria para el encabezado del artículo (nunca animación por párrafo:
// esto es una página de lectura, no una vitrina -- de-sincronizar el texto del cuerpo
// distraería en vez de ayudar). El cuerpo del artículo (dangerouslySetInnerHTML) se queda
// fuera de este componente y sin animar, a propósito.
export function ArticuloCabecera({
  categoria,
  titulo,
  meta,
  imagen,
}: {
  categoria: string;
  titulo: string;
  meta: string;
  imagen?: ReactNode;
}) {
  return (
    <>
      <motion.div
        initial={{ opacity: 0, y: -8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, ease: EASE }}
      >
        <Link
          href="/blog"
          className="inline-flex items-center gap-2 text-sm font-medium text-ink-soft transition-colors hover:text-ink"
        >
          <ArrowLeft weight="bold" className="h-4 w-4" />
          Volver al blog
        </Link>
      </motion.div>

      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, delay: 0.08, ease: EASE }}
      >
        <p className="mt-8 text-xs font-medium uppercase tracking-[0.1em] text-gold-deep">{categoria}</p>
        <h1 className="mt-3 text-balance font-display text-3xl leading-tight tracking-tight md:text-5xl">
          {titulo}
        </h1>
        <p className="mt-5 text-sm text-ink-soft">{meta}</p>
      </motion.div>

      {imagen && (
        <motion.div
          initial={{ opacity: 0, scale: 0.98 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.6, delay: 0.16, ease: EASE }}
        >
          {imagen}
        </motion.div>
      )}
    </>
  );
}
