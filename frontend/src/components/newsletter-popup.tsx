"use client";

import { useEffect, useState, type FormEvent } from "react";
import { Dialog } from "@base-ui/react/dialog";
import { toast } from "sonner";
import { EnvelopeSimple, Gift, PaperPlaneTilt, Spinner, X } from "@phosphor-icons/react";
import { suscribirNewsletter, ApiError } from "@/lib/api";

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
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-ink/50 backdrop-blur-sm" />
        <Dialog.Popup className="fixed left-1/2 top-1/2 z-50 w-[92vw] max-w-md -translate-x-1/2 -translate-y-1/2 overflow-hidden rounded-[2rem] bg-surface p-8 shadow-2xl ring-1 ring-line">
          <Dialog.Close
            onClick={cerrarYRecordar}
            className="absolute right-5 top-5 flex h-9 w-9 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5"
            aria-label="Cerrar"
          >
            <X className="h-4 w-4" />
          </Dialog.Close>

          <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-gold/10 text-gold">
            <Gift weight="light" className="h-6 w-6" />
          </span>

          <Dialog.Title className="mt-5 font-display text-2xl leading-tight text-ink">
            Suscríbete y recibe un descuento especial
          </Dialog.Title>
          <Dialog.Description className="mt-2 text-sm leading-relaxed text-ink-soft">
            Únete a nuestro boletín jurídico y obtén acceso a un descuento especial en tu
            primera consulta con nosotros. Te enviaremos los detalles directo a tu correo.
          </Dialog.Description>

          {estado === "enviado" ? (
            <p className="mt-6 flex items-center gap-2 text-sm font-medium text-ink">
              <EnvelopeSimple weight="fill" className="h-4 w-4 text-gold-deep" />
              Revisa tu bandeja de entrada.
            </p>
          ) : (
            <form onSubmit={onSubmit} className="mt-6 space-y-3">
              <input
                name="nombre"
                required
                placeholder="Tu nombre"
                className="w-full rounded-full border border-line bg-paper px-5 py-3 text-sm text-ink placeholder:text-ink-soft/60 focus:border-gold-deep focus:outline-none"
              />
              <input
                name="correo"
                type="email"
                required
                placeholder="tucorreo@ejemplo.com"
                className="w-full rounded-full border border-line bg-paper px-5 py-3 text-sm text-ink placeholder:text-ink-soft/60 focus:border-gold-deep focus:outline-none"
              />
              <button
                type="submit"
                disabled={estado === "enviando"}
                className="cta-boton flex w-full items-center justify-center gap-2 rounded-lg bg-gold px-6 py-3 text-sm font-medium text-ink-fixed active:scale-[0.98] disabled:opacity-60"
              >
                {estado === "enviando" ? (
                  <Spinner className="h-4 w-4 animate-spin" weight="bold" />
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
