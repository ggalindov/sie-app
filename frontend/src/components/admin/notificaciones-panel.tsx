"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { Dialog } from "@base-ui/react/dialog";
import { toast } from "sonner";
import { Bell, CalendarBlank, Check, Envelope, User, X } from "@phosphor-icons/react";
import { listarTareasPendientes, listarSolicitudes, completarTarea, ApiError, type Tarea } from "@/lib/admin-api";
import { useAuth } from "@/lib/auth-context";
import { cn } from "@/lib/utils";

function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });
}

function estaVencida(iso: string | null): boolean {
  if (!iso) return false;
  return new Date(iso).getTime() < Date.now();
}

// Campanita de notificaciones, disponible en todos los paneles para ambos roles (pedido
// explícito del usuario, tras quitar "Tareas" del menú de un ABOGADO): un lateral con el
// reporte general del sistema (solicitudes nuevas) y las tareas pendientes de quien tiene la
// sesión abierta -- mismos datos que /admin/tareas, en un formato rápido de revisar sin salir
// de la página en la que se está trabajando.
export function NotificacionesPanel({ variante = "oscura" }: { variante?: "oscura" | "clara" }) {
  const { sesion } = useAuth();
  const [abierto, setAbierto] = useState(false);
  const [tareas, setTareas] = useState<Tarea[] | null>(null);
  const [solicitudesNuevas, setSolicitudesNuevas] = useState<number | null>(null);
  const [actualizandoId, setActualizandoId] = useState<number | null>(null);

  const cargar = useCallback(() => {
    listarTareasPendientes()
      .then(setTareas)
      .catch(() => setTareas([]));
    listarSolicitudes({ estado: "NUEVO" })
      .then((s) => setSolicitudesNuevas(s.length))
      .catch(() => setSolicitudesNuevas(null));
  }, []);

  // Carga en cuanto hay sesión (para el contador del badge), y se refresca cada vez que se
  // abre el panel para no mostrar datos desactualizados si pasó un rato en la misma página.
  useEffect(() => {
    if (!sesion) return;
    cargar();
  }, [sesion, cargar]);

  useEffect(() => {
    if (abierto) cargar();
  }, [abierto, cargar]);

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

  const totalNotificaciones = (tareas?.length ?? 0) + (solicitudesNuevas ?? 0);

  return (
    <Dialog.Root open={abierto} onOpenChange={setAbierto}>
      <Dialog.Trigger
        className={cn(
          "relative flex h-9 w-9 shrink-0 items-center justify-center rounded-full transition-colors",
          variante === "oscura" ? "text-night-ink/80 hover:bg-white/10 hover:text-night-ink" : "text-ink/70 hover:bg-ink/5 hover:text-ink",
        )}
        aria-label="Notificaciones"
      >
        <Bell weight={totalNotificaciones > 0 ? "fill" : "regular"} className="h-5 w-5" />
        {totalNotificaciones > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-gold px-1 text-[10px] font-bold text-ink-fixed">
            {totalNotificaciones > 99 ? "99+" : totalNotificaciones}
          </span>
        )}
      </Dialog.Trigger>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-ink/30 backdrop-blur-[1px]" />
        <Dialog.Popup className="fixed right-0 top-0 z-50 flex h-dvh w-full max-w-sm flex-col bg-surface shadow-2xl ring-1 ring-line">
          <div className="flex shrink-0 items-center justify-between border-b border-line px-5 py-4">
            <Dialog.Title className="font-display text-lg text-ink">Notificaciones</Dialog.Title>
            <Dialog.Close className="flex h-8 w-8 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5">
              <X className="h-4 w-4" />
            </Dialog.Close>
          </div>

          <div className="min-h-0 flex-1 overflow-y-auto px-5 py-5">
            {/* Reporte general del sistema */}
            <p className="text-[11px] font-semibold uppercase tracking-wider text-ink-soft/70">Reporte general</p>
            <Link
              href="/admin/solicitudes?estado=NUEVO"
              onClick={() => setAbierto(false)}
              className="mt-2 flex items-center justify-between rounded-xl border border-line bg-paper-soft p-3 text-sm transition-colors hover:bg-ink/5"
            >
              <span className="flex items-center gap-2 text-ink">
                <Envelope weight="bold" className="h-4 w-4 text-gold-deep" />
                Solicitudes nuevas
              </span>
              <span className="font-mono font-semibold text-ink">{solicitudesNuevas ?? "..."}</span>
            </Link>

            {/* Tareas pendientes de quien tiene la sesión abierta */}
            <div className="mt-6 flex items-center justify-between">
              <p className="text-[11px] font-semibold uppercase tracking-wider text-ink-soft/70">
                {sesion?.rol === "ADMIN_GENERAL" ? "Tareas pendientes (toda la firma)" : "Tus tareas pendientes"}
              </p>
              {sesion?.rol === "ADMIN_GENERAL" && (
                <Link href="/admin/tareas" onClick={() => setAbierto(false)} className="text-xs font-medium text-gold-deep hover:underline">
                  Ver todas
                </Link>
              )}
            </div>

            {tareas === null ? (
              <p className="mt-3 text-sm text-ink-soft">Cargando...</p>
            ) : tareas.length === 0 ? (
              <p className="mt-3 rounded-xl border border-dashed border-line p-4 text-center text-sm text-ink-soft">
                No hay tareas pendientes.
              </p>
            ) : (
              <div className="mt-3 space-y-2">
                {tareas.map((t) => {
                  const vencida = estaVencida(t.fechaVencimiento);
                  return (
                    <div key={t.id} className={cn("flex items-start gap-2.5 rounded-xl border border-line bg-paper p-3", vencida && "border-l-4 border-l-rose-400")}>
                      <button
                        type="button"
                        disabled={actualizandoId === t.id}
                        onClick={() => onCompletar(t.id)}
                        title="Marcar como completada"
                        className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full border border-line text-ink-soft transition-colors hover:border-emerald-400 hover:bg-emerald-50 hover:text-emerald-700 disabled:opacity-50"
                      >
                        <Check className="h-3 w-3" weight="bold" />
                      </button>
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-medium text-ink">{t.titulo}</p>
                        <p className="mt-0.5 text-xs text-ink-soft">
                          {t.casoEtiqueta} · {t.clienteNombre}
                        </p>
                        <div className="mt-1.5 flex flex-wrap items-center gap-2 text-[11px] text-ink-soft">
                          {t.fechaVencimiento && (
                            <span className={cn("inline-flex items-center gap-1", vencida && "font-medium text-rose-600")}>
                              <CalendarBlank weight="bold" className="h-3 w-3" />
                              {formatearFecha(t.fechaVencimiento)}
                            </span>
                          )}
                          {sesion?.rol === "ADMIN_GENERAL" && (
                            <span className="inline-flex items-center gap-1">
                              <User weight="bold" className="h-3 w-3" />
                              {t.usuarioAsignadoNombre}
                            </span>
                          )}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
