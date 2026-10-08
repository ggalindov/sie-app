"use client";

import { useEffect, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import Image from "next/image";

const EASE = [0.16, 1, 0.3, 1] as const;
// Duración del trazo de la bandera; el loader se retira poco después de completarlo.
const DURACION_TRAZO_S = 1.4;

export function LoadingScreen() {
  const [visible, setVisible] = useState(true);

  useEffect(() => {
    const t = setTimeout(() => setVisible(false), DURACION_TRAZO_S * 1000 + 400);
    return () => clearTimeout(t);
  }, []);

  return (
    <AnimatePresence>
      {visible && (
        <motion.div
          initial={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.5, ease: EASE }}
          className="fixed inset-0 z-[100] flex items-center justify-center bg-night"
          aria-hidden="true"
        >
          <div className="relative flex flex-col items-center">
            {/* Halo dorado sutil de fondo */}
            <motion.div
              initial={{ opacity: 0, scale: 0.8 }}
              animate={{ opacity: 0.5, scale: 1 }}
              transition={{ duration: 1.1, ease: EASE }}
              className="pointer-events-none absolute -top-8 h-44 w-44 sm:h-56 sm:w-56 rounded-full bg-gold/20 blur-3xl"
            />

            {/* Logo oficial SIE Jurídicos: grande, elegante, nítido y con resplandor */}
            <motion.div
              initial={{ opacity: 0, scale: 0.92, filter: "blur(6px)" }}
              animate={{ opacity: 1, scale: 1, filter: "blur(0px)" }}
              transition={{ duration: 0.85, ease: EASE }}
              className="relative w-36 sm:w-44 aspect-[644/559]"
            >
              <Image
                src="/marca/logo.png"
                alt="SIE Jurídicos"
                fill
                sizes="(max-width: 640px) 144px, 176px"
                className="object-contain drop-shadow-[0_4px_24px_rgba(217,169,37,0.3)]"
                priority
              />
            </motion.div>

            {/* Trazo fino minimalista con la bandera de Colombia */}
            <div className="mt-7 h-[1.5px] w-32 sm:w-40 overflow-hidden rounded-full bg-white/10">
              <motion.div
                initial={{ scaleX: 0 }}
                animate={{ scaleX: 1 }}
                transition={{ duration: DURACION_TRAZO_S, delay: 0.15, ease: [0.65, 0, 0.35, 1] }}
                className="h-full w-full origin-left"
                style={{
                  background:
                    "linear-gradient(to right, #FCD116 0%, #FCD116 50%, #003893 50%, #003893 75%, #CE1126 75%, #CE1126 100%)",
                }}
              />
            </div>
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
