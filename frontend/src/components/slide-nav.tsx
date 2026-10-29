"use client";

import { useCallback, useEffect, useState } from "react";
import { CaretUp, CaretDown } from "@phosphor-icons/react";

// Flechas de navegación entre secciones en desktop, a pedido explícito del
// usuario: mismo destino que el scroll-snap (.snap-slide, ver globals.css),
// solo que con un clic en vez de rueda de mouse. Un IntersectionObserver
// mantiene el índice de la sección visible actual para saber si "siguiente"
// o "anterior" tienen a dónde ir. Solo aparece en desktop (md+): en móvil no
// hay scroll-snap tipo slide, así que las flechas no tendrían sentido ahí.
export function SlideNav() {
  const [index, setIndex] = useState(0);
  const [total, setTotal] = useState(0);

  useEffect(() => {
    const slides = Array.from(document.querySelectorAll<HTMLElement>(".snap-slide"));
    setTotal(slides.length);
    if (slides.length === 0) return;

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            const i = slides.indexOf(entry.target as HTMLElement);
            if (i !== -1) setIndex(i);
          }
        }
      },
      { threshold: 0.55 },
    );
    slides.forEach((s) => observer.observe(s));
    return () => observer.disconnect();
  }, []);

  // Scroll propio con la curva de marca del sitio (mismo cubic-bezier que el resto de
  // animaciones), en vez de depender del scroll nativo del navegador: pedido explícito del
  // usuario de una "animación suave" al usar las flechas -- el smooth scroll nativo de
  // Chrome es corto y algo mecánico, y combinado con scroll-snap "mandatory" a veces se
  // siente doble (el navegador snapea encima del scroll ya en curso). Con requestAnimationFrame
  // se controla la duración y la curva exactas, sin competir con el snap nativo.
  const goTo = useCallback((i: number) => {
    const slides = document.querySelectorAll<HTMLElement>(".snap-slide");
    const target = slides[i];
    if (!target) return;

    const navHeight = parseFloat(
      getComputedStyle(document.documentElement).getPropertyValue("--nav-height") || "88",
    );
    const destino = target.getBoundingClientRect().top + window.scrollY - navHeight;
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    if (reduce) {
      window.scrollTo(0, destino);
      return;
    }

    const inicio = window.scrollY;
    const distancia = destino - inicio;
    const duracion = 850;
    let inicioMs: number | null = null;

    // Mismo cubic-bezier(0.16, 1, 0.3, 1) que EASE en el resto del sitio, aproximado a
    // mano para requestAnimationFrame (no hay forma nativa de usar un cubic-bezier CSS
    // directo en un bucle de rAF sin traerlo calculado).
    function easeOut(t: number) {
      return 1 - Math.pow(1 - t, 3.2);
    }

    function paso(ahora: number) {
      if (inicioMs === null) inicioMs = ahora;
      const transcurrido = ahora - inicioMs;
      const t = Math.min(transcurrido / duracion, 1);
      window.scrollTo(0, inicio + distancia * easeOut(t));
      if (t < 1) requestAnimationFrame(paso);
    }

    requestAnimationFrame(paso);
  }, []);

  if (total === 0) return null;

  return (
    <div className="fixed right-6 top-1/2 z-30 hidden -translate-y-1/2 flex-col gap-2 md:flex">
      <button
        type="button"
        aria-label="Sección anterior"
        onClick={() => goTo(index - 1)}
        disabled={index <= 0}
        className="flex h-10 w-10 items-center justify-center rounded-full bg-ink/5 text-ink-soft ring-1 ring-ink/10 backdrop-blur-md transition-all duration-300 hover:bg-gold hover:text-ink-fixed disabled:pointer-events-none disabled:opacity-30"
      >
        <CaretUp weight="bold" className="h-4 w-4" />
      </button>
      <button
        type="button"
        aria-label="Siguiente sección"
        onClick={() => goTo(index + 1)}
        disabled={index >= total - 1}
        className="flex h-10 w-10 items-center justify-center rounded-full bg-ink/5 text-ink-soft ring-1 ring-ink/10 backdrop-blur-md transition-all duration-300 hover:bg-gold hover:text-ink-fixed disabled:pointer-events-none disabled:opacity-30"
      >
        <CaretDown weight="bold" className="h-4 w-4" />
      </button>
    </div>
  );
}
