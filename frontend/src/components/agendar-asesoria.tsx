"use client";

import { useState, type FormEvent, type ReactNode } from "react";
import { AnimatePresence, motion } from "motion/react";
import { toast } from "sonner";
import { Scales, SealCheck, ChatCircleText, ArrowRight } from "@phosphor-icons/react";
import { crearSolicitud, ApiError } from "@/lib/api";
import { siteConfig } from "@/lib/site-config";
import { CampoTrampa } from "@/components/campo-trampa";
import { MagneticButton } from "@/components/magnetic-button";
import { CasillaConsentimiento } from "@/components/casilla-consentimiento";

const EASE = [0.16, 1, 0.3, 1] as const;

type Estado = "idle" | "enviando" | "enviado";

// Tres hechos ya verificados en otras secciones (ver quienes-somos.tsx / trusted-by.tsx),
// nunca una cifra o promesa nueva inventada para "sonar mejor" (ver CLAUDE.md: nada de
// afirmaciones que no se puedan sostener). Reforzar confianza justo antes del formulario
// -- el momento exacto en el que un visitante decide si vale la pena escribir o no.
const puntosConfianza = [
  { icono: Scales, texto: "20+ años de experiencia" },
  { icono: SealCheck, texto: "800+ casos ganados" },
  { icono: ChatCircleText, texto: "Te responde un abogado, no un bot" },
];

const staggerContenedor = {
  oculto: {},
  visible: { transition: { staggerChildren: 0.09, delayChildren: 0.08 } },
};
const staggerItem = {
  oculto: { opacity: 0, y: 16 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.5, ease: EASE } },
};

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
        sitioWeb: String(data.get("sitioWeb") ?? ""),
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
            <h2 className="font-display text-4xl leading-tight tracking-tight text-night-ink md:text-5xl">
              Agendar asesoría
            </h2>
            <p className="mt-4 max-w-sm text-base leading-relaxed text-night-ink/70">
              Cuéntanos tu caso y un abogado de SIE Jurídicos te contactará
              para agendar tu asesoría.
            </p>

            <ul className="mt-8 space-y-4 border-t border-night-ink/15 pt-7">
              {puntosConfianza.map((punto, i) => (
                <motion.li
                  key={punto.texto}
                  initial={{ opacity: 0, x: -12 }}
                  whileInView={{ opacity: 1, x: 0 }}
                  viewport={{ once: true, amount: 0.6 }}
                  transition={{ duration: 0.5, delay: 0.15 + i * 0.1, ease: EASE }}
                  className="flex items-center gap-3"
                >
                  <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gold/12 text-gold ring-1 ring-gold/25">
                    <punto.icono weight="bold" className="h-4 w-4" />
                  </span>
                  <span className="text-sm font-medium text-night-ink/85">{punto.texto}</span>
                </motion.li>
              ))}
            </ul>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.3 }}
            transition={{ duration: 0.7, delay: 0.1, ease: EASE }}
            className="rounded-[2rem] bg-ink/5 p-2 ring-1 ring-ink/5 md:col-span-8"
          >
            <div className="rounded-[1.6rem] bg-paper p-7 ring-1 ring-line md:p-10">
              <AnimatePresence mode="wait">
                {estado === "enviado" ? (
                  <motion.div
                    key="enviado"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    transition={{ duration: 0.3, ease: EASE }}
                    className="flex min-h-[380px] flex-col items-center justify-center text-center"
                  >
                    <motion.span
                      initial={{ scale: 0.4, opacity: 0 }}
                      animate={{ scale: 1, opacity: 1 }}
                      transition={{ duration: 0.55, ease: EASE }}
                      className="flex h-20 w-20 items-center justify-center rounded-full bg-gold-pale/60 ring-1 ring-gold/30"
                    >
                      <svg viewBox="0 0 24 24" className="h-9 w-9 text-gold-deep" fill="none">
                        <motion.path
                          d="M4 12.5L9.5 18L20 6"
                          stroke="currentColor"
                          strokeWidth="2.4"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          initial={{ pathLength: 0 }}
                          animate={{ pathLength: 1 }}
                          transition={{ duration: 0.55, delay: 0.3, ease: EASE }}
                        />
                      </svg>
                    </motion.span>
                    <motion.p
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ duration: 0.5, delay: 0.4, ease: EASE }}
                      className="mt-6 font-display text-2xl"
                    >
                      Solicitud enviada
                    </motion.p>
                    <motion.p
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ duration: 0.5, delay: 0.5, ease: EASE }}
                      className="mt-3 max-w-xs text-sm leading-relaxed text-ink-soft"
                    >
                      Gracias por escribirnos. Un abogado de nuestro equipo te
                      contactará pronto.
                    </motion.p>
                    <motion.button
                      initial={{ opacity: 0, y: 10 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ duration: 0.5, delay: 0.6, ease: EASE }}
                      type="button"
                      onClick={() => setEstado("idle")}
                      className="mt-6 text-sm font-medium text-gold-deep underline underline-offset-4"
                    >
                      Enviar otra solicitud
                    </motion.button>
                  </motion.div>
                ) : (
                  <motion.form
                    key="formulario"
                    onSubmit={onSubmit}
                    variants={staggerContenedor}
                    initial="oculto"
                    animate="visible"
                    className="space-y-6"
                  >
                    <CampoTrampa />
                    <motion.div variants={staggerItem} className="space-y-2">
                      <label htmlFor="nombre" className="text-sm font-medium text-ink">
                        Nombre completo
                      </label>
                      <input
                        id="nombre"
                        name="nombre"
                        required
                        className="w-full rounded-xl border border-line bg-surface px-4 py-3.5 text-sm text-ink placeholder:text-ink-soft/60 transition-all duration-200 focus:border-gold-deep focus:outline-none focus:ring-4 focus:ring-gold/10"
                        placeholder="Tu nombre"
                      />
                    </motion.div>

                    <motion.div variants={staggerItem} className="grid gap-6 sm:grid-cols-2">
                      <div className="space-y-2">
                        <label htmlFor="correo" className="text-sm font-medium text-ink">
                          Correo
                        </label>
                        <input
                          id="correo"
                          name="correo"
                          type="email"
                          required
                          className="w-full rounded-xl border border-line bg-surface px-4 py-3.5 text-sm text-ink placeholder:text-ink-soft/60 transition-all duration-200 focus:border-gold-deep focus:outline-none focus:ring-4 focus:ring-gold/10"
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
                          className="w-full rounded-xl border border-line bg-surface px-4 py-3.5 text-sm text-ink placeholder:text-ink-soft/60 transition-all duration-200 focus:border-gold-deep focus:outline-none focus:ring-4 focus:ring-gold/10"
                          placeholder="300 000 0000"
                        />
                      </div>
                    </motion.div>

                    <motion.div variants={staggerItem} className="space-y-2">
                      <label htmlFor="mensaje" className="text-sm font-medium text-ink">
                        Cuéntanos tu caso
                      </label>
                      <textarea
                        id="mensaje"
                        name="mensaje"
                        required
                        rows={5}
                        className="w-full resize-none rounded-xl border border-line bg-surface px-4 py-3.5 text-sm text-ink placeholder:text-ink-soft/60 transition-all duration-200 focus:border-gold-deep focus:outline-none focus:ring-4 focus:ring-gold/10"
                        placeholder="¿En qué podemos ayudarte?"
                      />
                    </motion.div>

                    <motion.div variants={staggerItem} className="space-y-3 pt-1">
                      <CasillaConsentimiento
                        checked={aceptaDatos}
                        onChange={(v) => {
                          setAceptaDatos(v);
                          setErrorDatos(false);
                        }}
                      >
                        Autorizo el tratamiento de mis datos personales conforme
                        a la política de privacidad de SIE Jurídicos (Ley 1581
                        de 2012).
                      </CasillaConsentimiento>
                      {errorDatos && (
                        <motion.p
                          initial={{ opacity: 0, y: -4 }}
                          animate={{ opacity: 1, y: 0 }}
                          className="text-xs text-red-700"
                        >
                          Debes aceptar el tratamiento de datos para continuar.
                        </motion.p>
                      )}

                      <CasillaConsentimiento checked={aceptaMarketing} onChange={setAceptaMarketing}>
                        Sí, quiero recibir novedades y contenido jurídico por
                        correo electrónico.
                      </CasillaConsentimiento>
                    </motion.div>

                    <motion.div variants={staggerItem}>
                      <MagneticButton strength={0.25} className="w-full sm:w-auto">
                        <button
                          type="submit"
                          disabled={estado === "enviando"}
                          className="cta-boton flex w-full items-center justify-center gap-2 rounded-lg bg-gold py-4 text-sm font-medium text-ink-fixed disabled:opacity-60 sm:w-auto sm:px-10"
                        >
                          {estado === "enviando" ? (
                            <>
                              <motion.span
                                animate={{ rotate: 360 }}
                                transition={{ duration: 0.85, repeat: Infinity, ease: [0.65, 0, 0.35, 1] }}
                                className="h-4 w-4 rounded-full border-2 border-ink-fixed/25 border-t-ink-fixed"
                              />
                              Enviando
                            </>
                          ) : (
                            <>
                              {siteConfig.ctaPrincipal}
                              <ArrowRight weight="bold" className="h-4 w-4" />
                            </>
                          )}
                        </button>
                      </MagneticButton>
                    </motion.div>
                  </motion.form>
                )}
              </AnimatePresence>
            </div>
          </motion.div>
        </div>
      </div>
    </section>
  );
}
