"use client";

import Image from "next/image";
import Link from "next/link";
import { type MouseEvent, useRef } from "react";
import { motion, useMotionValue, useSpring, useTransform } from "motion/react";
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

function BrandCard() {
  const ref = useRef<HTMLDivElement>(null);
  const px = useMotionValue(0);
  const py = useMotionValue(0);
  const rotateX = useSpring(useTransform(py, [-0.5, 0.5], [7, -7]), {
    stiffness: 200,
    damping: 22,
  });
  const rotateY = useSpring(useTransform(px, [-0.5, 0.5], [-7, 7]), {
    stiffness: 200,
    damping: 22,
  });

  function onMouseMove(e: MouseEvent<HTMLDivElement>) {
    const el = ref.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    px.set((e.clientX - rect.left) / rect.width - 0.5);
    py.set((e.clientY - rect.top) / rect.height - 0.5);
  }

  function onMouseLeave() {
    px.set(0);
    py.set(0);
  }

  return (
    <motion.div
      animate={{ y: [0, -10, 0] }}
      transition={{ duration: 5.5, repeat: Infinity, ease: "easeInOut" }}
      style={{ perspective: 1200 }}
    >
      <motion.div
        ref={ref}
        onMouseMove={onMouseMove}
        onMouseLeave={onMouseLeave}
        style={{ rotateX, rotateY, transformStyle: "preserve-3d" }}
        className="rounded-[2rem] bg-ink/5 p-2 shadow-[0_0_80px_-20px_rgba(217,169,37,0.3)] ring-1 ring-gold/15"
      >
        <div
          className="gradient-animate relative aspect-[4/5] w-full overflow-hidden rounded-[1.6rem]"
          style={{
            backgroundImage:
              "linear-gradient(150deg, #0a0906 0%, #171310 40%, #221c11 62%, #0a0906 100%)",
          }}
        >
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="relative aspect-square w-[70%] max-w-[320px]">
              <SealRing />
              <motion.div
                animate={{ scale: [1, 1.03, 1] }}
                transition={{ duration: 5, repeat: Infinity, ease: "easeInOut" }}
                className="absolute inset-0 flex items-center justify-center"
              >
                <div className="relative aspect-[644/559] w-[62%] max-w-[300px]">
                  <Image
                    src="/marca/logo.png"
                    alt={siteConfig.nombre}
                    fill
                    sizes="300px"
                    className="object-contain drop-shadow-[0_12px_40px_rgba(0,0,0,0.5)]"
                    priority
                  />
                </div>
              </motion.div>
            </div>
          </div>

          <div className="absolute inset-0 bg-gradient-to-t from-night/60 via-transparent to-transparent" />
        </div>
      </motion.div>
    </motion.div>
  );
}

export function Hero() {
  return (
    <section className="relative overflow-hidden pt-24">
      <div className="mx-auto grid max-w-7xl items-center gap-12 px-6 pb-20 md:grid-cols-12 md:gap-8 md:pb-28">
        <div className="max-w-xl md:col-span-7">
          <motion.h1
            {...entry(0)}
            className="text-balance font-display text-4xl leading-[1.08] tracking-tight md:text-6xl"
          >
            Veinte años de experiencia legal, a tu lado.
          </motion.h1>

          <motion.p
            {...entry(0.12)}
            className="mt-6 max-w-md text-lg leading-relaxed text-ink-soft"
          >
            Asesoría jurídica clara y cercana para personas y empresas, con
            más de 800 casos ganados.
          </motion.p>

          <motion.div {...entry(0.22)} className="mt-9 flex flex-wrap items-center gap-x-8 gap-y-4">
            <MagneticButton strength={0.4}>
              <Link
                href="#contacto"
                className="group inline-flex items-center gap-3 rounded-full bg-gold py-3.5 pl-7 pr-3 text-sm font-medium text-ink-fixed transition-transform duration-300 active:scale-[0.98]"
              >
                {siteConfig.ctaPrincipal}
                <span className="flex h-8 w-8 items-center justify-center rounded-full bg-ink-fixed/10 transition-transform duration-300 group-hover:translate-x-0.5 group-hover:-translate-y-px">
                  <ArrowRight className="h-4 w-4" weight="bold" />
                </span>
              </Link>
            </MagneticButton>

            <Link
              href="#areas"
              className="text-sm font-medium text-ink underline decoration-line decoration-2 underline-offset-4 transition-colors duration-300 hover:decoration-gold"
            >
              Ver áreas de práctica
            </Link>
          </motion.div>
        </div>

        <motion.div
          {...entry(0.15)}
          className="relative mx-auto w-full max-w-sm md:col-span-5 md:col-start-9 md:max-w-none"
        >
          <BrandCard />

          <motion.div
            {...entry(0.5)}
            className="absolute -bottom-6 -left-6 rounded-[1.5rem] bg-ink/5 p-1.5 ring-1 ring-ink/5 sm:-left-10"
          >
            <div className="rounded-[1.15rem] bg-surface px-6 py-5 shadow-[0_20px_45px_-15px_rgba(28,26,22,0.35)] ring-1 ring-line">
              <p className="font-display text-4xl leading-none text-ink">
                +800
              </p>
              <p className="mt-1 text-xs font-medium uppercase tracking-[0.12em] text-ink-soft">
                Casos ganados
              </p>
            </div>
          </motion.div>
        </motion.div>
      </div>
    </section>
  );
}
