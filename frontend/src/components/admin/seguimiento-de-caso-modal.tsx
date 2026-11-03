"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Dialog } from "@base-ui/react/dialog";
import { toast } from "sonner";
import {
  ArrowsClockwise,
  ArrowSquareOut,
  Buildings,
  CaretDown,
  CaretUp,
  Check,
  CheckCircle,
  Clock,
  Copy,
  Database,
  DownloadSimple,
  EnvelopeSimple,
  FilePdf,
  FileText,
  Lightning,
  MapPin,
  PaperPlaneTilt,
  Scales,
  ShieldCheck,
  Sparkle,
  Spinner,
  WarningCircle,
  X,
} from "@phosphor-icons/react";
import {
  abrirPdfBoletin,
  abrirPdfDocumentoJudicial,
  ApiError,
  buscarDocumentosBoletin,
  descargarBoletin,
  enviarDocumentoJudicial,
  generarInformeLegalProCroma,
  obtenerBoletinesCacheDeCaso,
  obtenerCuotaCroma,
  obtenerLineaTiempoJudicial,
  obtenerMemoriaLegalProCroma,
  obtenerTextoDocumentoJudicial,
  type CromaEstadoCuota,
  type CromaLegalProResultado,
  type DocumentoCandidatoJudicial,
  type EventoLineaTiempoJudicial,
  type LineaTiempoJudicial,
} from "@/lib/admin-api";
import { AdminButton, AdminLoader, Badge } from "@/components/admin/ui";
import { LegalProWalkingLoader } from "@/components/admin/legal-pro-walking-loader";
import { formatearFechaBogota, formatearFechaHoraBogota } from "@/lib/fecha";

const formatearFecha = formatearFechaBogota;
const formatearFechaHora = formatearFechaHoraBogota;
const formatearFechaSolo = formatearFechaBogota;

function formatearPartes(sujetosProcesales: string): string[] {
  return sujetosProcesales
    .split("|")
    .map((parte) => parte.replace(/\s*\r?\n\s*/g, " ").replace(/\s+/g, " ").trim())
    .filter(Boolean);
}

export type CasoParaSeguimiento = {
  id: number;
  nombreCliente: string;
  radicadoId: string | null;
};

export function SeguimientoDeCasoModal({
  caso,
  onClose,
  onRevisado,
}: {
  caso: CasoParaSeguimiento | null;
  onClose: () => void;
  onRevisado?: (casoId: number) => void;
}) {
  const [linea, setLinea] = useState<LineaTiempoJudicial | null>(null);
  const [enviandoUrl, setEnviandoUrl] = useState<string | null>(null);
  const [textoPorUrl, setTextoPorUrl] = useState<Record<string, string>>({});
  const [textoAbiertoUrl, setTextoAbiertoUrl] = useState<string | null>(null);
  const [cargandoTextoUrl, setCargandoTextoUrl] = useState<string | null>(null);
  const [abriendoPdfUrl, setAbriendoPdfUrl] = useState<string | null>(null);
  const [candidatosPorIndice, setCandidatosPorIndice] = useState<Record<number, DocumentoCandidatoJudicial[]>>({});
  const [verificadoPorIndice, setVerificadoPorIndice] = useState<Record<number, boolean>>({});
  const [indiceAbierto, setIndiceAbierto] = useState<number | null>(null);
  const [precargando, setPrecargando] = useState(false);
  const [cargaInicialCompleta, setCargaInicialCompleta] = useState(false);
  const [errorCarga, setErrorCarga] = useState(false);
  const [publicacionesCaidas, setPublicacionesCaidas] = useState(false);
  const [progresoPrecarga, setProgresoPrecarga] = useState<{ revisadas: number; total: number } | null>(null);

  // Estado de la columna derecha: Motor Legal-Pro (Croma API + Memoria de 7 Días)
  const [cuotaCroma, setCuotaCroma] = useState<CromaEstadoCuota | null>(null);
  const [informeCroma, setInformeCroma] = useState<CromaLegalProResultado | null>(null);
  const [cargandoMemoriaCroma, setCargandoMemoriaCroma] = useState(false);
  const [extrayendoCroma, setExtrayendoCroma] = useState(false);
  const [pestanaCroma, setPestanaCroma] = useState<"actuaciones" | "boletines" | "correos">("actuaciones");
  const [copiadoTexto, setCopiadoTexto] = useState<string | null>(null);

  const PAUSA_ENTRE_BOLETINES_MS = 400;
  const UMBRAL_FALLOS_CONSECUTIVOS = 3;

  const cargarDesdeCache = useCallback(
    (eventos: EventoLineaTiempoJudicial[], cache: Record<number, DocumentoCandidatoJudicial[]>) => {
      const candidatos: Record<number, DocumentoCandidatoJudicial[]> = {};
      const verificado: Record<number, boolean> = {};
      eventos.forEach((evento, i) => {
        verificado[i] = true;
        if (cache[evento.id]) {
          candidatos[i] = cache[evento.id];
        }
      });
      setCandidatosPorIndice(candidatos);
      setVerificadoPorIndice(verificado);
    },
    [],
  );

  const verificarNovedadEnSegundoPlano = useCallback(async (casoId: number, eventos: EventoLineaTiempoJudicial[]) => {
    const indice = eventos.findIndex((e) => e.fechaRegistro);
    if (indice === -1) return;
    const evento = eventos[indice];
    try {
      const candidatos = await buscarDocumentosBoletin(casoId, {
        fechaRegistro: evento.fechaRegistro!.slice(0, 10),
        nombreActuacion: evento.nombreActuacion,
      });
      if (candidatos.length === 0) return;
      setCandidatosPorIndice((prev) => {
        const previo = prev[indice] ?? [];
        if (previo.length === candidatos.length) return prev;
        return { ...prev, [indice]: candidatos };
      });
    } catch {
      // Silencioso a propósito
    }
  }, []);

  const precargarBoletinesManual = useCallback(async (casoId: number, eventos: EventoLineaTiempoJudicial[]) => {
    setPrecargando(true);
    setPublicacionesCaidas(false);
    const conFecha = eventos.filter((e) => e.fechaRegistro).length;
    let revisadas = 0;
    let fallosConsecutivos = 0;
    let cortado = false;
    setProgresoPrecarga(conFecha > 0 ? { revisadas: 0, total: conFecha } : null);
    for (let i = 0; i < eventos.length; i++) {
      const evento = eventos[i];
      if (!evento.fechaRegistro) {
        setVerificadoPorIndice((prev) => ({ ...prev, [i]: true }));
        continue;
      }
      if (cortado) {
        setVerificadoPorIndice((prev) => ({ ...prev, [i]: true }));
        continue;
      }
      try {
        const candidatos = await buscarDocumentosBoletin(casoId, {
          fechaRegistro: evento.fechaRegistro.slice(0, 10),
          nombreActuacion: evento.nombreActuacion,
        });
        setCandidatosPorIndice((prev) => ({ ...prev, [i]: candidatos }));
        fallosConsecutivos = 0;
      } catch {
        fallosConsecutivos++;
        if (fallosConsecutivos >= UMBRAL_FALLOS_CONSECUTIVOS) {
          cortado = true;
          setPublicacionesCaidas(true);
        }
      } finally {
        setVerificadoPorIndice((prev) => ({ ...prev, [i]: true }));
        revisadas++;
        setProgresoPrecarga({ revisadas, total: conFecha });
      }
      if (i < eventos.length - 1) {
        await new Promise((resolve) => setTimeout(resolve, PAUSA_ENTRE_BOLETINES_MS));
      }
    }
    setPrecargando(false);
    setProgresoPrecarga(null);
  }, []);

  const cargar = useCallback(() => {
    if (!caso) return;
    setErrorCarga(false);
    const casoId = caso.id;
    obtenerLineaTiempoJudicial(casoId)
      .then((l) => {
        setLinea(l);
        onRevisado?.(casoId);
        return obtenerBoletinesCacheDeCaso(casoId)
          .catch(() => ({}) as Record<number, DocumentoCandidatoJudicial[]>)
          .then((cache) => {
            cargarDesdeCache(l.eventos, cache);
            setCargaInicialCompleta(true);
            verificarNovedadEnSegundoPlano(casoId, l.eventos);
          });
      })
      .catch(() => {
        setErrorCarga(true);
        toast.error("No se pudo cargar el seguimiento judicial de este caso.");
      });
  }, [caso, cargarDesdeCache, verificarNovedadEnSegundoPlano, onRevisado]);

  // Carga inicial de la columna derecha (Legal-Pro): revisa si ya hay un informe guardado en los últimos 7 días
  const cargarEstadoLegalPro = useCallback(async () => {
    if (!caso) return;
    obtenerCuotaCroma()
      .then(setCuotaCroma)
      .catch(() => {});

    setCargandoMemoriaCroma(true);
    try {
      const guardado = await obtenerMemoriaLegalProCroma({
        casoId: caso.id,
        radicado: caso.radicadoId ?? undefined,
      });
      setInformeCroma(guardado ?? null);
    } catch {
      setInformeCroma(null);
    } finally {
      setCargandoMemoriaCroma(false);
    }
  }, [caso]);

  const casoIdPrevioRef = useRef<number | null>(null);

  useEffect(() => {
    const idActual = caso?.id ?? null;
    if (casoIdPrevioRef.current === idActual) {
      return;
    }
    casoIdPrevioRef.current = idActual;
    if (!caso) {
      return;
    }
    setLinea(null);
    setCandidatosPorIndice({});
    setVerificadoPorIndice({});
    setIndiceAbierto(null);
    setTextoPorUrl({});
    setTextoAbiertoUrl(null);
    setCargaInicialCompleta(false);
    setErrorCarga(false);
    setPublicacionesCaidas(false);
    setInformeCroma(null);
    setPestanaCroma("actuaciones");
    cargar();
    void cargarEstadoLegalPro();
  }, [caso, cargar, cargarEstadoLegalPro]);

  async function onEjecutarLegalPro() {
    if (!caso) return;
    setExtrayendoCroma(true);
    try {
      const res = await generarInformeLegalProCroma({
        casoId: caso.id,
        radicado: caso.radicadoId ?? undefined,
        buscarBoletinesEnVivo: true,
      });
      obtenerCuotaCroma()
        .then(setCuotaCroma)
        .catch(() => {});

      if (res.exito) {
        setInformeCroma(res);
        toast.success(res.mensaje);
        // Si Croma rescató e inyectó nuevas actuaciones o boletines en SIE, refrescamos la columna izquierda para comparar en vivo
        if (res.actuacionesNuevasInyectadasEnSie > 0 || res.boletinesNuevosDescargados > 0) {
          cargar();
        }
      } else {
        toast.error(res.mensaje);
      }
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo extraer el informe Legal-Pro con Croma.");
    } finally {
      setExtrayendoCroma(false);
    }
  }

  function copiarTexto(texto: string, etiqueta: string) {
    navigator.clipboard.writeText(texto);
    setCopiadoTexto(texto);
    toast.success(`${etiqueta} copiado al portapapeles.`);
    setTimeout(() => setCopiadoTexto(null), 2200);
  }

  async function onEnviar(documento: DocumentoCandidatoJudicial, nombreActuacion: string) {
    if (!caso) return;
    if (
      !window.confirm(
        `¿Enviar "${documento.nombreArchivo}" al correo del cliente (${caso.nombreCliente})? ` +
          "Esta acción le manda un correo real al cliente con el documento adjunto -- revisa antes " +
          "que el nombre del archivo de verdad corresponda a esta actuación.",
      )
    ) {
      return;
    }
    setEnviandoUrl(documento.urlDescarga);
    try {
      const resultado = await enviarDocumentoJudicial(caso.id, {
        urlDocumento: documento.urlDescarga,
        nombreArchivo: documento.nombreArchivo,
        nombreActuacion,
      });
      if (resultado.estado === "ENVIADO") {
        toast.success(resultado.mensaje);
      } else if (resultado.estado === "BLOQUEADO_SEGURIDAD") {
        toast.warning(resultado.mensaje);
      } else {
        toast.error(resultado.mensaje);
      }
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo enviar el documento.");
    } finally {
      setEnviandoUrl(null);
    }
  }

  async function onVerTexto(d: DocumentoCandidatoJudicial) {
    if (!caso) return;
    if (textoAbiertoUrl === d.urlDescarga) {
      setTextoAbiertoUrl(null);
      return;
    }
    if (textoPorUrl[d.urlDescarga] !== undefined) {
      setTextoAbiertoUrl(d.urlDescarga);
      return;
    }
    setCargandoTextoUrl(d.urlDescarga);
    try {
      const resultado = await obtenerTextoDocumentoJudicial(caso.id, d.urlDescarga);
      setTextoPorUrl((prev) => ({
        ...prev,
        [d.urlDescarga]: resultado.disponible && resultado.texto
          ? resultado.texto
          : (resultado.mensaje ?? "No se pudo leer el texto de este documento."),
      }));
      setTextoAbiertoUrl(d.urlDescarga);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo leer el texto de este documento.");
    } finally {
      setCargandoTextoUrl(null);
    }
  }

  async function onVerPdf(d: DocumentoCandidatoJudicial) {
    if (!caso) return;
    setAbriendoPdfUrl(d.urlDescarga);
    try {
      await abrirPdfDocumentoJudicial(caso.id, d.urlDescarga);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo abrir el PDF de este documento.");
    } finally {
      setAbriendoPdfUrl(null);
    }
  }

  function renderCandidatos(candidatos: DocumentoCandidatoJudicial[], nombreActuacion: string) {
    if (candidatos.length === 0) {
      return (
        <p className="mt-2 text-xs text-ink-soft">
          No se encontró ningún boletín de Publicaciones Procesales para esta fecha -- el portal solo expone los
          boletines más recientes de cada despacho, puede que esta actuación ya haya salido de esa ventana.
        </p>
      );
    }
    return (
      <div className="mt-3 space-y-3">
        <p className="text-xs text-ink-soft">
          Todos estos documentos ya fueron verificados contra el número de proceso de este radicado. Los que NO
          traen la insignia dorada son listados del despacho que también incluyen a otros clientes o terceros --
          revísalos igual antes de remitir cualquiera al cliente.
        </p>
        {candidatos.map((d) => (
          <div key={d.urlDescarga} className="rounded-lg bg-surface p-4 ring-1 ring-line">
            <div className="flex items-center justify-between gap-3">
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-ink">{d.nombreArchivo}</p>
                {d.coincideConNombreActuacion && <Badge tone="gold">Verificado, documento individual</Badge>}
              </div>
            </div>
            <div className="mt-3 flex flex-wrap items-center gap-2">
              <AdminButton variant="ghost" onClick={() => onVerTexto(d)} disabled={cargandoTextoUrl !== null}>
                {textoAbiertoUrl === d.urlDescarga ? (
                  <CaretUp className="h-3.5 w-3.5" weight="bold" />
                ) : (
                  <CaretDown className="h-3.5 w-3.5" weight="bold" />
                )}
                {cargandoTextoUrl === d.urlDescarga ? "Leyendo..." : "Ver texto completo"}
              </AdminButton>
              <AdminButton variant="ghost" onClick={() => onVerPdf(d)} disabled={abriendoPdfUrl !== null}>
                <FilePdf className="h-3.5 w-3.5" weight="bold" />
                {abriendoPdfUrl === d.urlDescarga ? "Abriendo..." : "Ver / descargar PDF"}
              </AdminButton>
              <AdminButton variant="secondary" onClick={() => onEnviar(d, nombreActuacion)} disabled={enviandoUrl !== null}>
                <PaperPlaneTilt className="h-3.5 w-3.5" weight="bold" />
                {enviandoUrl === d.urlDescarga ? "Enviando..." : "Enviar al cliente"}
              </AdminButton>
            </div>
            {textoAbiertoUrl === d.urlDescarga && textoPorUrl[d.urlDescarga] && (
              <pre className="mt-2 whitespace-pre-wrap rounded-lg bg-paper p-2.5 font-sans text-[11px] leading-relaxed text-ink-soft ring-1 ring-line">
                {textoPorUrl[d.urlDescarga]}
              </pre>
            )}
          </div>
        ))}
      </div>
    );
  }

  return (
    <Dialog.Root open={caso !== null} onOpenChange={(open) => !open && onClose()}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-ink/40 backdrop-blur-sm" />
        {/* Vista de 2 Columnas lado a lado (Motor SIE a la izquierda | Motor Legal-Pro Croma a la derecha) */}
        <Dialog.Popup className="panel-admin fixed left-1/2 top-1/2 z-50 flex max-h-[92vh] w-[96vw] max-w-7xl -translate-x-1/2 -translate-y-1/2 flex-col overflow-hidden rounded-2xl bg-surface shadow-2xl ring-1 ring-line">
          <div className="flex shrink-0 items-start justify-between gap-4 border-b border-line px-8 py-5">
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2.5">
                <Dialog.Title className="font-display text-xl text-ink">Seguimiento judicial</Dialog.Title>
                <Badge tone="gold">Comparador Dual: Motor SIE + Legal-Pro (Croma)</Badge>
              </div>
              <Dialog.Description render={<div className="mt-1.5" />}>
                <span className="block text-base font-medium text-ink">{caso?.nombreCliente}</span>
                {caso?.radicadoId && (
                  <span className="mt-0.5 block font-mono text-xs tracking-wider text-gold-deep">
                    {caso.radicadoId}
                  </span>
                )}
              </Dialog.Description>
            </div>
            <Dialog.Close className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5">
              <X className="h-4.5 w-4.5" />
            </Dialog.Close>
          </div>

          <div className="grid min-h-0 flex-1 grid-cols-1 divide-y divide-line overflow-hidden lg:grid-cols-2 lg:divide-x lg:divide-y-0">
            {/* =====================================================================
                COLUMNA IZQUIERDA: MOTOR ESTÁNDAR DE SIE (CPNU / BOLETINES)
               ===================================================================== */}
            <div className="overflow-y-auto px-6 py-5">
              <div className="mb-4 flex items-center justify-between border-b border-line pb-3">
                <div className="flex items-center gap-2">
                  <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-ink/10 text-ink">
                    <Scales className="h-4 w-4" weight="bold" />
                  </span>
                  <div>
                    <h3 className="text-sm font-semibold text-ink">Motor Estándar SIE (Rama Judicial)</h3>
                    <p className="text-[11px] text-ink-soft">Seguimiento continuo automatizado 24/7 y caché de boletines</p>
                  </div>
                </div>
                {linea && <Badge tone="neutral">{linea.eventos.length} actuaciones</Badge>}
              </div>

              {errorCarga ? (
                <div className="py-14 text-center">
                  <WarningCircle className="mx-auto h-8 w-8 text-red-500" weight="fill" />
                  <p className="mt-4 text-sm font-medium text-ink">No se pudo cargar el seguimiento judicial</p>
                  <p className="mt-1 text-xs text-ink-soft">
                    Puede ser un problema temporal de conexión con el backend. Intenta de nuevo.
                  </p>
                  <AdminButton variant="secondary" onClick={cargar} className="mt-4">
                    <ArrowsClockwise className="h-3.5 w-3.5" weight="bold" />
                    Reintentar
                  </AdminButton>
                </div>
              ) : !cargaInicialCompleta || linea === null ? (
                <div className="py-14 text-center">
                  <AdminLoader label={null} size="md" />
                  <p className="mt-4 text-sm font-medium text-ink">Cargando seguimiento judicial</p>
                  <p className="mt-1 text-xs text-ink-soft">
                    {progresoPrecarga && progresoPrecarga.total > 0
                      ? `Verificando boletines de Publicaciones Procesales: actuación ${progresoPrecarga.revisadas} de ${progresoPrecarga.total}...`
                      : "Trayendo la línea de tiempo del caso..."}
                  </p>
                  {publicacionesCaidas && (
                    <p className="mx-auto mt-3 max-w-sm text-xs text-amber-700">
                      Publicaciones Procesales no está respondiendo ahora mismo -- se dejó de intentar para las
                      actuaciones restantes. Puedes revisarlas más tarde con &quot;Volver a verificar&quot;.
                    </p>
                  )}
                </div>
              ) : (
                <>
                  <div className="grid gap-3 sm:grid-cols-2">
                    <div className="rounded-xl border border-line bg-paper p-4">
                      {linea.tieneProcesoCpnu ? (
                        <Badge tone="success">
                          <Scales className="h-3 w-3" weight="bold" /> Proceso identificado en la Rama Judicial
                        </Badge>
                      ) : (
                        <Badge tone="neutral">
                          <WarningCircle className="h-3 w-3" weight="bold" /> Sin proceso CPNU identificado
                        </Badge>
                      )}
                      {!linea.tieneProcesoCpnu && (
                        <p className="mt-2 text-xs leading-relaxed text-ink-soft">
                          {caso?.radicadoId
                            ? "Todavía no se logra identificar este radicado en el motor ordinario -- puedes lanzar Legal-Pro (Croma) en la columna derecha para rescatarlo."
                            : "Este caso todavía no tiene un radicado asignado."}
                        </p>
                      )}
                    </div>
                    <div className="rounded-xl border border-line bg-paper p-4">
                      {linea.fechaUltimoSeguimiento ? (
                        <Badge tone={linea.ultimoSeguimientoExitoso ? "gold" : "danger"}>
                          <Clock className="h-3 w-3" weight="bold" />
                          Última revisión: {formatearFechaHora(linea.fechaUltimoSeguimiento)}
                        </Badge>
                      ) : (
                        <Badge tone="neutral">
                          <Clock className="h-3 w-3" weight="bold" /> Todavía no se ha revisado
                        </Badge>
                      )}
                    </div>
                  </div>

                  {(linea.sujetosProcesales ||
                    linea.despachoJudicial ||
                    linea.ponente ||
                    linea.tipoProceso) && (
                    <div className="mt-3 rounded-xl border border-line bg-paper p-4 text-sm text-ink">
                      {linea.sujetosProcesales && (
                        <ul className="space-y-0.5">
                          {formatearPartes(linea.sujetosProcesales).map((parte, i) => (
                            <li key={i} className="leading-relaxed">{parte}</li>
                          ))}
                        </ul>
                      )}
                      {linea.despachoJudicial && (
                        <p className="mt-1 text-xs text-ink-soft">
                          {linea.despachoJudicial}
                          {linea.departamentoJudicial && ` · ${linea.departamentoJudicial}`}
                        </p>
                      )}
                      <div className="mt-2 flex flex-wrap gap-1.5">
                        {linea.ponente && <Badge tone="neutral">Ponente: {linea.ponente}</Badge>}
                        {linea.tipoProceso && (
                          <Badge tone="neutral">
                            {linea.tipoProceso}
                            {linea.claseProceso && ` · ${linea.claseProceso}`}
                            {linea.subclaseProceso && ` · ${linea.subclaseProceso}`}
                          </Badge>
                        )}
                        {linea.recursoProceso && linea.recursoProceso !== "Sin Tipo de Recurso" && (
                          <Badge tone="neutral">Recurso: {linea.recursoProceso}</Badge>
                        )}
                        {linea.ubicacionProceso && <Badge tone="gold">Ubicación: {linea.ubicacionProceso}</Badge>}
                        {linea.fechaRadicacionProceso && (
                          <Badge tone="neutral">Radicado el {formatearFechaSolo(linea.fechaRadicacionProceso)}</Badge>
                        )}
                        {linea.procesoPrivado && <Badge tone="warning">Proceso privado</Badge>}
                        {linea.ultimaActualizacionRama && (
                          <Badge tone="neutral">Rama actualizó: {formatearFechaHora(linea.ultimaActualizacionRama)}</Badge>
                        )}
                      </div>
                    </div>
                  )}

                  {linea.eventos.length > 0 && (
                    <div className="mt-3 flex items-center justify-between gap-2 rounded-xl bg-ink/[0.03] px-4 py-2.5">
                      <p className="text-xs text-ink-soft">
                        {precargando
                          ? progresoPrecarga && progresoPrecarga.total > 0
                            ? `Verificando boletines: actuación ${progresoPrecarga.revisadas} de ${progresoPrecarga.total}...`
                            : "Verificando boletines de Publicaciones Procesales..."
                          : publicacionesCaidas
                            ? "Publicaciones Procesales no respondió -- algunas actuaciones quedaron sin verificar."
                            : "Boletines verificados para todas las actuaciones con fecha de publicación."}
                      </p>
                      {precargando ? (
                        <Spinner className="h-4 w-4 shrink-0 animate-spin text-ink-soft" weight="bold" />
                      ) : (
                        <button
                          type="button"
                          onClick={() => linea && precargarBoletinesManual(caso!.id, linea.eventos)}
                          className="flex shrink-0 items-center gap-1 text-xs font-medium text-gold-deep hover:underline"
                        >
                          <ArrowsClockwise className="h-3 w-3" weight="bold" />
                          Volver a verificar
                        </button>
                      )}
                    </div>
                  )}

                  {linea.eventos.length === 0 ? (
                    <div className="py-12 text-center">
                      <p className="text-sm text-ink-soft">
                        {linea.tieneProcesoCpnu
                          ? "Todavía no se ha detectado ninguna actuación para este proceso."
                          : "No hay historial propio todavía para este caso."}
                      </p>
                    </div>
                  ) : (
                    <ol className="mt-6 space-y-4 border-l-2 border-line pl-6">
                      {linea.eventos.map((e, i) => (
                        <li key={i} className="relative">
                          <span
                            className={`absolute -left-[29px] top-5 h-3.5 w-3.5 rounded-full ring-4 ring-surface ${
                              e.esFundamental ? "bg-gold-deep" : "bg-ink/25"
                            }`}
                          />
                          <div className="rounded-xl border border-line bg-paper p-4 transition-colors hover:border-gold-deep/30">
                            <div className="flex flex-wrap items-center gap-2">
                              <p className="text-sm font-semibold text-ink">{e.nombreActuacion}</p>
                              {e.esFundamental && <Badge tone="gold">De fondo</Badge>}
                              {e.conDocumentos && (
                                <Badge tone="neutral">
                                  <CheckCircle className="h-3 w-3" weight="bold" /> Con documento
                                </Badge>
                              )}
                              {e.posibleLiquidacionCostas && (
                                <Badge tone="warning">Posible liquidación de costas</Badge>
                              )}
                              {e.posibleAcreedorSubrogatorio && (
                                <Badge tone="warning">Posible acreedor subrogatorio</Badge>
                              )}
                              {e.posibleMedidaCautelar && (
                                <Badge tone="warning">Posible medida cautelar</Badge>
                              )}
                              {e.consecutivo !== null && (
                                <span className="text-xs text-ink-soft/70">Actuación #{e.consecutivo}</span>
                              )}
                            </div>
                            {e.anotacion && (
                              <p className="mt-2 text-xs leading-relaxed text-ink-soft">{e.anotacion}</p>
                            )}
                            {e.estadoTermino && e.fechaVencimientoTermino && (
                              <div className="mt-3">
                                <Badge
                                  tone={
                                    e.estadoTermino === "VENCIDO"
                                      ? "danger"
                                      : e.estadoTermino === "POR_VENCER"
                                        ? "warning"
                                        : "success"
                                  }
                                >
                                  {e.descripcionTermino} · vence {formatearFecha(e.fechaVencimientoTermino)} (
                                  {e.diasHabilesTermino} días hábiles)
                                  {e.terminoEsOficial ? " · informado por el despacho" : " · estimado"}
                                </Badge>
                              </div>
                            )}
                            <p className="mt-3 border-t border-line/70 pt-2.5 text-xs text-ink-soft/80">
                              {e.fechaRegistro ? `Publicada ${formatearFechaHora(e.fechaRegistro)}` : "Sin fecha de publicación"}
                              {" · "}detectada {formatearFechaHora(e.fechaDetectada)}
                            </p>

                            {e.fechaRegistro && !verificadoPorIndice[i] && (
                              <p className="mt-3 flex items-center gap-1.5 text-xs text-ink-soft">
                                <Spinner className="h-3 w-3 animate-spin" weight="bold" /> Verificando boletín...
                              </p>
                            )}
                            {verificadoPorIndice[i] && candidatosPorIndice[i] && candidatosPorIndice[i].length > 0 && (
                              <div className="mt-3">
                                <AdminButton variant="ghost" onClick={() => setIndiceAbierto(indiceAbierto === i ? null : i)}>
                                  {indiceAbierto === i ? (
                                    <CaretUp className="h-3.5 w-3.5" weight="bold" />
                                  ) : (
                                    <CaretDown className="h-3.5 w-3.5" weight="bold" />
                                  )}
                                  Boletín del Estado ({candidatosPorIndice[i].length} documento
                                  {candidatosPorIndice[i].length === 1 ? "" : "s"})
                                </AdminButton>
                                {indiceAbierto === i && renderCandidatos(candidatosPorIndice[i], e.nombreActuacion)}
                              </div>
                            )}
                          </div>
                        </li>
                      ))}
                    </ol>
                  )}
                </>
              )}
            </div>

            {/* =====================================================================
                COLUMNA DERECHA: MOTOR LEGAL-PRO (CROMA API + MEMORIA DE 7 DÍAS)
               ===================================================================== */}
            <div className="overflow-y-auto bg-paper/40 px-6 py-5">
              {/* Cabecera de Legal-Pro con botón de lanzamiento / actualización */}
              <div className="mb-4 flex flex-wrap items-center justify-between gap-3 border-b border-line pb-3">
                <div className="flex items-center gap-2.5">
                  <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-gold text-ink-fixed shadow-sm">
                    <ShieldCheck className="h-4.5 w-4.5" weight="fill" />
                  </span>
                  <div>
                    <div className="flex items-center gap-2">
                      <h3 className="text-sm font-semibold text-ink">Legal-Pro · Motor Profundo (Croma API)</h3>
                      {cuotaCroma && (
                        <span className="inline-flex items-center gap-1 rounded-full bg-gold-pale/60 px-2 py-0.5 text-[10px] font-bold text-gold-deep">
                          <Lightning className="h-3 w-3" weight="fill" />
                          {cuotaCroma.consultasRestantes} disp.
                          {cuotaCroma.totalKeysConfiguradas && cuotaCroma.totalKeysConfiguradas > 1
                            ? ` · Key #${cuotaCroma.keyActivaIndice ?? 1}/${cuotaCroma.totalKeysConfiguradas}`
                            : ""}
                        </span>
                      )}
                    </div>
                    <p className="text-[11px] text-ink-soft">
                      Compara contra el motor SIE, extrae correos del juzgado, términos y guarda memoria por 7 días
                    </p>
                  </div>
                </div>

                <AdminButton
                  variant="primary"
                  disabled={extrayendoCroma || !caso?.radicadoId}
                  onClick={onEjecutarLegalPro}
                >
                  {informeCroma ? (
                    <>
                      <ArrowsClockwise className={`h-3.5 w-3.5 ${extrayendoCroma ? "animate-spin" : ""}`} weight="bold" />
                      {extrayendoCroma ? "Actualizando Croma..." : "Actualizar Informe Croma"}
                    </>
                  ) : (
                    <>
                      <Sparkle className="h-3.5 w-3.5" weight="fill" />
                      {extrayendoCroma ? "Extrayendo con Croma..." : "Lanzar Legal-Pro (Croma)"}
                    </>
                  )}
                </AdminButton>
              </div>

              {/* Banner de Memoria de 7 Días */}
              {informeCroma && informeCroma.fechaExtraccionInforme && (
                <div className="mb-4 flex flex-wrap items-center justify-between gap-2 rounded-xl border border-gold/40 bg-gold-pale/40 px-3.5 py-2.5 text-xs">
                  <div className="flex items-center gap-2 text-ink">
                    <Database className="h-4 w-4 shrink-0 text-gold-deep" weight="fill" />
                    <span>
                      <strong>Informe Croma guardado {informeCroma.guardadoHaceTexto ?? "recientemente"}</strong>
                      {" "}({formatearFechaHoraBogota(informeCroma.fechaExtraccionInforme)})
                    </span>
                  </div>
                  <span className="rounded-full bg-surface px-2.5 py-0.5 text-[11px] font-bold text-gold-deep ring-1 ring-gold/30">
                    Al día 7 se borra ({informeCroma.diasParaBorrarInforme ?? 7} día(s) restantes)
                  </span>
                </div>
              )}

              {cargandoMemoriaCroma || extrayendoCroma ? (
                <LegalProWalkingLoader
                  titulo={extrayendoCroma ? "Extrayendo Legal-Pro en vivo" : "Verificando memoria de 7 días"}
                  subtitulo={
                    extrayendoCroma
                      ? "Consultando expediente, actuaciones, términos, boletines y correos oficiales del juzgado..."
                      : "Revisando caché de 7 días para evitar consumos adicionales de cuota..."
                  }
                />
              ) : !informeCroma ? (
                <div className="rounded-2xl border border-dashed border-line bg-paper p-8 text-center">
                  <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-gold/15 text-gold-deep">
                    <ShieldCheck className="h-6 w-6" weight="duotone" />
                  </div>
                  <h4 className="mt-3 font-display text-base font-semibold text-ink">
                    Comparar y profundizar con Legal-Pro (Croma)
                  </h4>
                  <p className="mx-auto mt-1.5 max-w-md text-xs leading-relaxed text-ink-soft">
                    Si el abogado desea contrastar los resultados del motor SIE o rescatar datos adicionales (correos oficiales del juzgado, partes completas, actuaciones faltantes y boletines), presiona{" "}
                    <strong>&quot;Lanzar Legal-Pro (Croma)&quot;</strong>.
                  </p>
                  <p className="mt-2 text-[11px] font-medium text-gold-deep">
                    El informe queda guardado en memoria durante 7 días y se borra automáticamente al día 7.
                  </p>
                  <div className="mt-4">
                    <AdminButton
                      variant="primary"
                      disabled={!caso?.radicadoId}
                      onClick={onEjecutarLegalPro}
                    >
                      <Sparkle className="h-4 w-4" weight="fill" />
                      Lanzar Legal-Pro y Comparar Motores
                    </AdminButton>
                  </div>
                </div>
              ) : (
                <div className="space-y-4">
                  {/* Tarjetas de Comparación entre Motores */}
                  <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-4">
                    <div className="rounded-xl border border-line bg-paper p-3">
                      <p className="text-[10px] font-semibold uppercase text-ink-soft">Actuaciones Croma</p>
                      <div className="mt-1 flex items-baseline gap-1.5">
                        <span className="font-display text-xl font-bold text-ink">{informeCroma.totalActuaciones}</span>
                        {informeCroma.actuacionesNuevasInyectadasEnSie > 0 && (
                          <span className="rounded-full bg-emerald-500/15 px-1.5 py-0.5 text-[10px] font-bold text-emerald-700">
                            +{informeCroma.actuacionesNuevasInyectadasEnSie} nuevas
                          </span>
                        )}
                      </div>
                    </div>

                    <div className="rounded-xl border border-line bg-paper p-3">
                      <p className="text-[10px] font-semibold uppercase text-ink-soft">De Fondo</p>
                      <p className="mt-1 font-display text-xl font-bold text-gold-deep">
                        {informeCroma.actuacionesFundamentalesTotal}
                      </p>
                    </div>

                    <div className="rounded-xl border border-line bg-paper p-3">
                      <p className="text-[10px] font-semibold uppercase text-ink-soft">Correos Juzgado</p>
                      <p className="mt-1 font-display text-xl font-bold text-ink">
                        {informeCroma.buzonesDespacho.length}
                      </p>
                    </div>

                    <div className="rounded-xl border border-line bg-paper p-3">
                      <p className="text-[10px] font-semibold uppercase text-ink-soft">Boletines</p>
                      <p className="mt-1 font-display text-xl font-bold text-blue-600">
                        {informeCroma.boletinesDisponiblesTotal}
                      </p>
                    </div>
                  </div>

                  {/* Ficha Técnica Completa de Croma + Correo del Juzgado */}
                  <div className="rounded-xl border border-line bg-paper p-4 space-y-3">
                    <div className="flex flex-wrap items-center justify-between gap-2 border-b border-line pb-2">
                      <div className="flex items-center gap-2">
                        <Buildings className="h-4 w-4 text-gold-deep" weight="bold" />
                        <span className="text-xs font-semibold text-ink">
                          {informeCroma.despacho || informeCroma.buzonesDespacho[0]?.nombre || "Despacho no reportado"}
                        </span>
                      </div>
                      {informeCroma.departamento && (
                        <span className="inline-flex items-center gap-1 text-xs text-ink-soft">
                          <MapPin className="h-3.5 w-3.5" /> {informeCroma.departamento}
                        </span>
                      )}
                    </div>

                    {/* Correo Oficial del Juzgado (siempre visible en la ficha principal) */}
                    {informeCroma.buzonesDespacho.length > 0 && (
                      <div className="rounded-xl border border-gold/30 bg-gold-pale/25 p-3">
                        <p className="text-[10px] font-bold uppercase tracking-wide text-gold-deep">
                          Correo Oficial del Despacho Judicial (Croma API)
                        </p>
                        <div className="mt-1.5 space-y-2">
                          {informeCroma.buzonesDespacho.map((bz, idx) => (
                            <div key={`${bz.email}-${idx}`} className="flex flex-wrap items-center justify-between gap-2">
                              <div className="min-w-0">
                                <div className="flex flex-wrap items-center gap-2">
                                  <EnvelopeSimple className="h-4 w-4 shrink-0 text-gold-deep" weight="fill" />
                                  <a
                                    href={`mailto:${bz.email}?subject=Radicado%20${informeCroma.radicado}`}
                                    className="font-mono text-xs font-bold text-ink underline-offset-2 hover:underline"
                                  >
                                    {bz.email}
                                  </a>
                                  <span className="rounded-full bg-surface px-2 py-0.5 text-[10px] font-semibold text-ink-soft ring-1 ring-line">
                                    {bz.tipoCuenta || "Despacho"}
                                  </span>
                                </div>
                                <p className="mt-0.5 text-[11px] text-ink-soft">
                                  {[
                                    bz.nombre,
                                    bz.corporacion,
                                    bz.especialidad,
                                    bz.ciudad ? `${bz.ciudad} (${bz.departamento})` : bz.departamento,
                                    bz.seccional ? `Seccional ${bz.seccional}` : "",
                                    bz.codigoDespacho ? `Cód. 12 dígitos: ${bz.codigoDespacho}` : "",
                                  ]
                                    .filter(Boolean)
                                    .join(" · ")}
                                </p>
                              </div>
                              <button
                                type="button"
                                onClick={() => copiarTexto(bz.email, "Correo del juzgado")}
                                className="inline-flex items-center gap-1 rounded-lg bg-gold px-2.5 py-1 text-[11px] font-semibold text-ink-fixed hover:bg-gold-deep"
                              >
                                {copiadoTexto === bz.email ? <Check className="h-3 w-3" weight="bold" /> : <Copy className="h-3 w-3" weight="bold" />}
                                {copiadoTexto === bz.email ? "Copiado" : "Copiar"}
                              </button>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Partes procesales de Croma */}
                    {informeCroma.sujetosProcesales && (
                      <div>
                        <p className="mb-1 text-[10px] font-bold uppercase tracking-wide text-ink-soft/70">
                          Partes en Croma
                        </p>
                        <ul className="space-y-1">
                          {formatearPartes(informeCroma.sujetosProcesales).map((p, idx) => (
                            <li key={idx} className="rounded-lg bg-surface px-2.5 py-1 text-xs text-ink">
                              {p}
                            </li>
                          ))}
                        </ul>
                      </div>
                    )}

                    {/* Metadatos completos de Croma */}
                    <div className="grid grid-cols-2 gap-2 rounded-xl bg-surface p-3 text-[11px]">
                      <div>
                        <span className="block text-[10px] uppercase text-ink-soft/70">Radicación</span>
                        <strong className="text-ink">
                          {informeCroma.fechaInicio ? formatearFechaSolo(informeCroma.fechaInicio) : "—"}
                        </strong>
                      </div>
                      <div>
                        <span className="block text-[10px] uppercase text-ink-soft/70">Última Actuación Croma</span>
                        <strong className="text-ink">
                          {informeCroma.fechaUltimaActuacion ? formatearFechaSolo(informeCroma.fechaUltimaActuacion) : "—"}
                        </strong>
                      </div>
                      <div>
                        <span className="block text-[10px] uppercase text-ink-soft/70">ID Proceso / Conn Croma</span>
                        <strong className="font-mono text-ink">
                          {informeCroma.idProcesoCpnu ?? "—"}
                          {informeCroma.connectionIdCroma ? ` (Conn #${informeCroma.connectionIdCroma})` : ""}
                        </strong>
                      </div>
                      <div>
                        <span className="block text-[10px] uppercase text-ink-soft/70">Código Despacho (12 díg.)</span>
                        <strong className="font-mono text-ink">
                          {informeCroma.codigoDespacho12Digitos || informeCroma.radicado.slice(0, 12)}
                        </strong>
                      </div>
                      {informeCroma.idsProcesosRelacionados && informeCroma.idsProcesosRelacionados.length > 1 && (
                        <div className="col-span-2">
                          <span className="block text-[10px] uppercase text-ink-soft/70">IDs Procesos Relacionados (Instancias)</span>
                          <strong className="font-mono text-ink">{informeCroma.idsProcesosRelacionados.join(", ")}</strong>
                        </div>
                      )}
                    </div>

                    {/* Bloque directo de Links de Boletines y Documentos recopilados del caso */}
                    {(informeCroma.boletinesCacheados.length > 0 || informeCroma.documentosEnVivo.length > 0) && (
                      <div className="mt-3 rounded-xl border border-blue-500/25 bg-blue-500/5 p-3">
                        <p className="mb-2 flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-wider text-blue-700">
                          <FileText weight="bold" className="h-3.5 w-3.5" />
                          Links directos de Boletines / Providencias recopilados ({informeCroma.boletinesDisponiblesTotal})
                        </p>
                        <div className="space-y-1.5">
                          {informeCroma.boletinesCacheados.map((b) => (
                            <div
                              key={b.id}
                              className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-line bg-surface px-2.5 py-1.5"
                            >
                              <span className="truncate text-xs font-medium text-ink">{b.nombreArchivo}</span>
                              <div className="flex items-center gap-1.5">
                                <button
                                  type="button"
                                  onClick={() => abrirPdfBoletin(b.id).catch(() => toast.error("No se pudo abrir el PDF."))}
                                  className="rounded-md bg-gold px-2 py-0.5 text-[11px] font-semibold text-ink-fixed hover:bg-gold-deep"
                                >
                                  Ver PDF
                                </button>
                                {b.urlOrigen && (
                                  <a
                                    href={b.urlOrigen}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="inline-flex items-center gap-1 rounded-md border border-line bg-paper px-2 py-0.5 text-[11px] font-medium text-ink hover:bg-ink/5"
                                  >
                                    <ArrowSquareOut className="h-3 w-3" weight="bold" />
                                    Link
                                  </a>
                                )}
                              </div>
                            </div>
                          ))}
                          {informeCroma.documentosEnVivo
                            .filter(
                              (doc) =>
                                !informeCroma.boletinesCacheados.some((b) => b.urlOrigen === doc.urlDescarga),
                            )
                            .map((doc, idx) => (
                              <div
                                key={`${doc.urlDescarga}-${idx}`}
                                className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-line bg-surface px-2.5 py-1.5"
                              >
                                <span className="truncate text-xs font-medium text-ink">{doc.nombreArchivo}</span>
                                <a
                                  href={doc.urlDescarga}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  className="inline-flex items-center gap-1 rounded-md border border-line bg-paper px-2 py-0.5 text-[11px] font-medium text-ink hover:bg-ink/5"
                                >
                                  <ArrowSquareOut className="h-3 w-3" weight="bold" />
                                  Abrir Link
                                </a>
                              </div>
                            ))}
                        </div>
                      </div>
                    )}
                  </div>

                  {/* Pestañas de Croma: Actuaciones / Boletines / Correos */}
                  <div className="flex flex-wrap items-center gap-1.5 border-b border-line pb-2">
                    <button
                      type="button"
                      onClick={() => setPestanaCroma("actuaciones")}
                      className={`rounded-xl px-3 py-1.5 text-xs font-semibold transition-all ${
                        pestanaCroma === "actuaciones" ? "bg-ink text-paper" : "text-ink-soft hover:bg-ink/5"
                      }`}
                    >
                      Actuaciones ({informeCroma.actuaciones.length})
                    </button>
                    <button
                      type="button"
                      onClick={() => setPestanaCroma("boletines")}
                      className={`rounded-xl px-3 py-1.5 text-xs font-semibold transition-all ${
                        pestanaCroma === "boletines" ? "bg-ink text-paper" : "text-ink-soft hover:bg-ink/5"
                      }`}
                    >
                      Boletines y Links ({informeCroma.boletinesDisponiblesTotal})
                    </button>
                    <button
                      type="button"
                      onClick={() => setPestanaCroma("correos")}
                      className={`rounded-xl px-3 py-1.5 text-xs font-semibold transition-all ${
                        pestanaCroma === "correos" ? "bg-ink text-paper" : "text-ink-soft hover:bg-ink/5"
                      }`}
                    >
                      Directorio Juzgado ({informeCroma.buzonesDespacho.length})
                    </button>
                  </div>

                  {pestanaCroma === "actuaciones" && (
                    <div className="space-y-3">
                      {informeCroma.actuaciones.map((act, idx) => (
                        <div
                          key={`${act.id ?? idx}-${idx}`}
                          className={`rounded-xl border p-4 ${
                            act.esFundamental ? "border-gold/50 bg-gold-pale/20" : "border-line bg-paper"
                          }`}
                        >
                          <div className="flex flex-wrap items-center justify-between gap-2">
                            <div className="flex flex-wrap items-center gap-2">
                              {act.consecutivo !== null && (
                                <span className="rounded bg-ink/10 px-1.5 py-0.5 font-mono text-[11px] font-bold text-ink">
                                  #{act.consecutivo}
                                </span>
                              )}
                              <span className="text-sm font-semibold text-ink">{act.nombreActuacion}</span>
                              {act.esFundamental && <Badge tone="gold">De fondo</Badge>}
                              {act.conDocumentos && <Badge tone="neutral">Con documento</Badge>}
                            </div>
                            <span className="font-mono text-xs text-ink-soft">
                              {act.fechaActuacion
                                ? formatearFechaHora(act.fechaActuacion)
                                : act.fechaRegistro
                                  ? formatearFechaHora(act.fechaRegistro)
                                  : "—"}
                            </span>
                          </div>
                          {act.anotacion && (
                            <p className="mt-2 whitespace-pre-line text-xs leading-relaxed text-ink-soft">
                              {act.anotacion}
                            </p>
                          )}
                          {(act.fechaInicialTermino || act.fechaFinalTermino) && (
                            <p className="mt-2 text-[11px] text-ink-soft">
                              {act.fechaInicialTermino && `Inicio término: ${formatearFechaSolo(act.fechaInicialTermino)} `}
                              {act.fechaFinalTermino && `· Fin término: ${formatearFechaSolo(act.fechaFinalTermino)}`}
                            </p>
                          )}
                        </div>
                      ))}
                    </div>
                  )}

                  {pestanaCroma === "boletines" && (
                    <div className="space-y-2.5">
                      {informeCroma.boletinesCacheados.length === 0 && informeCroma.documentosEnVivo.length === 0 ? (
                        <p className="rounded-xl border border-line bg-paper p-6 text-center text-xs text-ink-soft">
                          No hay boletines disponibles para las fechas recientes de este proceso.
                        </p>
                      ) : (
                        <>
                          {informeCroma.boletinesCacheados.map((b) => (
                            <div
                              key={b.id}
                              className="rounded-xl border border-line bg-paper p-3.5"
                            >
                              <div className="flex flex-wrap items-center justify-between gap-2">
                                <div className="min-w-0 flex-1">
                                  <p className="truncate text-xs font-semibold text-ink">{b.nombreArchivo}</p>
                                  <p className="text-[11px] text-ink-soft">
                                    Guardado en servidor · Descargado: {formatearFechaHora(b.fechaDescarga)}
                                  </p>
                                </div>
                                <div className="flex flex-wrap items-center gap-1.5">
                                  <button
                                    type="button"
                                    onClick={() => abrirPdfBoletin(b.id).catch(() => toast.error("No se pudo abrir el PDF."))}
                                    className="rounded-lg border border-line bg-surface px-2.5 py-1 text-xs font-medium text-ink hover:bg-ink/5"
                                  >
                                    Ver PDF
                                  </button>
                                  <button
                                    type="button"
                                    onClick={() =>
                                      descargarBoletin(b.id, b.nombreArchivo).catch(() =>
                                        toast.error("No se pudo descargar el boletín."),
                                      )
                                    }
                                    className="inline-flex items-center gap-1 rounded-lg bg-gold px-2.5 py-1 text-xs font-semibold text-ink-fixed hover:bg-gold-deep"
                                  >
                                    <DownloadSimple className="h-3.5 w-3.5" weight="bold" />
                                    Descargar
                                  </button>
                                  {b.urlOrigen && (
                                    <>
                                      <a
                                        href={b.urlOrigen}
                                        target="_blank"
                                        rel="noopener noreferrer"
                                        className="inline-flex items-center gap-1 rounded-lg border border-line bg-surface px-2.5 py-1 text-xs font-medium text-ink hover:bg-ink/5"
                                      >
                                        <ArrowSquareOut className="h-3.5 w-3.5" weight="bold" />
                                        Link oficial
                                      </a>
                                      <button
                                        type="button"
                                        onClick={() => copiarTexto(b.urlOrigen!, "Link del documento")}
                                        className="inline-flex items-center gap-1 rounded-lg border border-line bg-surface px-2 py-1 text-xs font-medium text-ink-soft hover:bg-ink/5"
                                        title="Copiar link directo"
                                      >
                                        <Copy className="h-3 w-3" weight="bold" />
                                      </button>
                                    </>
                                  )}
                                </div>
                              </div>
                              {b.urlOrigen && (
                                <p className="mt-1.5 truncate font-mono text-[10px] text-ink-soft/80">
                                  {b.urlOrigen}
                                </p>
                              )}
                            </div>
                          ))}
                          {informeCroma.documentosEnVivo
                            .filter(
                              (doc) =>
                                !informeCroma.boletinesCacheados.some((b) => b.urlOrigen === doc.urlDescarga),
                            )
                            .map((doc, idx) => (
                              <div
                                key={`${doc.urlDescarga}-${idx}`}
                                className="rounded-xl border border-line bg-paper p-3.5"
                              >
                                <div className="flex flex-wrap items-center justify-between gap-2">
                                  <div className="min-w-0 flex-1">
                                    <p className="truncate text-xs font-semibold text-ink">{doc.nombreArchivo}</p>
                                    <p className="mt-0.5 truncate font-mono text-[10px] text-ink-soft/80">
                                      {doc.urlDescarga}
                                    </p>
                                  </div>
                                  <div className="flex items-center gap-1.5">
                                    <button
                                      type="button"
                                      onClick={() => copiarTexto(doc.urlDescarga, "Link directo")}
                                      className="inline-flex items-center gap-1 rounded-lg border border-line bg-surface px-2.5 py-1 text-xs font-medium text-ink hover:bg-ink/5"
                                    >
                                      <Copy className="h-3 w-3" weight="bold" />
                                      Copiar link
                                    </button>
                                    <a
                                      href={doc.urlDescarga}
                                      target="_blank"
                                      rel="noopener noreferrer"
                                      className="inline-flex items-center gap-1 rounded-lg bg-gold px-2.5 py-1 text-xs font-semibold text-ink-fixed hover:bg-gold-deep"
                                    >
                                      <ArrowSquareOut className="h-3.5 w-3.5" weight="bold" />
                                      Abrir Link / PDF
                                    </a>
                                  </div>
                                </div>
                              </div>
                            ))}
                        </>
                      )}
                    </div>
                  )}

                  {pestanaCroma === "correos" && (
                    <div className="space-y-2.5">
                      {informeCroma.buzonesDespacho.map((bz, idx) => (
                        <div key={`${bz.email}-${idx}`} className="rounded-xl border border-line bg-paper p-3.5">
                          <div className="flex items-center justify-between gap-2">
                            <span className="font-mono text-xs font-bold text-ink">{bz.email}</span>
                            <button
                              type="button"
                              onClick={() => copiarTexto(bz.email, "Correo del juzgado")}
                              className="inline-flex items-center gap-1 rounded-lg bg-gold px-2.5 py-1 text-xs font-semibold text-ink-fixed hover:bg-gold-deep"
                            >
                              <Copy className="h-3 w-3" weight="bold" /> Copiar
                            </button>
                          </div>
                          <p className="mt-1 text-xs font-medium text-ink">{bz.nombre}</p>
                          <p className="mt-0.5 text-[11px] text-ink-soft">
                            {[bz.corporacion, bz.especialidad, bz.ciudad, bz.departamento, bz.seccional]
                              .filter(Boolean)
                              .join(" · ")}
                          </p>
                        </div>
                      ))}
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
