"use client";

import { motion } from "motion/react";
import { WhatsappLogo } from "@phosphor-icons/react";
import { siteConfig } from "@/lib/site-config";

export function WhatsappFloat() {
  return (
    <motion.a
      href={siteConfig.whatsapp}
      target="_blank"
      rel="noopener noreferrer"
      aria-label="Escríbenos por WhatsApp"
      initial={{ opacity: 0, scale: 0.8 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.5, delay: 0.8, ease: [0.16, 1, 0.3, 1] }}
      whileHover={{ scale: 1.06 }}
      whileTap={{ scale: 0.96 }}
      // texto/ícono en tinta oscura, no blanco: blanco sobre este verde da ~2:1 de
      // contraste (falla WCAG AA); con tinta oscura sube a ~8:1.
      // bottom-24 (no bottom-6): pedido explícito del usuario de reordenar la pila
      // flotante para que "Consulta tu caso" quede siempre la más cercana al borde (ver
      // consulta-caso-float.tsx), con WhatsApp justo encima.
      // h-11/w-11 y bottom-20 en móvil (no h-14/bottom-24): pedido explícito tras ver
      // capturas reales -- el stack flotante tapaba caras y texto al hacer scroll por
      // secciones con contenido denso (Equipo). Botón más chico y stack más compacto en
      // pantallas chicas, mismo tamaño/posición de siempre en sm:+.
      className="fixed bottom-20 right-4 sm:bottom-24 sm:right-6 z-40 flex h-11 w-11 sm:h-14 sm:w-14 items-center justify-center rounded-full bg-[#25D366] text-ink-fixed shadow-[0_10px_30px_-8px_rgba(0,0,0,0.4)]"
    >
      <motion.span
        aria-hidden="true"
        animate={{ scale: [1, 1.6, 1], opacity: [0.5, 0, 0.5] }}
        transition={{ duration: 2.6, repeat: Infinity, ease: "easeInOut" }}
        className="absolute inset-0 rounded-full bg-[#25D366]"
      />
      <WhatsappLogo weight="fill" className="relative h-5 w-5 sm:h-7 sm:w-7" />
    </motion.a>
  );
}
