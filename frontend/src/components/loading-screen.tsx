"use client";

import { useEffect, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import Image from "next/image";

const EASE = [0.16, 1, 0.3, 1] as const;
// Piso mínimo de aparición: en una conexión muy rápida "load" puede disparar en
// unos pocos ms, y una pantalla de carga que solo destella un instante se siente
// como un parpadeo roto, no como un momento de marca intencional.
const TIEMPO_MINIMO_MS = 600;
// Techo de seguridad: si "load" nunca dispara por lo que sea (una petición colgada
// de un recurso de terceros, por ejemplo), la pantalla de carga jamás debe atrapar
// al visitante indefinidamente.
const TIEMPO_MAXIMO_MS = 2500;

// Momento de marca al abrir el sitio, mientras terminan de asentarse fuentes,
// imágenes y el primer paint de las animaciones de fondo. Se queda montado hasta
// que la ventana dispara "load" (o hasta TIEMPO_MAXIMO_MS, lo que ocurra primero),
// nunca menos de TIEMPO_MINIMO_MS para que no se sienta como un parpadeo. Motion,
// no GSAP (regla del proyecto: nunca mezclar ambos en el mismo componente).
export function LoadingScreen() {
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    const inicio = Date.now();
    let resuelto = false;

    function ocultar() {
      if (resuelto) return;
      resuelto = true;
      const transcurrido = Date.now() - inicio;
      const espera = Math.max(0, TIEMPO_MINIMO_MS - transcurrido);
      window.setTimeout(() => setVisible(false), espera);
    }

    if (document.readyState === "complete") {
      ocultar();
    } else {
      window.addEventListener("load", ocultar);
    }
    const techo = window.setTimeout(ocultar, TIEMPO_MAXIMO_MS);

    return () => {
      window.removeEventListener("load", ocultar);
      window.clearTimeout(techo);
    };
  }, []);

  return (
    <AnimatePresence>
      {visible && (
        <motion.div
          initial={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.6, ease: EASE }}
          className="fixed inset-0 z-[100] flex items-center justify-center bg-night"
          aria-hidden="true"
        >
          <div
            className="absolute inset-0"
            style={{
              backgroundImage:
                "radial-gradient(60% 55% at 50% 45%, rgba(217,169,37,0.16), transparent 70%)",
            }}
          />

          <motion.div
            initial={{ opacity: 0, scale: 0.92 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.7, ease: EASE }}
            className="relative flex flex-col items-center"
          >
            <div className="relative h-24 w-24">
              {/* Anillo giratorio: mismo lenguaje visual que los anillos de
                  cifras de Quiénes Somos, aquí como indicador de progreso en
                  vez de contador. */}
              <motion.span
                aria-hidden="true"
                animate={{ rotate: 360 }}
                transition={{ duration: 2.2, repeat: Infinity, ease: "linear" }}
                className="absolute inset-0 rounded-full"
                style={{
                  background:
                    "conic-gradient(from 0deg, transparent 0%, var(--color-gold) 20%, transparent 40%)",
                  WebkitMask: "radial-gradient(farthest-side, transparent calc(100% - 2px), #000 calc(100% - 2px))",
                  mask: "radial-gradient(farthest-side, transparent calc(100% - 2px), #000 calc(100% - 2px))",
                }}
              />
              <motion.div
                animate={{ opacity: [0.75, 1, 0.75] }}
                transition={{ duration: 1.8, repeat: Infinity, ease: "easeInOut" }}
                className="absolute inset-[14px]"
              >
                <Image src="/icon.png" alt="" fill sizes="72px" className="object-contain" priority />
              </motion.div>
            </div>

            <motion.p
              initial={{ opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.6, delay: 0.3, ease: EASE }}
              className="mt-5 font-display text-sm tracking-[0.2em] text-night-ink/70"
            >
              SIE JURÍDICOS
            </motion.p>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
