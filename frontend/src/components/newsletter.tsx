"use client";

import { useState, type FormEvent } from "react";
import { motion } from "motion/react";
import { toast } from "sonner";
import { EnvelopeSimple, PaperPlaneTilt, Spinner } from "@phosphor-icons/react";
import { suscribirNewsletter, ApiError } from "@/lib/api";

const EASE = [0.16, 1, 0.3, 1] as const;

type Estado = "idle" | "enviando" | "enviado";

export function Newsletter() {
  const [estado, setEstado] = useState<Estado>("idle");

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const data = new FormData(form);

    setEstado("enviando");
    const toastId = toast.loading("Suscribiendo...");
    try {
      await suscribirNewsletter({
        nombre: String(data.get("nombre") ?? ""),
        correo: String(data.get("correo") ?? ""),
      });
      setEstado("enviado");
      form.reset();
      toast.success("Listo, quedaste suscrito al boletín.", { id: toastId });
    } catch (err) {
      setEstado("idle");
      const mensaje =
        err instanceof ApiError
          ? err.message
          : "No pudimos completar la suscripción. Intenta de nuevo.";
      toast.error(mensaje, { id: toastId });
    }
  }

  return (
    <section className="py-16 md:py-20">
      <div className="mx-auto max-w-7xl px-6">
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, amount: 0.4 }}
          transition={{ duration: 0.7, ease: EASE }}
          className="gradient-animate relative overflow-hidden rounded-[2rem] px-8 py-12 md:px-14 md:py-14"
          style={{
            backgroundImage:
              "linear-gradient(150deg, #0a0906 0%, #1c1811 55%, #0a0906 100%)",
          }}
        >
          <div className="relative grid gap-8 md:grid-cols-2 md:items-center md:gap-16">
            <div>
              <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-gold/10 text-gold">
                <EnvelopeSimple weight="light" className="h-5 w-5" />
              </span>
              <h2 className="mt-5 font-display text-2xl leading-tight text-night-ink md:text-3xl">
                Suscríbete a nuestro boletín jurídico.
              </h2>
              <p className="mt-3 max-w-sm text-sm leading-relaxed text-night-ink/60">
                Te avisamos por correo cada vez que publicamos contenido
                nuevo en el blog. Sin spam, puedes darte de baja cuando
                quieras.
              </p>
            </div>

            {estado === "enviado" ? (
              <p className="font-display text-xl text-night-ink">
                Gracias, ya quedaste suscrito al boletín.
              </p>
            ) : (
              <form onSubmit={onSubmit} className="flex flex-col gap-3 sm:flex-row sm:items-start">
                <input
                  name="nombre"
                  required
                  placeholder="Tu nombre"
                  className="w-full rounded-full border border-night-ink/15 bg-night-ink/5 px-5 py-3.5 text-sm text-night-ink placeholder:text-night-ink/40 focus:border-gold focus:outline-none sm:w-40"
                />
                <input
                  name="correo"
                  type="email"
                  required
                  placeholder="tucorreo@ejemplo.com"
                  className="w-full rounded-full border border-night-ink/15 bg-night-ink/5 px-5 py-3.5 text-sm text-night-ink placeholder:text-night-ink/40 focus:border-gold focus:outline-none sm:flex-1"
                />
                <button
                  type="submit"
                  disabled={estado === "enviando"}
                  className="flex shrink-0 items-center justify-center gap-2 rounded-full bg-gold px-6 py-3.5 text-sm font-medium text-ink-fixed transition-transform duration-300 active:scale-[0.98] disabled:opacity-60"
                >
                  {estado === "enviando" ? (
                    <Spinner className="h-4 w-4 animate-spin" weight="bold" />
                  ) : (
                    <PaperPlaneTilt className="h-4 w-4" weight="bold" />
                  )}
                  Suscribirme
                </button>
              </form>
            )}
          </div>
        </motion.div>
      </div>
    </section>
  );
}
