"use client";

import { useEffect, useState, type FormEvent } from "react";
import { Dialog } from "@base-ui/react/dialog";
import { toast } from "sonner";
import { X } from "@phosphor-icons/react";
import {
  crearTarea,
  actualizarTarea,
  listarResponsables,
  ApiError,
  type Tarea,
  type PrioridadTarea,
  type Responsable,
} from "@/lib/admin-api";
import { AdminButton } from "@/components/admin/ui";

const PRIORIDADES: { valor: PrioridadTarea; label: string }[] = [
  { valor: "BAJA", label: "Baja" },
  { valor: "MEDIA", label: "Media" },
  { valor: "ALTA", label: "Alta" },
];

// Formulario único de crear/editar tarea (ver TareaService en el backend): en modo "crear" se
// usa dentro de un caso puntual (casoId + casoContexto para el título del modal), en modo
// "editar" recibe la tarea existente y casoId se ignora (ya viene resuelto en la tarea misma).
// El selector de responsable reutiliza /api/admin/solicitudes/responsables (ya existente para
// asignar corresponsables de una reunión): misma noción de "usuario interno activo que puede
// quedar a cargo de algo", sin duplicar un segundo endpoint para lo mismo.
export function TareaFormModal({
  abierto,
  onClose,
  casoId,
  casoContexto,
  tarea,
  onGuardada,
}: {
  abierto: boolean;
  onClose: () => void;
  casoId?: number;
  casoContexto?: string;
  tarea?: Tarea | null;
  onGuardada: (t: Tarea) => void;
}) {
  const [responsables, setResponsables] = useState<Responsable[] | null>(null);
  const [guardando, setGuardando] = useState(false);
  const editando = Boolean(tarea);

  useEffect(() => {
    if (!abierto) return;
    listarResponsables()
      .then(setResponsables)
      .catch(() => toast.error("No se pudo cargar la lista de responsables."));
  }, [abierto]);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const data = new FormData(form);
    const titulo = String(data.get("titulo") ?? "").trim();
    const usuarioAsignadoId = Number(data.get("usuarioAsignadoId"));
    if (!titulo) {
      toast.error("El título de la tarea es obligatorio.");
      return;
    }
    if (!usuarioAsignadoId) {
      toast.error("Elige quién es el responsable.");
      return;
    }
    const fechaVencimientoLocal = String(data.get("fechaVencimiento") ?? "");

    const datos = {
      titulo,
      descripcion: String(data.get("descripcion") ?? "") || undefined,
      fechaVencimiento: fechaVencimientoLocal ? new Date(fechaVencimientoLocal).toISOString() : undefined,
      prioridad: (String(data.get("prioridad") ?? "MEDIA") as PrioridadTarea) || undefined,
      usuarioAsignadoId,
    };

    setGuardando(true);
    try {
      const guardada = editando ? await actualizarTarea(tarea!.id, datos) : await crearTarea(casoId!, datos);
      toast.success(editando ? "Tarea actualizada." : "Tarea creada.");
      form.reset();
      onGuardada(guardada);
      onClose();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo guardar la tarea.");
    } finally {
      setGuardando(false);
    }
  }

  // input[type=datetime-local] necesita "YYYY-MM-DDTHH:mm", el backend entrega ISO completo.
  function aFechaLocal(iso: string | null | undefined): string {
    if (!iso) return "";
    const d = new Date(iso);
    const pad = (n: number) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  return (
    <Dialog.Root open={abierto} onOpenChange={(open) => !open && onClose()}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-ink/40 backdrop-blur-sm" />
        <Dialog.Popup className="fixed left-1/2 top-1/2 z-50 w-full max-w-md -translate-x-1/2 -translate-y-1/2 rounded-2xl bg-surface p-6 shadow-2xl ring-1 ring-line">
          <div className="flex items-center justify-between">
            <Dialog.Title className="font-display text-lg text-ink">
              {editando ? "Editar tarea" : "Nueva tarea"}
            </Dialog.Title>
            <Dialog.Close className="flex h-8 w-8 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5">
              <X className="h-4 w-4" />
            </Dialog.Close>
          </div>
          <Dialog.Description className="mt-1 text-sm text-ink-soft">
            {editando ? tarea?.casoEtiqueta : casoContexto}
          </Dialog.Description>

          <form key={tarea?.id ?? "nueva"} onSubmit={onSubmit} className="mt-5 space-y-3">
            <input
              name="titulo"
              required
              defaultValue={tarea?.titulo ?? ""}
              placeholder="Título de la tarea"
              className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
            />
            <textarea
              name="descripcion"
              rows={3}
              defaultValue={tarea?.descripcion ?? ""}
              placeholder="Descripción (opcional)"
              className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
            />
            <div className="grid grid-cols-2 gap-3">
              <input
                name="fechaVencimiento"
                type="datetime-local"
                defaultValue={aFechaLocal(tarea?.fechaVencimiento)}
                className="w-full rounded-xl border border-line bg-paper px-3 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
              />
              <select
                name="prioridad"
                defaultValue={tarea?.prioridad ?? "MEDIA"}
                className="w-full rounded-xl border border-line bg-paper px-3 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
              >
                {PRIORIDADES.map((p) => (
                  <option key={p.valor} value={p.valor}>
                    {p.label}
                  </option>
                ))}
              </select>
            </div>
            <select
              name="usuarioAsignadoId"
              required
              defaultValue={tarea?.usuarioAsignadoId ?? ""}
              className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
            >
              <option value="" disabled>
                {responsables === null ? "Cargando responsables..." : "Asignar a..."}
              </option>
              {responsables?.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.nombre} ({r.rol === "ADMIN_GENERAL" ? "Admin" : "Abogado"})
                </option>
              ))}
            </select>

            <AdminButton type="submit" disabled={guardando} className="w-full">
              {guardando ? "Guardando..." : editando ? "Guardar cambios" : "Crear tarea"}
            </AdminButton>
          </form>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
