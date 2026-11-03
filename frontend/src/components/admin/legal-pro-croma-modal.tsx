"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Dialog } from "@base-ui/react/dialog";
import { toast } from "sonner";
import {
  MagnifyingGlass,
  X,
  Buildings,
  Copy,
  Check,
  Scales,
  CalendarBlank,
  MapPin,
  WarningCircle,
  Lightning,
  Sparkle,
  FilePdf,
  DownloadSimple,
  EnvelopeSimple,
  CheckCircle,
  Clock,
  ShieldCheck,
  Funnel,
  ArrowSquareOut,
  ArrowsClockwise,
  Database,
  IdentificationCard,
  UserCircle,
} from "@phosphor-icons/react";
import {
  obtenerCuotaCroma,
  generarInformeLegalProCroma,
  listarMemoriasLegalProCroma,
  obtenerMemoriaLegalProCroma,
  abrirPdfBoletin,
  descargarBoletin,
  ApiError,
  type CasoAdmin,
  type CromaEstadoCuota,
  type CromaLegalProResultado,
  type CromaMemoriaResumen,
} from "@/lib/admin-api";
import { AdminButton, Badge } from "@/components/admin/ui";
import { LegalProWalkingLoader } from "@/components/admin/legal-pro-walking-loader";
import { formatearFechaBogota, formatearFechaHoraBogota } from "@/lib/fecha";

function limpiarPartes(sujetosProcesales: string | null): string[] {
  if (!sujetosProcesales) return [];
  return sujetosProcesales
    .split("|")
    .map((p) => p.replace(/\s*\r?\n\s*/g, " ").replace(/\s+/g, " ").trim())
    .filter(Boolean);
}

export function LegalProCromaModal({
  abierto,
  onClose,
  casos,
  casoInicial,
  onCasoSincronizado,
}: {
  abierto: boolean;
  onClose: () => void;
  casos: CasoAdmin[];
  casoInicial?: CasoAdmin | null;
  onCasoSincronizado?: () => void;
}) {
  const [cuota, setCuota] = useState<CromaEstadoCuota | null>(null);
  const [memorias, setMemorias] = useState<CromaMemoriaResumen[]>([]);
  const [busquedaCaso, setBusquedaCaso] = useState("");
  const [soloConAlerta, setSoloConAlerta] = useState(false);
  const [casoSeleccionado, setCasoSeleccionado] = useState<CasoAdmin | null>(null);
  const [radicadoManual, setRadicadoManual] = useState("");
  const [buscarBoletinesEnVivo, setBuscarBoletinesEnVivo] = useState(true);
  const [cargandoInforme, setCargandoInforme] = useState(false);
  const [cargandoMemoria, setCargandoMemoria] = useState(false);
  const [resultado, setResultado] = useState<CromaLegalProResultado | null>(null);
  const [pestanaActiva, setPestanaActiva] = useState<"actuaciones" | "boletines" | "correos">("actuaciones");
  const [soloFundamentales, setSoloFundamentales] = useState(false);
  const [copiadoTexto, setCopiadoTexto] = useState<string | null>(null);

  const cargarCuotaYMemorias = useCallback(() => {
    obtenerCuotaCroma()
      .then(setCuota)
      .catch(() => {});
    listarMemoriasLegalProCroma()
      .then(setMemorias)
      .catch(() => {});
  }, []);

  const consultarMemoriaDeCaso = useCallback(async (casoObj: CasoAdmin | null, radManual?: string) => {
    const rad = radManual?.trim() || casoObj?.radicadoId || undefined;
    const cid = radManual?.trim() ? undefined : casoObj?.id;
    if (!cid && !rad) {
      setResultado(null);
      return;
    }

    setCargandoMemoria(true);
    try {
      const guardado = await obtenerMemoriaLegalProCroma({ casoId: cid, radicado: rad });
      setResultado(guardado ?? null);
    } catch {
      // Si no hay memoria previa simplemente queda listo para extraer
    } finally {
      setCargandoMemoria(false);
    }
  }, []);

  const abiertoPrevioRef = useRef(false);

  useEffect(() => {
    const recienAbierto = abierto && !abiertoPrevioRef.current;
    abiertoPrevioRef.current = abierto;

    if (!recienAbierto) return;

    cargarCuotaYMemorias();
    if (casoInicial) {
      setCasoSeleccionado(casoInicial);
      setRadicadoManual("");
      void consultarMemoriaDeCaso(casoInicial);
    } else {
      const target =
        casoSeleccionado ?? (casos.length > 0 ? (casos.find((c) => Boolean(c.radicadoId)) ?? casos[0]) : null);
      if (target) {
        setCasoSeleccionado(target);
        void consultarMemoriaDeCaso(target);
      }
    }
  }, [abierto, casoInicial, casos, cargarCuotaYMemorias, casoSeleccionado, consultarMemoriaDeCaso]);

  const mapaMemoriasPorCaso = useMemo(() => {
    const porId = new Map<number, CromaMemoriaResumen>();
    const porRad = new Map<string, CromaMemoriaResumen>();
    for (const m of memorias) {
      if (m.casoId !== null) porId.set(m.casoId, m);
      if (m.radicado) porRad.set(m.radicado, m);
    }
    return { porId, porRad };
  }, [memorias]);

  const casosFiltrados = useMemo(() => {
    const q = busquedaCaso.trim().toLowerCase();
    return casos.filter((c) => {
      if (soloConAlerta) {
        const requiereRescate = !c.tieneProcesoCpnu || c.ultimoSeguimientoExitoso === false || c.radicadoFormatoInvalido;
        if (!requiereRescate) return false;
      }
      if (!q) return true;
      return (
        c.nombreCliente.toLowerCase().includes(q) ||
        (c.radicadoId?.toLowerCase().includes(q) ?? false) ||
        (c.despachoJudicial?.toLowerCase().includes(q) ?? false) ||
        c.fuenteVisible.toLowerCase().includes(q)
      );
    });
  }, [casos, busquedaCaso, soloConAlerta]);

  async function ejecutarLegalPro(casoObj?: CasoAdmin | null, radManual?: string) {
    const targetCaso = casoObj !== undefined ? casoObj : casoSeleccionado;
    const targetRad = radManual !== undefined ? radManual.trim() : radicadoManual.trim();

    if (!targetCaso && !targetRad) {
      toast.error("Selecciona un caso de la lista o escribe un número de radicado.");
      return;
    }

    setCargandoInforme(true);

    try {
      const res = await generarInformeLegalProCroma({
        casoId: targetRad ? undefined : targetCaso?.id,
        radicado: targetRad || targetCaso?.radicadoId || undefined,
        buscarBoletinesEnVivo,
      });

      setPestanaActiva("actuaciones");
      cargarCuotaYMemorias();

      if (res.exito) {
        setResultado(res);
        toast.success(res.mensaje);
        if (res.actuacionesNuevasInyectadasEnSie > 0 || res.boletinesNuevosDescargados > 0 || res.encontradoEnCroma) {
          onCasoSincronizado?.();
        }
      } else {
        toast.error(res.mensaje);
      }
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Error al ejecutar Legal-Pro con Croma.");
      cargarCuotaYMemorias();
    } finally {
      setCargandoInforme(false);
    }
  }

  function copiar(texto: string, etiqueta: string) {
    navigator.clipboard.writeText(texto);
    setCopiadoTexto(texto);
    toast.success(`${etiqueta} copiado al portapapeles.`);
    setTimeout(() => setCopiadoTexto(null), 2200);
  }

  const actuacionesMostradas = useMemo(() => {
    if (!resultado) return [];
    if (!soloFundamentales) return resultado.actuaciones;
    return resultado.actuaciones.filter((a) => a.esFundamental || a.estadoTermino !== null);
  }, [resultado, soloFundamentales]);

  const buzonPrincipal = resultado?.buzonesDespacho?.[0] ?? null;

  return (
    <Dialog.Root open={abierto} onOpenChange={(ab) => !ab && onClose()}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-ink/65 backdrop-blur-sm" />
        <Dialog.Popup className="panel-admin fixed left-1/2 top-1/2 z-50 flex max-h-[92vh] w-[96vw] max-w-6xl -translate-x-1/2 -translate-y-1/2 flex-col overflow-hidden rounded-2xl bg-surface shadow-2xl ring-1 ring-line">
          {/* Encabezado Principal */}
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line bg-paper px-6 py-4">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gold text-ink-fixed shadow-sm">
                <ShieldCheck className="h-5 w-5" weight="fill" />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <Dialog.Title className="font-display text-lg font-semibold text-ink">
                    Legal-Pro · Auditoría y Rescate Profundo de Expediente
                  </Dialog.Title>
                  <Badge tone="gold">Croma API + Memoria 7 Días</Badge>
                </div>
                <Dialog.Description className="text-xs text-ink-soft">
                  Extrae todo el expediente, correos oficiales del juzgado, partes, términos y boletines. El informe queda guardado en memoria por 7 días sin gastar créditos adicionales.
                </Dialog.Description>
              </div>
            </div>

            <div className="flex items-center gap-3">
              {cuota && (
                <div className="hidden sm:flex items-center gap-2 rounded-xl border border-line bg-surface px-3 py-1.5 text-xs">
                  <Lightning className="h-3.5 w-3.5 text-gold-deep" weight="fill" />
                  <span className="font-semibold text-ink">{cuota.consultasRestantes} consultas disponibles</span>
                  <span className="text-ink-soft">({cuota.creditosRestantes}/{cuota.limiteCreditos} cr.)</span>
                  {cuota.totalKeysConfiguradas && cuota.totalKeysConfiguradas > 1 && (
                    <span className="rounded-full bg-gold-pale/60 px-2 py-0.5 text-[10px] font-bold text-gold-deep">
                      Key #{cuota.keyActivaIndice ?? 1}/{cuota.totalKeysConfiguradas}
                    </span>
                  )}
                </div>
              )}
              <Dialog.Close className="flex h-8 w-8 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5">
                <X className="h-4 w-4" />
              </Dialog.Close>
            </div>
          </div>

          {/* Cuerpo en 2 Columnas: Selector de Casos (Izquierda) + Dossier Legal-Pro (Derecha) */}
          <div className="grid min-h-0 flex-1 grid-cols-1 divide-y divide-line overflow-hidden lg:grid-cols-12 lg:divide-x lg:divide-y-0">
            {/* Columna Izquierda: Lista y Buscador de Casos de SIE */}
            <div className="flex flex-col overflow-hidden bg-paper/60 lg:col-span-4">
              <div className="border-b border-line p-4 space-y-2.5">
                <div className="relative">
                  <MagnifyingGlass className="pointer-events-none absolute left-3.5 top-2.5 h-4 w-4 text-ink-soft" />
                  <input
                    type="text"
                    value={busquedaCaso}
                    onChange={(e) => setBusquedaCaso(e.target.value)}
                    placeholder="Buscar caso por cliente, radicado o fuente..."
                    className="w-full rounded-xl border border-line bg-surface py-2 pl-9 pr-3 text-xs text-ink placeholder:text-ink-soft/60 focus:border-gold focus:outline-none"
                  />
                </div>

                <div className="flex items-center justify-between gap-2">
                  <span className="text-[11px] font-semibold uppercase tracking-wide text-ink-soft/70">
                    {casosFiltrados.length} de {casos.length} casos
                  </span>
                  <button
                    type="button"
                    onClick={() => setSoloConAlerta((v) => !v)}
                    className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-[11px] font-medium transition-colors ${
                      soloConAlerta
                        ? "bg-amber-500/20 text-amber-800 ring-1 ring-amber-500/40"
                        : "bg-ink/5 text-ink-soft hover:bg-ink/10"
                    }`}
                  >
                    <Funnel className="h-3 w-3" weight="bold" />
                    {soloConAlerta ? "Mostrando sin seguimiento" : "Filtrar sin seguimiento"}
                  </button>
                </div>
              </div>

              {/* Lista desplazable de casos */}
              <div className="flex-1 overflow-y-auto divide-y divide-line/60 max-h-60 lg:max-h-none">
                {casosFiltrados.length === 0 ? (
                  <div className="p-6 text-center text-xs text-ink-soft">
                    Ningún caso coincide con el filtro actual.
                  </div>
                ) : (
                  casosFiltrados.map((c) => {
                    const activo = !radicadoManual && casoSeleccionado?.id === c.id;
                    const necesitaRescate = !c.tieneProcesoCpnu || c.ultimoSeguimientoExitoso === false;
                    const memoriaCaso =
                      mapaMemoriasPorCaso.porId.get(c.id) ??
                      (c.radicadoId ? mapaMemoriasPorCaso.porRad.get(c.radicadoId.replace(/\D/g, "")) : undefined);

                    return (
                      <button
                        key={c.id}
                        type="button"
                        onClick={() => {
                          setCasoSeleccionado(c);
                          setRadicadoManual("");
                          void consultarMemoriaDeCaso(c);
                        }}
                        className={`w-full px-4 py-3 text-left transition-colors ${
                          activo ? "bg-gold-pale/50 ring-inset ring-2 ring-gold-deep/40" : "hover:bg-ink/[0.03]"
                        }`}
                      >
                        <div className="flex items-start justify-between gap-2">
                          <p className="truncate text-xs font-semibold text-ink">{c.nombreCliente}</p>
                          <span className="shrink-0 rounded-full bg-ink/5 px-2 py-0.5 text-[10px] font-medium text-ink-soft">
                            {c.fuenteVisible}
                          </span>
                        </div>
                        <p className="mt-1 truncate font-mono text-[11px] text-gold-deep">
                          {c.radicadoId ?? "Sin radicado asignado"}
                        </p>
                        <div className="mt-1.5 flex flex-wrap items-center gap-2 text-[10px]">
                          {memoriaCaso ? (
                            <span className="inline-flex items-center gap-1 rounded-full bg-gold-pale/70 px-2 py-0.5 font-semibold text-gold-deep ring-1 ring-gold-deep/20">
                              <Database className="h-3 w-3" weight="fill" />
                              Informe guardado ({memoriaCaso.guardadoHaceTexto} · se borra en {memoriaCaso.diasParaBorrar}d)
                            </span>
                          ) : necesitaRescate ? (
                            <span className="inline-flex items-center gap-1 font-medium text-amber-700">
                              <WarningCircle className="h-3 w-3" weight="fill" />
                              Recomendado para rescate Legal-Pro
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 text-emerald-700">
                              <CheckCircle className="h-3 w-3" weight="fill" />
                              En seguimiento
                            </span>
                          )}
                        </div>
                      </button>
                    );
                  })
                )}
              </div>

              {/* Opción de consultar un radicado manual directo */}
              <div className="border-t border-line bg-surface p-3.5">
                <label className="block text-[11px] font-semibold text-ink-soft">
                  ¿Consultar otro radicado de 23 dígitos directo?
                </label>
                <div className="mt-1.5 flex gap-1.5">
                  <input
                    type="text"
                    value={radicadoManual}
                    onChange={(e) => {
                      const val = e.target.value;
                      setRadicadoManual(val);
                      if (val.replace(/\D/g, "").length >= 20) {
                        void consultarMemoriaDeCaso(null, val);
                      }
                    }}
                    placeholder="Ej: 11001310300120240012300"
                    className="w-full rounded-lg border border-line bg-paper px-2.5 py-1.5 font-mono text-xs text-ink placeholder:text-ink-soft/50 focus:border-gold focus:outline-none"
                  />
                  {radicadoManual && (
                    <button
                      type="button"
                      onClick={() => {
                        setRadicadoManual("");
                        if (casoSeleccionado) {
                          void consultarMemoriaDeCaso(casoSeleccionado);
                        }
                      }}
                      className="rounded-lg border border-line px-2 text-xs text-ink-soft hover:bg-ink/5"
                    >
                      Limpiar
                    </button>
                  )}
                </div>
              </div>
            </div>

            {/* Columna Derecha: Panel de Ejecución e Informe Profundo */}
            <div className="flex flex-col overflow-y-auto p-6 lg:col-span-8">
              {/* Barra de Caso Seleccionado y Botón de Disparo */}
              <div className="rounded-2xl border border-line bg-paper p-4">
                <div className="flex flex-wrap items-center justify-between gap-4">
                  <div className="min-w-0 flex-1">
                    <p className="text-[11px] font-semibold uppercase tracking-wide text-gold-deep">
                      {radicadoManual ? "Consulta de Radicado Manual" : "Caso Seleccionado del Bufete"}
                    </p>
                    <h3 className="mt-0.5 truncate font-display text-base font-semibold text-ink">
                      {radicadoManual
                        ? `Radicado ${radicadoManual}`
                        : casoSeleccionado?.nombreCliente ?? "Selecciona un caso a la izquierda"}
                    </h3>
                    <p className="mt-0.5 font-mono text-xs text-ink-soft">
                      {radicadoManual
                        ? "Extracción directa desde Croma API"
                        : `Radicado: ${casoSeleccionado?.radicadoId ?? "Sin radicado"} · Fuente: ${casoSeleccionado?.fuenteVisible ?? "—"}`}
                    </p>
                  </div>

                  <div className="flex flex-col items-end gap-2">
                    <AdminButton
                      variant="primary"
                      disabled={
                        cargandoInforme ||
                        (!radicadoManual.trim() && !casoSeleccionado?.radicadoId) ||
                        (cuota !== null && cuota.creditosRestantes <= 0)
                      }
                      onClick={() => ejecutarLegalPro()}
                    >
                      {resultado ? (
                        <>
                          <ArrowsClockwise className={`h-4 w-4 ${cargandoInforme ? "animate-spin" : ""}`} weight="bold" />
                          {cargandoInforme ? "Actualizando informe Croma..." : "Actualizar Informe (Croma)"}
                        </>
                      ) : (
                        <>
                          <Sparkle className="h-4 w-4" weight="fill" />
                          {cargandoInforme ? "Extrayendo informe profundo..." : "Extraer Informe Profundo (Croma)"}
                        </>
                      )}
                    </AdminButton>

                    <label className="flex cursor-pointer select-none items-center gap-1.5 text-[11px] text-ink-soft">
                      <input
                        type="checkbox"
                        checked={buscarBoletinesEnVivo}
                        onChange={(e) => setBuscarBoletinesEnVivo(e.target.checked)}
                        className="h-3.5 w-3.5 rounded border-line text-gold focus:ring-gold"
                      />
                      Buscar y descargar boletines/estados electrónicos en vivo
                    </label>
                  </div>
                </div>

                {/* Banner de Memoria de 7 Días (cuando el informe está guardado) */}
                {resultado && resultado.fechaExtraccionInforme && (
                  <div className="mt-3 flex flex-wrap items-center justify-between gap-2 rounded-xl border border-gold/40 bg-gold-pale/35 px-3.5 py-2 text-xs">
                    <div className="flex items-center gap-2 text-ink">
                      <Database className="h-4 w-4 shrink-0 text-gold-deep" weight="fill" />
                      <span>
                        <strong>Informe Croma guardado {resultado.guardadoHaceTexto ?? "recientemente"}</strong>
                        {" "}({formatearFechaHoraBogota(resultado.fechaExtraccionInforme)})
                      </span>
                    </div>
                    <span className="rounded-full bg-surface px-2.5 py-0.5 text-[11px] font-semibold text-gold-deep ring-1 ring-gold/30">
                      Al día 7 se borra automáticamente (faltan {resultado.diasParaBorrarInforme ?? 7} día(s))
                    </span>
                  </div>
                )}
              </div>

              {/* Estado de Carga o Informe Resultado con Personita y Maletín */}
              {cargandoInforme || cargandoMemoria ? (
                <LegalProWalkingLoader
                  titulo={cargandoInforme ? "Auditoría Legal-Pro en marcha" : "Verificando memoria de 7 días"}
                  subtitulo={
                    cargandoInforme
                      ? "Consultando Croma API, sincronizando actuaciones y extrayendo boletines y correos oficiales..."
                      : "Comprobando si existe un informe previamente rescatado para este radicado..."
                  }
                />
              ) : !resultado ? (
                <div className="my-auto py-14 text-center">
                  <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-gold/15 text-gold-deep">
                    <Scales className="h-6 w-6" weight="duotone" />
                  </div>
                  <p className="mt-3 font-display text-base font-semibold text-ink">
                    Listo para auditar y rescatar información del caso
                  </p>
                  <p className="mx-auto mt-1 max-w-md text-xs leading-relaxed text-ink-soft">
                    Presiona <strong>&quot;Extraer Informe Profundo (Croma)&quot;</strong> para traer todo el expediente, correos oficiales del juzgado, términos y boletines. El informe quedará guardado en memoria durante 7 días.
                  </p>
                </div>
              ) : (
                <div className="mt-5 space-y-5">
                  {/* 4 KPIs de Rescate y Auditoría */}
                  <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                    <div className="rounded-xl border border-line bg-paper p-3.5">
                      <p className="text-[11px] font-medium text-ink-soft">Total Actuaciones</p>
                      <div className="mt-1 flex items-baseline gap-2">
                        <span className="font-display text-2xl font-bold text-ink">{resultado.totalActuaciones}</span>
                        {resultado.actuacionesNuevasInyectadasEnSie > 0 && (
                          <span className="rounded-full bg-emerald-500/15 px-2 py-0.5 text-[10px] font-bold text-emerald-700">
                            +{resultado.actuacionesNuevasInyectadasEnSie} rescatadas
                          </span>
                        )}
                      </div>
                    </div>

                    <div className="rounded-xl border border-line bg-paper p-3.5">
                      <p className="text-[11px] font-medium text-ink-soft">Notificaciones Clave</p>
                      <div className="mt-1 flex items-baseline gap-2">
                        <span className="font-display text-2xl font-bold text-gold-deep">
                          {resultado.actuacionesFundamentalesTotal}
                        </span>
                        <span className="text-[11px] text-ink-soft">de fondo</span>
                      </div>
                    </div>

                    <div className="rounded-xl border border-line bg-paper p-3.5">
                      <p className="text-[11px] font-medium text-ink-soft">Términos Procesales</p>
                      <div className="mt-1 flex items-baseline gap-2">
                        <span className="font-display text-2xl font-bold text-ink">
                          {resultado.terminosActivosTotal}
                        </span>
                        <span className="text-[11px] text-ink-soft">en curso</span>
                      </div>
                    </div>

                    <div className="rounded-xl border border-line bg-paper p-3.5">
                      <p className="text-[11px] font-medium text-ink-soft">Boletines / Estados</p>
                      <div className="mt-1 flex items-baseline gap-2">
                        <span className="font-display text-2xl font-bold text-blue-600">
                          {resultado.boletinesDisponiblesTotal}
                        </span>
                        {resultado.boletinesNuevosDescargados > 0 && (
                          <span className="rounded-full bg-blue-500/15 px-2 py-0.5 text-[10px] font-bold text-blue-700">
                            +{resultado.boletinesNuevosDescargados} nuevos
                          </span>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Ficha Técnica Integral del Proceso + Correo Oficial del Juzgado + Partes + Metadatos */}
                  <div className="rounded-2xl border border-line bg-paper p-4 space-y-3.5">
                    <div className="flex flex-wrap items-center justify-between gap-2 border-b border-line pb-2.5">
                      <div className="flex items-center gap-2">
                        <Buildings className="h-4 w-4 text-gold-deep" weight="bold" />
                        <span className="text-xs font-semibold text-ink">
                          {resultado.despacho || buzonPrincipal?.nombre || "Despacho no reportado"}
                        </span>
                        {resultado.esPrivado && <Badge tone="warning">Expediente reservado</Badge>}
                      </div>
                      <div className="flex items-center gap-2">
                        {(resultado.departamento || buzonPrincipal?.departamento) && (
                          <span className="inline-flex items-center gap-1 text-xs text-ink-soft">
                            <MapPin className="h-3.5 w-3.5" /> {resultado.departamento || buzonPrincipal?.departamento}
                          </span>
                        )}
                        <button
                          type="button"
                          onClick={() => copiar(resultado.radicado, "Radicado")}
                          className="inline-flex items-center gap-1 rounded-lg border border-line bg-surface px-2 py-1 font-mono text-[11px] text-gold-deep hover:bg-gold/10"
                        >
                          {resultado.radicado}
                          {copiadoTexto === resultado.radicado ? <Check className="h-3 w-3" /> : <Copy className="h-3 w-3" />}
                        </button>
                      </div>
                    </div>

                    {/* Correo Oficial del Juzgado visible directamente en la Ficha Principal */}
                    {resultado.buzonesDespacho.length > 0 && (
                      <div className="rounded-xl border border-gold/30 bg-gold-pale/25 p-3">
                        <p className="text-[10px] font-bold uppercase tracking-wide text-gold-deep">
                          Directorio Oficial del Despacho Judicial (Croma API)
                        </p>
                        <div className="mt-1.5 space-y-2">
                          {resultado.buzonesDespacho.map((bz, idx) => (
                            <div key={`${bz.email}-${idx}`} className="flex flex-wrap items-center justify-between gap-2">
                              <div className="min-w-0">
                                <div className="flex flex-wrap items-center gap-2">
                                  <EnvelopeSimple className="h-4 w-4 shrink-0 text-gold-deep" weight="fill" />
                                  <a
                                    href={`mailto:${bz.email}?subject=Radicado%20${resultado.radicado}`}
                                    className="font-mono text-xs font-bold text-ink underline-offset-2 hover:underline"
                                  >
                                    {bz.email}
                                  </a>
                                  <span className="rounded-full bg-surface px-2 py-0.5 text-[10px] font-semibold text-ink-soft ring-1 ring-line">
                                    {bz.tipoCuenta || "Despacho Judicial"}
                                  </span>
                                </div>
                                <p className="mt-0.5 text-[11px] text-ink-soft">
                                  {[
                                    bz.nombre,
                                    bz.corporacion,
                                    bz.especialidad,
                                    bz.ciudad ? `${bz.ciudad} (${bz.departamento})` : bz.departamento,
                                    bz.seccional ? `Seccional ${bz.seccional}` : "",
                                    bz.codigoDespacho ? `Cód. Despacho: ${bz.codigoDespacho}` : "",
                                  ]
                                    .filter(Boolean)
                                    .join(" · ")}
                                </p>
                              </div>
                              <button
                                type="button"
                                onClick={() => copiar(bz.email, "Correo del juzgado")}
                                className="inline-flex items-center gap-1.5 rounded-lg bg-gold px-2.5 py-1 text-[11px] font-semibold text-ink-fixed hover:bg-gold-deep"
                              >
                                {copiadoTexto === bz.email ? <Check className="h-3 w-3" weight="bold" /> : <Copy className="h-3 w-3" weight="bold" />}
                                {copiadoTexto === bz.email ? "Copiado" : "Copiar correo"}
                              </button>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Partes procesales */}
                    {limpiarPartes(resultado.sujetosProcesales).length > 0 && (
                      <div>
                        <p className="mb-1.5 text-[10px] font-bold uppercase tracking-wide text-ink-soft/70">
                          Sujetos Procesales Registrados
                        </p>
                        <div className="grid gap-1.5 sm:grid-cols-2">
                          {limpiarPartes(resultado.sujetosProcesales).map((parte, idx) => (
                            <div key={idx} className="rounded-lg bg-surface px-3 py-1.5 text-xs text-ink">
                              {parte}
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Grilla completa de todos los metadatos técnicos de Croma, CPNU y SIE */}
                    <div className="grid grid-cols-2 gap-2 rounded-xl bg-surface p-3 text-[11px] sm:grid-cols-3">
                      <div>
                        <span className="block text-[10px] uppercase text-ink-soft/70">Fecha de Radicación</span>
                        <strong className="text-ink">
                          {resultado.fechaInicio ? formatearFechaBogota(resultado.fechaInicio) : "No reportada"}
                        </strong>
                      </div>
                      <div>
                        <span className="block text-[10px] uppercase text-ink-soft/70">Última Actuación</span>
                        <strong className="text-ink">
                          {resultado.fechaUltimaActuacion ? formatearFechaBogota(resultado.fechaUltimaActuacion) : "No reportada"}
                        </strong>
                      </div>
                      <div>
                        <span className="block text-[10px] uppercase text-ink-soft/70">Juez / Ponente</span>
                        <strong className="text-ink">{resultado.ponente || "No reportado"}</strong>
                      </div>
                      <div>
                        <span className="block text-[10px] uppercase text-ink-soft/70">Ubicación Expediente</span>
                        <strong className="text-gold-deep">{resultado.ubicacion || "En despacho"}</strong>
                      </div>
                      <div>
                        <span className="block text-[10px] uppercase text-ink-soft/70">Tipo / Clase Proceso</span>
                        <strong className="text-ink">
                          {[resultado.tipoProceso, resultado.claseProceso, resultado.subclaseProceso]
                            .filter(Boolean)
                            .join(" · ") || "Ordinario / Judicial"}
                        </strong>
                      </div>
                      <div>
                        <span className="block text-[10px] uppercase text-ink-soft/70">Recurso / Instancia</span>
                        <strong className="text-ink">{resultado.recursoProceso || "Sin recurso activo"}</strong>
                      </div>
                      <div>
                        <span className="block text-[10px] uppercase text-ink-soft/70">ID Proceso CPNU / Croma</span>
                        <strong className="font-mono text-ink">
                          {resultado.idProcesoCpnu ?? "—"}
                          {resultado.connectionIdCroma ? ` (Conn #${resultado.connectionIdCroma})` : ""}
                        </strong>
                      </div>
                      <div>
                        <span className="block text-[10px] uppercase text-ink-soft/70">Cód. Despacho (12 dígitos)</span>
                        <strong className="font-mono text-ink">
                          {resultado.codigoDespacho12Digitos || resultado.radicado.slice(0, 12)}
                        </strong>
                      </div>
                      <div>
                        <span className="block text-[10px] uppercase text-ink-soft/70">IDs Relacionados en Rama</span>
                        <strong className="font-mono text-ink">
                          {resultado.idsProcesosRelacionados && resultado.idsProcesosRelacionados.length > 0
                            ? resultado.idsProcesosRelacionados.join(", ")
                            : resultado.idProcesoCpnu ?? "—"}
                        </strong>
                      </div>
                      {resultado.ultimaActualizacionRama && (
                        <div>
                          <span className="block text-[10px] uppercase text-ink-soft/70">Rama Actualizó Sistema</span>
                          <strong className="text-ink">{formatearFechaHoraBogota(resultado.ultimaActualizacionRama)}</strong>
                        </div>
                      )}
                      {(resultado.correoCliente || resultado.telefonoCliente) && (
                        <div>
                          <span className="block text-[10px] uppercase text-ink-soft/70">Contacto Cliente SIE</span>
                          <strong className="text-ink">
                            {[resultado.correoCliente, resultado.telefonoCliente].filter(Boolean).join(" · ")}
                          </strong>
                        </div>
                      )}
                      {resultado.abogadoAsignadoNombre && (
                        <div>
                          <span className="block text-[10px] uppercase text-ink-soft/70">Abogado Asignado</span>
                          <strong className="inline-flex items-center gap-1 text-emerald-800">
                            <UserCircle className="h-3.5 w-3.5" weight="fill" />
                            {resultado.abogadoAsignadoNombre}
                          </strong>
                        </div>
                      )}
                    </div>

                    {resultado.contenidoRadicacion && (
                      <div className="rounded-lg bg-surface px-3 py-2 text-xs text-ink-soft">
                        <strong className="text-ink">Contenido de radicación: </strong>
                        {resultado.contenidoRadicacion}
                      </div>
                    )}
                  </div>

                  {/* Pestañas de Detalle: Seguimiento / Boletines / Correos Oficiales del Juzgado */}
                  <div className="flex flex-wrap items-center justify-between gap-2 border-b border-line pb-2">
                    <div className="flex items-center gap-1.5">
                      <button
                        type="button"
                        onClick={() => setPestanaActiva("actuaciones")}
                        className={`rounded-xl px-3.5 py-2 text-xs font-semibold transition-all ${
                          pestanaActiva === "actuaciones"
                            ? "bg-ink text-paper shadow-sm"
                            : "text-ink-soft hover:bg-ink/5"
                        }`}
                      >
                        Seguimiento y Notificaciones ({resultado.actuaciones.length})
                      </button>
                      <button
                        type="button"
                        onClick={() => setPestanaActiva("boletines")}
                        className={`rounded-xl px-3.5 py-2 text-xs font-semibold transition-all ${
                          pestanaActiva === "boletines"
                            ? "bg-ink text-paper shadow-sm"
                            : "text-ink-soft hover:bg-ink/5"
                        }`}
                      >
                        Boletines y Estados ({resultado.boletinesCacheados.length + resultado.documentosEnVivo.length})
                      </button>
                      <button
                        type="button"
                        onClick={() => setPestanaActiva("correos")}
                        className={`rounded-xl px-3.5 py-2 text-xs font-semibold transition-all ${
                          pestanaActiva === "correos"
                            ? "bg-ink text-paper shadow-sm"
                            : "text-ink-soft hover:bg-ink/5"
                        }`}
                      >
                        Correos del Juzgado ({resultado.buzonesDespacho.length})
                      </button>
                    </div>

                    {pestanaActiva === "actuaciones" && (
                      <label className="flex cursor-pointer select-none items-center gap-1.5 text-xs text-ink-soft">
                        <input
                          type="checkbox"
                          checked={soloFundamentales}
                          onChange={(e) => setSoloFundamentales(e.target.checked)}
                          className="h-3.5 w-3.5 rounded border-line text-gold focus:ring-gold"
                        />
                        Solo decisiones de fondo / con término
                      </label>
                    )}
                  </div>

                  {/* Contenido Pestaña 1: Seguimiento y Notificaciones */}
                  {pestanaActiva === "actuaciones" && (
                    <div className="space-y-3">
                      {actuacionesMostradas.length === 0 ? (
                        <div className="rounded-xl border border-line bg-paper p-8 text-center text-xs text-ink-soft">
                          No hay actuaciones registradas para este filtro.
                        </div>
                      ) : (
                        actuacionesMostradas.map((act, i) => (
                          <div
                            key={`${act.id ?? i}-${i}`}
                            className={`rounded-xl border p-4 transition-all ${
                              act.esFundamental
                                ? "border-gold/50 bg-gold-pale/20"
                                : "border-line bg-paper"
                            }`}
                          >
                            <div className="flex flex-wrap items-start justify-between gap-2">
                              <div className="flex flex-wrap items-center gap-2">
                                {act.consecutivo !== null && (
                                  <span className="rounded-md bg-ink/10 px-2 py-0.5 font-mono text-[11px] font-bold text-ink">
                                    #{act.consecutivo}
                                  </span>
                                )}
                                <span className="text-sm font-semibold text-ink">{act.nombreActuacion}</span>
                                {act.esFundamental && <Badge tone="gold">Decisión de fondo</Badge>}
                                {act.conDocumentos && <Badge tone="neutral">Con providencia</Badge>}
                                {act.posibleMedidaCautelar && <Badge tone="danger">Medida cautelar</Badge>}
                                {act.posibleLiquidacionCostas && <Badge tone="warning">Liquidación costas</Badge>}
                                {act.posibleAcreedorSubrogatorio && <Badge tone="warning">Acreedor subrogatorio</Badge>}
                              </div>

                              <span className="font-mono text-xs text-ink-soft">
                                {act.fechaActuacion
                                  ? formatearFechaHoraBogota(act.fechaActuacion)
                                  : act.fechaRegistro
                                    ? formatearFechaHoraBogota(act.fechaRegistro)
                                    : "—"}
                              </span>
                            </div>

                            {act.anotacion && (
                              <p className="mt-2 whitespace-pre-line text-xs leading-relaxed text-ink-soft">
                                {act.anotacion}
                              </p>
                            )}

                            {(act.fechaInicialTermino || act.fechaFinalTermino) && (
                              <div className="mt-2 flex flex-wrap items-center gap-4 text-[11px] text-ink-soft">
                                {act.fechaInicialTermino && (
                                  <span>
                                    Inicio término despacho: <strong>{formatearFechaBogota(act.fechaInicialTermino)}</strong>
                                  </span>
                                )}
                                {act.fechaFinalTermino && (
                                  <span>
                                    Fin término despacho: <strong>{formatearFechaBogota(act.fechaFinalTermino)}</strong>
                                  </span>
                                )}
                              </div>
                            )}

                            {act.fechaVencimientoTermino && (
                              <div className="mt-2.5 flex flex-wrap items-center justify-between gap-2 rounded-lg border border-gold/30 bg-surface px-3 py-2 text-xs">
                                <div>
                                  <span className="font-semibold text-ink">{act.descripcionTermino}: </span>
                                  <span className="text-ink-soft">
                                    Vence el <strong>{formatearFechaBogota(act.fechaVencimientoTermino)}</strong> ({act.diasHabilesTermino} días hábiles)
                                  </span>
                                </div>
                                <Badge
                                  tone={
                                    act.estadoTermino === "VENCIDO"
                                      ? "neutral"
                                      : act.estadoTermino === "POR_VENCER"
                                        ? "danger"
                                        : "gold"
                                  }
                                >
                                  {act.estadoTermino === "POR_VENCER"
                                    ? "Por vencer"
                                    : act.estadoTermino === "CORRIENDO"
                                      ? "Término corriendo"
                                      : "Vencido"}
                                </Badge>
                              </div>
                            )}
                          </div>
                        ))
                      )}
                    </div>
                  )}

                  {/* Contenido Pestaña 2: Boletines y Estados Electrónicos */}
                  {pestanaActiva === "boletines" && (
                    <div className="space-y-3">
                      {resultado.boletinesCacheados.length === 0 && resultado.documentosEnVivo.length === 0 ? (
                        <div className="rounded-xl border border-line bg-paper p-8 text-center text-xs text-ink-soft">
                          No se encontraron boletines ni providencias publicadas en el portal de Publicaciones Procesales para las fechas recientes de este caso.
                        </div>
                      ) : (
                        <>
                          {resultado.boletinesCacheados.map((b) => (
                            <div
                              key={b.id}
                              className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-line bg-paper p-3.5"
                            >
                              <div className="flex items-center gap-3 min-w-0">
                                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-blue-500/15 text-blue-600">
                                  <FilePdf className="h-5 w-5" weight="fill" />
                                </div>
                                <div className="min-w-0">
                                  <p className="truncate text-xs font-semibold text-ink">{b.nombreArchivo}</p>
                                  <p className="mt-0.5 text-[11px] text-ink-soft">
                                    Descargado: {formatearFechaHoraBogota(b.fechaDescarga)} · Expira:{" "}
                                    {formatearFechaBogota(b.fechaExpiracion)}
                                  </p>
                                </div>
                              </div>

                              <div className="flex items-center gap-2">
                                <button
                                  type="button"
                                  onClick={() =>
                                    abrirPdfBoletin(b.id).catch(() => toast.error("No se pudo abrir el PDF."))
                                  }
                                  className="inline-flex items-center gap-1.5 rounded-lg border border-line bg-surface px-3 py-1.5 text-xs font-medium text-ink hover:bg-ink/5"
                                >
                                  <ArrowSquareOut className="h-3.5 w-3.5" />
                                  Ver PDF
                                </button>
                                <button
                                  type="button"
                                  onClick={() =>
                                    descargarBoletin(b.id, b.nombreArchivo).catch(() =>
                                      toast.error("No se pudo descargar el boletín."),
                                    )
                                  }
                                  className="inline-flex items-center gap-1.5 rounded-lg bg-gold px-3 py-1.5 text-xs font-semibold text-ink-fixed hover:bg-gold-deep"
                                >
                                  <DownloadSimple className="h-3.5 w-3.5" weight="bold" />
                                  Descargar
                                </button>
                              </div>
                            </div>
                          ))}

                          {resultado.documentosEnVivo.map((doc, idx) => (
                            <div
                              key={`${doc.urlDescarga}-${idx}`}
                              className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-line bg-paper p-3.5"
                            >
                              <div className="flex items-center gap-3 min-w-0">
                                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-gold/15 text-gold-deep">
                                  <FilePdf className="h-5 w-5" weight="fill" />
                                </div>
                                <div className="min-w-0">
                                  <p className="truncate text-xs font-semibold text-ink">{doc.nombreArchivo}</p>
                                  <p className="mt-0.5 truncate font-mono text-[10px] text-ink-soft">
                                    {doc.urlDescarga}
                                  </p>
                                </div>
                              </div>

                              <a
                                href={doc.urlDescarga}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="inline-flex items-center gap-1.5 rounded-lg bg-gold px-3 py-1.5 text-xs font-semibold text-ink-fixed hover:bg-gold-deep"
                              >
                                <ArrowSquareOut className="h-3.5 w-3.5" weight="bold" />
                                Abrir en Portal Rama
                              </a>
                            </div>
                          ))}
                        </>
                      )}
                    </div>
                  )}

                  {/* Contenido Pestaña 3: Directorio Oficial de Correos del Juzgado */}
                  {pestanaActiva === "correos" && (
                    <div className="space-y-3">
                      {resultado.buzonesDespacho.length === 0 ? (
                        <div className="rounded-xl border border-line bg-paper p-8 text-center text-xs text-ink-soft">
                          No se encontraron buzones electrónicos oficiales asociados al código de despacho de este radicado.
                        </div>
                      ) : (
                        resultado.buzonesDespacho.map((bz, idx) => (
                          <div
                            key={`${bz.email}-${idx}`}
                            className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-line bg-paper p-4"
                          >
                            <div className="space-y-1">
                              <div className="flex items-center gap-2">
                                <EnvelopeSimple className="h-4 w-4 text-gold-deep" weight="bold" />
                                <span className="font-mono text-xs font-bold text-ink">{bz.email}</span>
                                <Badge tone="gold">{bz.tipoCuenta || "Despacho"}</Badge>
                              </div>
                              <p className="text-xs font-medium text-ink">{bz.nombre}</p>
                              <p className="text-[11px] text-ink-soft">
                                {[
                                  bz.corporacion,
                                  bz.especialidad,
                                  bz.ciudad,
                                  bz.departamento,
                                  bz.seccional ? `Seccional ${bz.seccional}` : "",
                                  bz.codigoDespacho ? `Cód: ${bz.codigoDespacho}` : "",
                                ]
                                  .filter(Boolean)
                                  .join(" · ")}
                              </p>
                            </div>

                            <button
                              type="button"
                              onClick={() => copiar(bz.email, "Correo del juzgado")}
                              className="inline-flex items-center gap-1.5 rounded-xl bg-gold px-3 py-1.5 text-xs font-semibold text-ink-fixed hover:bg-gold-deep"
                            >
                              {copiadoTexto === bz.email ? (
                                <>
                                  <Check className="h-3.5 w-3.5" weight="bold" />
                                  Copiado
                                </>
                              ) : (
                                <>
                                  <Copy className="h-3.5 w-3.5" weight="bold" />
                                  Copiar correo
                                </>
                              )}
                            </button>
                          </div>
                        ))
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
