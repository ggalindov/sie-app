"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { CalendarBlank, Check, PencilSimple, Trash, User } from "@phosphor-icons/react";
import {
  listarTareasPendientes,
  completarTarea,
  eliminarTarea,
  ApiError,
  type Tarea,
  type PrioridadTarea,
} from "@/lib/admin-api";
import { AdminPageHeader, AdminCard, Badge, NotificationBadge, EmptyState, AdminLoader } from "@/components/admin/ui";
import { TareaFormModal } from "@/components/admin/tarea-form-modal";
import { useAuth } from "@/lib/auth-context";
import { cn } from "@/lib/utils";

const PRIORIDAD_TONO: Record<PrioridadTarea, "neutral" | "warning" | "danger"> = {
  BAJA: "neutral",
  MEDIA: "warning",
  ALTA: "danger",
};

const PRIORIDAD_LABEL: Record<PrioridadTarea, string> = {
  BAJA: "Baja",
  MEDIA: "Media",
  ALTA: "Alta",
};

function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

function estaVencida(iso: string | null): boolean {
  if (!iso) return false;
  return new Date(iso).getTime() < Date.now();
}

export default function TareasAdminPage() {
  const { sesion } = useAuth();
  const esAdmin = sesion?.rol === "ADMIN_GENERAL";
  const [tareas, setTareas] = useState<Tarea[] | null>(null);
  const [filtroPrioridad, setFiltroPrioridad] = useState<PrioridadTarea | "TODAS">("TODAS");
  const [filtroAsignado, setFiltroAsignado] = useState<number | "TODOS">("TODOS");
  const [tareaEditando, setTareaEditando] = useState<Tarea | null>(null);
  const [actualizandoId, setActualizandoId] = useState<number | null>(null);

  const cargar = useCallback(() => {
    listarTareasPendientes()
      .then(setTareas)
      .catch(() => toast.error("No se pudieron cargar las tareas pendientes."));
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  // Lista de asignados presentes en el lote actual -- solo tiene sentido como filtro para
  // ADMIN_GENERAL, que ve las tareas de toda la firma (un ABOGADO ya ve únicamente las suyas).
  const asignadosDisponibles = useMemo(() => {
    if (!tareas) return [];
    const mapa = new Map<number, string>();
    for (const t of tareas) mapa.set(t.usuarioAsignadoId, t.usuarioAsignadoNombre);
    return Array.from(mapa.entries()).map(([id, nombre]) => ({ id, nombre }));
  }, [tareas]);

  const tareasFiltradas = useMemo(() => {
    if (!tareas) return null;
    return tareas
      .filter((t) => filtroPrioridad === "TODAS" || t.prioridad === filtroPrioridad)
      .filter((t) => filtroAsignado === "TODOS" || t.usuarioAsignadoId === filtroAsignado)
      .slice()
      .sort((a, b) => {
        // Sin fecha de vencimiento va al final -- lo que sí tiene fecha se ordena por cuál
        // vence primero, para que lo urgente siempre quede arriba.
        if (!a.fechaVencimiento && !b.fechaVencimiento) return 0;
        if (!a.fechaVencimiento) return 1;
        if (!b.fechaVencimiento) return -1;
        return new Date(a.fechaVencimiento).getTime() - new Date(b.fechaVencimiento).getTime();
      });
  }, [tareas, filtroPrioridad, filtroAsignado]);

  async function onCompletar(id: number) {
    setActualizandoId(id);
    try {
      await completarTarea(id, true);
      setTareas((prev) => (prev ? prev.filter((t) => t.id !== id) : null));
      toast.success("Tarea completada.");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo completar la tarea.");
    } finally {
      setActualizandoId(null);
    }
  }

  async function onEliminar(id: number) {
    if (!window.confirm("¿Eliminar esta tarea? Esta acción no se puede deshacer.")) return;
    setActualizandoId(id);
    try {
      await eliminarTarea(id);
      setTareas((prev) => (prev ? prev.filter((t) => t.id !== id) : null));
      toast.success("Tarea eliminada.");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo eliminar la tarea.");
    } finally {
      setActualizandoId(null);
    }
  }

  return (
    <div>
      <AdminPageHeader
        title="Tareas"
        description={
          esAdmin
            ? "Todo lo pendiente por caso en la firma, sin importar quién lo tenga asignado. Las tareas se crean desde el caso al que pertenecen (pestaña Casos)."
            : "Lo que tienes pendiente por hacer en tus casos. Las tareas se crean desde el caso al que pertenecen (pestaña Casos)."
        }
      />

      {tareas !== null && tareas.length > 0 && (
        <div className="mt-6 flex flex-wrap items-center gap-2">
          {(["TODAS", "ALTA", "MEDIA", "BAJA"] as const).map((p) => (
            <button
              key={p}
              type="button"
              onClick={() => setFiltroPrioridad(p)}
              className={cn(
                "rounded-full px-4 py-2 text-sm transition-colors",
                filtroPrioridad === p ? "bg-ink text-paper" : "bg-ink/5 text-ink-soft hover:bg-ink/10",
              )}
            >
              {p === "TODAS" ? "Todas" : PRIORIDAD_LABEL[p]}
            </button>
          ))}

          {esAdmin && asignadosDisponibles.length > 1 && (
            <select
              value={filtroAsignado}
              onChange={(e) => setFiltroAsignado(e.target.value === "TODOS" ? "TODOS" : Number(e.target.value))}
              className="rounded-full border border-line bg-paper px-4 py-2 text-sm text-ink-soft focus:border-gold-deep focus:outline-none"
            >
              <option value="TODOS">Cualquier responsable</option>
              {asignadosDisponibles.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.nombre}
                </option>
              ))}
            </select>
          )}
        </div>
      )}

      {tareas === null ? (
        <AdminLoader />
      ) : tareas.length === 0 ? (
        <EmptyState
          title="No hay tareas pendientes"
          description={esAdmin ? "Nadie en la firma tiene tareas pendientes en este momento." : "No tienes tareas pendientes asignadas en este momento."}
        />
      ) : (
        <div className="mt-6 space-y-3">
          {tareasFiltradas?.map((t) => {
            const vencida = estaVencida(t.fechaVencimiento);
            return (
              <AdminCard
                key={t.id}
                className={cn("flex flex-col gap-4 lg:flex-row lg:items-center", vencida && "border-l-4 border-l-rose-400")}
              >
                <button
                  type="button"
                  disabled={actualizandoId === t.id}
                  onClick={() => onCompletar(t.id)}
                  title="Marcar como completada"
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-line text-ink-soft transition-colors hover:border-emerald-400 hover:bg-emerald-50 hover:text-emerald-700 disabled:opacity-50"
                >
                  <Check className="h-4 w-4" weight="bold" />
                </button>

                <div className="min-w-0 flex-1">
                  <p className="font-medium text-ink">{t.titulo}</p>
                  <p className="mt-1 text-sm text-ink-soft">
                    {t.casoEtiqueta} · {t.clienteNombre}
                  </p>
                  {t.descripcion && <p className="mt-1.5 text-xs text-ink-soft">{t.descripcion}</p>}
                </div>

                <div className="flex shrink-0 flex-wrap items-center gap-1.5 lg:justify-end">
                  <Badge tone={PRIORIDAD_TONO[t.prioridad]}>{PRIORIDAD_LABEL[t.prioridad]}</Badge>
                  {t.fechaVencimiento && (
                    <NotificationBadge
                      size="sm"
                      tone={vencida ? "danger" : "neutral"}
                      icon={<CalendarBlank weight="bold" className="h-3.5 w-3.5" />}
                    >
                      {vencida ? "Venció " : "Vence "}
                      {formatearFecha(t.fechaVencimiento)}
                    </NotificationBadge>
                  )}
                  <NotificationBadge size="sm" tone="neutral" icon={<User weight="bold" className="h-3.5 w-3.5" />}>
                    {t.usuarioAsignadoNombre}
                  </NotificationBadge>

                  <button
                    type="button"
                    onClick={() => setTareaEditando(t)}
                    title="Editar tarea"
                    className="flex h-8 w-8 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5 hover:text-ink"
                  >
                    <PencilSimple className="h-4 w-4" weight="bold" />
                  </button>
                  <button
                    type="button"
                    disabled={actualizandoId === t.id}
                    onClick={() => onEliminar(t.id)}
                    title="Eliminar tarea"
                    className="flex h-8 w-8 items-center justify-center rounded-full text-ink-soft hover:bg-red-50 hover:text-red-700 disabled:opacity-50"
                  >
                    <Trash className="h-4 w-4" weight="bold" />
                  </button>
                </div>
              </AdminCard>
            );
          })}
        </div>
      )}

      <TareaFormModal
        abierto={tareaEditando !== null}
        onClose={() => setTareaEditando(null)}
        tarea={tareaEditando}
        onGuardada={(actualizada) => setTareas((prev) => (prev ? prev.map((t) => (t.id === actualizada.id ? actualizada : t)) : null))}
      />
    </div>
  );
}
