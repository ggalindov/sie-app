"use client";

import { useEffect, useRef, useState, useCallback, type ReactNode } from "react";
import { motion } from "motion/react";
import { CaretLeft, CaretRight, Quotes, Star } from "@phosphor-icons/react";
import { testimonios as testimoniosBase } from "@/lib/content";
import { getTestimoniosAprobados, type TestimonioPublico } from "@/lib/api";
import { TestimonioFormModal } from "@/components/testimonio-form-modal";
import { MagneticButton } from "@/components/magnetic-button";

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
    <section id="testimonios" className="snap-slide section-seam frame-fixed relative overflow-hidden">
      <div className="absolute inset-0">{media}</div>
      <div className="absolute inset-0 bg-gradient-to-b from-night/93 via-night/88 to-night/93" />

      <div className="relative z-10 md:h-full md:overflow-hidden">
        <div className="mx-auto max-w-7xl px-5 sm:px-6 py-14 sm:py-20 md:flex md:h-full md:flex-col md:justify-center md:py-16 lg:px-8">
          <CarruselTestimonios testimonios={todos} onAbrirModal={() => setModalAbierto(true)} />
        </div>
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

          <motion.div
            initial={{ opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.4 }}
            transition={{ duration: 0.7, ease: EASE }}
          >
            <MagneticButton strength={0.3}>
              <button
                type="button"
                onClick={onAbrirModal}
                className="cta-boton-glass rounded-xl border border-night-ink/25 bg-night-ink/10 px-4 py-2 text-xs font-semibold text-night-ink transition-all duration-200 hover:border-gold hover:text-gold active:scale-[0.97] sm:px-5 sm:py-2.5 sm:text-sm"
              >
                <span className="relative z-10">Deja tu testimonio</span>
              </button>
            </MagneticButton>
          </motion.div>
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
              className="relative flex h-full min-h-[260px] sm:min-h-[300px] w-full flex-none flex-col justify-between border-t border-gold/25 bg-surface/60 pt-6 pr-5 pb-5 pl-1 sm:pt-7 sm:pr-7 sm:pb-6 sm:w-[calc(50%-0.75rem)] lg:w-[calc(33.333%-1rem)] lg:pr-8"
            >
              {/* Pull-quote editorial: filo vertical dorado delgado a la izquierda en vez de
                  una caja completa con borde y sombra -- se lee como una cita de revista, no
                  como una tarjeta de producto SaaS. */}
              <span aria-hidden="true" className="absolute inset-y-1 left-0 w-px bg-gradient-to-b from-gold/60 via-gold/20 to-transparent" />

              <div className="pl-5">
                <Quotes weight="fill" className="h-8 w-8 text-gold/70" />
                <p className="mt-3 sm:mt-4 font-display text-base sm:text-xl leading-relaxed text-ink line-clamp-5 sm:line-clamp-6">
                  {t.cita}
                </p>
              </div>

              <footer className="mt-6 flex items-center justify-between gap-3 pl-5">
                <div className="flex min-w-0 items-center gap-3">
                  <div className="relative flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-gold-pale/70 to-gold/20 font-display text-sm font-semibold text-gold-deep ring-1 ring-gold/30">
                    {obtenerIniciales(t.nombre)}
                  </div>
                  <div className="min-w-0 text-sm">
                    <p className="truncate font-medium text-ink">{t.nombre}</p>
                    <p className="truncate text-xs text-ink-soft sm:text-sm">{t.cargo || "Cliente verificado"}</p>
                  </div>
                </div>
                <div className="flex shrink-0 gap-0.5" aria-label={`${t.calificacion} de 5 estrellas`}>
                  {[1, 2, 3, 4, 5].map((v) => (
                    <Star
                      key={v}
                      weight={v <= t.calificacion ? "fill" : "regular"}
                      className={`h-3 w-3 ${v <= t.calificacion ? "text-gold" : "text-ink-soft/25"}`}
                    />
                  ))}
                </div>
              </footer>
            </blockquote>
          ))}
        </div>
      </motion.div>

      {/* Indicador de progreso: contador + línea delgada en vez de una fila de puntos que
          crece sin límite con el número real de testimonios aprobados (se veía desbordada
          con muchos testimonios, ver captura de referencia) -- escala a cualquier cantidad
          sin perder elegancia. */}
      {maxIndice > 0 && (
        <div className="mt-8 flex items-center justify-center gap-3">
          <span className="font-mono text-xs tabular-nums text-ink-soft/70">
            {String(indice + 1).padStart(2, "0")}
          </span>
          <div className="relative h-px w-24 overflow-hidden rounded-full bg-night-ink/15 sm:w-32">
            <motion.span
              className="absolute inset-y-0 left-0 bg-gold"
              animate={{ width: `${((indice + 1) / (maxIndice + 1)) * 100}%` }}
              transition={{ duration: 0.5, ease: EASE }}
            />
          </div>
          <span className="font-mono text-xs tabular-nums text-ink-soft/70">
            {String(maxIndice + 1).padStart(2, "0")}
          </span>
        </div>
      )}
    </div>
  );
}

