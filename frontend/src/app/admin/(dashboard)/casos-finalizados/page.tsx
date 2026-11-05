"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import {
  Archive,
  ArrowCounterClockwise,
  ArrowsClockwise,
  ClockCounterClockwise,
  MagnifyingGlass,
  X,
} from "@phosphor-icons/react";
import { listarCasosFinalizados, reabrirCaso, ApiError, type CasoAdmin } from "@/lib/admin-api";
import { AdminPageHeader, AdminCard, Badge, EmptyState, AdminLoader } from "@/components/admin/ui";
import { SeguimientoDeCasoModal, type CasoParaSeguimiento } from "@/components/admin/seguimiento-de-caso-modal";
import { formatearFechaBogota, formatearFechaHoraBogota } from "@/lib/fecha";

const formatearFecha = formatearFechaBogota;
const formatearFechaHora = formatearFechaHoraBogota;

// Pedido explícito del usuario (2026-11-05): "el caso se remite a un nuevo apartado de que
// diga casos finalizados para guardarlo en el sistema y guarde todo el ultimo seguimiento
// pero ya una vez finalizado el sistema ya no le hara vigilancia". Esta pantalla es de solo
// lectura sobre lo que ya quedó guardado -- nunca vuelve a sincronizarse con la Rama Judicial
// (ver CasoRepository.findByRadicadoIdIsNotNullAndFinalizadoFalse en el backend).
export default function CasosFinalizadosPage() {
  const [casos, setCasos] = useState<CasoAdmin[] | null>(null);
  const [cargando, setCargando] = useState(false);
  const [busqueda, setBusqueda] = useState("");
  const [reabriendoId, setReabriendoId] = useState<number | null>(null);
  const [casoConSeguimientoAbierto, setCasoConSeguimientoAbierto] = useState<CasoParaSeguimiento | null>(null);

  const cargar = useCallback(() => {
    setCargando(true);
    listarCasosFinalizados()
      .then(setCasos)
      .catch((err) => {
        toast.error(err instanceof ApiError ? err.message : "No se pudieron cargar los casos finalizados.");
      })
      .finally(() => setCargando(false));
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  async function onReabrir(caso: CasoAdmin) {
    if (!window.confirm(`¿Reabrir el caso de ${caso.nombreCliente}? Vuelve a "Casos" y retoma la vigilancia judicial automática.`)) {
      return;
    }
    setReabriendoId(caso.id);
    try {
      await reabrirCaso(caso.id);
      setCasos((prev) => (prev ? prev.filter((c) => c.id !== caso.id) : prev));
      toast.success(`Caso de ${caso.nombreCliente} reabierto -- vuelve a Casos activos.`);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo reabrir el caso.");
    } finally {
      setReabriendoId(null);
    }
  }

  const q = busqueda.trim().toLowerCase();
  const casosFiltrados = (casos ?? []).filter((c) => {
    if (!q) return true;
    return (
      c.nombreCliente.toLowerCase().includes(q) ||
      (c.radicadoId && c.radicadoId.toLowerCase().includes(q))
    );
  });

  return (
    <div>
      <AdminPageHeader
        title="Casos Finalizados"
        description="Expedientes cerrados por decisión del equipo. Se conserva todo el último seguimiento tal como quedó, pero el sistema ya no vuelve a consultar la Rama Judicial para estos casos. Puedes reabrir uno en cualquier momento si hace falta."
      />

      <div className="mt-6 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-line bg-surface p-4">
        <div className="relative min-w-[280px] flex-1 max-w-md">
          <MagnifyingGlass className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-soft" />
          <input
            type="text"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            placeholder="Buscar por cliente o radicado..."
            className="w-full rounded-xl border border-line bg-paper py-2 pl-9 pr-4 text-sm text-ink placeholder:text-ink-soft/70 focus:border-gold-deep focus:outline-none"
          />
          {busqueda && (
            <button
              type="button"
              onClick={() => setBusqueda("")}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-soft hover:text-ink"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          )}
        </div>
        <button
          type="button"
          onClick={cargar}
          disabled={cargando}
          className="inline-flex items-center gap-1.5 rounded-xl border border-line bg-paper px-3 py-2 text-xs font-medium text-ink-soft hover:bg-ink/5 disabled:opacity-50"
        >
          <ArrowsClockwise className={`h-3.5 w-3.5 ${cargando ? "animate-spin" : ""}`} />
          Actualizar
        </button>
      </div>

      {casos === null ? (
        <div className="mt-8 flex justify-center">
          <AdminLoader />
        </div>
      ) : casosFiltrados.length === 0 ? (
        <div className="mt-8">
          <EmptyState
            title={busqueda ? "Sin coincidencias" : "Todavía no hay casos finalizados"}
            description={
              busqueda
                ? "Ningún caso finalizado coincide con el filtro ingresado."
                : "Cuando finalices un caso desde /admin/casos, aparecerá aquí con todo su último seguimiento guardado."
            }
          />
        </div>
      ) : (
        <div className="mt-6 space-y-4">
          {casosFiltrados.map((c) => (
            <AdminCard key={c.id} className="!p-5 opacity-90">
              <div className="grid gap-5 lg:grid-cols-[1fr_1fr_220px]">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="inline-flex items-center gap-1 rounded-full bg-ink/80 px-2.5 py-0.5 text-[11px] font-bold text-paper">
                      <Archive className="h-3 w-3" weight="bold" />
                      Finalizado
                    </span>
                    <Badge tone="gold">{c.fuenteVisible}</Badge>
                    {c.radicadoId && <span className="font-mono text-xs text-ink-soft">{c.radicadoId}</span>}
                  </div>
                  <h3 className="mt-2 font-display text-base font-bold text-ink">{c.nombreCliente}</h3>
                  <p className="mt-1 text-xs text-ink-soft">
                    Finalizado {c.fechaFinalizacion ? formatearFechaHora(c.fechaFinalizacion) : ""}
                    {c.finalizadoPorNombre ? ` por ${c.finalizadoPorNombre}` : ""}
                  </p>
                </div>

                <div className="min-w-0 border-t border-line/70 pt-4 lg:border-l lg:border-t-0 lg:pl-6 lg:pt-0">
                  <p className="text-[11px] font-semibold uppercase tracking-wider text-ink-soft">
                    Último seguimiento guardado
                  </p>
                  <p className="mt-1 text-sm font-semibold text-ink">
                    {c.despachoJudicial ?? "Despacho no identificado"}
                  </p>
                  {c.fechaUltimoMovimientoJudicial && (
                    <p className="mt-2 text-xs text-ink-soft">
                      Último movimiento judicial:{" "}
                      <strong className="text-ink font-semibold">
                        {formatearFechaHora(c.fechaUltimoMovimientoJudicial)}
                      </strong>
                    </p>
                  )}
                  <p className="mt-1 text-[11px] text-ink-soft/70">
                    Creado: {formatearFecha(c.fechaCreacion)}
                  </p>
                </div>

                <div className="min-w-0 border-t border-line/70 pt-4 lg:border-l lg:border-t-0 lg:pl-6 lg:pt-0">
                  <div className="space-y-2">
                    <button
                      type="button"
                      onClick={() =>
                        setCasoConSeguimientoAbierto({ id: c.id, nombreCliente: c.nombreCliente, radicadoId: c.radicadoId })
                      }
                      className="flex w-full items-center gap-2 rounded-xl bg-gold-pale/50 px-3 py-2 text-xs font-semibold text-gold-deep ring-1 ring-gold-deep/20 hover:bg-gold-pale transition-colors"
                    >
                      <ClockCounterClockwise className="h-4 w-4" weight="bold" />
                      <span>Ver último seguimiento</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => onReabrir(c)}
                      disabled={reabriendoId === c.id}
                      className="flex w-full items-center justify-center gap-1.5 rounded-xl border border-line bg-paper px-3 py-1.5 text-xs font-medium text-ink hover:bg-ink/5 transition-colors disabled:opacity-50"
                    >
                      <ArrowCounterClockwise className="h-3.5 w-3.5" />
                      <span>{reabriendoId === c.id ? "Reabriendo..." : "Reabrir caso"}</span>
                    </button>
                  </div>
                </div>
              </div>
            </AdminCard>
          ))}
        </div>
      )}

      <SeguimientoDeCasoModal
        caso={casoConSeguimientoAbierto}
        onClose={() => setCasoConSeguimientoAbierto(null)}
      />
    </div>
  );
}
