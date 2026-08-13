"use client";

import { useState, type FormEvent } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Star, X } from "@phosphor-icons/react";
import { toast } from "sonner";
import { crearTestimonio, ApiError } from "@/lib/api";

const EASE = [0.16, 1, 0.3, 1] as const;

type Estado = "idle" | "enviando" | "enviado";

export function TestimonioFormModal({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const [estado, setEstado] = useState<Estado>("idle");
  const [calificacion, setCalificacion] = useState(5);
  const [hoverEstrella, setHoverEstrella] = useState(0);
  const [aceptaDatos, setAceptaDatos] = useState(false);
  const [errorDatos, setErrorDatos] = useState(false);

  function cerrar() {
    onClose();
    setTimeout(() => {
      setEstado("idle");
      setCalificacion(5);
      setAceptaDatos(false);
      setErrorDatos(false);
    }, 300);
  }

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!aceptaDatos) {
      setErrorDatos(true);
      return;
    }

    const form = e.currentTarget;
    const data = new FormData(form);

    setEstado("enviando");
    const toastId = toast.loading("Enviando tu testimonio...");
    try {
      await crearTestimonio({
        nombre: String(data.get("nombre") ?? ""),
        empresa: String(data.get("empresa") ?? "") || undefined,
        cargo: String(data.get("cargo") ?? "") || undefined,
        cita: String(data.get("cita") ?? ""),
        calificacion,
        correo: String(data.get("correo") ?? ""),
      });
      setEstado("enviado");
      form.reset();
      toast.success("Testimonio enviado. Gracias por tu tiempo.", { id: toastId });
    } catch (err) {
      setEstado("idle");
      const mensaje =
        err instanceof ApiError
          ? err.message
          : "No pudimos enviar tu testimonio. Intenta de nuevo en unos minutos.";
      toast.error(mensaje, { id: toastId });
    }
  }

  return (
    <AnimatePresence>
      {open && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.25 }}
            onClick={cerrar}
            className="fixed inset-0 z-50 bg-night/70 backdrop-blur-sm"
          />
          <motion.div
            initial={{ opacity: 0, y: 24, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 24, scale: 0.97 }}
            transition={{ duration: 0.35, ease: EASE }}
            className="fixed inset-x-4 top-1/2 z-50 mx-auto max-w-lg -translate-y-1/2 rounded-[1.75rem] bg-paper p-7 shadow-[0_40px_80px_-20px_rgba(0,0,0,0.5)] ring-1 ring-line sm:p-9"
            role="dialog"
            aria-modal="true"
          >
            <button
              type="button"
              onClick={cerrar}
              aria-label="Cerrar"
              className="absolute right-5 top-5 flex h-9 w-9 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5 hover:text-ink"
            >
              <X className="h-4 w-4" />
            </button>

            {estado === "enviado" ? (
              <div className="flex min-h-[280px] flex-col items-center justify-center text-center">
                <p className="font-display text-2xl">Gracias por tu testimonio</p>
                <p className="mt-3 max-w-xs text-sm leading-relaxed text-ink-soft">
                  Lo revisaremos y, una vez aprobado, se mostrará en nuestro sitio.
                </p>
                <button
                  type="button"
                  onClick={cerrar}
                  className="mt-6 text-sm font-medium text-gold-deep underline underline-offset-4"
                >
                  Cerrar
                </button>
              </div>
            ) : (
              <>
                <p className="font-display text-2xl leading-snug">Cuéntanos tu experiencia</p>
                <p className="mt-2 text-sm leading-relaxed text-ink-soft">
                  Tu testimonio se publica luego de una breve revisión de nuestro equipo.
                </p>

                <form onSubmit={onSubmit} className="mt-6 space-y-4">
                  <div className="flex items-center gap-1">
                    {[1, 2, 3, 4, 5].map((valor) => (
                      <button
                        key={valor}
                        type="button"
                        onClick={() => setCalificacion(valor)}
                        onMouseEnter={() => setHoverEstrella(valor)}
                        onMouseLeave={() => setHoverEstrella(0)}
                        aria-label={`${valor} estrellas`}
                        className="p-0.5"
                      >
                        <Star
                          weight={(hoverEstrella || calificacion) >= valor ? "fill" : "regular"}
                          className={`h-6 w-6 ${
                            (hoverEstrella || calificacion) >= valor
                              ? "text-gold"
                              : "text-ink-soft/40"
                          }`}
                        />
                      </button>
                    ))}
                  </div>

                  <div className="grid gap-4 sm:grid-cols-2">
                    <div className="space-y-2">
                      <label htmlFor="t-nombre" className="text-sm font-medium text-ink">
                        Nombre
                      </label>
                      <input
                        id="t-nombre"
                        name="nombre"
                        required
                        maxLength={150}
                        className="w-full rounded-xl border border-line bg-surface px-4 py-2.5 text-sm text-ink focus:border-gold-deep focus:outline-none"
                      />
                    </div>
                    <div className="space-y-2">
                      <label htmlFor="t-correo" className="text-sm font-medium text-ink">
                        Correo
                      </label>
                      <input
                        id="t-correo"
                        name="correo"
                        type="email"
                        required
                        className="w-full rounded-xl border border-line bg-surface px-4 py-2.5 text-sm text-ink focus:border-gold-deep focus:outline-none"
                      />
                    </div>
                    <div className="space-y-2">
                      <label htmlFor="t-empresa" className="text-sm font-medium text-ink">
                        Empresa (opcional)
                      </label>
                      <input
                        id="t-empresa"
                        name="empresa"
                        maxLength={150}
                        className="w-full rounded-xl border border-line bg-surface px-4 py-2.5 text-sm text-ink focus:border-gold-deep focus:outline-none"
                      />
                    </div>
                    <div className="space-y-2">
                      <label htmlFor="t-cargo" className="text-sm font-medium text-ink">
                        Cargo (opcional)
                      </label>
                      <input
                        id="t-cargo"
                        name="cargo"
                        maxLength={150}
                        className="w-full rounded-xl border border-line bg-surface px-4 py-2.5 text-sm text-ink focus:border-gold-deep focus:outline-none"
                      />
                    </div>
                  </div>

                  <div className="space-y-2">
                    <label htmlFor="t-cita" className="text-sm font-medium text-ink">
                      Tu testimonio
                    </label>
                    <textarea
                      id="t-cita"
                      name="cita"
                      required
                      rows={4}
                      maxLength={600}
                      className="w-full resize-none rounded-xl border border-line bg-surface px-4 py-2.5 text-sm text-ink focus:border-gold-deep focus:outline-none"
                      placeholder="Cuéntanos cómo fue tu experiencia con SIE Jurídicos"
                    />
                  </div>

                  <label className="flex items-start gap-3 text-xs leading-relaxed text-ink-soft">
                    <input
                      type="checkbox"
                      checked={aceptaDatos}
                      onChange={(e) => {
                        setAceptaDatos(e.target.checked);
                        setErrorDatos(false);
                      }}
                      className="mt-0.5 h-4 w-4 shrink-0 accent-gold-deep"
                    />
                    Autorizo el tratamiento de mis datos personales conforme a la política de
                    privacidad de SIE Jurídicos (Ley 1581 de 2012).
                  </label>
                  {errorDatos && (
                    <p className="text-xs text-red-500">
                      Debes aceptar el tratamiento de datos para continuar.
                    </p>
                  )}

                  <button
                    type="submit"
                    disabled={estado === "enviando"}
                    className="w-full rounded-full bg-gold py-3 text-sm font-medium text-ink-fixed transition-opacity disabled:opacity-60"
                  >
                    {estado === "enviando" ? "Enviando" : "Enviar testimonio"}
                  </button>
                </form>
              </>
            )}
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
