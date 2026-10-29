"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { AnimatePresence, motion } from "motion/react";
import { FileMagnifyingGlass } from "@phosphor-icons/react";

const EASE = [0.16, 1, 0.3, 1] as const;

// Acceso directo a "Consulta tu caso", visible en cualquier página. Pedido explícito del
// usuario (segunda vuelta de ajustes):
// 1. Va DEBAJO de todo el resto de la pila flotante (WhatsApp, Cuida tu marca), no encima
//    -- es la acción de autoservicio más usada por clientes que ya tienen un radicado, así
//    que debe ser la más fácil de encontrar con la vista, pegada a la esquina.
// 2. En escritorio queda SIEMPRE expandida mostrando el texto completo "Consulta tu caso"
//    -- nunca depende de pasar el mouse para que alguien sepa qué es. Muchos de quienes
//    usan esto son personas mayores que no van a "descubrir" un ícono con hover.
// 3. En celular (sin hover posible) se revela sola una vez, poco después de aparecer, para
//    enseñar qué es, y luego se repliega a un ícono discreto -- animada pero sin exagerar
//    ni repetirse en bucle, que terminaría siendo molesto.
export function ConsultaCasoFloat() {
  const [expandidoMovil, setExpandidoMovil] = useState(false);

  useEffect(() => {
    const abrir = window.setTimeout(() => setExpandidoMovil(true), 2600);
    const cerrar = window.setTimeout(() => setExpandidoMovil(false), 6200);
    return () => {
      window.clearTimeout(abrir);
      window.clearTimeout(cerrar);
    };
  }, []);

  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.8 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.5, delay: 1.15, ease: EASE }}
      // La pila flotante completa, de abajo hacia arriba: Consulta tu caso (bottom-6, el
      // más cercano al borde), WhatsApp (bottom-22/24), Cuida tu marca solo en escritorio
      // (sm:bottom-44). right-5/sm:right-6 para alinear el borde derecho con los demás
      // círculos (48px de diámetro en reposo en móvil, 56px alto fijo en escritorio).
      className="fixed bottom-5 right-5 z-40 sm:right-6"
    >
      <div>
        <Link
          href="/consulta-caso"
          aria-label="Consulta el estado de tu caso"
          className="btn-sweep flex h-12 items-center gap-2 overflow-hidden rounded-full bg-night pl-3.5 pr-3.5 text-night-ink shadow-[0_10px_26px_-10px_rgba(0,0,0,0.5)] ring-1 ring-gold/25 sm:h-14 sm:pl-4 sm:pr-4"
        >
          <FileMagnifyingGlass weight="bold" className="h-5 w-5 shrink-0 text-gold sm:h-6 sm:w-6" />

          {/* Escritorio: siempre expandido, sin AnimatePresence -- el texto es parte
              permanente del botón, no una revelación. */}
          <span className="hidden overflow-hidden whitespace-nowrap text-sm font-medium sm:inline">
            Consulta tu caso
          </span>

          {/* Móvil: revelación única, suave, breve -- ver useEffect de arriba. */}
          <AnimatePresence initial={false}>
            {expandidoMovil && (
              <motion.span
                initial={{ width: 0, opacity: 0 }}
                animate={{ width: "auto", opacity: 1 }}
                exit={{ width: 0, opacity: 0 }}
                transition={{ duration: 0.4, ease: EASE }}
                className="overflow-hidden whitespace-nowrap text-sm font-medium sm:hidden"
              >
                Consulta tu caso
              </motion.span>
            )}
          </AnimatePresence>
        </Link>
      </div>
    </motion.div>
  );
}
