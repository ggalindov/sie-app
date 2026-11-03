"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Dialog } from "@base-ui/react/dialog";
import { toast } from "sonner";
import {
  MagnifyingGlass,
  X,
  Buildings,
  User,
  Copy,
  Check,
  Scales,
  Calendar,
  MapPin,
  WarningCircle,
  Lightning,
  Sparkle,
  Plus,
  Funnel,
} from "@phosphor-icons/react";
import {
  obtenerCuotaCroma,
  buscarProcesosCroma,
  ApiError,
  type CromaEstadoCuota,
  type CromaCaso,
} from "@/lib/admin-api";
import { AdminButton, Badge } from "@/components/admin/ui";
import { formatearFechaBogota } from "@/lib/fecha";

export interface DatosCasoDesdeCroma {
  nombreCliente: string;
  radicadoId: string;
  despacho?: string;
  departamento?: string;
  partes?: string;
}

export function BusquedaCromaModal({
  abierto,
  onClose,
  radicadosExistentes,
  onSeleccionarRadicado,
  onCrearCaso,
}: {
  abierto: boolean;
  onClose: () => void;
  radicadosExistentes?: Set<string>;
  onSeleccionarRadicado?: (radicado: string, despacho?: string) => void;
  onCrearCaso?: (datos: DatosCasoDesdeCroma) => void;
}) {
  const [cuota, setCuota] = useState<CromaEstadoCuota | null>(null);
  const [nombre, setNombre] = useState("");
  const [tipoEntidad, setTipoEntidad] = useState<"natural" | "juridical">("natural");
  const [soloActivos, setSoloActivos] = useState(false);
  const [buscando, setBuscando] = useState(false);
  const [casos, setCasos] = useState<CromaCaso[] | null>(null);
  const [copiadoRadicado, setCopiadoRadicado] = useState<string | null>(null);

  const cargarCuota = useCallback(() => {
    obtenerCuotaCroma()
      .then(setCuota)
      .catch(() => {
        // Silencioso ante fallo de carga inicial
      });
  }, []);

  useEffect(() => {
    if (abierto) {
      cargarCuota();
    }
  }, [abierto, cargarCuota]);

  async function onBuscar(e: FormEvent) {
    e.preventDefault();
    const query = nombre.trim();
    if (query.length < 3) {
      toast.error("Ingresa al menos 3 caracteres para buscar.");
      return;
    }

    if (cuota && cuota.creditosConsumidos >= cuota.limiteCreditos) {
      toast.warning(
        `Límite mensual gratuito de ${cuota.limiteCreditos} créditos alcanzado. Se renovará el 1 del próximo mes.`,
      );
      return;
    }

    setBuscando(true);
    setCasos(null);

    try {
      const res = await buscarProcesosCroma({
        nombre: query,
        tipoEntidad,
        soloActivos,
      });

      if (res.exito) {
        setCasos(res.casos);
        if (res.casos.length === 0) {
          toast.info("No se encontraron procesos judiciales con ese criterio.");
        } else {
          toast.success(`Se encontraron ${res.casos.length} procesos en la Rama Judicial.`);
        }
      } else {
        toast.error(res.mensaje);
      }

      // Refrescar cuota tras la consulta
      cargarCuota();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Error al consultar Croma API.");
      cargarCuota();
    } finally {
      setBuscando(false);
    }
  }

  function copiarRadicado(rad: string) {
    navigator.clipboard.writeText(rad);
    setCopiadoRadicado(rad);
    toast.success("Radicado de 23 dígitos copiado al portapapeles.");
    setTimeout(() => setCopiadoRadicado(null), 2500);
  }

  function formatearFecha(iso: string | null) {
    if (!iso) return "—";
    return formatearFechaBogota(iso);
  }

  const porcentajeUsado = cuota
    ? Math.min(100, Math.round((cuota.creditosConsumidos / Math.max(1, cuota.limiteCreditos)) * 100))
    : 0;

  return (
    <Dialog.Root open={abierto} onOpenChange={(ab) => !ab && onClose()}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-ink/60 backdrop-blur-sm" />
        <Dialog.Popup className="fixed left-1/2 top-1/2 z-50 max-h-[90vh] w-full max-w-2xl -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-2xl bg-surface p-6 shadow-2xl ring-1 ring-line">
          {/* Header */}
          <div className="flex items-center justify-between border-b border-line pb-4">
            <div className="flex items-center gap-2.5">
              <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-gold/15 text-gold-deep ring-1 ring-gold/30">
                <Sparkle className="h-5 w-5" weight="fill" />
              </div>
              <div>
                <Dialog.Title className="font-display text-lg font-semibold text-ink">
                  Búsqueda Legal-Tech en la Rama Judicial
                </Dialog.Title>
                <Dialog.Description className="text-xs text-ink-soft">
                  Encuentra procesos por Cédula, NIT o Nombre cuando un cliente nuevo no conoce su radicado (Croma API).
                </Dialog.Description>
              </div>
            </div>
            <Dialog.Close className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5">
              <X className="h-4 w-4" />
            </Dialog.Close>
          </div>

          {/* Banner y Barra de Cuota Mensual Orquestada (4,500 créditos en 3 Keys) */}
          <div className="mt-4 rounded-xl border border-line bg-paper px-4 py-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex flex-wrap items-center gap-2">
                <Lightning className="h-4 w-4 text-gold-deep" weight="fill" />
                <span className="text-xs font-semibold text-ink">Orquestador Croma ($0 USD):</span>
                <span className="text-xs text-ink-soft">
                  {cuota
                    ? `${cuota.consultasRestantes} consultas disponibles (${cuota.creditosRestantes} / ${cuota.limiteCreditos} créditos)`
                    : "Cargando saldo..."}
                </span>
                {cuota?.totalKeysConfiguradas && cuota.totalKeysConfiguradas > 1 && (
                  <span className="rounded-full bg-gold-pale/60 px-2 py-0.5 text-[10px] font-bold text-gold-deep ring-1 ring-gold-deep/20">
                    Key #{cuota.keyActivaIndice ?? 1} de {cuota.totalKeysConfiguradas} ({cuota.creditosConsumidosKeyActiva ?? 0}/{cuota.creditosPorKey ?? 1500} cr.)
                  </span>
                )}
              </div>
              {cuota && cuota.creditosRestantes <= 0 ? (
                <Badge tone="danger">Cuota del mes agotada</Badge>
              ) : (
                <Badge tone="gold">{cuota ? `${cuota.creditosPorConsulta} créditos / búsqueda` : "10 créditos / búsqueda"}</Badge>
              )}
            </div>
            {cuota && (
              <div className="mt-2.5">
                <div className="h-1.5 w-full overflow-hidden rounded-full bg-ink/10">
                  <div
                    className={`h-full rounded-full transition-all ${
                      porcentajeUsado >= 90 ? "bg-red-500" : porcentajeUsado >= 70 ? "bg-amber-500" : "bg-gold-deep"
                    }`}
                    style={{ width: `${porcentajeUsado}%` }}
                  />
                </div>
                <div className="mt-1 flex justify-between text-[11px] text-ink-soft/70">
                  <span>Período: {cuota.mesAno} ({cuota.consultasRealizadas} búsquedas realizadas)</span>
                  <span>Límite estricto: {cuota.creditosConsumidos} / {cuota.limiteCreditos} créditos</span>
                </div>
              </div>
            )}
          </div>

          {cuota && !cuota.tieneApiKeyConfigurada && (
            <div className="mt-3 flex items-start gap-2.5 rounded-xl border border-amber-500/30 bg-amber-500/10 p-3 text-xs text-ink">
              <WarningCircle className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" weight="fill" />
              <div>
                <p className="font-semibold text-ink">API Key de Croma pendiente de configurar</p>
                <p className="mt-0.5 text-ink-soft">
                  El módulo está 100% activo y protegido con el tope de 1.500 créditos/mes. Para empezar a consultar en vivo, obtén tu llave gratuita en{" "}
                  <a href="https://usecroma.com" target="_blank" rel="noreferrer" className="font-semibold text-gold-deep underline">
                    usecroma.com
                  </a>{" "}
                  y colócala en <code className="rounded bg-ink/10 px-1 py-0.5 font-mono">CROMA_API_KEY</code> (en <code className="rounded bg-ink/10 px-1 py-0.5 font-mono">run.local.bat</code> o variables de entorno).
                </p>
              </div>
            </div>
          )}

          {/* Formulario de búsqueda */}
          <form onSubmit={onBuscar} className="mt-5 space-y-3.5">
            <div>
              <label className="mb-1 block text-xs font-medium text-ink-soft">
                Nombre completo, Cédula, NIT o Razón Social
              </label>
              <div className="relative">
                <input
                  type="text"
                  value={nombre}
                  onChange={(e) => setNombre(e.target.value)}
                  placeholder="Ej: Carlos Alberto Gómez, 1020304050 o Bancolombia S.A."
                  className="w-full rounded-xl border border-line bg-paper py-2.5 pl-10 pr-4 text-sm text-ink outline-none placeholder:text-ink-soft/50 focus:border-gold focus:ring-1 focus:ring-gold"
                />
                <MagnifyingGlass className="absolute left-3.5 top-3 h-4 w-4 text-ink-soft" />
              </div>
            </div>

            <div className="flex flex-wrap items-center justify-between gap-3 pt-1">
              {/* Tipo de entidad */}
              <div className="flex items-center gap-1.5 rounded-xl border border-line bg-paper p-1">
                <button
                  type="button"
                  onClick={() => setTipoEntidad("natural")}
                  className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium transition-all ${
                    tipoEntidad === "natural"
                      ? "bg-gold text-ink-fixed shadow-sm"
                      : "text-ink-soft hover:text-ink"
                  }`}
                >
                  <User className="h-3.5 w-3.5" weight="bold" />
                  Persona Natural
                </button>
                <button
                  type="button"
                  onClick={() => setTipoEntidad("juridical")}
                  className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium transition-all ${
                    tipoEntidad === "juridical"
                      ? "bg-gold text-ink-fixed shadow-sm"
                      : "text-ink-soft hover:text-ink"
                  }`}
                >
                  <Buildings className="h-3.5 w-3.5" weight="bold" />
                  Persona Jurídica (Empresa)
                </button>
              </div>

              {/* Checkbox solo activos */}
              <label className="flex cursor-pointer select-none items-center gap-2 text-xs text-ink-soft hover:text-ink">
                <input
                  type="checkbox"
                  checked={soloActivos}
                  onChange={(e) => setSoloActivos(e.target.checked)}
                  className="h-3.5 w-3.5 rounded border-line bg-paper text-gold focus:ring-gold"
                />
                Solo activos (últimos 30 días)
              </label>

              {/* Botón de búsqueda */}
              <AdminButton
                type="submit"
                variant="primary"
                disabled={buscando || !nombre.trim() || (cuota !== null && cuota.creditosRestantes <= 0)}
              >
                <MagnifyingGlass className="h-4 w-4" weight="bold" />
                {buscando ? "Consultando Croma..." : "Buscar procesos"}
              </AdminButton>
            </div>
          </form>

          {/* Resultados de la búsqueda */}
          <div className="mt-6 border-t border-line pt-4">
            {buscando ? (
              <div className="py-10 text-center">
                <div className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-gold border-t-transparent" />
                <p className="mt-2 text-xs text-ink-soft">Consultando la Rama Judicial a través de Croma API...</p>
              </div>
            ) : casos === null ? (
              <div className="py-8 text-center text-xs text-ink-soft/70">
                Ingresa el nombre, cédula o razón social y presiona &quot;Buscar procesos&quot; para consultar.
              </div>
            ) : casos.length === 0 ? (
              <div className="py-8 text-center text-xs text-ink-soft">
                No se encontraron procesos judiciales asociados a ese criterio de búsqueda.
              </div>
            ) : (
              <div className="space-y-3">
                <p className="text-xs font-medium text-ink-soft">
                  {casos.length} {casos.length === 1 ? "proceso encontrado" : "procesos encontrados"}:
                </p>

                {casos.map((caso, idx) => {
                  const yaExisteEnSie = radicadosExistentes?.has(caso.radicado) ?? false;
                  return (
                    <div
                      key={`${caso.radicado}-${idx}`}
                      className="rounded-xl border border-line bg-paper p-3.5 transition-all hover:border-gold/40"
                    >
                      <div className="flex flex-wrap items-start justify-between gap-2">
                        <div>
                          <div className="flex flex-wrap items-center gap-2">
                            <Scales className="h-4 w-4 text-gold-deep" weight="fill" />
                            <span className="font-mono text-xs font-bold text-ink">
                              {caso.radicado}
                            </span>
                            {yaExisteEnSie && (
                              <Badge tone="success">Ya registrado en SIE</Badge>
                            )}
                          </div>
                          <p className="mt-1 text-xs font-medium text-ink">{caso.despacho || "Despacho no reportado"}</p>
                        </div>

                        <div className="flex flex-wrap items-center gap-1.5">
                          <button
                            type="button"
                            onClick={() => copiarRadicado(caso.radicado)}
                            className="flex items-center gap-1 rounded-lg border border-line bg-surface px-2.5 py-1 text-[11px] font-medium text-ink-soft transition-all hover:bg-gold/10 hover:text-gold-deep"
                            title="Copiar radicado de 23 dígitos"
                          >
                            {copiadoRadicado === caso.radicado ? (
                              <>
                                <Check className="h-3 w-3 text-emerald-500" weight="bold" />
                                <span className="text-emerald-500">Copiado</span>
                              </>
                            ) : (
                              <>
                                <Copy className="h-3 w-3" />
                                <span>Copiar</span>
                              </>
                            )}
                          </button>

                          {yaExisteEnSie && onSeleccionarRadicado && (
                            <button
                              type="button"
                              onClick={() => {
                                onSeleccionarRadicado(caso.radicado, caso.despacho);
                                onClose();
                              }}
                              className="flex items-center gap-1 rounded-lg border border-line bg-surface px-2.5 py-1 text-[11px] font-semibold text-ink transition-all hover:bg-ink/5"
                            >
                              <Funnel className="h-3 w-3" weight="bold" />
                              <span>Ver en mis casos</span>
                            </button>
                          )}

                          {!yaExisteEnSie && onCrearCaso && (
                            <button
                              type="button"
                              onClick={() => {
                                onCrearCaso({
                                  nombreCliente: nombre.trim(),
                                  radicadoId: caso.radicado,
                                  despacho: caso.despacho,
                                  departamento: caso.departamento,
                                  partes: caso.partes,
                                });
                                onClose();
                              }}
                              className="flex items-center gap-1 rounded-lg bg-gold px-2.5 py-1 text-[11px] font-semibold text-ink-fixed transition-all hover:bg-gold-deep"
                            >
                              <Plus className="h-3 w-3" weight="bold" />
                              <span>Crear caso</span>
                            </button>
                          )}

                          {!yaExisteEnSie && !onCrearCaso && onSeleccionarRadicado && (
                            <button
                              type="button"
                              onClick={() => {
                                onSeleccionarRadicado(caso.radicado, caso.despacho);
                                onClose();
                              }}
                              className="flex items-center gap-1 rounded-lg bg-gold px-2.5 py-1 text-[11px] font-semibold text-ink-fixed transition-all hover:bg-gold-deep"
                            >
                              <Plus className="h-3 w-3" weight="bold" />
                              <span>Usar radicado</span>
                            </button>
                          )}
                        </div>
                      </div>

                      {caso.partes && (
                        <p className="mt-2 text-xs text-ink-soft">
                          <strong className="text-ink">Partes:</strong> {caso.partes}
                        </p>
                      )}

                      <div className="mt-2.5 flex flex-wrap items-center gap-4 border-t border-line/60 pt-2 text-[11px] text-ink-soft/80">
                        {caso.departamento && (
                          <span className="flex items-center gap-1">
                            <MapPin className="h-3 w-3" /> {caso.departamento}
                          </span>
                        )}
                        {caso.fechaInicio && (
                          <span className="flex items-center gap-1">
                            <Calendar className="h-3 w-3" /> Radicado: {formatearFecha(caso.fechaInicio)}
                          </span>
                        )}
                        {caso.fechaUltimaActuacion && (
                          <span className="flex items-center gap-1 font-medium text-gold-deep">
                            Última actuación: {formatearFecha(caso.fechaUltimaActuacion)}
                          </span>
                        )}
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
