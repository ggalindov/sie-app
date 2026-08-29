"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Quotes, Star } from "@phosphor-icons/react";
import { testimonios as testimoniosBase } from "@/lib/content";
import { getTestimoniosAprobados, type TestimonioPublico } from "@/lib/api";
import { TestimonioFormModal } from "@/components/testimonio-form-modal";

const EASE = [0.16, 1, 0.3, 1] as const;
const INTERVALO_AUTOPLAY_MS = 7000;

export function Testimonios({ media }: { media: ReactNode }) {
  const [aprobados, setAprobados] = useState<TestimonioPublico[]>([]);
  const [modalAbierto, setModalAbierto] = useState(false);

  useEffect(() => {
    getTestimoniosAprobados()
      .then(setAprobados)
      .catch(() => {
        // Sin conexión al backend, la sección igual muestra los testimonios base.
      });
  }, []);

  const todos = [
    ...testimoniosBase.map((t) => ({
      cita: t.cita,
      nombre: t.nombre,
      cargo: t.cargo,
      calificacion: 5,
    })),
    ...aprobados.map((t) => ({
      cita: t.cita,
      nombre: t.nombre,
      cargo: [t.cargo, t.empresa].filter(Boolean).join(", "),
      calificacion: t.calificacion,
    })),
  ];

  return (
    <section className="snap-slide section-seam relative overflow-hidden py-20 md:py-28">
      <div className="absolute inset-0">{media}</div>
      <div className="absolute inset-0 bg-gradient-to-b from-night/93 via-night/88 to-night/93" />

      <div className="relative mx-auto max-w-4xl px-6">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <motion.h2
            initial={{ opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.4 }}
            transition={{ duration: 0.7, ease: EASE }}
            className="font-display text-4xl leading-tight tracking-tight text-night-ink md:text-5xl"
          >
            Lo que dicen nuestros clientes
          </motion.h2>

          <motion.button
            type="button"
            onClick={() => setModalAbierto(true)}
            initial={{ opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.4 }}
            transition={{ duration: 0.7, ease: EASE }}
            className="cta-boton rounded-lg border border-night-ink/25 px-5 py-2.5 text-sm font-medium text-night-ink transition-colors duration-200 hover:border-gold-deep hover:text-ink-fixed active:scale-[0.97]"
          >
            Deja tu testimonio
          </motion.button>
        </div>

        <motion.div
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, amount: 0.3 }}
          transition={{ duration: 0.7, delay: 0.1, ease: EASE }}
          className="mt-10"
        >
          <CarruselTestimonios testimonios={todos} />
        </motion.div>
      </div>

      <TestimonioFormModal open={modalAbierto} onClose={() => setModalAbierto(false)} />
    </section>
  );
}

type TestimonioItem = {
  cita: string;
  nombre: string;
  cargo: string;
  calificacion: number;
};

// Carrusel de una tarjeta a la vez, completamente automático: la sección de
// testimonios no puede crecer verticalmente cada vez que se aprueba uno nuevo
// desde el panel (antes era una grilla de 2 columnas que se alargaba sin
// límite). Sin controles manuales a propósito (pedido explícito: "que no
// sea con flechas") — el fundido entre testimonios es puro (solo opacidad,
// sin desplazamiento horizontal) para que se sienta como una transición de
// diapositivas, no como un carrusel deslizante.
function CarruselTestimonios({ testimonios }: { testimonios: TestimonioItem[] }) {
  const [indice, setIndice] = useState(0);
  const [enPausa, setEnPausa] = useState(false);
  const total = testimonios.length;
  const reducirMovimiento = useRef(false);

  useEffect(() => {
    reducirMovimiento.current = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  }, []);

  useEffect(() => {
    if (total <= 1 || enPausa || reducirMovimiento.current) return;
    const id = window.setInterval(() => setIndice((i) => (i + 1) % total), INTERVALO_AUTOPLAY_MS);
    return () => window.clearInterval(id);
  }, [enPausa, total]);

  if (total === 0) return null;

  const actual = testimonios[indice];

  return (
    <div
      className="relative"
      onMouseEnter={() => setEnPausa(true)}
      onMouseLeave={() => setEnPausa(false)}
      onFocus={() => setEnPausa(true)}
      onBlur={() => setEnPausa(false)}
    >
      <div className="relative min-h-[320px] overflow-hidden md:min-h-[280px]">
        <AnimatePresence mode="wait">
          <motion.blockquote
            key={indice}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.9, ease: EASE }}
            aria-live="polite"
            className="card-edged flex min-h-[320px] flex-col bg-surface/97 p-8 md:min-h-[280px] md:p-10"
          >
            <div className="flex items-center justify-between">
              <Quotes weight="fill" className="h-8 w-8 text-gold" />
              <div className="flex gap-0.5">
                {[1, 2, 3, 4, 5].map((v) => (
                  <Star
                    key={v}
                    weight={v <= actual.calificacion ? "fill" : "regular"}
                    className={`h-3.5 w-3.5 ${v <= actual.calificacion ? "text-gold" : "text-ink-soft/30"}`}
                  />
                ))}
              </div>
            </div>
            <p className="mt-5 flex-1 text-balance font-display text-xl leading-snug text-ink md:text-2xl">
              {actual.cita}
            </p>
            <footer className="mt-6 text-sm">
              <p className="font-medium text-ink">{actual.nombre}</p>
              <p className="text-ink-soft">{actual.cargo}</p>
            </footer>
          </motion.blockquote>
        </AnimatePresence>
      </div>

      {total > 1 && (
        <div className="mt-6 flex items-center justify-center gap-2" aria-hidden="true">
          {testimonios.map((t, i) => (
            <span
              key={`${t.nombre}-${i}`}
              className={`h-1.5 rounded-full transition-all duration-500 ${
                i === indice ? "w-6 bg-gold" : "w-1.5 bg-night-ink/20"
              }`}
            />
          ))}
        </div>
      )}
    </div>
  );
}
