"use client";

import { useState } from "react";
import Link from "next/link";
import { AnimatePresence, motion } from "motion/react";
import { SealCheck } from "@phosphor-icons/react";

// Botón flotante que lleva a la landing gemela /cuida-tu-marca (pedido explícito del
// usuario: "algo interactivo en el landing que me lleve a lo de cuida tu marca").
// Encima del de WhatsApp (bottom-6 right-6) para no chocar con él, con su propia
// etiqueta que se expande al pasar el mouse, igual de discreto en reposo.
export function CuidaMarcaFloat() {
  const [hover, setHover] = useState(false);

  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.8 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.5, delay: 1, ease: [0.16, 1, 0.3, 1] }}
      className="fixed bottom-24 right-6 z-40"
    >
      {/* onHoverStart/onHoverEnd viven en un div normal aparte, nunca en el mismo
          motion.div que anima la entrada con delay -- bug real encontrado en vivo: con
          ambas cosas juntas, la animación de entrada se quedaba congelada a mitad de
          camino (opacity/scale nunca llegaban al valor final). Separar el hover a un
          elemento sin animación propia lo resuelve sin perder el efecto. */}
      <div onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)}>
        <Link
          href="/cuida-tu-marca"
          aria-label="Protege tu marca: conoce cómo te ayudamos"
          className="btn-sweep flex h-14 items-center gap-2 overflow-hidden rounded-full bg-gold pl-4 pr-4 text-ink-fixed shadow-[0_10px_30px_-8px_rgba(169,124,22,0.6)]"
        >
          <SealCheck weight="fill" className="h-6 w-6 shrink-0" />
          <AnimatePresence initial={false}>
            {hover && (
              <motion.span
                initial={{ width: 0, opacity: 0 }}
                animate={{ width: "auto", opacity: 1 }}
                exit={{ width: 0, opacity: 0 }}
                transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
                className="overflow-hidden whitespace-nowrap text-sm font-medium"
              >
                Protege tu marca
              </motion.span>
            )}
          </AnimatePresence>
        </Link>
      </div>
    </motion.div>
  );
}
