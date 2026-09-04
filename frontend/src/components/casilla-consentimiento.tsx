"use client";

import { AnimatePresence, motion } from "motion/react";
import type { ReactNode } from "react";

const EASE = [0.16, 1, 0.3, 1] as const;

// Casilla de verificación de marca, usada en todo formulario público que pide
// consentimiento (agendar-asesoria.tsx, testimonio-form-modal.tsx): reemplaza el
// checkbox nativo del navegador, cuyo aspecto varía entre SO/navegador y no tenía
// ninguna relación visual con el resto del sitio. El input real sigue ahí para
// accesibilidad/teclado, solo queda invisible encima del cuadro dibujado a mano; el
// check se dibuja con Motion en vez de aparecer de golpe.
export function CasillaConsentimiento({
  checked,
  onChange,
  children,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  children: ReactNode;
}) {
  return (
    <label className="flex cursor-pointer items-start gap-3 text-xs leading-relaxed text-ink-soft">
      <span className="relative mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center">
        <input
          type="checkbox"
          checked={checked}
          onChange={(e) => onChange(e.target.checked)}
          className="peer absolute inset-0 z-10 h-full w-full cursor-pointer opacity-0"
        />
        <span
          className={`flex h-5 w-5 items-center justify-center rounded-md border transition-colors duration-200 peer-focus-visible:ring-4 peer-focus-visible:ring-gold/20 ${
            checked ? "border-gold-deep bg-gold" : "border-line bg-surface peer-hover:border-gold-deep/50"
          }`}
        >
          <AnimatePresence>
            {checked && (
              <motion.svg
                initial={{ scale: 0, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                exit={{ scale: 0, opacity: 0 }}
                transition={{ duration: 0.2, ease: EASE }}
                viewBox="0 0 16 16"
                fill="none"
                className="h-3 w-3"
              >
                <path
                  d="M3 8.5L6.2 11.5L13 4.5"
                  stroke="var(--color-ink-fixed)"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </motion.svg>
            )}
          </AnimatePresence>
        </span>
      </span>
      {children}
    </label>
  );
}
