"use client";

import { useEffect } from "react";
import { gsap } from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";

gsap.registerPlugin(ScrollTrigger);

// Pedido explícito del usuario: que el paso de una sección a la siguiente en la home
// no se sienta como diapositivas cortadas, sino como un solo recorrido. Un degradado de
// color estático (section-seam) ayuda, pero no "desenfoca" nada de verdad. Esto sí: cada
// sección de scroll-snap nace desenfocada y atenuada mientras entra desde abajo, y se
// aclara justo cuando termina de encajar en su lugar -- como si la cámara enfocara al
// llegar. Es un componente GSAP puro, sin nada de Motion (regla del proyecto: nunca
// mezclar ambas librerías en el mismo árbol), aislado del contenido interno de cada
// sección -- no le importa qué haya adentro, solo opera sobre el contenedor .snap-slide.
export function ScrollSectionBlur() {
  useEffect(() => {
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (reduce) return;

    const ctx = gsap.context(() => {
      const secciones = gsap.utils.toArray<HTMLElement>(".snap-slide");
      secciones.forEach((seccion, i) => {
        // La primera sección (el Hero) ya está a la vista al cargar la página --
        // desenfocarla de entrada se sentiría como un error de carga, no como un
        // recorrido, así que empieza directo desde la segunda.
        if (i === 0) return;
        gsap.fromTo(
          seccion,
          { filter: "blur(16px)", opacity: 0.45 },
          {
            filter: "blur(0px)",
            opacity: 1,
            ease: "none",
            scrollTrigger: {
              trigger: seccion,
              start: "top bottom",
              end: "top center",
              scrub: true,
            },
          },
        );
      });
    });

    return () => ctx.revert();
  }, []);

  return null;
}
