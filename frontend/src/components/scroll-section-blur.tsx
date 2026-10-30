"use client";

import { useEffect } from "react";

// Cuarta vuelta de este componente. Las dos primeras versiones (GSAP scrub, luego GSAP
// ScrollTrigger onEnter/onEnterBack con duración fija) seguían sin notarse en la práctica
// -- en ambos casos GSAP calcula la posición del "trigger" en coordenadas de scroll
// absolutas al montar, y con scroll-snap "mandatory" el navegador puede reflowar/ajustar
// el scroll por su cuenta de formas que GSAP no vuelve a medir sin un refresh explícito,
// así que el disparo quedaba desalineado o simplemente no ocurría ("de momento no hay
// nada", reporte real del usuario, dos veces).
//
// Esta versión no depende de ninguna coordenada de scroll calculada de antemano: usa
// IntersectionObserver, que por diseño reacciona a la visibilidad REAL del elemento en
// cada momento, sin importar cómo se movió el scroll para llegar ahí. Cuando una sección
// cruza el umbral de visibilidad se le agrega la clase "seccion-vista" (ver globals.css,
// la transición de filter/opacity/transform vive ahí, no en JS) -- y se le quita cuando
// deja de ser visible, así que tanto la que entra como la que sale animan, en cualquier
// dirección del scroll.
export function ScrollSectionBlur() {
  useEffect(() => {
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduce) return;

    const secciones = Array.from(document.querySelectorAll<HTMLElement>(".snap-slide"));
    if (secciones.length === 0) return;

    const esMovil = typeof window !== "undefined" && window.innerWidth < 768;

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          entry.target.classList.toggle("seccion-vista", entry.isIntersecting);
        }
      },
      {
        // En móvil, threshold: 0 con rootMargin de -20px garantiza que cualquier sección
        // (especialmente Áreas de práctica, que al medir ~1800px requería 180px para alcanzar
        // un threshold porcentual) comience a desenfocarse y elevarse de inmediato apenas asoma
        // 20px en la parte inferior de la pantalla, sin quedarse pegada al bajar del Hero.
        threshold: esMovil ? 0 : 0.45,
        rootMargin: esMovil ? "0px 0px -20px 0px" : "0px",
      },
    );
    secciones.forEach((seccion) => observer.observe(seccion));

    return () => observer.disconnect();
  }, []);

  return null;
}
