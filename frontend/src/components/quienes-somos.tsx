"use client";

import { useEffect, useRef } from "react";
import { motion, useInView, useMotionValue } from "motion/react";
import { animate } from "motion";

const EASE = [0.16, 1, 0.3, 1] as const;

const stats = [
  { numero: 20, prefijo: "", sufijo: " años", etiqueta: "De trayectoria" },
  { numero: 800, prefijo: "+", sufijo: "", etiqueta: "Casos ganados" },
  { numero: 8, prefijo: "", sufijo: "", etiqueta: "Profesionales en el equipo" },
];

function Contador({
  numero,
  prefijo,
  sufijo,
}: {
  numero: number;
  prefijo: string;
  sufijo: string;
}) {
  const ref = useRef<HTMLSpanElement>(null);
  const enVista = useInView(ref, { once: true, amount: 0.6 });
  const valor = useMotionValue(0);

  useEffect(() => {
    if (!enVista) return;
    const controles = animate(valor, numero, {
      duration: 1.7,
      ease: [0.16, 1, 0.3, 1],
      onUpdate: (v) => {
        if (ref.current) ref.current.textContent = `${prefijo}${Math.round(v)}${sufijo}`;
      },
    });
    return () => controles.stop();
  }, [enVista, numero, prefijo, sufijo, valor]);

  return (
    <span ref={ref}>
      {prefijo}0{sufijo}
    </span>
  );
}

export function QuienesSomos() {
  return (
    <section id="quienes-somos" className="section-seam py-24 md:py-32">
      <div className="mx-auto max-w-7xl px-6">
        <div className="grid gap-16 md:grid-cols-12">
          <motion.div
            initial={{ opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.4 }}
            transition={{ duration: 0.7, ease: EASE }}
            className="md:col-span-7"
          >
            <h2 className="font-display text-3xl leading-tight tracking-tight md:text-4xl">
              Más que abogados, somos aliados.
            </h2>
            <div className="mt-6 max-w-xl space-y-4 text-base leading-relaxed text-ink-soft">
              <p>
                Hace más de 20 años iniciamos este camino con un propósito
                claro: ofrecer asesoría jurídica seria, transparente y
                comprometida con la justicia. Con el tiempo hemos crecido
                junto a quienes han confiado en nosotros, acompañando
                decisiones importantes y desafíos empresariales que requieren
                respaldo legal sólido.
              </p>
              <p>
                Creemos que el derecho no debe ser confuso ni distante. Por
                eso brindamos seguimiento constante a cada proceso, con
                informes periódicos que permiten a nuestros clientes estar
                informados y tranquilos.
              </p>
            </div>
          </motion.div>

          <div className="grid grid-cols-3 gap-4 self-center md:col-span-5 md:grid-cols-1 md:gap-6">
            {stats.map((stat, i) => (
              <motion.div
                key={stat.etiqueta}
                initial={{ opacity: 0, y: 24 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, amount: 0.4 }}
                transition={{ duration: 0.6, delay: 0.1 * i, ease: EASE }}
                className="rounded-[1.5rem] bg-ink/5 p-1.5 ring-1 ring-ink/5 md:flex md:items-center md:gap-5 md:p-2"
              >
                <div className="flex flex-col items-start justify-center gap-1 rounded-[1.15rem] bg-surface px-5 py-6 ring-1 ring-line md:w-full md:flex-row md:items-baseline md:gap-3 md:px-7">
                  <p className="font-display text-3xl leading-none text-gold-deep md:text-4xl">
                    <Contador numero={stat.numero} prefijo={stat.prefijo} sufijo={stat.sufijo} />
                  </p>
                  <p className="text-xs font-medium text-ink-soft md:text-sm">
                    {stat.etiqueta}
                  </p>
                </div>
              </motion.div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
