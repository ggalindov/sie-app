"use client";

import { motion } from "motion/react";

const EASE = [0.16, 1, 0.3, 1] as const;

// Mascota simple y geométrica del chatbot ("Siebot"), no una ilustración
// decorativa suelta: reutiliza la balanza del logo real de la marca como
// "cara", así el personaje se siente parte de la identidad y no un clip-art
// genérico pegado encima. Toda la animación es Motion (el widget del chat ya
// usa Motion, nunca se mezcla con GSAP en el mismo árbol).
export function SiebotMascot({
  size = 32,
  pensando = false,
}: {
  size?: number;
  pensando?: boolean;
}) {
  return (
    <motion.div
      animate={{ y: [0, -3, 0], rotate: pensando ? [0, -4, 4, 0] : [0, 0] }}
      transition={{
        y: { duration: 2.4, repeat: Infinity, ease: "easeInOut" },
        rotate: pensando
          ? { duration: 1.1, repeat: Infinity, ease: "easeInOut" }
          : { duration: 0 },
      }}
      style={{ width: size, height: size }}
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

        {/* sonrisa */}
        <path
          d="M22 40c3.5 4 16.5 4 20 0"
          stroke="var(--color-ink-fixed)"
          strokeWidth="3"
          strokeLinecap="round"
          fill="none"
        />
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
