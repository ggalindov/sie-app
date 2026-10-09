"use client";

import { useEffect, useState } from "react";
import { Dialog } from "@base-ui/react/dialog";
import { toast } from "sonner";
import { X, EnvelopeSimple, PaperPlaneTilt } from "@phosphor-icons/react";
import { responderSolicitud, ApiError, type Solicitud } from "@/lib/admin-api";
import { AdminButton } from "@/components/admin/ui";

const ASUNTO_MAXIMO = 150;
const MENSAJE_MAXIMO = 5000;

export function ResponderSolicitudModal({
  solicitud,
  onClose,
  onEnviada,
}: {
  solicitud: Solicitud | null;
  onClose: () => void;
  onEnviada?: () => void;
}) {
  const [asunto, setAsunto] = useState("");
  const [mensaje, setMensaje] = useState("");
  const [enviando, setEnviando] = useState(false);

  // Precarga un asunto razonable cada vez que se abre con una solicitud distinta -- el
  // abogado puede editarlo libremente, esto solo evita arrancar con el campo vacío.
  useEffect(() => {
    if (!solicitud) return;
    setAsunto("Respuesta a tu solicitud - SIE Jurídicos");
    setMensaje("");
  }, [solicitud]);

  const puedeEnviar = !!solicitud && asunto.trim().length > 0 && mensaje.trim().length > 0 && !enviando;

  async function onSubmit() {
    if (!solicitud || !puedeEnviar) return;
    setEnviando(true);
    try {
      await responderSolicitud(solicitud.id, asunto.trim(), mensaje.trim());
      toast.success("Respuesta enviada. Gerencia quedó copiada.");
      onEnviada?.();
      onClose();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo enviar la respuesta.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Dialog.Root open={!!solicitud} onOpenChange={(open) => !open && onClose()}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-ink/40 backdrop-blur-sm" />
        <Dialog.Popup className="fixed left-1/2 top-1/2 z-50 max-h-[90vh] w-full max-w-lg -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-2xl bg-surface p-6 shadow-2xl ring-1 ring-line">
          <div className="flex items-center justify-between">
            <Dialog.Title className="flex items-center gap-2 font-display text-lg text-ink">
              <EnvelopeSimple className="h-5 w-5 text-gold-deep" weight="duotone" />
              Responder solicitud
            </Dialog.Title>
            <Dialog.Close className="flex h-8 w-8 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5">
              <X className="h-4 w-4" />
            </Dialog.Close>
          </div>
          <Dialog.Description className="mt-1 text-sm text-ink-soft">
            Para {solicitud?.nombre} &middot; {solicitud?.correo}
          </Dialog.Description>

          <div className="mt-5 space-y-4">
            <div className="space-y-2">
              <label className="text-sm font-medium text-ink">Asunto</label>
              <input
                type="text"
                value={asunto}
                onChange={(e) => setAsunto(e.target.value.slice(0, ASUNTO_MAXIMO))}
                placeholder="Asunto del correo"
                className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium text-ink">Mensaje</label>
              <textarea
                value={mensaje}
                onChange={(e) => setMensaje(e.target.value.slice(0, MENSAJE_MAXIMO))}
                placeholder="Escribe tu respuesta. Se enviará con el saludo y la firma de la firma ya incluidos."
                rows={8}
                className="w-full resize-none rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
              />
              <p className="text-right text-xs text-ink-soft">
                {mensaje.length}/{MENSAJE_MAXIMO}
              </p>
            </div>

            <div className="rounded-xl border border-line bg-paper px-4 py-3 text-xs text-ink-soft">
              El correo sale con el formato de marca de SIE Jurídicos (encabezado, logo y pie),
              empieza con &quot;Hola {solicitud?.nombre ?? "..."},&quot; y queda siempre con copia a{" "}
              <strong className="text-ink">gerencia@siejuridicos.com</strong>.
            </div>
          </div>

          <AdminButton onClick={onSubmit} disabled={!puedeEnviar} className="mt-6 w-full">
            <PaperPlaneTilt className="h-4 w-4" weight="fill" />
            {enviando ? "Enviando..." : "Enviar respuesta"}
          </AdminButton>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
