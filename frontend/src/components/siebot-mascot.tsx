"use client";

import { motion } from "motion/react";

const EASE = [0.16, 1, 0.3, 1] as const;

// Mascota del chatbot ("Siebot"): antes era una carita sonriente genérica, sin
// ninguna seña de que fuera "de abogado" ni de la firma. Los anteojos redondos y el
// corbatín (dibujados a mano en SVG, misma disciplina que mascota-escudo.tsx de
// /cuida-tu-marca) son las dos señas mínimas que la vuelven reconocible como una
// mascota de abogado de un vistazo, incluso a 32px. Toda la animación es Motion (el
// widget del chat ya usa Motion, nunca se mezcla con GSAP en el mismo árbol).
export function SiebotMascot({
  size = 32,
  pensando = false,
}: {
  size?: number;
  pensando?: boolean;
}) {
  return (
    <motion.div
      animate={{
        y: pensando ? [0, -3, 0] : [0, 2, -14, 1, 0],
        scaleY: pensando ? 1 : [1, 0.88, 1.16, 0.94, 1],
        scaleX: pensando ? 1 : [1, 1.08, 0.9, 1.04, 1],
        rotate: pensando ? [0, -4, 4, 0] : [0, 0],
      }}
      transition={{
        y: pensando
          ? { duration: 2.4, repeat: Infinity, ease: "easeInOut" }
          : { duration: 1.9, repeat: Infinity, ease: "easeInOut", times: [0, 0.25, 0.55, 0.8, 1], repeatDelay: 0.5 },
        scaleY: { duration: 1.9, repeat: Infinity, ease: "easeInOut", times: [0, 0.25, 0.55, 0.8, 1], repeatDelay: 0.5 },
        scaleX: { duration: 1.9, repeat: Infinity, ease: "easeInOut", times: [0, 0.25, 0.55, 0.8, 1], repeatDelay: 0.5 },
        rotate: pensando
          ? { duration: 1.1, repeat: Infinity, ease: "easeInOut" }
          : { duration: 0 },
      }}
      style={{ width: size, height: size, transformOrigin: "50% 100%" }}
      className="relative shrink-0"
    >
      <svg viewBox="0 0 64 64" width={size} height={size} aria-hidden="true">
        <circle cx="32" cy="32" r="30" fill="var(--color-gold)" />
        <circle cx="32" cy="32" r="30" fill="url(#siebot-sheen)" />
        <defs>
          <linearGradient id="siebot-sheen" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#ffffff" stopOpacity="0.35" />
            <stop offset="100%" stopColor="#ffffff" stopOpacity="0" />
          </linearGradient>
        </defs>

        {/* ojos: parpadean solos, sin depender de props */}
        <motion.g
          animate={{ scaleY: [1, 1, 0.1, 1, 1] }}
          transition={{
            duration: 3.6,
            repeat: Infinity,
            repeatDelay: 1.4,
            times: [0, 0.85, 0.9, 0.95, 1],
            ease: "easeInOut",
          }}
          style={{ transformOrigin: "32px 28px" }}
        >
          <circle cx="23" cy="28" r="4" fill="var(--color-ink-fixed)" />
          <circle cx="41" cy="28" r="4" fill="var(--color-ink-fixed)" />
        </motion.g>

        {/* anteojos redondos: el marco no parpadea, solo la pupila detrás de cada
            lente (el mismo grupo de arriba) */}
        <g fill="none" stroke="var(--color-ink-fixed)" strokeWidth="2" strokeLinecap="round" opacity="0.9">
          <circle cx="23" cy="28" r="7.5" />
          <circle cx="41" cy="28" r="7.5" />
          <path d="M30.5 27h3" />
          <path d="M15.5 25.5c-2.5 0-3.5 1.5-3.5 3.5" />
          <path d="M48.5 25.5c2.5 0 3.5 1.5 3.5 3.5" />
        </g>

        {/* sonrisa */}
        <path
          d="M22 40c3.5 4 16.5 4 20 0"
          stroke="var(--color-ink-fixed)"
          strokeWidth="3"
          strokeLinecap="round"
          fill="none"
        />

        {/* corbatín: la seña más directa de "mascota de abogado", en vez de un
            personaje genérico sin ninguna referencia al gremio */}
        <g transform="translate(32, 50)">
          <path d="M-9 -4 L-1.5 0 L-9 4 Z" fill="var(--color-ink-fixed)" />
          <path d="M9 -4 L1.5 0 L9 4 Z" fill="var(--color-ink-fixed)" />
          <circle cx="0" cy="0" r="2.4" fill="var(--color-gold-deep)" stroke="var(--color-ink-fixed)" strokeWidth="1" />
        </g>
      </svg>

      {/* punto de estado, como un indicador "en línea" */}
      <motion.span
        animate={{ opacity: pensando ? [0.4, 1, 0.4] : 1, scale: pensando ? [1, 1.15, 1] : 1 }}
        transition={{ duration: 0.9, repeat: pensando ? Infinity : 0, ease: EASE }}
        className="absolute -bottom-0.5 -right-0.5 h-2.5 w-2.5 rounded-full ring-2 ring-surface"
        style={{ backgroundColor: "#3f9b5c" }}
      />
    </motion.div>
  );
}
