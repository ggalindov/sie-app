"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { toast } from "sonner";
import { DownloadSimple, ShieldCheck, ShieldWarning, Certificate, Prohibit, Trash } from "@phosphor-icons/react";
import {
  obtenerSeguimientoDocumento,
  descargarDocumentoFirma,
  descargarCertificadoEvidencia,
  listarCambiosSolicitudDocumento,
  crearCambioSolicitudDocumento,
  confirmarCambioSolicitudDocumento,
  rechazarCambioSolicitudDocumento,
  ejecutarCambioSolicitudDocumento,
  cancelarDocumentoFirma,
  eliminarDocumentoFirma,
  ApiError,
  type DocumentoSeguimiento,
  type DocumentoCambioSolicitud,
  type TipoAccionCambioDocumento,
} from "@/lib/admin-api";
import { AdminPageHeader, AdminButton, AdminCard, Badge, AdminLoader } from "@/components/admin/ui";
import { useAuth } from "@/lib/auth-context";

function formatearFecha(iso: string | null) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("es-CO", { timeZone: "America/Bogota", dateStyle: "medium", timeStyle: "short" });
}

const etiquetaTipoFirmante: Record<string, string> = {
  STAFF_GERENTE: "Gerente",
  STAFF_ABOGADO: "Abogado",
  PARTE: "Parte",
};

const etiquetaEstadoFirmante: Record<string, string> = {
  PENDIENTE: "Pendiente",
  NOTIFICADO: "Notificado, esperando que inicie",
  OTP_VERIFICADO: "Verificado, firmando",
  FIRMADO: "Firmado",
  RECHAZADO: "Rechazó firmar",
  EXPIRADO: "Enlace expirado",
  REVOCADO: "Enlace revocado",
};

const tiposAccionCambio: { value: TipoAccionCambioDocumento; label: string }[] = [
  { value: "INVALIDAR", label: "Invalidar documento" },
  { value: "ANULAR_FIRMA_PARTE", label: "Anular firma de una parte" },
  { value: "REVERTIR_ALTERADO", label: "Revertir estado ALTERADO" },
  { value: "CORREGIR_METADATO", label: "Corregir un metadato" },
  { value: "RE_EMITIR", label: "Re-emitir documento" },
];

// Estados en los que ya no tiene sentido cancelar: ya terminó, para bien (ENCRIPTADO) o
// para mal (ya ANULADO/CANCELADO) -- ver DocumentoFirmaService#cancelar.
const ESTADOS_NO_CANCELABLES = ["ENCRIPTADO", "ANULADO", "CANCELADO"];
// Solo se puede eliminar un documento que nunca llegó a ser un documento legal completo
// (ver DocumentoAdminService#eliminar) -- ENCRIPTADO nunca es eliminable por aquí.
const ESTADOS_ELIMINABLES = ["ANULADO", "CANCELADO"];

export default function DocumentoDetallePage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { sesion } = useAuth();
  const [datos, setDatos] = useState<DocumentoSeguimiento | null>(null);
  const [cambios, setCambios] = useState<DocumentoCambioSolicitud[]>([]);
  const [mostrarFormCambio, setMostrarFormCambio] = useState(false);
  const [cancelando, setCancelando] = useState(false);
  const [eliminando, setEliminando] = useState(false);

  useEffect(() => {
    cargar();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  function cargar() {
    obtenerSeguimientoDocumento(id)
      .then(setDatos)
      .catch(() => toast.error("No se pudo cargar el documento."));
    listarCambiosSolicitudDocumento()
      .then((todos) => setCambios(todos.filter((c) => c.documentoId === id)))
      .catch(() => undefined);
  }

  async function accionCambio(fn: () => Promise<DocumentoCambioSolicitud>) {
    try {
      await fn();
      toast.success("Listo.");
      cargar();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo completar la acción.");
    }
  }

  async function onCancelar() {
    if (!datos) return;
    const motivo = window.prompt(
      "¿Por qué se cancela este proceso de firma? (se le avisa a quien lo consulte más adelante)",
    );
    if (!motivo || !motivo.trim()) return;
    if (!window.confirm("¿Cancelar el proceso de firma? Se revocan los enlaces pendientes y el documento queda marcado como CANCELADO.")) {
      return;
    }
    setCancelando(true);
    try {
      await cancelarDocumentoFirma(datos.documentoId, motivo.trim());
      toast.success("Proceso de firma cancelado.");
      cargar();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo cancelar el proceso.");
    } finally {
      setCancelando(false);
    }
  }

  async function onEliminar() {
    if (!datos) return;
    if (
      !window.confirm(
        "¿Eliminar este documento por completo del sistema? Esta acción no se puede deshacer -- se borran el archivo, los firmantes y toda la línea de tiempo.",
      )
    ) {
      return;
    }
    setEliminando(true);
    try {
      await eliminarDocumentoFirma(datos.documentoId);
      toast.success("Documento eliminado.");
      router.push("/admin/documentos");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo eliminar el documento.");
      setEliminando(false);
    }
  }

  if (!datos) return <AdminLoader />;

  const puedeDescargarFinal = ["SELLADO", "REMITIDO", "ENCRIPTADO"].includes(datos.estadoDocumento);
  const esAdminGeneral = sesion?.rol === "ADMIN_GENERAL";
  const puedeCancelar = esAdminGeneral && !ESTADOS_NO_CANCELABLES.includes(datos.estadoDocumento);
  const puedeEliminar = esAdminGeneral && ESTADOS_ELIMINABLES.includes(datos.estadoDocumento);

  return (
    <div>
      <AdminPageHeader
        title={datos.nombre}
        description={`Estado: ${datos.estadoDocumento}`}
        action={
          <div className="flex gap-2">
            <AdminButton
              variant="secondary"
              onClick={() => descargarDocumentoFirma(datos.documentoId, datos.nombre, false).catch(() => toast.error("No se pudo descargar."))}
            >
              <DownloadSimple className="h-4 w-4" weight="bold" />
              {puedeDescargarFinal ? "Descargar documento" : "Descargar borrador"}
            </AdminButton>
            <AdminButton
              variant="ghost"
              onClick={() =>
                descargarCertificadoEvidencia(datos.documentoId, datos.nombre).catch(() => toast.error("No se pudo descargar."))
              }
            >
              <Certificate className="h-4 w-4" weight="bold" />
              Certificado de evidencia
            </AdminButton>
            {puedeCancelar && (
              <AdminButton variant="danger" onClick={onCancelar} disabled={cancelando}>
                <Prohibit className="h-4 w-4" weight="bold" />
                {cancelando ? "Cancelando..." : "Cancelar proceso"}
              </AdminButton>
            )}
            {puedeEliminar && (
              <AdminButton variant="danger" onClick={onEliminar} disabled={eliminando}>
                <Trash className="h-4 w-4" weight="bold" />
                {eliminando ? "Eliminando..." : "Eliminar documento"}
              </AdminButton>
            )}
          </div>
        }
      />

      <div className="mb-6 flex items-center gap-2">
        {datos.cadenaEventosIntegra ? (
          <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-3 py-1.5 text-xs font-medium text-emerald-800 ring-1 ring-emerald-200">
            <ShieldCheck className="h-4 w-4" weight="bold" /> Cadena de eventos íntegra
          </span>
        ) : (
          <span className="inline-flex items-center gap-1.5 rounded-full bg-red-50 px-3 py-1.5 text-xs font-medium text-red-800 ring-1 ring-red-200">
            <ShieldWarning className="h-4 w-4" weight="bold" /> {datos.cadenaEventosDetalle ?? "Cadena de eventos rota"}
          </span>
        )}
        {datos.estadoIntegridad === "ALTERADO" && <Badge tone="danger">Documento ALTERADO</Badge>}
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <AdminCard accent>
          <h2 className="font-display text-lg text-ink">Firmantes</h2>
          <div className="mt-4 space-y-3">
            {datos.firmantes.map((f) => (
              <div key={f.id} className="rounded-lg bg-ink/[0.03] px-4 py-3">
                <div className="flex items-center justify-between gap-2">
                  <p className="text-sm font-medium text-ink">{f.nombreCapturado}</p>
                  <Badge tone={f.estado === "FIRMADO" ? "success" : f.estado === "RECHAZADO" ? "danger" : "neutral"}>
                    {etiquetaEstadoFirmante[f.estado] ?? f.estado}
                  </Badge>
                </div>
                <p className="mt-1 text-xs text-ink-soft">
                  {etiquetaTipoFirmante[f.tipoFirmante] ?? f.tipoFirmante}
                  {f.canalNotificacion ? ` · canal ${f.canalNotificacion === "EMAIL" ? "correo" : "WhatsApp"}` : ""}
                  {f.firmadoEn ? ` · firmado ${formatearFecha(f.firmadoEn)}` : ""}
                </p>
              </div>
            ))}
          </div>
        </AdminCard>

        <AdminCard accent>
          <h2 className="font-display text-lg text-ink">Línea de tiempo</h2>
          <div className="mt-4 max-h-[420px] space-y-3 overflow-y-auto pr-1">
            {datos.eventos.map((e) => (
              <div key={e.secuencia} className="border-l-2 border-gold-pale pl-3">
                <p className="text-xs font-medium uppercase tracking-wide text-gold-deep">{e.tipoEvento}</p>
                <p className="text-sm text-ink">{e.descripcion}</p>
                <p className="mt-0.5 text-xs text-ink-soft">
                  {e.actorNombre ? `${e.actorNombre} · ` : ""}
                  {formatearFecha(e.ocurridoEn)}
                </p>
              </div>
            ))}
          </div>
        </AdminCard>
      </div>

      <AdminCard accent className="mt-6">
        <div className="flex items-center justify-between">
          <h2 className="font-display text-lg text-ink">Solicitudes de cambio</h2>
          <AdminButton variant="secondary" onClick={() => setMostrarFormCambio((v) => !v)}>
            Solicitar un cambio
          </AdminButton>
        </div>
        <p className="mt-1 text-xs text-ink-soft">
          Un cambio sensible sobre este documento necesita la confirmación de un Administrador General y de un Abogado
          distintos antes de poder ejecutarse.
        </p>

        {mostrarFormCambio && (
          <FormularioCambio
            documentoId={datos.documentoId}
            onCreado={() => {
              setMostrarFormCambio(false);
              cargar();
            }}
          />
        )}

        <div className="mt-4 space-y-3">
          {cambios.length === 0 && <p className="text-sm text-ink-soft">No hay solicitudes de cambio sobre este documento.</p>}
          {cambios.map((c) => (
            <div key={c.id} className="rounded-lg bg-ink/[0.03] px-4 py-3">
              <div className="flex items-center justify-between gap-2">
                <p className="text-sm font-medium text-ink">{c.tipoAccion}</p>
                <Badge tone={c.estado === "EJECUTADA" ? "success" : c.estado === "RECHAZADA" ? "danger" : "warning"}>
                  {c.estado}
                </Badge>
              </div>
              <p className="mt-1 text-xs text-ink-soft">{c.justificacion}</p>
              <p className="mt-1 text-xs text-ink-soft">
                Solicitado por {c.solicitadoPorNombre} · Confirmó gerente: {c.confirmacionGerenteNombre ?? "—"} · Confirmó
                abogado: {c.confirmacionAbogadoNombre ?? "—"}
              </p>
              {c.motivoRechazo && <p className="mt-1 text-xs text-red-700">Rechazado: {c.motivoRechazo}</p>}
              {(c.estado === "PENDIENTE" || c.estado === "CONFIRMADA_PARCIAL") && (
                <div className="mt-2 flex gap-2">
                  <AdminButton
                    variant="secondary"
                    className="px-3 py-1.5 text-xs"
                    onClick={() => accionCambio(() => confirmarCambioSolicitudDocumento(c.id))}
                  >
                    Confirmar
                  </AdminButton>
                  <AdminButton
                    variant="danger"
                    className="px-3 py-1.5 text-xs"
                    onClick={() => {
                      const motivo = window.prompt("Motivo del rechazo:");
                      if (motivo) accionCambio(() => rechazarCambioSolicitudDocumento(c.id, motivo));
                    }}
                  >
                    Rechazar
                  </AdminButton>
                </div>
              )}
              {c.estado === "APROBADA" && (
                <div className="mt-2">
                  <AdminButton
                    className="px-3 py-1.5 text-xs"
                    onClick={() => accionCambio(() => ejecutarCambioSolicitudDocumento(c.id))}
                  >
                    Ejecutar cambio
                  </AdminButton>
                </div>
              )}
            </div>
          ))}
        </div>
      </AdminCard>
    </div>
  );
}

function FormularioCambio({ documentoId, onCreado }: { documentoId: string; onCreado: () => void }) {
  const [tipoAccion, setTipoAccion] = useState<TipoAccionCambioDocumento>("INVALIDAR");
  const [justificacion, setJustificacion] = useState("");
  const [enviando, setEnviando] = useState(false);

  async function enviar() {
    if (!justificacion.trim()) {
      toast.error("Debes justificar la solicitud.");
      return;
    }
    setEnviando(true);
    try {
      await crearCambioSolicitudDocumento({ documentoId, tipoAccion, justificacion: justificacion.trim() });
      toast.success("Solicitud creada, pendiente de confirmación.");
      onCreado();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo crear la solicitud.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="mt-4 rounded-xl border border-line bg-paper p-4">
      <div className="grid gap-3 sm:grid-cols-2">
        <select
          value={tipoAccion}
          onChange={(e) => setTipoAccion(e.target.value as TipoAccionCambioDocumento)}
          className="rounded-lg border border-line bg-surface px-3 py-2 text-sm outline-none focus:border-gold"
        >
          {tiposAccionCambio.map((t) => (
            <option key={t.value} value={t.value}>
              {t.label}
            </option>
          ))}
        </select>
      </div>
      <textarea
        value={justificacion}
        onChange={(e) => setJustificacion(e.target.value)}
        placeholder="Justificación de la solicitud..."
        rows={3}
        className="mt-3 w-full rounded-lg border border-line bg-surface px-3 py-2 text-sm outline-none focus:border-gold"
      />
      <div className="mt-3 flex justify-end">
        <AdminButton onClick={enviar} disabled={enviando}>
          {enviando ? "Enviando..." : "Crear solicitud"}
        </AdminButton>
      </div>
    </div>
  );
}
