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
      className="fixed bottom-6 right-6 z-40 flex h-14 w-14 items-center justify-center rounded-full bg-[#25D366] text-ink-fixed shadow-[0_10px_30px_-8px_rgba(0,0,0,0.4)]"
    >
      <motion.span
        aria-hidden="true"
        animate={{ scale: [1, 1.6, 1], opacity: [0.5, 0, 0.5] }}
        transition={{ duration: 2.6, repeat: Infinity, ease: "easeInOut" }}
        className="absolute inset-0 rounded-full bg-[#25D366]"
      />
      <WhatsappLogo weight="fill" className="relative h-7 w-7" />
    </motion.a>
  );
}
