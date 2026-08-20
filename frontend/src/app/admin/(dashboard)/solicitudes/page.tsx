"use client";

import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Dialog } from "@base-ui/react/dialog";
import { toast } from "sonner";
import { CalendarPlus, DownloadSimple, X } from "@phosphor-icons/react";
import {
  listarSolicitudes,
  actualizarEstadoSolicitud,
  agendarCita,
  exportarSolicitudes,
  ApiError,
  type Solicitud,
  type EstadoSolicitud,
} from "@/lib/admin-api";
import { AdminPageHeader, AdminCard, AdminButton, Badge, EmptyState } from "@/components/admin/ui";

const ESTADOS: EstadoSolicitud[] = ["NUEVO", "CONTACTADO", "CERRADO"];

const origenLabel: Record<string, string> = {
  FORMULARIO: "Formulario",
  CHATBOT: "Chatbot",
  WHATSAPP: "WhatsApp",
};

function tonoEstado(estado: EstadoSolicitud) {
  if (estado === "NUEVO") return "gold" as const;
  if (estado === "CONTACTADO") return "warning" as const;
  return "neutral" as const;
}

function formatearFecha(iso: string | null) {
  if (!iso) return null;
  return new Date(iso).toLocaleString("es-CO", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export default function SolicitudesPage() {
  const searchParams = useSearchParams();
  const [solicitudes, setSolicitudes] = useState<Solicitud[] | null>(null);
  const [estadoFiltro, setEstadoFiltro] = useState<EstadoSolicitud | "">(
    (searchParams.get("estado") as EstadoSolicitud) ?? "",
  );
  const [desde, setDesde] = useState("");
  const [hasta, setHasta] = useState("");
  const [modalCitaId, setModalCitaId] = useState<number | null>(null);
  const [descargando, setDescargando] = useState(false);

  async function onDescargar() {
    setDescargando(true);
    try {
      await exportarSolicitudes({
        estado: estadoFiltro || undefined,
        desde: desde || undefined,
        hasta: hasta || undefined,
      });
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo descargar el Excel.");
    } finally {
      setDescargando(false);
    }
  }

  const cargar = useCallback(() => {
    listarSolicitudes({
      estado: estadoFiltro || undefined,
      desde: desde || undefined,
      hasta: hasta || undefined,
    })
      .then(setSolicitudes)
      .catch(() => toast.error("No se pudieron cargar las solicitudes."));
  }, [estadoFiltro, desde, hasta]);

  useEffect(() => {
    cargar();
  }, [cargar]);

  async function onCambiarEstado(id: number, nuevoEstado: EstadoSolicitud) {
    try {
      const actualizada = await actualizarEstadoSolicitud(id, nuevoEstado);
      setSolicitudes((prev) => prev?.map((s) => (s.id === id ? actualizada : s)) ?? null);
      toast.success("Estado actualizado.");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo actualizar el estado.");
    }
  }

  const solicitudCita = solicitudes?.find((s) => s.id === modalCitaId) ?? null;

  return (
    <div>
      <AdminPageHeader
        title="Solicitudes"
        description="Leads recibidos por el formulario y el chatbot."
        action={
          <AdminButton
            variant="secondary"
            onClick={onDescargar}
            disabled={descargando || !solicitudes?.length}
          >
            <DownloadSimple className="h-4 w-4" weight="light" />
            {descargando ? "Generando..." : "Descargar Excel"}
          </AdminButton>
        }
      />

      <div className="mb-6 flex flex-wrap items-center gap-3">
        <select
          value={estadoFiltro}
          onChange={(e) => setEstadoFiltro(e.target.value as EstadoSolicitud | "")}
          className="rounded-full border border-line bg-surface px-4 py-2 text-sm text-ink focus:border-gold-deep focus:outline-none"
        >
          <option value="">Todos los estados</option>
          {ESTADOS.map((e) => (
            <option key={e} value={e}>
              {e}
            </option>
          ))}
        </select>
        <input
          type="date"
          value={desde}
          onChange={(e) => setDesde(e.target.value)}
          className="rounded-full border border-line bg-surface px-4 py-2 text-sm text-ink focus:border-gold-deep focus:outline-none"
        />
        <span className="text-sm text-ink-soft">a</span>
        <input
          type="date"
          value={hasta}
          onChange={(e) => setHasta(e.target.value)}
          className="rounded-full border border-line bg-surface px-4 py-2 text-sm text-ink focus:border-gold-deep focus:outline-none"
        />
      </div>

      {solicitudes === null ? (
        <p className="text-sm text-ink-soft">Cargando...</p>
      ) : solicitudes.length === 0 ? (
        <EmptyState title="No hay solicitudes" description="Ajusta los filtros o espera a que lleguen nuevas." />
      ) : (
        <div className="space-y-3">
          {solicitudes.map((s) => (
            <AdminCard key={s.id} className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="font-medium text-ink">{s.nombre}</p>
                  <Badge tone={tonoEstado(s.estado)}>{s.estado}</Badge>
                  <Badge>{origenLabel[s.origen] ?? s.origen}</Badge>
                </div>
                <p className="mt-1 text-sm text-ink-soft">
                  {s.correo}
                  {s.telefono && ` · ${s.telefono}`}
                </p>
                <p className="mt-2 text-sm text-ink-soft">{s.mensaje}</p>
                <p className="mt-2 text-xs text-ink-soft">
                  Recibida {formatearFecha(s.fechaCreacion)}
                  {s.fechaCita && ` · Cita: ${formatearFecha(s.fechaCita)}`}
                </p>
              </div>

              <div className="flex shrink-0 flex-wrap items-center gap-2">
                <select
                  value={s.estado}
                  onChange={(e) => onCambiarEstado(s.id, e.target.value as EstadoSolicitud)}
                  className="rounded-full border border-line bg-paper px-3 py-2 text-xs text-ink focus:border-gold-deep focus:outline-none"
                >
                  {ESTADOS.map((e) => (
                    <option key={e} value={e}>
                      {e}
                    </option>
                  ))}
                </select>
                <AdminButton variant="ghost" onClick={() => setModalCitaId(s.id)} className="text-xs">
                  <CalendarPlus className="h-4 w-4" weight="light" />
                  {s.fechaCita ? "Reprogramar" : "Agendar cita"}
                </AdminButton>
              </div>
            </AdminCard>
          ))}
        </div>
      )}

      <ModalAgendarCita
        solicitud={solicitudCita}
        onClose={() => setModalCitaId(null)}
        onAgendada={(actualizada) => {
          setSolicitudes((prev) => prev?.map((s) => (s.id === actualizada.id ? actualizada : s)) ?? null);
          setModalCitaId(null);
        }}
      />
    </div>
  );
}

function ModalAgendarCita({
  solicitud,
  onClose,
  onAgendada,
}: {
  solicitud: Solicitud | null;
  onClose: () => void;
  onAgendada: (s: Solicitud) => void;
}) {
  const [fechaHora, setFechaHora] = useState("");
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    setFechaHora("");
  }, [solicitud?.id]);

  async function onSubmit() {
    if (!solicitud || !fechaHora) return;
    setEnviando(true);
    try {
      const actualizada = await agendarCita(solicitud.id, fechaHora);
      toast.success("Cita agendada. Se notificó al cliente y al administrador.");
      onAgendada(actualizada);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo agendar la cita.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Dialog.Root open={!!solicitud} onOpenChange={(open) => !open && onClose()}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-ink/40 backdrop-blur-sm" />
        <Dialog.Popup className="fixed left-1/2 top-1/2 z-50 w-full max-w-sm -translate-x-1/2 -translate-y-1/2 rounded-2xl bg-surface p-6 shadow-2xl ring-1 ring-line">
          <div className="flex items-center justify-between">
            <Dialog.Title className="font-display text-lg text-ink">Agendar cita</Dialog.Title>
            <Dialog.Close className="flex h-8 w-8 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5">
              <X className="h-4 w-4" />
            </Dialog.Close>
          </div>
          <Dialog.Description className="mt-1 text-sm text-ink-soft">
            {solicitud?.nombre}
          </Dialog.Description>

          <div className="mt-5 space-y-2">
            <label htmlFor="fechaHora" className="text-sm font-medium text-ink">
              Fecha y hora
            </label>
            <input
              id="fechaHora"
              type="datetime-local"
              value={fechaHora}
              onChange={(e) => setFechaHora(e.target.value)}
              min={new Date().toISOString().slice(0, 16)}
              className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
            />
          </div>

          <AdminButton
            onClick={onSubmit}
            disabled={!fechaHora || enviando}
            className="mt-6 w-full"
          >
            {enviando ? "Guardando..." : "Confirmar cita"}
          </AdminButton>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
