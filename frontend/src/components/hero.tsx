"use client";

import Image from "next/image";
import Link from "next/link";
import { type MouseEvent, type ReactNode, useRef } from "react";
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

// La foto/video real de la firma es el protagonista (así lo hacen los
// bufetes reales: Lloreda Camacho, PGP, ALL Abogados), no un logo dorado
// gigante brillando en 3D, eso es lo que hacía sentir el sitio "generado por
// IA". El logo queda como una insignia pequeña y funcional sobre la foto,
// igual que el sello circular que usa PGP sobre su oficina.
function FotoFirma({ media }: { media: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  const px = useMotionValue(0);
  const py = useMotionValue(0);
  const rotateX = useSpring(useTransform(py, [-0.5, 0.5], [4, -4]), {
    stiffness: 200,
    damping: 22,
  });
  const rotateY = useSpring(useTransform(px, [-0.5, 0.5], [-4, 4]), {
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
      ref={ref}
      onMouseMove={onMouseMove}
      onMouseLeave={onMouseLeave}
      style={{ rotateX, rotateY, transformStyle: "preserve-3d", perspective: 1200 }}
      className="rounded-[2rem] bg-ink/5 p-2 shadow-[0_30px_80px_-30px_rgba(20,19,15,0.45)] ring-1 ring-ink/5"
    >
      <div className="relative aspect-[5/6] w-full overflow-hidden rounded-[1.6rem] bg-night">
        {media}
        <div className="absolute inset-0 bg-gradient-to-t from-night/55 via-transparent to-night/10" />

        <div className="absolute -bottom-6 -left-6 flex h-24 w-24 items-center justify-center rounded-full bg-night p-2 shadow-[0_15px_35px_-10px_rgba(0,0,0,0.55)] sm:h-32 sm:w-32">
          <div className="relative flex h-full w-full items-center justify-center rounded-full">
            <SealRing delay={0.8} />
            <div className="relative aspect-[644/559] w-[64%]">
              <Image
                src="/marca/logo.png"
                alt={siteConfig.nombre}
                fill
                sizes="128px"
                className="object-contain"
              />
            </div>
          </div>
        </div>
      </div>
    </motion.div>
  );
}

export function Hero({ media }: { media: ReactNode }) {
  return (
    <section className="snap-slide relative overflow-hidden pt-24 md:pt-0">
      <div className="mx-auto grid max-w-7xl items-center gap-12 px-6 pb-20 md:grid-cols-12 md:gap-8 md:pb-28">
        <div className="max-w-xl md:col-span-7">
          <motion.h1
            {...entry(0)}
            className="text-balance font-display text-5xl leading-[1.05] tracking-tight md:text-7xl"
          >
            Veinte años de experiencia legal, a tu lado.
          </motion.h1>

          <motion.p
            {...entry(0.12)}
            className="mt-7 max-w-md text-xl leading-relaxed text-ink-soft"
          >
            Asesoría jurídica clara y cercana para personas y empresas, con
            más de 800 casos ganados.
          </motion.p>

          <motion.div {...entry(0.22)} className="mt-10 flex flex-wrap items-center gap-x-8 gap-y-4">
            <MagneticButton strength={0.4}>
              <Link
                href="#agendar"
                className="group inline-flex items-center gap-4 rounded-full bg-gold py-4 pl-9 pr-4 text-base font-medium text-ink-fixed transition-transform duration-300 active:scale-[0.98]"
              >
                {siteConfig.ctaPrincipal}
                <span className="flex h-10 w-10 items-center justify-center rounded-full bg-ink-fixed/10 transition-transform duration-300 group-hover:translate-x-0.5 group-hover:-translate-y-px">
                  <ArrowRight className="h-5 w-5" weight="bold" />
                </span>
              </Link>
            </MagneticButton>

            <Link
              href="#areas"
              className="text-base font-medium text-ink underline decoration-line decoration-2 underline-offset-4 transition-colors duration-300 hover:decoration-gold"
            >
              Ver áreas de práctica
            </Link>
          </motion.div>
        </div>

        <motion.div
          {...entry(0.15)}
          className="relative mx-auto w-full max-w-sm md:col-span-5 md:col-start-8 md:max-w-none"
        >
          <FotoFirma media={media} />

          <motion.div
            {...entry(0.5)}
            className="absolute -top-5 -right-4 rounded-[1.5rem] bg-ink/5 p-1.5 ring-1 ring-ink/5 sm:-right-8"
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
