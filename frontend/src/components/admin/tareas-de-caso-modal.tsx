"use client";

import { useCallback, useEffect, useState } from "react";
import { Dialog } from "@base-ui/react/dialog";
import { toast } from "sonner";
import { CalendarBlank, Check, PencilSimple, Plus, Trash, User, X } from "@phosphor-icons/react";
import {
  listarTareasPorCaso,
  completarTarea,
  eliminarTarea,
  ApiError,
  type Tarea,
  type PrioridadTarea,
} from "@/lib/admin-api";
import { AdminButton, Badge, NotificationBadge } from "@/components/admin/ui";
import { TareaFormModal } from "@/components/admin/tarea-form-modal";
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
  return new Date(iso).toLocaleDateString("es-CO", { day: "numeric", month: "short", year: "numeric" });
}

function estaVencida(iso: string | null): boolean {
  if (!iso) return false;
  return new Date(iso).getTime() < Date.now();
}

// Solo los campos que este modal realmente necesita -- cualquier objeto con forma de caso
// (CasoAdmin en /admin/casos, o un CasoVinculado del detalle de un cliente en el CRM) sirve
// sin conversión, en vez de exigir el tipo completo de una sola pantalla.
export type CasoParaTareas = {
  id: number;
  nombreCliente: string;
  radicadoId: string | null;
};

// Registro completo de tareas de UN caso puntual (pedido explícito del usuario: "deja un
// registro de tareas por caso"), abierto desde la tarjeta de ese caso en /admin/casos, o desde
// la ficha 360 de un cliente en el CRM ("ahi es donde se asginara las tareas por cada persona").
// A diferencia de /admin/tareas (que solo muestra pendientes, de uno o todos los casos), aquí se
// ve el historial completo -- pendientes y completadas -- de este caso en particular.
export function TareasDeCasoModal({
  caso,
  onClose,
  onCambioPendientes,
}: {
  caso: CasoParaTareas | null;
  onClose: () => void;
  // Avisa al listado de Casos que el conteo de pendientes de este caso pudo haber cambiado,
  // para refrescar el badge sin recargar la página completa.
  onCambioPendientes?: () => void;
}) {
  const [tareas, setTareas] = useState<Tarea[] | null>(null);
  const [formularioAbierto, setFormularioAbierto] = useState(false);
  const [tareaEditando, setTareaEditando] = useState<Tarea | null>(null);
  const [actualizandoId, setActualizandoId] = useState<number | null>(null);

  const cargar = useCallback(() => {
    if (!caso) return;
    listarTareasPorCaso(caso.id)
      .then(setTareas)
      .catch(() => toast.error("No se pudieron cargar las tareas de este caso."));
  }, [caso]);

  useEffect(() => {
    setTareas(null);
    cargar();
  }, [cargar]);

  async function onCompletar(id: number, completada: boolean) {
    setActualizandoId(id);
    try {
      const actualizada = await completarTarea(id, completada);
      setTareas((prev) => (prev ? prev.map((t) => (t.id === id ? actualizada : t)) : null));
      onCambioPendientes?.();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo actualizar la tarea.");
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
      onCambioPendientes?.();
      toast.success("Tarea eliminada.");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo eliminar la tarea.");
    } finally {
      setActualizandoId(null);
    }
  }

  const pendientes = tareas?.filter((t) => !t.completada) ?? [];
  const completadas = tareas?.filter((t) => t.completada) ?? [];

  return (
    <>
      <Dialog.Root open={caso !== null} onOpenChange={(open) => !open && onClose()}>
        <Dialog.Portal>
          <Dialog.Backdrop className="fixed inset-0 z-50 bg-ink/40 backdrop-blur-sm" />
          <Dialog.Popup className="fixed left-1/2 top-1/2 z-50 max-h-[85vh] w-full max-w-lg -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-2xl bg-surface p-6 shadow-2xl ring-1 ring-line">
            <div className="flex items-center justify-between">
              <div>
                <Dialog.Title className="font-display text-lg text-ink">Tareas del caso</Dialog.Title>
                <Dialog.Description className="mt-1 text-sm text-ink-soft">
                  {caso?.nombreCliente} {caso?.radicadoId && `· ${caso.radicadoId}`}
                </Dialog.Description>
              </div>
              <Dialog.Close className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5">
                <X className="h-4 w-4" />
              </Dialog.Close>
            </div>

            <AdminButton variant="ghost" className="mt-4 w-full" onClick={() => setFormularioAbierto(true)}>
              <Plus className="h-4 w-4" weight="bold" />
              Nueva tarea
            </AdminButton>

            {tareas === null ? (
              <p className="mt-6 text-center text-sm text-ink-soft">Cargando...</p>
            ) : tareas.length === 0 ? (
              <p className="mt-6 text-center text-sm text-ink-soft">Este caso todavía no tiene tareas.</p>
            ) : (
              <div className="mt-5 space-y-4">
                {pendientes.length > 0 && (
                  <div className="space-y-2">
                    <p className="text-[11px] font-medium uppercase tracking-wider text-ink-soft/70">Pendientes</p>
                    {pendientes.map((t) => (
                      <FilaTarea
                        key={t.id}
                        tarea={t}
                        ocupado={actualizandoId === t.id}
                        onCompletar={() => onCompletar(t.id, true)}
                        onEditar={() => setTareaEditando(t)}
                        onEliminar={() => onEliminar(t.id)}
                      />
                    ))}
                  </div>
                )}
                {completadas.length > 0 && (
                  <div className="space-y-2">
                    <p className="text-[11px] font-medium uppercase tracking-wider text-ink-soft/70">Completadas</p>
                    {completadas.map((t) => (
                      <FilaTarea
                        key={t.id}
                        tarea={t}
                        ocupado={actualizandoId === t.id}
                        onCompletar={() => onCompletar(t.id, false)}
                        onEditar={() => setTareaEditando(t)}
                        onEliminar={() => onEliminar(t.id)}
                      />
                    ))}
                  </div>
                )}
              </div>
            )}
          </Dialog.Popup>
        </Dialog.Portal>
      </Dialog.Root>

      {caso && (
        <TareaFormModal
          abierto={formularioAbierto}
          onClose={() => setFormularioAbierto(false)}
          casoId={caso.id}
          casoContexto={`${caso.nombreCliente}${caso.radicadoId ? ` · ${caso.radicadoId}` : ""}`}
          onGuardada={(nueva) => {
            setTareas((prev) => (prev ? [nueva, ...prev] : [nueva]));
            onCambioPendientes?.();
          }}
        />
      )}
      <TareaFormModal
        abierto={tareaEditando !== null}
        onClose={() => setTareaEditando(null)}
        tarea={tareaEditando}
        onGuardada={(actualizada) => {
          setTareas((prev) => (prev ? prev.map((t) => (t.id === actualizada.id ? actualizada : t)) : null));
          onCambioPendientes?.();
        }}
      />
    </>
  );
}

function FilaTarea({
  tarea,
  ocupado,
  onCompletar,
  onEditar,
  onEliminar,
}: {
  tarea: Tarea;
  ocupado: boolean;
  onCompletar: () => void;
  onEditar: () => void;
  onEliminar: () => void;
}) {
  const vencida = !tarea.completada && estaVencida(tarea.fechaVencimiento);
  return (
    <div
      className={cn(
        "flex items-start gap-3 rounded-xl border border-line bg-paper p-3",
        tarea.completada && "opacity-60",
        vencida && "border-l-4 border-l-rose-400",
      )}
    >
      <button
        type="button"
        disabled={ocupado}
        onClick={onCompletar}
        title={tarea.completada ? "Marcar como pendiente" : "Marcar como completada"}
        className={cn(
          "mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full border transition-colors disabled:opacity-50",
          tarea.completada
            ? "border-emerald-300 bg-emerald-100 text-emerald-700"
            : "border-line text-ink-soft hover:border-emerald-400 hover:bg-emerald-50 hover:text-emerald-700",
        )}
      >
        <Check className="h-3.5 w-3.5" weight="bold" />
      </button>

      <div className="min-w-0 flex-1">
        <p className={cn("text-sm font-medium text-ink", tarea.completada && "line-through")}>{tarea.titulo}</p>
        {tarea.descripcion && <p className="mt-0.5 text-xs text-ink-soft">{tarea.descripcion}</p>}
        <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
          <Badge tone={PRIORIDAD_TONO[tarea.prioridad]}>{PRIORIDAD_LABEL[tarea.prioridad]}</Badge>
          {tarea.fechaVencimiento && (
            <NotificationBadge size="sm" tone={vencida ? "danger" : "neutral"} icon={<CalendarBlank weight="bold" className="h-3.5 w-3.5" />}>
              {formatearFecha(tarea.fechaVencimiento)}
            </NotificationBadge>
          )}
          <NotificationBadge size="sm" tone="neutral" icon={<User weight="bold" className="h-3.5 w-3.5" />}>
            {tarea.usuarioAsignadoNombre}
          </NotificationBadge>
        </div>
      </div>

      <div className="flex shrink-0 items-center gap-0.5">
        <button
          type="button"
          onClick={onEditar}
          title="Editar tarea"
          className="flex h-7 w-7 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5 hover:text-ink"
        >
          <PencilSimple className="h-3.5 w-3.5" weight="bold" />
        </button>
        <button
          type="button"
          disabled={ocupado}
          onClick={onEliminar}
          title="Eliminar tarea"
          className="flex h-7 w-7 items-center justify-center rounded-full text-ink-soft hover:bg-red-50 hover:text-red-700 disabled:opacity-50"
        >
          <Trash className="h-3.5 w-3.5" weight="bold" />
        </button>
      </div>
    </div>
  );
}
