"use client";

import Image from "next/image";
import { useRef } from "react";
import { motion, useScroll, useTransform } from "motion/react";

const EASE = [0.16, 1, 0.3, 1] as const;

// Momento editorial de la home: la biblioteca real de la firma como fondo,
// sin ningún objeto 3D flotando encima (el martillo se retiró a pedido
// explícito del usuario, "pierde la elegancia del sitio"). El fondo se
// mueve levemente con el scroll (parallax con Motion, no GSAP) para que la
// sección no se sienta como una imagen estática pegada detrás del texto.
export function Criterio() {
  const ref = useRef<HTMLDivElement>(null);
  const { scrollYProgress } = useScroll({
    target: ref,
    offset: ["start end", "end start"],
  });
  const y = useTransform(scrollYProgress, [0, 1], ["-8%", "8%"]);

  return (
    <section ref={ref} className="relative overflow-hidden py-28 md:py-40">
      <motion.div style={{ y }} className="absolute inset-[-8%]">
        <Image
          src="/fondos/dmitrij-paskevic-YjVa-F9P9kk-unsplash.jpg"
          alt=""
          fill
          sizes="100vw"
          className="object-cover"
        />
      </motion.div>
      <div className="absolute inset-0 bg-gradient-to-b from-night/90 via-night/80 to-night/90" />
      <div
        aria-hidden="true"
        className="absolute inset-0"
        style={{
          backgroundImage:
            "radial-gradient(60% 50% at 50% 45%, rgba(217,169,37,0.14), transparent 70%)",
        }}
      />

      <div className="relative mx-auto max-w-4xl px-6 text-center">
        <motion.h2
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, amount: 0.5 }}
          transition={{ duration: 0.8, ease: EASE }}
          className="text-balance font-display text-3xl leading-[1.15] text-night-ink md:text-5xl"
        >
          Cada caso se resuelve con el mismo criterio: veinte años de
          experiencia.
        </motion.h2>
        <motion.p
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, amount: 0.5 }}
          transition={{ duration: 0.8, delay: 0.12, ease: EASE }}
          className="mx-auto mt-6 max-w-xl text-balance text-base leading-relaxed text-night-ink/70 md:text-lg"
        >
          No improvisamos. Cada estrategia se construye sobre precedentes
          reales y el seguimiento constante de un equipo que conoce el
          sistema.
        </motion.p>
      </div>
    </section>
  );
}
