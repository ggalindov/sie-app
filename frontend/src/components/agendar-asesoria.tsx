"use client";

import { useState, type FormEvent, type ReactNode } from "react";
import { motion } from "motion/react";
import { toast } from "sonner";
import { Spinner } from "@phosphor-icons/react";
import { crearSolicitud, ApiError } from "@/lib/api";
import { siteConfig } from "@/lib/site-config";

const EASE = [0.16, 1, 0.3, 1] as const;

type Estado = "idle" | "enviando" | "enviado";

// Sección propia e independiente, a pedido explícito del usuario: "Agendar
// asesoría" no comparte espacio ni intención con "Contacto" (ese es solo un
// directorio de medios oficiales, ver contacto.tsx). Aquí vive el único
// formulario que crea una solicitud real en el backend. El video de fondo
// queda detrás de todo con un velo oscuro; la tarjeta del formulario se
// queda opaca (bg-paper) a propósito, para que los campos nunca pierdan
// contraste sin importar qué esté pasando en el video.
export function AgendarAsesoria({ media }: { media: ReactNode }) {
  const [estado, setEstado] = useState<Estado>("idle");
  const [aceptaDatos, setAceptaDatos] = useState(false);
  const [aceptaMarketing, setAceptaMarketing] = useState(false);
  const [errorDatos, setErrorDatos] = useState(false);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();

    if (!aceptaDatos) {
      setErrorDatos(true);
      return;
    }

    const form = e.currentTarget;
    const data = new FormData(form);

    setEstado("enviando");
    const toastId = toast.loading("Enviando tu solicitud...");
    try {
      await crearSolicitud({
        nombre: String(data.get("nombre") ?? ""),
        correo: String(data.get("correo") ?? ""),
        telefono: String(data.get("telefono") ?? "") || undefined,
        mensaje: String(data.get("mensaje") ?? ""),
        aceptaTratamientoDatos: aceptaDatos,
        aceptaMarketing,
      });
      setEstado("enviado");
      form.reset();
      setAceptaDatos(false);
      setAceptaMarketing(false);
      toast.success("Solicitud enviada. Te contactaremos pronto.", { id: toastId });
    } catch (err) {
      setEstado("idle");
      const mensaje =
        err instanceof ApiError
          ? err.message
          : "No pudimos enviar tu solicitud. Intenta de nuevo en unos minutos.";
      toast.error(mensaje, { id: toastId });
    }
  }

  return (
    <section id="agendar" className="snap-slide section-seam relative overflow-hidden py-24 md:py-32">
      <div className="absolute inset-0">{media}</div>
      <div className="absolute inset-0 bg-gradient-to-r from-night/92 via-night/85 to-night/60" />

      <div className="relative mx-auto max-w-7xl px-6">
        <div className="grid gap-14 md:grid-cols-12 md:gap-12">
          <motion.div
            initial={{ opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.3 }}
            transition={{ duration: 0.7, ease: EASE }}
            className="md:col-span-4 md:self-center"
          >
            <h2 className="font-display text-3xl leading-tight tracking-tight text-night-ink md:text-4xl">
              Agendar asesoría
            </h2>
            <p className="mt-4 max-w-sm text-base leading-relaxed text-night-ink/70">
              Cuéntanos tu caso y un abogado de SIE Jurídicos te contactará
              para agendar tu asesoría.
            </p>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.3 }}
            transition={{ duration: 0.7, delay: 0.1, ease: EASE }}
            className="rounded-[2rem] bg-ink/5 p-2 ring-1 ring-ink/5 md:col-span-8"
          >
            <div className="rounded-[1.6rem] bg-paper p-7 ring-1 ring-line md:p-10">
              {estado === "enviado" ? (
                <div className="flex min-h-[320px] flex-col items-center justify-center text-center">
                  <p className="font-display text-2xl">Solicitud enviada</p>
                  <p className="mt-3 max-w-xs text-sm leading-relaxed text-ink-soft">
                    Gracias por escribirnos. Un abogado de nuestro equipo te
                    contactará pronto.
                  </p>
                  <button
                    type="button"
                    onClick={() => setEstado("idle")}
                    className="mt-6 text-sm font-medium text-gold-deep underline underline-offset-4"
                  >
                    Enviar otra solicitud
                  </button>
                </div>
              ) : (
                <form onSubmit={onSubmit} className="space-y-6">
                  <div className="space-y-2">
                    <label htmlFor="nombre" className="text-sm font-medium text-ink">
                      Nombre completo
                    </label>
                    <input
                      id="nombre"
                      name="nombre"
                      required
                      className="w-full rounded-xl border border-line bg-surface px-4 py-3.5 text-sm text-ink placeholder:text-ink-soft/60 focus:border-gold-deep focus:outline-none"
                      placeholder="Tu nombre"
                    />
                  </div>

                  <div className="grid gap-6 sm:grid-cols-2">
                    <div className="space-y-2">
                      <label htmlFor="correo" className="text-sm font-medium text-ink">
                        Correo
                      </label>
                      <input
                        id="correo"
                        name="correo"
                        type="email"
                        required
                        className="w-full rounded-xl border border-line bg-surface px-4 py-3.5 text-sm text-ink placeholder:text-ink-soft/60 focus:border-gold-deep focus:outline-none"
                        placeholder="tucorreo@ejemplo.com"
                      />
                    </div>
                    <div className="space-y-2">
                      <label htmlFor="telefono" className="text-sm font-medium text-ink">
                        Teléfono (opcional)
                      </label>
                      <input
                        id="telefono"
                        name="telefono"
                        className="w-full rounded-xl border border-line bg-surface px-4 py-3.5 text-sm text-ink placeholder:text-ink-soft/60 focus:border-gold-deep focus:outline-none"
                        placeholder="300 000 0000"
                      />
                    </div>
                  </div>

                  <div className="space-y-2">
                    <label htmlFor="mensaje" className="text-sm font-medium text-ink">
                      Cuéntanos tu caso
                    </label>
                    <textarea
                      id="mensaje"
                      name="mensaje"
                      required
                      rows={5}
                      className="w-full resize-none rounded-xl border border-line bg-surface px-4 py-3.5 text-sm text-ink placeholder:text-ink-soft/60 focus:border-gold-deep focus:outline-none"
                      placeholder="¿En qué podemos ayudarte?"
                    />
                  </div>

                  <div className="space-y-3 pt-1">
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
                      Autorizo el tratamiento de mis datos personales conforme
                      a la política de privacidad de SIE Jurídicos (Ley 1581
                      de 2012).
                    </label>
                    {errorDatos && (
                      <p className="text-xs text-red-700">
                        Debes aceptar el tratamiento de datos para continuar.
                      </p>
                    )}

                    <label className="flex items-start gap-3 text-xs leading-relaxed text-ink-soft">
                      <input
                        type="checkbox"
                        checked={aceptaMarketing}
                        onChange={(e) => setAceptaMarketing(e.target.checked)}
                        className="mt-0.5 h-4 w-4 shrink-0 accent-gold-deep"
                      />
                      Sí, quiero recibir novedades y contenido jurídico por
                      correo electrónico.
                    </label>
                  </div>

                  <button
                    type="submit"
                    disabled={estado === "enviando"}
                    className="flex w-full items-center justify-center gap-2 rounded-full bg-gold py-4 text-sm font-medium text-ink-fixed transition-opacity duration-300 disabled:opacity-60 sm:w-auto sm:px-10"
                  >
                    {estado === "enviando" && (
                      <Spinner className="h-4 w-4 animate-spin" weight="bold" />
                    )}
                    {estado === "enviando" ? "Enviando" : siteConfig.ctaPrincipal}
                  </button>
                </form>
              )}
            </div>
          </motion.div>
        </div>
      </div>
    </section>
  );
}
