"use client";

import { useEffect } from "react";
import { gsap } from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";

gsap.registerPlugin(ScrollTrigger);

// Continuidad de color entre secciones: en vez de cortes secos, el campo
// ambiental gira de tono suavemente con el avance real del scroll. Aislado
// de Motion; solo toca una custom property CSS, nunca el DOM de React.
export function ScrollAmbient() {
  useEffect(() => {
    const field = document.querySelector<HTMLElement>(".ambient-field");
    if (!field) return;

    const ctx = gsap.context(() => {
      ScrollTrigger.create({
        start: 0,
        end: () => document.documentElement.scrollHeight - window.innerHeight,
        scrub: 0.6,
        onUpdate: (self) => {
          field.style.setProperty("--scroll-hue", `${self.progress * 26}deg`);
        },
      });
    });

    return () => ctx.revert();
  }, []);

  return null;
}
