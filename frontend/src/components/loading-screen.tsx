"use client";

import { useEffect, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import Image from "next/image";

const EASE = [0.16, 1, 0.3, 1] as const;

export function LoadingScreen() {
  const [visible, setVisible] = useState(true);
  const [progreso, setProgreso] = useState(10);
  const [textoEstado, setTextoEstado] = useState("Iniciando despacho legal...");

  useEffect(() => {
    let montado = true;
    let intervalo: ReturnType<typeof setInterval>;
    const inicio = Date.now();
    const duracionCargaMs = 2600; // ~2.6 segundos para apreciar la animación de la bandera con calma

    // Progreso suave con curva cúbica natural
    intervalo = setInterval(() => {
      if (!montado) return;
      const transcurrido = Date.now() - inicio;
      const t = Math.min(transcurrido / duracionCargaMs, 1);

      // Curva cubic-bezier easeInOut
      const easedT = t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
      const nuevoProgreso = Math.min(Math.round(easedT * 100), 100);

      setProgreso(nuevoProgreso);

      if (nuevoProgreso >= 100) {
        clearInterval(intervalo);
        setTextoEstado("¡Bienvenido a SIE Jurídicos!");
        // Momento para contemplar la bandera al 100% antes del fade-out
        setTimeout(() => {
          if (montado) setVisible(false);
        }, 650);
      }
    }, 24);

    return () => {
      montado = false;
      clearInterval(intervalo);
    };
  }, []);

  // Actualizar micro-estado según porcentaje
  useEffect(() => {
    if (progreso < 35) {
      setTextoEstado("Iniciando plataforma legal...");
    } else if (progreso < 70) {
      setTextoEstado("Cargando áreas de práctica y criterio...");
    } else if (progreso < 95) {
      setTextoEstado("Verificando jurisprudencia y servicios...");
    } else {
      setTextoEstado("Bienvenido a SIE Jurídicos");
    }
  }, [progreso]);

  return (
    <AnimatePresence>
      {visible && (
        <motion.div
          initial={{ opacity: 1 }}
          exit={{ opacity: 0, scale: 1.02 }}
          transition={{ duration: 0.5, ease: EASE }}
          className="fixed inset-0 z-[100] flex flex-col items-center justify-center bg-night selection:bg-gold selection:text-ink-fixed"
          aria-hidden="true"
        >
          {/* Fondo con halo radial dorado cálido */}
          <div
            className="absolute inset-0 pointer-events-none"
            style={{
              backgroundImage:
                "radial-gradient(55% 50% at 50% 48%, rgba(217,169,37,0.18), transparent 75%)",
            }}
          />

          <motion.div
            initial={{ opacity: 0, scale: 0.94 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.6, ease: EASE }}
            className="relative z-10 flex flex-col items-center px-6 text-center"
          >
            {/* Emblema central con aura y anillo giratorio de precisión */}
            <div className="relative mb-6 h-20 w-20 sm:h-24 sm:w-24">
              <motion.span
                animate={{ rotate: 360 }}
                transition={{ duration: 3, repeat: Infinity, ease: "linear" }}
                className="absolute inset-0 rounded-full"
                style={{
                  background:
                    "conic-gradient(from 0deg, transparent 0%, var(--color-gold) 35%, transparent 65%)",
                  WebkitMask:
                    "radial-gradient(farthest-side, transparent calc(100% - 2.5px), #000 calc(100% - 2.5px))",
                  mask:
                    "radial-gradient(farthest-side, transparent calc(100% - 2.5px), #000 calc(100% - 2.5px))",
                }}
              />
              <motion.div
                animate={{
                  scale: [0.98, 1.03, 0.98],
                  opacity: [0.85, 1, 0.85],
                }}
                transition={{ duration: 2, repeat: Infinity, ease: "easeInOut" }}
                className="absolute inset-[10px] flex items-center justify-center rounded-full bg-night/80 shadow-inner ring-1 ring-gold/20"
              >
                <Image
                  src="/icon.png"
                  alt="SIE Jurídicos"
                  fill
                  sizes="80px"
                  className="object-contain p-2.5 drop-shadow-[0_2px_8px_rgba(217,169,37,0.3)]"
                  priority
                />
              </motion.div>
            </div>

            {/* Titular institucional */}
            <h1 className="font-display text-lg sm:text-xl tracking-[0.25em] text-night-ink font-medium">
              SIE JURÍDICOS
            </h1>
            <p className="mt-1 text-[11px] sm:text-xs tracking-widest text-gold-pale/75 uppercase font-medium">
              Firma de Abogados · Colombia
            </p>

            {/* Barrita interactiva con la bandera de Colombia */}
            <div className="mt-8 w-72 sm:w-80">
              <div className="relative h-3 sm:h-3.5 w-full overflow-hidden rounded-full bg-black/40 p-[2px] ring-1 ring-gold/30 shadow-[0_0_20px_rgba(217,169,37,0.15)]">
                {/* Relleno dinámico con las franjas oficiales de la Bandera de Colombia: 50% Amarillo, 25% Azul, 25% Rojo */}
                <motion.div
                  className="relative h-full rounded-full transition-all duration-100 ease-out overflow-hidden"
                  style={{
                    width: `${progreso}%`,
                    background:
                      "linear-gradient(to bottom, #FCD116 0%, #FCD116 50%, #003893 50%, #003893 75%, #CE1126 75%, #CE1126 100%)",
                    boxShadow: "0 0 18px rgba(252,209,22,0.6), 0 0 10px rgba(206,17,38,0.4)",
                  }}
                >
                  {/* Destello de brillo viajando continuamente sobre la bandera */}
                  <motion.span
                    className="absolute inset-0 bg-gradient-to-r from-transparent via-white/55 to-transparent"
                    animate={{ x: ["-100%", "200%"] }}
                    transition={{ duration: 1.1, repeat: Infinity, ease: "easeInOut" }}
                  />
                </motion.div>
              </div>

              {/* Indicador de estado y porcentaje */}
              <div className="mt-3.5 flex items-center justify-between text-[11px] sm:text-xs text-night-ink/70">
                <span className="font-sans transition-all duration-300 font-medium">
                  {textoEstado}
                </span>
                <span className="font-mono font-semibold text-gold-pale tabular-nums">
                  {progreso}%
                </span>
              </div>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
