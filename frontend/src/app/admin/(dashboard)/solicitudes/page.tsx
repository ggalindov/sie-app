"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { toast } from "sonner";
import { CalendarBlank, CalendarPlus, DownloadSimple } from "@phosphor-icons/react";
import {
  listarSolicitudes,
  actualizarEstadoSolicitud,
  exportarSolicitudes,
  listarResponsables,
  ApiError,
  type Responsable,
  type Solicitud,
  type EstadoSolicitud,
} from "@/lib/admin-api";
import { useAuth } from "@/lib/auth-context";
import { AdminPageHeader, AdminCard, AdminButton, Badge, EmptyState, AdminLoader } from "@/components/admin/ui";
import { AgendarReunionModal } from "@/components/admin/agendar-reunion-modal";

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
  const { sesion } = useAuth();
  const esAdmin = sesion?.rol === "ADMIN_GENERAL";
  const [solicitudes, setSolicitudes] = useState<Solicitud[] | null>(null);
  const [responsables, setResponsables] = useState<Responsable[]>([]);
  const [estadoFiltro, setEstadoFiltro] = useState<EstadoSolicitud | "">(
    (searchParams.get("estado") as EstadoSolicitud) ?? "",
  );
  const [desde, setDesde] = useState("");
  const [hasta, setHasta] = useState("");
  const [modalCitaId, setModalCitaId] = useState<number | null>(null);
  const [descargando, setDescargando] = useState(false);

  useEffect(() => {
    if (esAdmin) {
      listarResponsables().then(setResponsables).catch(() => {});
    }
  }, [esAdmin]);

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
          <div className="flex flex-wrap items-center gap-2">
            <Link href="/admin/calendario">
              <AdminButton variant="ghost">
                <CalendarBlank className="h-4 w-4" weight="light" />
                Ver calendario
              </AdminButton>
            </Link>
            <AdminButton
              variant="secondary"
              onClick={onDescargar}
              disabled={descargando || !solicitudes?.length}
            >
              <DownloadSimple className="h-4 w-4" weight="light" />
              {descargando ? "Generando..." : "Descargar Excel"}
            </AdminButton>
          </div>
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
        <AdminLoader />
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
                  {s.fechaCita && ` · Reunión: ${formatearFecha(s.fechaCita)}`}
                  {s.fechaCita && s.abogadoAsignadoNombre && ` (${s.abogadoAsignadoNombre})`}
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
                  {s.fechaCita ? "Reprogramar" : "Agendar reunión"}
                </AdminButton>
              </div>
            </AdminCard>
          ))}
        </div>
      )}

      <AgendarReunionModal
        solicitud={solicitudCita}
        responsables={responsables}
        rolActual={esAdmin ? "ADMIN_GENERAL" : "ABOGADO"}
        onClose={() => setModalCitaId(null)}
        onAgendada={(actualizada) => {
          setSolicitudes((prev) => prev?.map((s) => (s.id === actualizada.id ? actualizada : s)) ?? null);
          setModalCitaId(null);
        }}
      />
    </div>
  );
}
