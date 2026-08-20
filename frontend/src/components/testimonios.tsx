"use client";

import { useEffect, useState, type ReactNode } from "react";
import { motion } from "motion/react";
import { Quotes, Star } from "@phosphor-icons/react";
import { testimonios as testimoniosBase } from "@/lib/content";
import { getTestimoniosAprobados, type TestimonioPublico } from "@/lib/api";
import { TestimonioFormModal } from "@/components/testimonio-form-modal";

const EASE = [0.16, 1, 0.3, 1] as const;

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

      <div className="relative mx-auto max-w-6xl px-6">
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
            className="btn-sweep-ink rounded-lg border border-night-ink/25 px-5 py-2.5 text-sm font-medium text-night-ink transition-colors duration-200 hover:border-gold-deep hover:text-ink-fixed active:scale-[0.97]"
          >
            Deja tu testimonio
          </motion.button>
        </div>

        <div className="mt-10 grid gap-6 md:grid-cols-2">
          {todos.map((t, i) => (
            <motion.blockquote
              key={`${t.nombre}-${i}`}
              initial={{ opacity: 0, y: 24 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, amount: 0.4 }}
              transition={{ duration: 0.7, delay: 0.08 * i, ease: EASE }}
              className="card-edged flex flex-col bg-surface/97 p-8 md:p-10"
            >
              <div className="flex items-center justify-between">
                <motion.span
                  animate={{ y: [0, -5, 0], rotate: [0, -4, 0] }}
                  transition={{ duration: 4 + i * 0.5, repeat: Infinity, ease: "easeInOut" }}
                  className="inline-block"
                >
                  <Quotes weight="fill" className="h-8 w-8 text-gold" />
                </motion.span>
                <div className="flex gap-0.5">
                  {[1, 2, 3, 4, 5].map((v) => (
                    <Star
                      key={v}
                      weight={v <= t.calificacion ? "fill" : "regular"}
                      className={`h-3.5 w-3.5 ${v <= t.calificacion ? "text-gold" : "text-ink-soft/30"}`}
                    />
                  ))}
                </div>
              </div>
              <p className="mt-5 flex-1 text-balance font-display text-xl leading-snug text-ink md:text-2xl">
                {t.cita}
              </p>
              <footer className="mt-6 text-sm">
                <p className="font-medium text-ink">{t.nombre}</p>
                <p className="text-ink-soft">{t.cargo}</p>
              </footer>
            </motion.blockquote>
          ))}
        </div>
      </div>

      <TestimonioFormModal open={modalAbierto} onClose={() => setModalAbierto(false)} />
    </section>
  );
}
