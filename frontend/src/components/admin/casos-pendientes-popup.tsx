"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Dialog } from "@base-ui/react/dialog";
import { Sparkle, PencilSimple, Check, X, ArrowsClockwise } from "@phosphor-icons/react";
import { toast } from "sonner";
import { AdminButton } from "@/components/admin/ui";
import {
  listarCasosPendientesAprobacion,
  aprobarCasoNuevo,
  listarCambiosPendientesCasos,
  marcarCambiosRevisados,
  ApiError,
  type CasoAdmin,
  type CasoCambioDetectado,
} from "@/lib/admin-api";

const NOMBRES_CAMPO: Record<string, string> = {
  radicado: "Radicado",
  nombre_en_hoja: "Nombre del cliente",
  abogado_asignado: "Abogado asignado",
};

// Pedido explícito del usuario (2026-11-09): "que cuando el sistema detecte la creacion de
// un nuevo caso del google sheet salga un popup de notificaciones de casos nuevos
// identificados, donde se pueda aceptar la vinculacion del caso al sistema y se pueda
// editar, asi mismo como un pop-up al actualizarlos que detecte que casos tuvieron cambios y
// cuales fueron, esto para llevar una mayor integridad entre el google sheet y el sistema".
// Montado una sola vez en admin-shell.tsx (ver ahí) -- se revisa al abrir el panel, nunca con
// polling agresivo: si queda algo sin revisar, vuelve a aparecer la próxima vez que se abra
// el panel, hasta que un gerente o abogado lo acepte/marque revisado.
export function CasosPendientesPopup() {
  const [nuevos, setNuevos] = useState<CasoAdmin[] | null>(null);
  const [cambios, setCambios] = useState<CasoCambioDetectado[] | null>(null);
  const [procesando, setProcesando] = useState<Set<string>>(new Set());
  const [abierto, setAbierto] = useState(false);

  useEffect(() => {
    Promise.all([listarCasosPendientesAprobacion(), listarCambiosPendientesCasos()])
      .then(([n, c]) => {
        setNuevos(n);
        setCambios(c);
        if (n.length > 0 || c.length > 0) setAbierto(true);
      })
      .catch(() => {
        setNuevos([]);
        setCambios([]);
      });
    // Solo al montar (una vez por carga del panel) -- a propósito, ver comentario de arriba.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function aceptar(caso: CasoAdmin) {
    const clave = `nuevo-${caso.id}`;
    setProcesando((prev) => new Set(prev).add(clave));
    try {
      await aprobarCasoNuevo(caso.id);
      setNuevos((prev) => (prev ?? []).filter((c) => c.id !== caso.id));
      toast.success("Caso vinculado al sistema.");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo aceptar el caso.");
    } finally {
      setProcesando((prev) => {
        const next = new Set(prev);
        next.delete(clave);
        return next;
      });
    }
  }

  async function revisarCambio(id: number) {
    const clave = `cambio-${id}`;
    setProcesando((prev) => new Set(prev).add(clave));
    try {
      await marcarCambiosRevisados([id]);
      setCambios((prev) => (prev ?? []).filter((c) => c.id !== id));
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo marcar el cambio como revisado.");
    } finally {
      setProcesando((prev) => {
        const next = new Set(prev);
        next.delete(clave);
        return next;
      });
    }
  }

  async function revisarTodosLosCambios() {
    const ids = (cambios ?? []).map((c) => c.id);
    if (ids.length === 0) return;
    try {
      await marcarCambiosRevisados(ids);
      setCambios([]);
      toast.success("Cambios marcados como revisados.");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo marcar los cambios como revisados.");
    }
  }

  const hayAlgo = (nuevos?.length ?? 0) > 0 || (cambios?.length ?? 0) > 0;
  if (!hayAlgo) return null;

  return (
    <Dialog.Root open={abierto} onOpenChange={setAbierto}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-[60] bg-ink/40 backdrop-blur-sm" />
        <Dialog.Popup className="fixed left-1/2 top-1/2 z-[60] max-h-[85vh] w-full max-w-2xl -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-2xl bg-surface p-6 shadow-2xl ring-1 ring-line">
          <div className="flex items-center justify-between">
            <Dialog.Title className="flex items-center gap-2 font-display text-lg text-ink">
              <Sparkle className="h-5 w-5 text-gold" weight="duotone" />
              Integridad con el Google Sheets
            </Dialog.Title>
            <Dialog.Close className="flex h-8 w-8 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5">
              <X className="h-4 w-4" />
            </Dialog.Close>
          </div>

          {(nuevos?.length ?? 0) > 0 && (
            <div className="mt-5">
              <p className="text-sm font-medium text-ink">
                Casos nuevos identificados ({nuevos!.length})
              </p>
              <p className="mt-1 text-xs text-ink-soft">
                Se crearon automáticamente desde el Google Sheets. Revísalos y acepta su vinculación, o edítalos
                primero si algo no está correcto.
              </p>
              <div className="mt-3 space-y-2">
                {nuevos!.map((caso) => (
                  <div
                    key={caso.id}
                    className="flex items-center justify-between gap-3 rounded-xl border border-line bg-paper px-4 py-3"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-ink">{caso.nombreCliente}</p>
                      <p className="truncate text-xs text-ink-soft">
                        {caso.fuenteVisible ?? caso.fuente} · Nº{caso.numeroCaso ?? "-"} ·{" "}
                        {caso.radicadoId ? caso.radicadoId : "sin radicado todavía"}
                      </p>
                    </div>
                    <div className="flex shrink-0 items-center gap-2">
                      <Link
                        href={`/admin/casos?q=${encodeURIComponent(caso.numeroCaso ?? caso.nombreCliente)}`}
                        className="flex h-9 w-9 items-center justify-center rounded-lg bg-ink/5 text-ink-soft hover:bg-ink/10"
                        title="Ver y editar"
                      >
                        <PencilSimple className="h-4 w-4" />
                      </Link>
                      <AdminButton
                        variant="secondary"
                        className="!px-3 !py-2 text-xs"
                        disabled={procesando.has(`nuevo-${caso.id}`)}
                        onClick={() => aceptar(caso)}
                      >
                        <Check className="h-3.5 w-3.5" />
                        Aceptar
                      </AdminButton>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {(cambios?.length ?? 0) > 0 && (
            <div className="mt-6">
              <div className="flex items-center justify-between">
                <p className="text-sm font-medium text-ink">
                  Cambios detectados en casos ya existentes ({cambios!.length})
                </p>
                <button
                  type="button"
                  onClick={revisarTodosLosCambios}
                  className="text-xs font-medium text-gold-deep hover:underline"
                >
                  Marcar todos como revisados
                </button>
              </div>
              <p className="mt-1 text-xs text-ink-soft">
                La hoja corrigió un dato que ya estaba asignado en el sistema -- verifica que el cambio sea correcto.
              </p>
              <div className="mt-3 space-y-2">
                {cambios!.map((cambio) => (
                  <div
                    key={cambio.id}
                    className="flex items-center justify-between gap-3 rounded-xl border border-line bg-paper px-4 py-3"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-ink">
                        {cambio.nombreCliente} <span className="text-ink-soft">· Nº{cambio.numeroCaso ?? "-"}</span>
                      </p>
                      <p className="mt-0.5 flex items-center gap-1 text-xs text-ink-soft">
                        <ArrowsClockwise className="h-3 w-3 shrink-0" />
                        <strong>{NOMBRES_CAMPO[cambio.campo] ?? cambio.campo}:</strong>{" "}
                        <span className="truncate line-through decoration-red-400/60">
                          {cambio.valorAnterior ?? "(vacío)"}
                        </span>{" "}
                        &rarr; <span className="truncate font-medium text-ink">{cambio.valorNuevo ?? "(vacío)"}</span>
                      </p>
                    </div>
                    <div className="flex shrink-0 items-center gap-2">
                      <Link
                        href={`/admin/casos?q=${encodeURIComponent(cambio.numeroCaso ?? cambio.nombreCliente)}`}
                        className="flex h-9 w-9 items-center justify-center rounded-lg bg-ink/5 text-ink-soft hover:bg-ink/10"
                        title="Ver caso"
                      >
                        <PencilSimple className="h-4 w-4" />
                      </Link>
                      <AdminButton
                        variant="ghost"
                        className="!px-3 !py-2 text-xs"
                        disabled={procesando.has(`cambio-${cambio.id}`)}
                        onClick={() => revisarCambio(cambio.id)}
                      >
                        <Check className="h-3.5 w-3.5" />
                        Revisado
                      </AdminButton>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
