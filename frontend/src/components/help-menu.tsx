"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Question, FileMagnifyingGlass, ChatCircleDots } from "@phosphor-icons/react";

const EASE = [0.16, 1, 0.3, 1] as const;

const ENLACES = [
  {
    href: "/consulta-caso",
    label: "Consulta el estado de tu caso",
    hint: "Con el código que te enviamos por correo",
    icon: FileMagnifyingGlass,
  },
  {
    href: "/preguntas-frecuentes",
    label: "Preguntas frecuentes",
    hint: "Respuestas a lo que más nos preguntan",
    icon: ChatCircleDots,
  },
] as const;

// El "signo de interrogación" del nav: un solo ícono que abre un menú compacto hacia
// los dos destinos que un cliente (no un lead nuevo) más necesita encontrar rápido,
// sin sumar otro botón flotante a los que ya compiten por la esquina de la pantalla
// (WhatsApp, chatbot, Cuida tu marca).
export function HelpMenu({
  className,
  align = "right",
  direction = "down",
  onNavigate,
  size = 36,
}: {
  className?: string;
  align?: "left" | "right";
  // "up" para el uso dentro del menú móvil (site-nav.tsx): ese botón vive cerca del
  // borde inferior del overlay a pantalla completa, así que el panel abriendo hacia
  // ABAJO (el comportamiento por defecto, pensado para el nav de escritorio donde el
  // botón está arriba de todo) se salía del viewport y quedaba cortado -- bug real
  // reportado: "al abrirlo no se visualiza correctamente" en móvil.
  direction?: "up" | "down";
  // El menú móvil (overlay a pantalla completa) que envuelve este componente
  // en site-nav.tsx tiene su propio estado "open" separado del de este
  // dropdown: sin este callback, al navegar desde aquí solo se cerraba el
  // dropdown chiquito y el overlay oscuro se quedaba tapando la página nueva.
  onNavigate?: () => void;
  // En px, vía inline style en vez de clases h-*/w-* -- así el tamaño
  // pedido por quien lo usa siempre gana sin depender del orden en que
  // Tailwind genere las clases (className vs. el tamaño por defecto).
  size?: number;
}) {
  const [open, setOpen] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    function onPointerDown(e: PointerEvent) {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) setOpen(false);
    }
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    window.addEventListener("pointerdown", onPointerDown);
    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.removeEventListener("pointerdown", onPointerDown);
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  return (
    <div ref={wrapRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label="Consulta tu caso o resuelve dudas frecuentes"
        aria-expanded={open}
        style={{ width: size, height: size }}
        className={`${className ?? ""} flex shrink-0 items-center justify-center rounded-full transition-colors`}
      >
        <Question style={{ width: size * 0.48, height: size * 0.48 }} weight="bold" />
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: direction === "up" ? 6 : -6, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: direction === "up" ? 6 : -6, scale: 0.97 }}
            transition={{ duration: 0.22, ease: EASE }}
            style={{
              transformOrigin: `${direction === "up" ? "bottom" : "top"} ${align === "right" ? "right" : "left"}`,
            }}
            className={`absolute z-50 w-72 max-w-[calc(100vw-2.5rem)] overflow-hidden rounded-2xl bg-surface p-1.5 text-left shadow-[0_20px_45px_-15px_rgba(28,26,22,0.35)] ring-1 ring-line ${
              direction === "up" ? "bottom-full mb-3" : "top-full mt-3"
            } ${align === "right" ? "right-0" : "left-0"}`}
          >
            {ENLACES.map(({ href, label, hint, icon: Icon }) => (
              <Link
                key={href}
                href={href}
                onClick={() => {
                  setOpen(false);
                  onNavigate?.();
                }}
                className="flex items-start gap-3 rounded-xl px-3 py-2.5 transition-colors hover:bg-gold/10"
              >
                <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gold-pale text-gold-deep">
                  <Icon className="h-4 w-4" weight="bold" />
                </span>
                <span>
                  <span className="block text-sm font-medium text-ink">{label}</span>
                  <span className="block text-xs text-ink-soft">{hint}</span>
                </span>
              </Link>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
