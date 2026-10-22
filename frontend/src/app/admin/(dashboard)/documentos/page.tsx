"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Plus, FileText, ShieldCheck, WarningCircle } from "@phosphor-icons/react";
import {
  listarDocumentosFirma,
  type DocumentoFirmaListado,
  type EstadoDocumentoFirma,
} from "@/lib/admin-api";
import { AdminPageHeader, AdminButton, AdminCard, Badge, EmptyState, AdminLoader } from "@/components/admin/ui";
import { useAuth } from "@/lib/auth-context";

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
};

function tonoEstado(estado: EstadoDocumentoFirma): "neutral" | "gold" | "success" | "warning" | "danger" {
  if (estado === "ANULADO") return "danger";
  if (estado === "ENCRIPTADO" || estado === "REMITIDO" || estado === "SELLADO") return "success";
  if (estado === "EN_FIRMA_PARTES" || estado === "NOTIFICADO") return "gold";
  if (estado === "EMITIDO") return "warning";
  return "neutral";
}

const filtros: { label: string; estado?: EstadoDocumentoFirma }[] = [
  { label: "Todos" },
  { label: "Pendientes de firma staff", estado: "EMITIDO" },
  { label: "En firma de partes", estado: "EN_FIRMA_PARTES" },
  { label: "Completados", estado: "ENCRIPTADO" },
  { label: "Anulados", estado: "ANULADO" },
];

export default function DocumentosPage() {
  const { sesion } = useAuth();
  const [documentos, setDocumentos] = useState<DocumentoFirmaListado[] | null>(null);
  const [filtroActivo, setFiltroActivo] = useState(0);

  useEffect(() => {
    cargar(filtros[filtroActivo].estado);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filtroActivo]);

  function cargar(estado?: EstadoDocumentoFirma) {
    setDocumentos(null);
    listarDocumentosFirma(estado)
      .then(setDocumentos)
      .catch(() => toast.error("No se pudieron cargar los documentos."));
  }

  return (
    <div>
      <AdminPageHeader
        title="Documentos para firma"
        description="Sube un documento, asigna abogados y partes, y el motor de firma electrónica se encarga del resto."
        action={
          sesion?.rol === "ADMIN_GENERAL" ? (
            <Link href="/admin/documentos/nuevo">
              <AdminButton>
                <Plus className="h-4 w-4" weight="bold" />
                Nuevo documento
              </AdminButton>
            </Link>
          ) : undefined
        }
      />

      <div className="mb-5 flex flex-wrap gap-2">
        {filtros.map((f, i) => (
          <button
            key={f.label}
            type="button"
            onClick={() => setFiltroActivo(i)}
            className={`rounded-full px-3.5 py-1.5 text-xs font-medium transition-colors ${
              i === filtroActivo ? "bg-ink text-paper" : "bg-ink/5 text-ink-soft hover:bg-ink/10"
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {documentos === null ? (
        <AdminLoader />
      ) : documentos.length === 0 ? (
        <EmptyState
          title="No hay documentos en este filtro"
          description={sesion?.rol === "ADMIN_GENERAL" ? "Crea el primero desde el botón de arriba." : undefined}
        />
      ) : (
        <div className="space-y-3">
          {documentos.map((d) => (
            <Link key={d.id} href={`/admin/documentos/${d.id}`}>
              <AdminCard className="flex flex-col gap-3 transition-shadow hover:shadow-sm sm:flex-row sm:items-center sm:justify-between">
                <div className="flex min-w-0 items-start gap-3">
                  <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gold-pale/50 text-gold-deep">
                    <FileText className="h-4 w-4" weight="light" />
                  </span>
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-medium text-ink">{d.nombre}</p>
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
              </AdminCard>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
