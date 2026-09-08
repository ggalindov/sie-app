"use client";

import Link from "next/link";
import { Question } from "@phosphor-icons/react";

// El "signo de interrogación" del nav -- pedido explícito del usuario: antes abría un menú
// con dos destinos (consulta tu caso / preguntas frecuentes), y quedaba a dos clics de la
// respuesta más buscada. Ahora va DIRECTO a preguntas frecuentes, un solo clic. La consulta
// de estado de caso ya tiene su propio botón flotante aparte (ver consulta-caso-float.tsx),
// así que no se pierde ningún acceso al quitarlo de aquí.
export function HelpMenu({
  className,
  onNavigate,
  size = 36,
}: {
  className?: string;
  // Mantenidos por compatibilidad con los call-sites existentes (site-nav.tsx), aunque ya
  // no tengan efecto sin el menú desplegable: align/direction solo importaban para
  // posicionar el panel que ya no existe.
  align?: "left" | "right";
  direction?: "up" | "down";
  onNavigate?: () => void;
  size?: number;
}) {
  return (
    <Link
      href="/preguntas-frecuentes"
      onClick={onNavigate}
      aria-label="Preguntas frecuentes"
      style={{ width: size, height: size }}
      className={`${className ?? ""} flex shrink-0 items-center justify-center rounded-full transition-colors`}
    >
      <Question style={{ width: size * 0.48, height: size * 0.48 }} weight="bold" />
    </Link>
  );
}
