"use client";

import { useState } from "react";
import Link from "next/link";
import { AnimatePresence, motion } from "motion/react";
import { FileMagnifyingGlass } from "@phosphor-icons/react";

const EASE = [0.16, 1, 0.3, 1] as const;

// Acceso directo a "Consulta tu caso", visible en cualquier página sin tener que abrir el
// menú de ayuda primero (antes solo vivía dentro de HelpMenu, a dos clics de distancia).
// Pedido explícito del usuario: mismo lenguaje visual que los otros dos flotantes de la
// esquina inferior derecha (WhatsappFloat, CuidaMarcaFloat) -- circular en reposo, con
// etiqueta que se revela al pasar el mouse -- después de probar una pestaña lateral que
// resultaba menos reconocible. Apilado arriba de los otros dos (ver bottom-44) y más
// pequeño que ambos (h-12, no h-14): es una acción de autoservicio secundaria, no la vía
// principal de contacto.
export function ConsultaCasoFloat() {
  const [hover, setHover] = useState(false);

  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.8 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.5, delay: 1.15, ease: EASE }}
      // right-7, no right-6 como los otros dos flotantes: este botón mide 48px de
      // diámetro (h-12) contra los 56px (h-14) de WhatsApp/Cuida tu marca, así que con el
      // mismo "right" sus CENTROS quedan 4px desalineados entre sí (el radio es distinto
      // aunque el borde derecho coincida) -- el corrimiento que se veía en la captura. 4px
      // extra de margen compensa exactamente esa diferencia de radio para que los tres
      // círculos queden perfectamente centrados en el mismo eje vertical.
      className="fixed bottom-44 right-7 z-40"
    >
      {/* onHoverStart/onHoverEnd viven en un div normal aparte, nunca en el mismo
          motion.div que anima la entrada con delay -- bug real encontrado en vivo: con
          ambas cosas juntas, la animación de entrada se quedaba congelada a mitad de
          camino (opacity/scale nunca llegaban al valor final, se veía "torcido"/a medio
          tamaño) tanto aquí como en CuidaMarcaFloat, que tiene el mismo patrón. Separar
          el hover a un elemento sin animación propia lo resuelve sin perder el efecto. */}
      <div onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)}>
        <Link
          href="/consulta-caso"
          aria-label="Consulta el estado de tu caso"
          className="btn-sweep flex h-12 items-center gap-2 overflow-hidden rounded-full bg-night pl-3.5 pr-3.5 text-night-ink shadow-[0_10px_26px_-10px_rgba(0,0,0,0.5)] ring-1 ring-gold/25"
        >
          <FileMagnifyingGlass weight="bold" className="h-5 w-5 shrink-0 text-gold" />
          <AnimatePresence initial={false}>
            {hover && (
              <motion.span
                initial={{ width: 0, opacity: 0 }}
                animate={{ width: "auto", opacity: 1 }}
                exit={{ width: 0, opacity: 0 }}
                transition={{ duration: 0.25, ease: EASE }}
                className="overflow-hidden whitespace-nowrap text-sm font-medium"
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
