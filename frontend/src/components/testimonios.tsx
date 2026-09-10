"use client";

import { useEffect, useRef, useState, useCallback, type ReactNode } from "react";
import { motion } from "motion/react";
import { CaretLeft, CaretRight, Quotes, Star } from "@phosphor-icons/react";
import { testimonios as testimoniosBase } from "@/lib/content";
import { getTestimoniosAprobados, type TestimonioPublico } from "@/lib/api";
import { TestimonioFormModal } from "@/components/testimonio-form-modal";

const EASE = [0.16, 1, 0.3, 1] as const;
const INTERVALO_AUTOPLAY_MS = 6500;

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

  // Link directo "abre el popup de testimonio solo": ?abrir=testimonio en la URL del home
  useEffect(() => {
    try {
      const parametros = new URLSearchParams(window.location.search);
      if (parametros.get("abrir") === "testimonio") {
        setModalAbierto(true);
      }
    } catch {
      // no crítico
    }
  }, []);

  const todos: TestimonioItem[] = [
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

      <div className="relative mx-auto max-w-7xl px-6 lg:px-8">
        <CarruselTestimonios testimonios={todos} onAbrirModal={() => setModalAbierto(true)} />
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

function obtenerIniciales(nombre: string) {
  const palabras = nombre.trim().split(/\s+/).filter(Boolean);
  if (palabras.length === 0) return "SJ";
  if (palabras.length === 1) return palabras[0].slice(0, 2).toUpperCase();
  return (palabras[0][0] + palabras[palabras.length - 1][0]).toUpperCase();
}

function CarruselTestimonios({
  testimonios,
  onAbrirModal,
}: {
  testimonios: TestimonioItem[];
  onAbrirModal: () => void;
}) {
  const [indice, setIndice] = useState(0);
  const [enPausa, setEnPausa] = useState(false);
  const [visibles, setVisibles] = useState(3);
  const [stepWidth, setStepWidth] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);
  const trackRef = useRef<HTMLDivElement>(null);
  const touchStartX = useRef<number | null>(null);
  const reducirMovimiento = useRef(false);

  const total = testimonios.length;
  const maxIndice = Math.max(0, total - visibles);

  useEffect(() => {
    reducirMovimiento.current = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  }, []);

  const actualizarDimensiones = useCallback(() => {
    if (typeof window === "undefined") return;
    const ancho = window.innerWidth;
    const numVisibles = ancho < 640 ? 1 : ancho < 1024 ? 2 : 3;
    setVisibles(numVisibles);

    if (trackRef.current && trackRef.current.children.length > 1) {
      const card0 = trackRef.current.children[0] as HTMLElement;
      const card1 = trackRef.current.children[1] as HTMLElement;
      setStepWidth(card1.offsetLeft - card0.offsetLeft);
    } else if (trackRef.current && trackRef.current.children.length === 1) {
      const card0 = trackRef.current.children[0] as HTMLElement;
      setStepWidth(card0.offsetWidth + 24);
    }
  }, []);

  useEffect(() => {
    actualizarDimensiones();
    window.addEventListener("resize", actualizarDimensiones);
    return () => window.removeEventListener("resize", actualizarDimensiones);
  }, [actualizarDimensiones, testimonios.length]);

  useEffect(() => {
    if (indice > maxIndice) {
      setIndice(maxIndice);
    }
  }, [indice, maxIndice]);

  const avanzar = useCallback(() => {
    setIndice((i) => (i >= maxIndice ? 0 : i + 1));
  }, [maxIndice]);

  const retroceder = useCallback(() => {
    setIndice((i) => (i <= 0 ? maxIndice : i - 1));
  }, [maxIndice]);

  // Autoplay continuo con pausa suave al interactuar
  useEffect(() => {
    if (total <= visibles || enPausa || reducirMovimiento.current) return;
    const id = window.setInterval(avanzar, INTERVALO_AUTOPLAY_MS);
    return () => window.clearInterval(id);
  }, [avanzar, enPausa, total, visibles]);

  const handleTouchStart = (e: React.TouchEvent) => {
    touchStartX.current = e.touches[0].clientX;
    setEnPausa(true);
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    if (touchStartX.current === null) return;
    const deltaX = e.changedTouches[0].clientX - touchStartX.current;
    if (deltaX < -45) {
      avanzar();
    } else if (deltaX > 45) {
      retroceder();
    }
    touchStartX.current = null;
    setEnPausa(false);
  };

  if (total === 0) return null;

  const translateX = stepWidth > 0 ? indice * stepWidth : 0;

  return (
    <div
      onMouseEnter={() => setEnPausa(true)}
      onMouseLeave={() => setEnPausa(false)}
      onFocus={() => setEnPausa(true)}
      onBlur={() => setEnPausa(false)}
      onTouchStart={handleTouchStart}
      onTouchEnd={handleTouchEnd}
      className="relative"
    >
      {/* Cabecera de la sección con título y controles de navegación */}
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <motion.p
            initial={{ opacity: 0, y: 16 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.4 }}
            transition={{ duration: 0.7, ease: EASE }}
            className="text-xs font-semibold uppercase tracking-widest text-gold-deep"
          >
            Testimonios reales
          </motion.p>
          <motion.h2
            initial={{ opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.4 }}
            transition={{ duration: 0.7, ease: EASE }}
            className="mt-1.5 font-display text-3xl leading-tight tracking-tight text-night-ink sm:text-4xl md:text-5xl"
          >
            Lo que dicen nuestros clientes
          </motion.h2>
        </div>

        <div className="flex items-center gap-3">
          {total > visibles && (
            <div className="flex items-center gap-1.5" aria-label="Navegación de testimonios">
              <button
                type="button"
                onClick={retroceder}
                aria-label="Testimonios anteriores"
                className="flex h-9 w-9 items-center justify-center rounded-full border border-night-ink/20 text-night-ink transition-colors hover:border-gold hover:text-gold active:scale-95 sm:h-10 sm:w-10"
              >
                <CaretLeft weight="bold" className="h-4 w-4" />
              </button>
              <button
                type="button"
                onClick={avanzar}
                aria-label="Siguientes testimonios"
                className="flex h-9 w-9 items-center justify-center rounded-full border border-night-ink/20 text-night-ink transition-colors hover:border-gold hover:text-gold active:scale-95 sm:h-10 sm:w-10"
              >
                <CaretRight weight="bold" className="h-4 w-4" />
              </button>
            </div>
          )}

          <motion.button
            type="button"
            onClick={onAbrirModal}
            initial={{ opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.4 }}
            transition={{ duration: 0.7, ease: EASE }}
            className="cta-boton rounded-lg border border-night-ink/25 px-4 py-2 text-xs font-medium text-night-ink transition-colors duration-200 hover:border-gold-deep hover:text-ink-fixed active:scale-[0.97] sm:px-5 sm:py-2.5 sm:text-sm"
          >
            Deja tu testimonio
          </motion.button>
        </div>
      </div>

      {/* Carrusel multitarjeta con desplazamiento suave */}
      <motion.div
        initial={{ opacity: 0, y: 24 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true, amount: 0.3 }}
        transition={{ duration: 0.7, delay: 0.1, ease: EASE }}
        ref={containerRef}
        className="mt-10 overflow-hidden"
      >
        <div
          ref={trackRef}
          className="flex items-stretch gap-6 transition-transform duration-700 ease-[cubic-bezier(0.16,1,0.3,1)]"
          style={{
            transform: `translate3d(-${translateX}px, 0, 0)`,
          }}
        >
          {testimonios.map((t, idx) => (
            <blockquote
              key={`${t.nombre}-${idx}`}
              className="card-edged flex h-full min-h-[300px] w-full flex-none flex-col justify-between bg-surface/97 p-6 sm:w-[calc(50%-0.75rem)] sm:p-7 lg:w-[calc(33.333%-1rem)] lg:p-8"
            >
              <div>
                <div className="flex items-center justify-between">
                  <Quotes weight="fill" className="h-7 w-7 text-gold" />
                  <div className="flex gap-0.5" aria-label={`${t.calificacion} de 5 estrellas`}>
                    {[1, 2, 3, 4, 5].map((v) => (
                      <Star
                        key={v}
                        weight={v <= t.calificacion ? "fill" : "regular"}
                        className={`h-3.5 w-3.5 ${v <= t.calificacion ? "text-gold" : "text-ink-soft/30"}`}
                      />
                    ))}
                  </div>
                </div>
                <p className="mt-4 font-display text-base leading-relaxed text-ink line-clamp-6 sm:text-lg">
                  &ldquo;{t.cita}&rdquo;
                </p>
              </div>

              <footer className="mt-6 flex items-center gap-3 border-t border-line/40 pt-4">
                <div
                  className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-gold/30 bg-gold/15 font-display text-sm font-semibold text-gold-deep"
                  aria-hidden="true"
                >
                  {obtenerIniciales(t.nombre)}
                </div>
                <div className="min-w-0 flex-1 text-sm">
                  <p className="truncate font-medium text-ink">{t.nombre}</p>
                  <p className="truncate text-xs text-ink-soft sm:text-sm">{t.cargo || "Cliente verificado"}</p>
                </div>
              </footer>
            </blockquote>
          ))}
        </div>
      </motion.div>

      {/* Indicadores de progreso / puntos de salto */}
      {maxIndice > 0 && (
        <div className="mt-8 flex items-center justify-center gap-2" aria-hidden="true">
          {Array.from({ length: maxIndice + 1 }).map((_, i) => (
            <button
              key={i}
              type="button"
              onClick={() => setIndice(i)}
              aria-label={`Ver testimonios grupo ${i + 1}`}
              className={`h-1.5 rounded-full transition-all duration-300 ${
                i === indice ? "w-6 bg-gold" : "w-1.5 bg-night-ink/25 hover:bg-night-ink/50"
              }`}
            />
          ))}
        </div>
      )}
    </div>
  );
}

