"use client";

import { useEffect, useState, type FormEvent } from "react";
import { Dialog } from "@base-ui/react/dialog";
import { toast } from "sonner";
import { X } from "@phosphor-icons/react";
import { actualizarCaso, ApiError, listarUsuarios, type CasoAdmin, type UsuarioInterno } from "@/lib/admin-api";
import { AdminButton } from "@/components/admin/ui";

// Pedido explícito del usuario: "una variable para editar los casos, la idea es
// desligarnos del excel, un boton para editar caso para administrador unicamente y
// asignacion de caso por abogado" -- este modal es la única forma de corregir datos de un
// caso sin depender de que el Google Sheets de la firma los traiga bien. Solo se monta
// desde /admin/casos cuando sesion.rol === "ADMIN_GENERAL" (el backend igual lo rechaza con
// 403 si un ABOGADO llegara a invocar el endpoint).
export function EditarCasoModal({
  caso,
  onClose,
  onActualizado,
}: {
  caso: CasoAdmin | null;
  onClose: () => void;
  onActualizado: (actualizado: CasoAdmin) => void;
}) {
  const [nombreEnHoja, setNombreEnHoja] = useState("");
  const [correoCliente, setCorreoCliente] = useState("");
  const [telefonoCliente, setTelefonoCliente] = useState("");
  const [radicadoId, setRadicadoId] = useState("");
  const [notasInternas, setNotasInternas] = useState("");
  const [abogadoAsignadoId, setAbogadoAsignadoId] = useState<string>("");
  const [usuarios, setUsuarios] = useState<UsuarioInterno[]>([]);
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    if (!caso) return;
    setNombreEnHoja(caso.nombreCliente);
    setCorreoCliente(caso.correoCliente ?? "");
    setTelefonoCliente(caso.telefonoCliente ?? "");
    setRadicadoId(caso.radicadoId ?? "");
    setNotasInternas(caso.notasInternas ?? "");
    setAbogadoAsignadoId(caso.abogadoAsignadoId ? String(caso.abogadoAsignadoId) : "");
    listarUsuarios()
      .then((lista) => setUsuarios(lista.filter((u) => u.activo)))
      .catch(() => {
        // Silencioso: si no carga la lista, el selector simplemente queda vacío -- no debe
        // bloquear la edición del resto de campos del caso.
      });
  }, [caso]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!caso) return;
    setGuardando(true);
    try {
      const actualizado = await actualizarCaso(caso.id, {
        nombreEnHoja: nombreEnHoja.trim() || null,
        correoCliente: correoCliente.trim() || "",
        telefonoCliente: telefonoCliente.trim() || "",
        radicadoId: radicadoId.trim() || "",
        notasInternas: notasInternas.trim() || "",
        abogadoAsignadoId: abogadoAsignadoId ? Number(abogadoAsignadoId) : null,
      });
      toast.success("Caso actualizado.");
      onActualizado(actualizado);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo actualizar el caso.");
    } finally {
      setGuardando(false);
    }
  }

  return (
    <Dialog.Root open={caso !== null} onOpenChange={(open) => !open && onClose()}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-ink/40 backdrop-blur-sm" />
        <Dialog.Popup className="fixed left-1/2 top-1/2 z-50 max-h-[90vh] w-full max-w-lg -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-2xl bg-surface p-6 shadow-2xl ring-1 ring-line">
          <div className="flex items-center justify-between">
            <div>
              <Dialog.Title className="font-display text-lg text-ink">Editar caso</Dialog.Title>
              <Dialog.Description className="mt-1 text-sm text-ink-soft">
                {caso?.fuenteVisible}
                {caso?.numeroCaso ? ` · Caso Nº ${caso.numeroCaso}` : ""}
              </Dialog.Description>
            </div>
            <Dialog.Close className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5">
              <X className="h-4 w-4" />
            </Dialog.Close>
          </div>

          {/* Pedido explícito del usuario: "la idea es desligarnos del excel" -- aviso
              honesto de que la próxima sincronización automática todavía puede volver a
              traer el nombre/radicado si la fila de la hoja cambia, no es un candado
              permanente contra el Excel. */}
          <p className="mt-4 rounded-xl bg-ink/[0.04] px-3 py-2.5 text-xs leading-relaxed text-ink-soft">
            Estos cambios quedan guardados aquí mismo, sin tocar el Google Sheets de la firma.
            El número de caso (columna interna de la hoja) no se puede editar -- es la llave
            que usa la sincronización automática para reconocer esta fila.
          </p>

          <form onSubmit={onSubmit} className="mt-4 space-y-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-ink-soft">Nombre del cliente</label>
              <input
                value={nombreEnHoja}
                onChange={(e) => setNombreEnHoja(e.target.value)}
                className="w-full rounded-xl border border-line bg-paper px-4 py-2.5 text-sm text-ink focus:border-gold-deep focus:outline-none"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-ink-soft">Correo</label>
              <input
                type="email"
                value={correoCliente}
                onChange={(e) => setCorreoCliente(e.target.value)}
                placeholder="Sin correo capturado"
                className="w-full rounded-xl border border-line bg-paper px-4 py-2.5 text-sm text-ink focus:border-gold-deep focus:outline-none"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-ink-soft">Teléfono</label>
              <input
                value={telefonoCliente}
                onChange={(e) => setTelefonoCliente(e.target.value)}
                placeholder="Sin teléfono capturado"
                className="w-full rounded-xl border border-line bg-paper px-4 py-2.5 text-sm text-ink focus:border-gold-deep focus:outline-none"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-ink-soft">Radicado</label>
              <input
                value={radicadoId}
                onChange={(e) => setRadicadoId(e.target.value)}
                placeholder="Sin radicado aún"
                className="w-full rounded-xl border border-line bg-paper px-4 py-2.5 font-mono text-sm text-ink focus:border-gold-deep focus:outline-none"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-ink-soft">Abogado asignado</label>
              <select
                value={abogadoAsignadoId}
                onChange={(e) => setAbogadoAsignadoId(e.target.value)}
                className="w-full rounded-xl border border-line bg-paper px-4 py-2.5 text-sm text-ink focus:border-gold-deep focus:outline-none"
              >
                <option value="">Sin asignar</option>
                {usuarios.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.nombre} ({u.rol === "ADMIN_GENERAL" ? "Admin" : "Abogado"})
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-ink-soft">Notas internas</label>
              <textarea
                value={notasInternas}
                onChange={(e) => setNotasInternas(e.target.value)}
                rows={3}
                placeholder="Nunca visibles para el cliente"
                className="w-full rounded-xl border border-line bg-paper px-4 py-2.5 text-sm text-ink focus:border-gold-deep focus:outline-none"
              />
            </div>

            <AdminButton type="submit" disabled={guardando} className="w-full">
              {guardando ? "Guardando..." : "Guardar cambios"}
            </AdminButton>
          </form>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
