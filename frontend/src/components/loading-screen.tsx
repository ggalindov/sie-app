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
    const t = setTimeout(() => setVisible(false), DURACION_TRAZO_S * 1000 + 450);
    return () => clearTimeout(t);
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
          <div className="flex flex-col items-center">
            {/* Emblema: aparición sutil, sin giros ni pulsos */}
            <motion.div
              initial={{ opacity: 0, y: 6, filter: "blur(4px)" }}
              animate={{ opacity: 1, y: 0, filter: "blur(0px)" }}
              transition={{ duration: 0.8, ease: EASE }}
              className="relative h-14 w-14 sm:h-16 sm:w-16"
            >
              <Image
                src="/icon.png"
                alt=""
                fill
                sizes="64px"
                className="object-contain"
                priority
              />
            </motion.div>

            {/* Trazo fino con la bandera de Colombia (50% amarillo, 25% azul, 25% rojo) */}
            <div className="mt-8 h-px w-28 sm:w-32 overflow-hidden rounded-full bg-night-ink/10">
              <motion.div
                initial={{ scaleX: 0 }}
                animate={{ scaleX: 1 }}
                transition={{ duration: DURACION_TRAZO_S, delay: 0.2, ease: [0.65, 0, 0.35, 1] }}
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
