"use client";

import { useState, type FormEvent } from "react";
import { Dialog } from "@base-ui/react/dialog";
import { Star, X } from "@phosphor-icons/react";
import { toast } from "sonner";
import { crearTestimonio, ApiError } from "@/lib/api";
import { CampoTrampa } from "@/components/campo-trampa";
import { CasillaConsentimiento } from "@/components/casilla-consentimiento";

type Estado = "idle" | "enviando" | "enviado";

// Migrado de un <motion.div role="dialog"> hecho a mano a Dialog de @base-ui/react
// (mismo patrón que newsletter-popup.tsx): a mano le faltaba cierre con Escape, trampa
// de foco y devolución de foco al elemento que lo abrió — base-ui resuelve las tres
// cosas de fábrica. La animación de entrada/salida ahora es CSS sobre los atributos de
// estado de base-ui (data-starting-style/data-ending-style), no Motion.
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
        sitioWeb: String(data.get("sitioWeb") ?? ""),
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
    <Dialog.Root open={open} onOpenChange={(next) => !next && cerrar()}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-night/70 backdrop-blur-sm transition-opacity duration-300 data-[ending-style]:opacity-0 data-[starting-style]:opacity-0" />
        <Dialog.Popup className="fixed left-1/2 top-1/2 z-50 w-[calc(100%-2rem)] max-w-lg -translate-x-1/2 -translate-y-1/2 max-h-[90dvh] overflow-y-auto rounded-2xl sm:rounded-[1.75rem] bg-paper p-5 sm:p-8 shadow-[0_25px_60px_-15px_rgba(0,0,0,0.5)] ring-1 ring-line transition-all duration-300 data-[ending-style]:scale-95 data-[ending-style]:opacity-0 data-[starting-style]:scale-95 data-[starting-style]:opacity-0">
          <Dialog.Close
            aria-label="Cerrar"
            className="absolute right-3.5 top-3.5 sm:right-5 sm:top-5 flex h-8 w-8 sm:h-9 sm:w-9 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5 hover:text-ink transition-colors"
          >
            <X className="h-4 w-4" />
          </Dialog.Close>

          {estado === "enviado" ? (
            <div className="flex min-h-[220px] sm:min-h-[260px] flex-col items-center justify-center text-center p-2 sm:p-4">
              <Dialog.Title className="font-display text-xl sm:text-2xl">Gracias por tu testimonio</Dialog.Title>
              <Dialog.Description className="mt-2 sm:mt-3 max-w-xs text-xs sm:text-sm leading-relaxed text-ink-soft">
                Lo revisaremos y, una vez aprobado, se mostrará en nuestro sitio.
              </Dialog.Description>
              <Dialog.Close className="mt-5 sm:mt-6 text-sm font-medium text-gold-deep underline underline-offset-4">
                Cerrar
              </Dialog.Close>
            </div>
          ) : (
            <>
              <div className="pr-6">
                <Dialog.Title className="font-display text-xl sm:text-2xl leading-snug">
                  Cuéntanos tu experiencia
                </Dialog.Title>
                <Dialog.Description className="mt-1 sm:mt-2 text-xs sm:text-sm leading-relaxed text-ink-soft">
                  Tu testimonio se publica luego de una breve revisión de nuestro equipo.
                </Dialog.Description>
              </div>

              <form onSubmit={onSubmit} className="mt-4 sm:mt-6 space-y-3 sm:space-y-4">
                <CampoTrampa />
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
                        className={`h-5 w-5 sm:h-6 sm:w-6 ${
                          (hoverEstrella || calificacion) >= valor
                            ? "text-gold"
                            : "text-ink-soft/40"
                        }`}
                      />
                    </button>
                  ))}
                </div>

                <div className="grid grid-cols-2 gap-2.5 sm:gap-4">
                  <div className="col-span-2 sm:col-span-1 space-y-1 sm:space-y-1.5">
                    <label htmlFor="t-nombre" className="text-xs sm:text-sm font-medium text-ink">
                      Nombre
                    </label>
                    <input
                      id="t-nombre"
                      name="nombre"
                      required
                      maxLength={150}
                      className="w-full rounded-lg sm:rounded-xl border border-line bg-surface px-3 py-2 sm:px-4 sm:py-2.5 text-xs sm:text-sm text-ink focus:border-gold-deep focus:outline-none"
                    />
                  </div>
                  <div className="col-span-2 sm:col-span-1 space-y-1 sm:space-y-1.5">
                    <label htmlFor="t-correo" className="text-xs sm:text-sm font-medium text-ink">
                      Correo
                    </label>
                    <input
                      id="t-correo"
                      name="correo"
                      type="email"
                      required
                      className="w-full rounded-lg sm:rounded-xl border border-line bg-surface px-3 py-2 sm:px-4 sm:py-2.5 text-xs sm:text-sm text-ink focus:border-gold-deep focus:outline-none"
                    />
                  </div>
                  <div className="col-span-1 space-y-1 sm:space-y-1.5">
                    <label htmlFor="t-empresa" className="text-xs sm:text-sm font-medium text-ink">
                      Empresa <span className="text-[10px] sm:text-xs text-ink-soft font-normal">(opcional)</span>
                    </label>
                    <input
                      id="t-empresa"
                      name="empresa"
                      maxLength={150}
                      className="w-full rounded-lg sm:rounded-xl border border-line bg-surface px-3 py-2 sm:px-4 sm:py-2.5 text-xs sm:text-sm text-ink focus:border-gold-deep focus:outline-none"
                    />
                  </div>
                  <div className="col-span-1 space-y-1 sm:space-y-1.5">
                    <label htmlFor="t-cargo" className="text-xs sm:text-sm font-medium text-ink">
                      Cargo <span className="text-[10px] sm:text-xs text-ink-soft font-normal">(opcional)</span>
                    </label>
                    <input
                      id="t-cargo"
                      name="cargo"
                      maxLength={150}
                      className="w-full rounded-lg sm:rounded-xl border border-line bg-surface px-3 py-2 sm:px-4 sm:py-2.5 text-xs sm:text-sm text-ink focus:border-gold-deep focus:outline-none"
                    />
                  </div>
                </div>

                <div className="space-y-1 sm:space-y-1.5">
                  <label htmlFor="t-cita" className="text-xs sm:text-sm font-medium text-ink">
                    Tu testimonio
                  </label>
                  <textarea
                    id="t-cita"
                    name="cita"
                    required
                    rows={3}
                    maxLength={600}
                    className="w-full resize-none rounded-lg sm:rounded-xl border border-line bg-surface px-3 py-2 sm:px-4 sm:py-2.5 text-xs sm:text-sm text-ink focus:border-gold-deep focus:outline-none"
                    placeholder="Cuéntanos cómo fue tu experiencia con SIE Jurídicos"
                  />
                </div>

                <CasillaConsentimiento
                  checked={aceptaDatos}
                  onChange={(v) => {
                    setAceptaDatos(v);
                    setErrorDatos(false);
                  }}
                >
                  Autorizo el tratamiento de mis datos personales conforme a la política de
                  privacidad de SIE Jurídicos (Ley 1581 de 2012).
                </CasillaConsentimiento>
                {errorDatos && (
                  <p className="text-xs text-red-500">
                    Debes aceptar el tratamiento de datos para continuar.
                  </p>
                )}

                <button
                  type="submit"
                  disabled={estado === "enviando"}
                  className="cta-boton w-full rounded-lg bg-gold py-2.5 sm:py-3 text-sm font-medium text-ink-fixed transition-opacity disabled:opacity-60"
                >
                  {estado === "enviando" ? "Enviando" : "Enviar testimonio"}
                </button>
              </form>
            </>
          )}
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
