"use client";

import { useEffect, useState } from "react";
import { Dialog } from "@base-ui/react/dialog";
import { Trash, Warning, X } from "@phosphor-icons/react";
import { AdminButton } from "@/components/admin/ui";

const PALABRA_CONFIRMACION = "ELIMINAR";

// Confirmación reforzada para acciones destructivas e irreversibles: no basta un clic en un
// window.confirm() (patrón ya usado para artículos), hay que escribir la palabra "ELIMINAR"
// a mano -- pedido explícito del usuario para borrar solicitudes, pensado para que borrar un
// lead real por error sea mucho más difícil que un doble clic accidental.
export function ConfirmarEliminarModal({
  abierto,
  titulo,
  descripcion,
  eliminando,
  onConfirmar,
  onCancelar,
}: {
  abierto: boolean;
  titulo: string;
  descripcion: string;
  eliminando: boolean;
  onConfirmar: () => void;
  onCancelar: () => void;
}) {
  const [texto, setTexto] = useState("");

  useEffect(() => {
    if (abierto) setTexto("");
  }, [abierto]);

  const confirmado = texto.trim().toUpperCase() === PALABRA_CONFIRMACION;

  return (
    <Dialog.Root open={abierto} onOpenChange={(open) => !open && onCancelar()}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-[60] bg-ink/40 backdrop-blur-sm" />
        <Dialog.Popup className="fixed left-1/2 top-1/2 z-[60] w-full max-w-sm -translate-x-1/2 -translate-y-1/2 rounded-2xl bg-surface p-6 shadow-2xl ring-1 ring-line">
          <div className="flex items-center justify-between">
            <Dialog.Title className="flex items-center gap-2 font-display text-lg text-ink">
              <Warning className="h-5 w-5 text-red-600" weight="duotone" />
              {titulo}
            </Dialog.Title>
            <Dialog.Close className="flex h-8 w-8 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5">
              <X className="h-4 w-4" />
            </Dialog.Close>
          </div>
          <Dialog.Description className="mt-2 text-sm text-ink-soft">{descripcion}</Dialog.Description>

          <div className="mt-4 space-y-2">
            <label className="text-sm font-medium text-ink">
              Escribe <strong>{PALABRA_CONFIRMACION}</strong> para confirmar
            </label>
            <input
              type="text"
              value={texto}
              onChange={(e) => setTexto(e.target.value)}
              placeholder={PALABRA_CONFIRMACION}
              autoFocus
              className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-red-400 focus:outline-none"
            />
          </div>

          <AdminButton
            variant="danger"
            onClick={onConfirmar}
            disabled={!confirmado || eliminando}
            className="mt-5 w-full"
          >
            <Trash className="h-4 w-4" weight="fill" />
            {eliminando ? "Eliminando..." : "Eliminar definitivamente"}
          </AdminButton>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
