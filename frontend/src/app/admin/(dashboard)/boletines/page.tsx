"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
  Download,
  MagnifyingGlass,
  FilePdf,
  CheckCircle,
  FolderOpen,
  CaretDown,
  CaretUp,
  CalendarBlank,
} from "@phosphor-icons/react";
import {
  listarBoletinesJudiciales,
  descargarBoletin,
  limpiarBoletinesNoCoincidentes,
  ApiError,
  type BoletinJudicial,
} from "@/lib/admin-api";
import { BoletinesProgreso } from "@/components/admin/boletines-progreso";
import { AdminPageHeader, AdminCard, AdminButton, Badge, EmptyState, AdminLoader } from "@/components/admin/ui";
import { formatearFechaBogota, formatearFechaHoraBogota, timestampUtcDesdeBogota } from "@/lib/fecha";

const formatearFechaHora = formatearFechaHoraBogota;

function formatearFechaEmision(iso?: string | null): string {
  if (!iso) return "";
  if (iso.endsWith("T00:00:00") || iso.endsWith("T00:00") || !iso.includes("T")) {
    return formatearFechaBogota(iso);
  }
  return formatearFechaHoraBogota(iso);
}

function diasParaVencer(fechaExpiracion: string): number {
  const ms = timestampUtcDesdeBogota(fechaExpiracion) - Date.now();
  return Math.max(0, Math.ceil(ms / (1000 * 60 * 60 * 24)));
}

interface GrupoCasoBoletines {
  casoId: number;
  nombreCliente: string;
  radicadoId: string | null;
  ultimaDescarga: string;
  ultimaDescargaMs: number;
  boletines: BoletinJudicial[];
}

// Pedido explícito del usuario: "el sistema debe de traer y darle la opcion de todos los
// casos de ver/descargar todos los boletines que esten disponibles en las ramas o donde
// esten" -- agrupados por caso para mejorar su visibilidad.
export default function BoletinesPage() {
  const [boletines, setBoletines] = useState<BoletinJudicial[] | null>(null);
  const [busqueda, setBusqueda] = useState("");
  const [procesandoId, setProcesandoId] = useState<number | null>(null);
  const [casosColapsados, setCasosColapsados] = useState<Record<number, boolean>>({});
  const [limpiando, setLimpiando] = useState(false);

  const cargar = useCallback(() => {
    listarBoletinesJudiciales()
      .then(setBoletines)
      .catch(() => toast.error("No se pudieron cargar los boletines."));
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  async function onDescargar(b: BoletinJudicial) {
    setProcesandoId(b.id);
    try {
      await descargarBoletin(b.id, b.nombreArchivo);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo descargar el documento.");
    } finally {
      setProcesandoId(null);
    }
  }

  const noCoincidentesCount = (boletines ?? []).filter((b) => !b.coincideNombreActuacion).length;

  // Pedido explícito del usuario (2026-11-05, caso 553/radicado 11001418907820250083400):
  // "haz un recorrido y identifica tu los documentos no correctos y eliminalos" -- borra solo
  // los que el propio sistema ya marcó como no confiables (ver
  // BoletinJudicialCacheService.descargarYGuardarCandidatos, que desde ahora ya no vuelve a
  // cachear estos). Nunca toca los de coincidencia confirmada.
  async function onLimpiarNoCoincidentes() {
    if (!window.confirm(`Se van a borrar ${noCoincidentesCount} documento(s) que el sistema marcó como no confiables (no coinciden con el nombre de la actuación). Los demás no se tocan. ¿Continuar?`)) {
      return;
    }
    setLimpiando(true);
    try {
      const borrados = await limpiarBoletinesNoCoincidentes();
      toast.success(`Se borraron ${borrados} documento(s) mal emparejados.`);
      cargar();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo completar la limpieza.");
    } finally {
      setLimpiando(false);
    }
  }

  const busquedaNormalizada = busqueda.trim().toLowerCase();
  const boletinesFiltrados = useMemo(() => {
    return (boletines ?? []).filter((b) => {
      if (!busquedaNormalizada) return true;
      return (
        b.nombreCliente.toLowerCase().includes(busquedaNormalizada) ||
        (b.radicadoId?.toLowerCase().includes(busquedaNormalizada) ?? false) ||
        b.nombreArchivo.toLowerCase().includes(busquedaNormalizada)
      );
    });
  }, [boletines, busquedaNormalizada]);

  const gruposPorCaso = useMemo<GrupoCasoBoletines[]>(() => {
    const mapa = new Map<number, GrupoCasoBoletines>();
    for (const b of boletinesFiltrados) {
      const descargaMs = timestampUtcDesdeBogota(b.fechaDescarga);
      const existente = mapa.get(b.casoId);
      if (!existente) {
        mapa.set(b.casoId, {
          casoId: b.casoId,
          nombreCliente: b.nombreCliente,
          radicadoId: b.radicadoId,
          ultimaDescarga: b.fechaDescarga,
          ultimaDescargaMs: descargaMs,
          boletines: [b],
        });
      } else {
        existente.boletines.push(b);
        if (descargaMs > existente.ultimaDescargaMs) {
          existente.ultimaDescargaMs = descargaMs;
          existente.ultimaDescarga = b.fechaDescarga;
        }
      }
    }
    const lista = Array.from(mapa.values());
    for (const grupo of lista) {
      grupo.boletines.sort(
        (a, b) => timestampUtcDesdeBogota(b.fechaDescarga) - timestampUtcDesdeBogota(a.fechaDescarga)
      );
    }
    lista.sort((a, b) => b.ultimaDescargaMs - a.ultimaDescargaMs);
    return lista;
  }, [boletinesFiltrados]);

  function alternarCaso(casoId: number) {
    setCasosColapsados((prev) => ({
      ...prev,
      [casoId]: !prev[casoId],
    }));
  }

  function expandirTodos() {
    setCasosColapsados({});
  }

  function contraerTodos() {
    const nuevo: Record<number, boolean> = {};
    for (const g of gruposPorCaso) {
      nuevo[g.casoId] = true;
    }
    setCasosColapsados(nuevo);
  }

  return (
    <div>
      {/* Pedido explícito del usuario (2026-11-01): "quita todo boton que haga que dispare
          el ciclo manualmente, el ciclo sera permanentemente automatizado" -- se quitó
          "Buscar boletines nuevos ahora" (disparaba el ciclo completo de
          VigilanciaExternaOrquestador). La búsqueda corre sola, una vez al día, por el
          cron nocturno (ver app.vigilancia.ciclo-nocturno-cron) -- sin ningún botón que
          pueda volver a disparar ese ciclo fuera de horario. */}
      <AdminPageHeader
        title="Boletines"
        description="Todos los documentos oficiales (autos, providencias) que el sistema ya encontró y descargó de Publicaciones Procesales del Estado, agrupados por expediente judicial. Se guardan 30 días y luego se borran solos. La búsqueda corre sola, una vez al día, a las 5:15 a. m. (hora Colombia)."
      />

      {noCoincidentesCount > 0 && (
        <div className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-300 bg-amber-50 px-4 py-3">
          <p className="text-sm text-amber-900">
            <strong>{noCoincidentesCount}</strong> documento{noCoincidentesCount === 1 ? "" : "s"} no coincide
            {noCoincidentesCount === 1 ? "" : "n"} con el nombre de la actuación (el sistema ya detectó que probablemente
            no son del caso al que están atribuidos).
          </p>
          <AdminButton variant="secondary" onClick={onLimpiarNoCoincidentes} disabled={limpiando}>
            {limpiando ? "Limpiando..." : "Limpiar no coincidentes"}
          </AdminButton>
        </div>
      )}

      <BoletinesProgreso onAvance={cargar} />

      {boletines !== null && boletines.length > 0 && (
        <div className="relative mt-6">
          <MagnifyingGlass
            weight="bold"
            className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-soft"
          />
          <input
            type="text"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            placeholder="Buscar por cliente, radicado o nombre de documento..."
            className="w-full rounded-full border border-line bg-surface py-2.5 pl-11 pr-4 text-sm text-ink placeholder:text-ink-soft focus:border-gold-deep focus:outline-none"
          />
        </div>
      )}

      {boletines === null ? (
        <AdminLoader />
      ) : boletines.length === 0 ? (
        <EmptyState
          title="Todavía no hay boletines cacheados"
          description="El ciclo automático corre una vez al día, a las 5:15 a. m. -- vuelve a revisar mañana."
        />
      ) : boletinesFiltrados.length === 0 ? (
        <EmptyState title="Sin resultados" description="Ningún boletín coincide con la búsqueda actual." />
      ) : (
        <>
          <div className="mb-3 mt-6 flex flex-wrap items-center justify-between gap-2">
            <p className="text-xs font-semibold uppercase tracking-wide text-ink-soft/70">
              {gruposPorCaso.length} caso{gruposPorCaso.length === 1 ? "" : "s"} con boletines ·{" "}
              {boletinesFiltrados.length} de {boletines.length} boletín{boletines.length === 1 ? "" : "es"} disponible
              {boletines.length === 1 ? "" : "s"}
            </p>
            {gruposPorCaso.length > 1 && (
              <div className="flex items-center gap-2 text-xs">
                <button
                  type="button"
                  onClick={expandirTodos}
                  className="rounded-full border border-line bg-surface px-3 py-1 font-medium text-ink-soft transition hover:border-gold-deep hover:text-ink"
                >
                  Expandir todos
                </button>
                <button
                  type="button"
                  onClick={contraerTodos}
                  className="rounded-full border border-line bg-surface px-3 py-1 font-medium text-ink-soft transition hover:border-gold-deep hover:text-ink"
                >
                  Contraer todos
                </button>
              </div>
            )}
          </div>

          <div className="space-y-4">
            {gruposPorCaso.map((grupo) => {
              const colapsado = Boolean(casosColapsados[grupo.casoId]);
              const coincidencias = grupo.boletines.filter((b) => b.coincideNombreActuacion).length;

              return (
                <AdminCard key={grupo.casoId} className="overflow-hidden p-0">
                  {/* Cabecera del Caso */}
                  <button
                    type="button"
                    onClick={() => alternarCaso(grupo.casoId)}
                    className="flex w-full flex-col gap-3 bg-surface-soft/50 px-5 py-4 text-left transition hover:bg-gold-pale/20 sm:flex-row sm:items-center sm:justify-between"
                  >
                    <div className="flex min-w-0 items-start gap-3.5 sm:items-center">
                      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-gold-pale/70 text-gold-deep">
                        <FolderOpen className="h-5 w-5" weight="bold" />
                      </span>
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <h3 className="text-base font-semibold text-ink">{grupo.nombreCliente}</h3>
                          <Badge tone="gold">
                            {grupo.boletines.length} boletín{grupo.boletines.length === 1 ? "" : "es"}
                          </Badge>
                          {coincidencias > 0 && (
                            <Badge tone="success">
                              <CheckCircle className="h-3 w-3" weight="bold" /> {coincidencias} coincide
                              {coincidencias === 1 ? "" : "n"} con actuación
                            </Badge>
                          )}
                        </div>
                        <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-ink-soft">
                          {grupo.radicadoId ? (
                            <span className="rounded bg-gold-pale/40 px-2 py-0.5 font-mono text-xs font-medium tracking-wider text-gold-deep">
                              Radicado: {grupo.radicadoId}
                            </span>
                          ) : (
                            <span className="italic text-ink-soft/70">Sin radicado asignado</span>
                          )}
                          <span className="text-[11px] text-ink-soft/70">
                            Última descarga: {formatearFechaHora(grupo.ultimaDescarga)}
                          </span>
                        </div>
                      </div>
                    </div>

                    <div className="flex shrink-0 items-center gap-2 self-end sm:self-center">
                      <span className="text-xs font-medium text-ink-soft">
                        {colapsado ? "Ver documentos" : "Ocultar"}
                      </span>
                      <span className="flex h-7 w-7 items-center justify-center rounded-full border border-line bg-surface text-ink-soft">
                        {colapsado ? (
                          <CaretDown className="h-4 w-4" weight="bold" />
                        ) : (
                          <CaretUp className="h-4 w-4" weight="bold" />
                        )}
                      </span>
                    </div>
                  </button>

                  {/* Lista de Boletines del Caso */}
                  {!colapsado && (
                    <div className="divide-y divide-line/60 border-t border-line/70 bg-surface">
                      {grupo.boletines.map((b) => {
                        const procesando = procesandoId === b.id;
                        const dias = diasParaVencer(b.fechaExpiracion);
                        return (
                          <div
                            key={b.id}
                            className="flex flex-col gap-3 px-5 py-3.5 transition hover:bg-surface-soft/30 sm:flex-row sm:items-center sm:justify-between"
                          >
                            <div className="flex min-w-0 items-start gap-3">
                              <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gold-pale/45 text-gold-deep">
                                <FilePdf className="h-4 w-4" weight="bold" />
                              </span>
                              <div className="min-w-0">
                                <div className="flex flex-wrap items-center gap-2">
                                  <p className="truncate text-sm font-medium text-ink">{b.nombreArchivo}</p>
                                  {b.coincideNombreActuacion && (
                                    <Badge tone="success">
                                      <CheckCircle className="h-3 w-3" weight="bold" /> Coincide con la actuación
                                    </Badge>
                                  )}
                                  {b.tipoActuacion && (
                                    <span className="rounded bg-surface-soft px-2 py-0.5 text-[11px] font-medium text-ink-soft ring-1 ring-line">
                                      Actuación: {b.tipoActuacion}
                                    </span>
                                  )}
                                </div>
                                <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1.5 text-[11px] text-ink-soft">
                                  {b.fechaEmision && (
                                    <span className="inline-flex items-center gap-1.5 rounded-md bg-gold-pale/80 px-2.5 py-0.5 font-semibold text-gold-deep ring-1 ring-gold-deep/20 shadow-xs">
                                      <CalendarBlank className="h-3.5 w-3.5" weight="bold" />
                                      Emitido / Subido: {formatearFechaEmision(b.fechaEmision)}
                                    </span>
                                  )}
                                  <span className="text-ink-soft/75">
                                    Descargado: {formatearFechaHora(b.fechaDescarga)} · se borra en {dias} día
                                    {dias === 1 ? "" : "s"}
                                  </span>
                                </div>
                              </div>
                            </div>

                            <div className="flex shrink-0 gap-2 self-end sm:self-center">
                              {/* Pedido explícito del usuario (2026-11-05): "el ver boletin no esta
                                  sirviendo... deja solo el descargar" -- estos PDFs del canal
                                  heurístico pueden pesar 10+ MB (el boletín completo del despacho,
                                  no un documento aislado), y abrirlos vía blob URL en una pestaña
                                  en blanco falla en Chrome con archivos de ese tamaño. La descarga
                                  sí es confiable sin importar el peso. */}
                              <AdminButton variant="secondary" onClick={() => onDescargar(b)} disabled={procesando}>
                                <Download className="h-4 w-4" weight="bold" />
                                Descargar
                              </AdminButton>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </AdminCard>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}
