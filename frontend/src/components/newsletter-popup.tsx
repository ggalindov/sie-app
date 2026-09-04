"use client";

import { useEffect, useState, type FormEvent } from "react";
import { Dialog } from "@base-ui/react/dialog";
import { motion } from "motion/react";
import { toast } from "sonner";
import { Gift, PaperPlaneTilt, X } from "@phosphor-icons/react";
import { suscribirNewsletter, ApiError } from "@/lib/api";
import { CampoTrampa } from "@/components/campo-trampa";

const EASE = [0.16, 1, 0.3, 1] as const;

const CLAVE_LOCALSTORAGE = "sie-newsletter-popup-visto";
const RETRASO_MS = 2500;

// Se muestra una sola vez por navegador (localStorage), con un pequeño retraso para no
// interrumpir la carga inicial. El descuento se ofrece deliberadamente sin porcentaje: los
// detalles los recibe el suscriptor por correo (ver EmailService.enviarBienvenidaBoletin),
// no es un cupón automatizado.
export function NewsletterPopup() {
  const [abierto, setAbierto] = useState(false);
  const [estado, setEstado] = useState<"idle" | "enviando" | "enviado">("idle");

  useEffect(() => {
    if (typeof window === "undefined") return;
    if (window.localStorage.getItem(CLAVE_LOCALSTORAGE)) return;

    const temporizador = setTimeout(() => setAbierto(true), RETRASO_MS);
    return () => clearTimeout(temporizador);
  }, []);

  function cerrarYRecordar() {
    setAbierto(false);
    window.localStorage.setItem(CLAVE_LOCALSTORAGE, "1");
  }

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const data = new FormData(form);

    setEstado("enviando");
    try {
      await suscribirNewsletter({
        nombre: String(data.get("nombre") ?? ""),
        correo: String(data.get("correo") ?? ""),
        sitioWeb: String(data.get("sitioWeb") ?? ""),
      });
      setEstado("enviado");
      toast.success("Listo, revisa tu correo para conocer el detalle de tu descuento.");
      window.localStorage.setItem(CLAVE_LOCALSTORAGE, "1");
    } catch (err) {
      setEstado("idle");
      toast.error(
        err instanceof ApiError ? err.message : "No pudimos completar la suscripción. Intenta de nuevo.",
      );
    }
  }

  return (
    <Dialog.Root open={abierto} onOpenChange={(open) => !open && cerrarYRecordar()}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-ink/50 backdrop-blur-sm transition-opacity duration-300 data-[ending-style]:opacity-0 data-[starting-style]:opacity-0" />
        <Dialog.Popup className="fixed left-1/2 top-1/2 z-50 w-[92vw] max-w-md -translate-x-1/2 -translate-y-1/2 overflow-hidden rounded-[2rem] bg-surface p-8 shadow-2xl ring-1 ring-line transition-all duration-300 ease-[cubic-bezier(0.16,1,0.3,1)] data-[ending-style]:scale-95 data-[ending-style]:opacity-0 data-[starting-style]:scale-90 data-[starting-style]:opacity-0">
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-x-0 top-0 h-[3px]"
            style={{ backgroundImage: "linear-gradient(90deg, var(--color-gold-deep), var(--color-gold) 55%, var(--color-gold-pale))" }}
          />
          <Dialog.Close
            onClick={cerrarYRecordar}
            className="absolute right-5 top-5 flex h-9 w-9 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5"
            aria-label="Cerrar"
          >
            <X className="h-4 w-4" />
          </Dialog.Close>

          <span className="relative flex h-12 w-12 items-center justify-center rounded-2xl bg-gold/10 text-gold">
            <span aria-hidden="true" className="absolute inset-0 animate-pulse rounded-2xl bg-gold/10 blur-md" />
            <Gift weight="light" className="relative h-6 w-6" />
          </span>

          <Dialog.Title className="mt-5 font-display text-2xl leading-tight text-ink">
            Suscríbete y recibe un descuento especial
          </Dialog.Title>
          <Dialog.Description className="mt-2 text-sm leading-relaxed text-ink-soft">
            Únete a nuestro boletín jurídico y obtén acceso a un descuento especial en tu
            primera consulta con nosotros. Te enviaremos los detalles directo a tu correo.
          </Dialog.Description>

          {estado === "enviado" ? (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="mt-6 flex flex-col items-center py-4 text-center"
            >
              <motion.span
                initial={{ scale: 0.4, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                transition={{ duration: 0.5, ease: EASE }}
                className="flex h-14 w-14 items-center justify-center rounded-full bg-gold-pale/60 ring-1 ring-gold/30"
              >
                <svg viewBox="0 0 24 24" className="h-6 w-6 text-gold-deep" fill="none">
                  <motion.path
                    d="M4 12.5L9.5 18L20 6"
                    stroke="currentColor"
                    strokeWidth="2.4"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    initial={{ pathLength: 0 }}
                    animate={{ pathLength: 1 }}
                    transition={{ duration: 0.5, delay: 0.25, ease: EASE }}
                  />
                </svg>
              </motion.span>
              <p className="mt-4 text-sm font-medium text-ink">Revisa tu bandeja de entrada.</p>
            </motion.div>
          ) : (
            <form onSubmit={onSubmit} className="mt-6 space-y-3">
              <CampoTrampa />
              <input
                name="nombre"
                required
                placeholder="Tu nombre"
                className="w-full rounded-full border border-line bg-paper px-5 py-3 text-sm text-ink placeholder:text-ink-soft/60 transition-all duration-200 focus:border-gold-deep focus:outline-none focus:ring-4 focus:ring-gold/10"
              />
              <input
                name="correo"
                type="email"
                required
                placeholder="tucorreo@ejemplo.com"
                className="w-full rounded-full border border-line bg-paper px-5 py-3 text-sm text-ink placeholder:text-ink-soft/60 transition-all duration-200 focus:border-gold-deep focus:outline-none focus:ring-4 focus:ring-gold/10"
              />
              <button
                type="submit"
                disabled={estado === "enviando"}
                className="cta-boton flex w-full items-center justify-center gap-2 rounded-lg bg-gold px-6 py-3 text-sm font-medium text-ink-fixed active:scale-[0.98] disabled:opacity-60"
              >
                {estado === "enviando" ? (
                  <motion.span
                    animate={{ rotate: 360 }}
                    transition={{ duration: 0.85, repeat: Infinity, ease: [0.65, 0, 0.35, 1] }}
                    className="h-4 w-4 rounded-full border-2 border-ink-fixed/25 border-t-ink-fixed"
                  />
                ) : (
                  <PaperPlaneTilt className="h-4 w-4" weight="bold" />
                )}
                Suscribirme
              </button>
              <button
                type="button"
                onClick={cerrarYRecordar}
                className="w-full text-center text-xs text-ink-soft hover:text-ink"
              >
                Ahora no
              </button>
            </form>
          )}
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
