"use client";

import { useState, type FormEvent } from "react";
import { motion } from "motion/react";
import { toast } from "sonner";
import {
  EnvelopeSimple,
  FacebookLogo,
  InstagramLogo,
  LinkedinLogo,
  MapPin,
  Phone,
  ShieldCheck,
  WhatsappLogo,
  Spinner,
} from "@phosphor-icons/react";
import { crearSolicitud, ApiError } from "@/lib/api";
import { siteConfig } from "@/lib/site-config";

const EASE = [0.16, 1, 0.3, 1] as const;

type Estado = "idle" | "enviando" | "enviado";

export function Contacto() {
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
    } catch (err) {
      setEstado("idle");
      const mensaje =
        err instanceof ApiError
          ? err.message
          : "No pudimos enviar tu mensaje. Intenta de nuevo en unos minutos.";
      toast.error(mensaje);
    }
  }

  return (
    <section id="contacto" className="section-seam bg-surface/90 py-24 md:py-32">
      <div className="mx-auto max-w-7xl px-6">
        <div className="grid gap-14 md:grid-cols-2 md:gap-16">
          <motion.div
            initial={{ opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.3 }}
            transition={{ duration: 0.7, ease: EASE }}
          >
            <h2 className="font-display text-3xl leading-tight tracking-tight md:text-4xl">
              Cuando necesitas apoyo legal, aquí estamos.
            </h2>
            <p className="mt-4 max-w-md text-base leading-relaxed text-ink-soft">
              Cuéntanos tu caso y un abogado de SIE Jurídicos te contactará
              para agendar tu asesoría.
            </p>

            <div className="mt-10 space-y-5">
              <a
                href={siteConfig.telefonoHref}
                className="flex items-center gap-4 text-sm text-ink transition-colors hover:text-gold-deep"
              >
                <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-ink/5">
                  <Phone weight="light" className="h-5 w-5" />
                </span>
                {siteConfig.telefono}
              </a>
              <a
                href={`mailto:${siteConfig.correo}`}
                className="flex items-center gap-4 text-sm text-ink transition-colors hover:text-gold-deep"
              >
                <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-ink/5">
                  <EnvelopeSimple weight="light" className="h-5 w-5" />
                </span>
                {siteConfig.correo}
              </a>
              <div className="flex items-center gap-4 text-sm text-ink">
                <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-ink/5">
                  <MapPin weight="light" className="h-5 w-5" />
                </span>
                {siteConfig.ciudad}
              </div>
              <a
                href={siteConfig.whatsapp}
                target="_blank"
                rel="noopener noreferrer"
                className="group inline-flex items-center gap-3 rounded-full bg-ink py-3 pl-6 pr-3 text-sm font-medium text-paper transition-transform duration-300 active:scale-[0.98]"
              >
                Escríbenos por WhatsApp
                <span className="flex h-8 w-8 items-center justify-center rounded-full bg-paper/15 transition-transform duration-300 group-hover:translate-x-0.5">
                  <WhatsappLogo weight="fill" className="h-4 w-4" />
                </span>
              </a>
            </div>

            <div className="mt-8 flex items-center gap-3">
              <a
                href={siteConfig.redes.facebook}
                target="_blank"
                rel="noopener noreferrer"
                aria-label="Facebook"
                className="flex h-10 w-10 items-center justify-center rounded-full bg-ink/5 text-ink-soft transition-colors hover:bg-gold hover:text-ink-fixed"
              >
                <FacebookLogo weight="light" className="h-5 w-5" />
              </a>
              <a
                href={siteConfig.redes.instagram}
                target="_blank"
                rel="noopener noreferrer"
                aria-label="Instagram"
                className="flex h-10 w-10 items-center justify-center rounded-full bg-ink/5 text-ink-soft transition-colors hover:bg-gold hover:text-ink-fixed"
              >
                <InstagramLogo weight="light" className="h-5 w-5" />
              </a>
              <a
                href={siteConfig.redes.linkedin}
                target="_blank"
                rel="noopener noreferrer"
                aria-label="LinkedIn"
                className="flex h-10 w-10 items-center justify-center rounded-full bg-ink/5 text-ink-soft transition-colors hover:bg-gold hover:text-ink-fixed"
              >
                <LinkedinLogo weight="light" className="h-5 w-5" />
              </a>
            </div>

            <div className="mt-8 flex items-start gap-3 rounded-2xl bg-ink/5 p-5">
              <ShieldCheck weight="light" className="h-6 w-6 shrink-0 text-gold-deep" />
              <p className="text-xs leading-relaxed text-ink-soft">
                Protegemos tus datos personales conforme a la Ley 1581 de 2012 (Habeas
                Data). La información que nos compartas solo se usa para dar respuesta a
                tu solicitud y nunca se cede a terceros sin tu autorización.
              </p>
            </div>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.3 }}
            transition={{ duration: 0.7, delay: 0.1, ease: EASE }}
            className="rounded-[2rem] bg-ink/5 p-2 ring-1 ring-ink/5"
          >
            <div className="rounded-[1.6rem] bg-paper p-7 ring-1 ring-line md:p-9">
              {estado === "enviado" ? (
                <div className="flex min-h-[320px] flex-col items-center justify-center text-center">
                  <p className="font-display text-2xl">Mensaje enviado</p>
                  <p className="mt-3 max-w-xs text-sm leading-relaxed text-ink-soft">
                    Gracias por escribirnos. Un abogado de nuestro equipo te
                    contactará pronto.
                  </p>
                  <button
                    type="button"
                    onClick={() => setEstado("idle")}
                    className="mt-6 text-sm font-medium text-gold-deep underline underline-offset-4"
                  >
                    Enviar otro mensaje
                  </button>
                </div>
              ) : (
                <form onSubmit={onSubmit} className="space-y-5">
                  <div className="space-y-2">
                    <label htmlFor="nombre" className="text-sm font-medium text-ink">
                      Nombre completo
                    </label>
                    <input
                      id="nombre"
                      name="nombre"
                      required
                      className="w-full rounded-xl border border-line bg-surface px-4 py-3 text-sm text-ink placeholder:text-ink-soft/60 focus:border-gold-deep focus:outline-none"
                      placeholder="Tu nombre"
                    />
                  </div>

                  <div className="grid gap-5 sm:grid-cols-2">
                    <div className="space-y-2">
                      <label htmlFor="correo" className="text-sm font-medium text-ink">
                        Correo
                      </label>
                      <input
                        id="correo"
                        name="correo"
                        type="email"
                        required
                        className="w-full rounded-xl border border-line bg-surface px-4 py-3 text-sm text-ink placeholder:text-ink-soft/60 focus:border-gold-deep focus:outline-none"
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
                        className="w-full rounded-xl border border-line bg-surface px-4 py-3 text-sm text-ink placeholder:text-ink-soft/60 focus:border-gold-deep focus:outline-none"
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
                      rows={4}
                      className="w-full resize-none rounded-xl border border-line bg-surface px-4 py-3 text-sm text-ink placeholder:text-ink-soft/60 focus:border-gold-deep focus:outline-none"
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
                    className="flex w-full items-center justify-center gap-2 rounded-full bg-gold py-3.5 text-sm font-medium text-ink-fixed transition-opacity duration-300 disabled:opacity-60"
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
