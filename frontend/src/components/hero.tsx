"use client";

import Image from "next/image";
import Link from "next/link";
import { type ReactNode } from "react";
import { motion, useScroll, useTransform } from "motion/react";
import { ArrowRight } from "@phosphor-icons/react";
import { siteConfig } from "@/lib/site-config";
import { MagneticButton } from "@/components/magnetic-button";
import { SealRing } from "@/components/seal-ring";

const EASE = [0.16, 1, 0.3, 1] as const;

// La reducción de movimiento la maneja MotionConfig (reducedMotion="user") en el
// layout raíz, no aquí: evita el desajuste de hidratación de useReducedMotion() en SSR.
function entry(delay = 0) {
  return {
    initial: { opacity: 0, y: 20 },
    animate: { opacity: 1, y: 0 },
    transition: { duration: 0.7, delay, ease: EASE },
  } as const;
}

// Segunda pasada de identidad: la bienvenida ya no es un split texto/tarjeta,
// es un momento de marca a pantalla completa, como abre una firma real
// (Lloreda Camacho, CABA): video de la firma de fondo edge-to-edge, un velo
// para legibilidad, y el sello/logo como protagonista, grande y centrado,
// no una insignia pequeña en la esquina de una tarjeta.
export function Hero({ media }: { media: ReactNode }) {
  const { scrollYProgress } = useScroll();
  const y = useTransform(scrollYProgress, [0, 1], ["0%", "18%"]);

  return (
    <section className="snap-slide relative overflow-hidden">
      <motion.div style={{ y }} className="absolute inset-[-10%_0_0_0]">
        {media}
      </motion.div>
      <div className="absolute inset-0 bg-gradient-to-b from-night/75 via-night/45 to-night/85" />
      <div className="absolute inset-0 bg-gradient-to-t from-night via-night/10 to-transparent" />

      <div className="relative mx-auto flex min-h-[100dvh] max-w-3xl flex-col items-center justify-center px-6 pb-16 pt-32 text-center md:pt-24">
        <motion.div
          {...entry(0)}
          className="relative flex h-36 w-36 items-center justify-center rounded-full bg-night-ink/10 ring-1 ring-night-ink/25 backdrop-blur-md sm:h-44 sm:w-44 md:h-52 md:w-52"
        >
          <SealRing delay={0.6} />
          <div className="relative aspect-[644/559] w-[74%]">
            <Image
              src="/marca/logo.png"
              alt={siteConfig.nombre}
              fill
              sizes="208px"
              priority
              className="object-contain"
            />
          </div>
        </motion.div>

        <motion.h1
          {...entry(0.12)}
          className="mt-9 text-balance font-display text-5xl leading-[1.04] tracking-tight text-night-ink md:text-7xl lg:text-8xl"
        >
          Veinte años de experiencia legal, a tu lado.
        </motion.h1>

        <motion.p
          {...entry(0.24)}
          className="mt-6 max-w-xl text-balance text-lg leading-relaxed text-night-ink/75 md:text-xl"
        >
          Asesoría jurídica clara y cercana para personas y empresas, con
          más de 800 casos ganados.
        </motion.p>

        <motion.div {...entry(0.34)} className="mt-10 flex flex-wrap items-center justify-center gap-x-8 gap-y-4">
          <MagneticButton strength={0.4}>
            <Link
              href="#agendar"
              className="cta-boton group inline-flex items-center gap-3 rounded-lg bg-gold px-7 py-4 text-base font-medium text-ink-fixed active:scale-[0.98]"
            >
              {siteConfig.ctaPrincipal}
              <ArrowRight
                weight="bold"
                className="h-4 w-4 shrink-0 transition-transform duration-300 group-hover:translate-x-1"
              />
            </Link>
          </MagneticButton>

          <Link
            href="#areas"
            className="text-base font-medium text-night-ink underline decoration-night-ink/30 decoration-2 underline-offset-4 transition-colors duration-300 hover:decoration-gold"
          >
            Ver áreas de práctica
          </Link>
        </motion.div>
      </div>
    </section>
  );
}
