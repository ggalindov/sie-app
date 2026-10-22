"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Dialog } from "@base-ui/react/dialog";
import { toast } from "sonner";
import { FileText, ShieldCheck, WarningCircle, X } from "@phosphor-icons/react";
import {
  listarDocumentosFirma,
  type DocumentoFirmaListado,
  type EstadoDocumentoFirma,
} from "@/lib/admin-api";
import { Badge } from "@/components/admin/ui";

// Mismo diccionario de estados que /admin/documentos (ver etiquetaEstado/tonoEstado ahí) --
// se duplica a propósito en vez de importarlo: es una pantalla distinta (resumen dentro de
// un modal, no la lista completa) y el proyecto ya repite este tipo de helper pequeño por
// archivo (ver formatearFecha en casos/page.tsx y tareas-de-caso-modal.tsx) en vez de forzar
// una abstracción compartida para dos usos.
const etiquetaEstado: Record<EstadoDocumentoFirma, string> = {
  DILIGENCIADO: "Diligenciado",
  EMITIDO: "Emitido (pendiente firma staff)",
  FIRMADO_STAFF: "Firmado por staff",
  NOTIFICADO: "Notificado a las partes",
  EN_FIRMA_PARTES: "En firma de partes",
  SELLADO: "Sellado",
  REMITIDO: "Remitido",
  ENCRIPTADO: "Completado",
  ANULADO: "Anulado",
  CANCELADO: "Cancelado",
};

function tonoEstado(estado: EstadoDocumentoFirma): "neutral" | "gold" | "success" | "warning" | "danger" {
  if (estado === "ANULADO" || estado === "CANCELADO") return "danger";
  if (estado === "ENCRIPTADO" || estado === "REMITIDO" || estado === "SELLADO") return "success";
  if (estado === "EN_FIRMA_PARTES" || estado === "NOTIFICADO") return "gold";
  if (estado === "EMITIDO") return "warning";
  return "neutral";
}

function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    timeZone: "America/Bogota",
  });
}

// Solo lo que este modal necesita de un caso -- mismo criterio que CasoParaTareas en
// tareas-de-caso-modal.tsx.
export type CasoParaDocumentos = {
  id: number;
  nombreCliente: string;
  radicadoId: string | null;
};

// Pedido explícito del usuario: "crea en el area de casos un apartado por caso para ver los
// documentos anexados por el motor de firma electronica y visualizar directamente el estado
// de ellos" -- antes, para saber si un caso tenía algún documento en el motor de firma (y en
// qué estado iba), había que ir a /admin/documentos y buscarlo a mano entre todos. Se abre
// desde la tarjeta del caso en /admin/casos, mismo patrón que TareasDeCasoModal.
//
// DocumentoFirmaListado ya trae casoId (ver DocumentoFirmaListadoDto en el backend) -- no
// hace falta ningún endpoint nuevo, solo filtrar en el cliente la misma lista que ya usa
// /admin/documentos.
export function DocumentosDeCasoModal({
  caso,
  onClose,
}: {
  caso: CasoParaDocumentos | null;
  onClose: () => void;
}) {
  const [documentos, setDocumentos] = useState<DocumentoFirmaListado[] | null>(null);

  const cargar = useCallback(() => {
    if (!caso) return;
    listarDocumentosFirma()
      .then((todos) => setDocumentos(todos.filter((d) => d.casoId === caso.id)))
      .catch(() => toast.error("No se pudieron cargar los documentos de este caso."));
  }, [caso]);

  useEffect(() => {
    setDocumentos(null);
    cargar();
  }, [cargar]);

  return (
    <Dialog.Root open={caso !== null} onOpenChange={(open) => !open && onClose()}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-ink/40 backdrop-blur-sm" />
        <Dialog.Popup className="fixed left-1/2 top-1/2 z-50 max-h-[85vh] w-full max-w-lg -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-2xl bg-surface p-6 shadow-2xl ring-1 ring-line">
          <div className="flex items-center justify-between">
            <div>
              <Dialog.Title className="font-display text-lg text-ink">Documentos del caso</Dialog.Title>
              <Dialog.Description className="mt-1 text-sm text-ink-soft">
                {caso?.nombreCliente}
                {caso?.radicadoId ? ` · ${caso.radicadoId}` : ""}
              </Dialog.Description>
            </div>
            <Dialog.Close className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5">
              <X className="h-4 w-4" />
            </Dialog.Close>
          </div>

          {documentos === null ? (
            <p className="mt-6 text-center text-sm text-ink-soft">Cargando...</p>
          ) : documentos.length === 0 ? (
            <div className="mt-6 text-center">
              <p className="text-sm text-ink-soft">Este caso todavía no tiene ningún documento del motor de firma.</p>
              <Link
                href="/admin/documentos/nuevo"
                className="mt-3 inline-block text-xs font-medium text-gold-deep hover:underline"
              >
                Emitir un documento nuevo
              </Link>
            </div>
          ) : (
            <div className="mt-5 space-y-2.5">
              {documentos.map((d) => (
                <Link
                  key={d.id}
                  href={`/admin/documentos/${d.id}`}
                  className="block rounded-xl border border-line bg-paper p-3.5 transition-colors hover:border-gold-deep/40 hover:bg-ink/[0.02]"
                >
                  <div className="flex min-w-0 items-start gap-3">
                    <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gold-pale/50 text-gold-deep">
                      <FileText className="h-3.5 w-3.5" weight="light" />
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-1.5">
                        <p className="truncate text-sm font-medium text-ink">{d.nombre}</p>
                        <Badge tone={tonoEstado(d.estadoDocumento)}>{etiquetaEstado[d.estadoDocumento]}</Badge>
                        {d.estadoIntegridad === "ALTERADO" && (
                          <Badge tone="danger">
                            <WarningCircle className="h-3 w-3" weight="bold" /> Alterado
                          </Badge>
                        )}
                        {d.estadoIntegridad === "VIGENTE" && d.estadoDocumento !== "DILIGENCIADO" && (
                          <span className="inline-flex items-center gap-1 text-[11px] text-emerald-700">
                            <ShieldCheck className="h-3.5 w-3.5" weight="bold" /> Íntegro
                          </span>
                        )}
                      </div>
                      <p className="mt-1 text-xs text-ink-soft">
                        {d.numeroSecuencia ? `Nº ${d.numeroSecuencia} · ` : ""}
                        Subido por {d.creadoPorNombre ?? "—"} · {formatearFecha(d.fechaCreacion)}
                      </p>
                    </div>
                  </div>
                </Link>
              ))}
            </div>
          )}
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
