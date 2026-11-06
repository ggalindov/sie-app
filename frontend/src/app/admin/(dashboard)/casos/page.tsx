"use client";

import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { useSearchParams } from "next/navigation";
import { Dialog } from "@base-ui/react/dialog";
import { toast } from "sonner";
import {
  Archive,
  ArrowsClockwise,
  Bell,
  CalendarPlus,
  CheckSquare,
  Clock,
  ClockCounterClockwise,
  EnvelopeSimple,
  FileText,
  Funnel,
  HourglassMedium,
  MagnifyingGlass,
  PencilSimple,
  PhoneSlash,
  Plus,
  ShieldCheck,
  Sparkle,
  Spinner,
  UserCircle,
  WarningCircle,
  WhatsappLogo,
  X,
} from "@phosphor-icons/react";
import {
  listarCasos,
  crearCaso,
  sincronizarCasos,
  enviarCorreosPendientesCasos,
  enviarReporteSemanalCasos,
  contarTareasPendientesPorCaso,
  finalizarCaso,
  ApiError,
  type CasoAdmin,
  type FuenteCaso,
} from "@/lib/admin-api";
import { useAuth } from "@/lib/auth-context";
import { AdminPageHeader, AdminCard, AdminButton, Badge, NotificationBadge, EmptyState, AdminLoader } from "@/components/admin/ui";
import { EnvioLoteProgreso } from "@/components/admin/envio-lote-progreso";
import { TareasDeCasoModal, type ObjetivoTareas } from "@/components/admin/tareas-de-caso-modal";
import { DocumentosDeCasoModal, type CasoParaDocumentos } from "@/components/admin/documentos-de-caso-modal";
import { SeguimientoDeCasoModal, type CasoParaSeguimiento } from "@/components/admin/seguimiento-de-caso-modal";
import { EditarCasoModal } from "@/components/admin/editar-caso-modal";
import { BusquedaCromaModal, type DatosCasoDesdeCroma } from "@/components/admin/busqueda-croma-modal";
import { LegalProCromaModal } from "@/components/admin/legal-pro-croma-modal";
import { formatearFechaBogota, formatearFechaHoraBogota } from "@/lib/fecha";

const formatearFecha = formatearFechaBogota;
const formatearFechaHora = formatearFechaHoraBogota;
const formatearFechaSolo = formatearFechaBogota;

// Pedido explícito del usuario: "que se vean las partes que estan en el sistema del estado,
// demandante, demandado, todas las partes publicas" -- sujetosProcesales llega del CPNU como
// un solo string con todas las partes separadas por " | " y saltos de línea \r\n crudos
// dentro de cada una (ej. "Demandante/accionante: JUAN\r\n PEREZ | Demandado: ANA\r\n GOMEZ"),
// nunca ya separado en un arreglo -- se parte y se limpia aquí para mostrar una parte por
// línea en vez de un párrafo con saltos de línea literales rotos.
function formatearPartes(sujetosProcesales: string): string[] {
  return sujetosProcesales
    .split("|")
    .map((parte) => parte.replace(/\s*\r?\n\s*/g, " ").replace(/\s+/g, " ").trim())
    .filter(Boolean);
}

// Para la vista de tabla (pedido explícito del usuario: "como en un libro de excel") --
// separa demandante/demandado en columnas propias en vez de un solo bloque de texto, igual
// que IUS lo muestra. El CPNU no siempre usa la palabra exacta "Demandante"/"Demandado"
// (ej. "Demandante/accionante", "Demandado/indiciado/causante" en tutelas y procesos
// penales) -- por eso se busca por coincidencia parcial, no por igualdad exacta.
const RE_DEMANDANTE = /demandante|accionante|ejecutante|denunciante/i;
const RE_DEMANDADO = /demandado|accionado|indiciado|causante|ejecutado|denunciado/i;

function extraerParte(sujetosProcesales: string | null, patron: RegExp): string {
  if (!sujetosProcesales) return "";
  const encontrada = formatearPartes(sujetosProcesales).find((parte) => patron.test(parte));
  if (!encontrada) return "";
  const i = encontrada.indexOf(":");
  return (i === -1 ? encontrada : encontrada.slice(i + 1)).trim();
}

type CriterioOrden = "id" | "creado" | "actualizado";

// "actualizado" = fechaUltimoMovimientoJudicial -- corrección real pedida por el usuario:
// "un caso que tuvo movimiento hoy tiene que salir arriba, realmente no están saliendo en
// orden". Antes esto ordenaba por fechaUltimaActuacionDetectada (cuándo NUESTRO sistema vio
// la actuación por primera vez), pero el historial completo de los 362 casos reales se cargó
// de una sola vez en un backfill masivo, así que esa fecha deja casi empatados a un caso con
// movimiento real de HOY y a uno con movimiento real de hace meses: ambos fueron
// "detectados" en la misma corrida. fechaUltimoMovimientoJudicial es la fecha real en que el
// despacho registró el movimiento (ver backend ActuacionJudicialRegistradaRepository.
// ultimaFechaMovimientoPorCaso()), la única que responde de verdad a la pregunta. Los casos
// sin ninguna actuación con fecha todavía (null) siempre quedan al final, sin importar la
// dirección: "sin movimiento" no es ni más viejo ni más nuevo, es un estado aparte.
function compararCasos(a: CasoAdmin, b: CasoAdmin, criterio: CriterioOrden, direccion: "asc" | "desc"): number {
  let base: number;
  if (criterio === "id") {
    base = a.id - b.id;
  } else if (criterio === "creado") {
    base = new Date(a.fechaCreacion).getTime() - new Date(b.fechaCreacion).getTime();
  } else {
    const fa = a.fechaUltimoMovimientoJudicial ? new Date(a.fechaUltimoMovimientoJudicial).getTime() : null;
    const fb = b.fechaUltimoMovimientoJudicial ? new Date(b.fechaUltimoMovimientoJudicial).getTime() : null;
    if (fa === null && fb === null) return 0;
    if (fa === null) return 1;
    if (fb === null) return -1;
    base = fa - fb;
  }
  return direccion === "asc" ? base : -base;
}

// "Todos" + las 3 fuentes activas de produccion + Manuales.
// Pedido explícito del usuario: "deja solo Judiciales, Super intendencia y procesos comisaria como esta en produccion... posteriormente integramos las otras hojas"
const FUENTES_FILTRO: { valor: FuenteCaso | "TODOS"; label: string }[] = [
  { valor: "TODOS", label: "Todos" },
  { valor: "JUDICIALES", label: "Judiciales" },
  { valor: "SUPERINTENDENCIA", label: "Superintendencia" },
  { valor: "PROCESOS_COMISARIA", label: "Procesos Comisaría" },
  { valor: "MANUAL", label: "Manuales" },
];

export default function CasosAdminPage() {
  // Llega prellenado desde el buscador total del panel (ver buscador-global.tsx, navega a
  // /admin/casos?q=...) -- reusa el filtro 100% local que esta página ya tenía, no abre
  // ningún caso en concreto (no hay ruta [id] en este módulo, es de una sola página).
  const qInicial = useSearchParams().get("q") ?? "";
  // Link directo de "Radicado" en el reporte diario de vigilancia por correo (gerencia y
  // abogados, ver BoletinJudicialDigestService.enlaceSeguimientoInterno) -- pedido explícito
  // del usuario: ese link debe llevar al seguimiento del caso DENTRO del panel (no a la
  // consulta pública), sin saltarse el login (si no hay sesión, el guard normal de
  // /admin/** ya redirige a /admin/login antes de llegar aquí), pero si ya hay una sesión
  // abierta debe entrar directo al caso sin que el usuario tenga que buscarlo a mano.
  const radicadoInicial = useSearchParams().get("radicado") ?? "";
  const { sesion } = useAuth();
  // Pedido explícito del usuario (2026-11-06): "para abogado simplemente deja actualizar
  // desde hoja, busqueda legal tech y legal pro, y crear caso" -- el ABOGADO ya no ve las
  // dos acciones de comunicación masiva a clientes (notificaciones pendientes, reporte
  // periódico), reservadas para ADMIN_GENERAL.
  const esAdmin = sesion?.rol === "ADMIN_GENERAL";
  const [casos, setCasos] = useState<CasoAdmin[] | null>(null);
  const [modalAbierto, setModalAbierto] = useState(false);
  const [sincronizando, setSincronizando] = useState(false);
  const [enviandoPendientes, setEnviandoPendientes] = useState(false);
  const [enviandoReporteSemanal, setEnviandoReporteSemanal] = useState(false);
  const [finalizandoId, setFinalizandoId] = useState<number | null>(null);
  const [filtroFuente, setFiltroFuente] = useState<FuenteCaso | "TODOS">("TODOS");
  const [busqueda, setBusqueda] = useState(qInicial);
  // Pedido explícito del usuario: "implementar un nuevo apartado solo para ver asi como en
  // IUS... para ver todos los casos como en un libro de excel" -- un segundo modo de ver la
  // MISMA lista ya cargada/filtrada/ordenada (nunca una página aparte con su propia carga),
  // como una tabla densa de una sola fila por caso en vez de las tarjetas grandes. Las
  // tarjetas siguen siendo la vista por defecto -- esto es un complemento, no un reemplazo.
  const [vista, setVista] = useState<"tarjetas" | "tabla">("tarjetas");
  // Pedido explícito del usuario (origen): "implementa un ordenador por actualizacion, deja
  // ese predeterminado, arriba se van poniendo los que tuvieron actualizacion de
  // seguimiento" -- el caso con el movimiento judicial real más reciente va primero (ver
  // fechaUltimoMovimientoJudicial en compararCasos). Pedido explícito posterior
  // (2026-11-06): "elimina ordenar casos" -- se quitó el desplegable/botón que permitía
  // cambiar el criterio a mano; el criterio queda fijo en este único orden, que ya era el
  // predeterminado y el más usado.
  const ordenarPor: CriterioOrden = "actualizado";
  const ordenDireccion: "asc" | "desc" = "desc";
  // Pedido explícito del usuario: "implementa un filtrador para los botones, uno que filtre
  // todos los que tiene boletines disponibles (azul) y otro para casos sin revisar (rojo)" --
  // dos interruptores independientes (no mutuamente excluyentes: un caso puede tener ambos a
  // la vez), igual que los dos puntos pueden estar prendidos juntos en la tarjeta.
  const [soloSinRevisar, setSoloSinRevisar] = useState(false);
  const [soloConBoletin, setSoloConBoletin] = useState(false);
  const [casoConTareasAbierto, setCasoConTareasAbierto] = useState<ObjetivoTareas | null>(null);
  const [casoConDocumentosAbierto, setCasoConDocumentosAbierto] = useState<CasoParaDocumentos | null>(null);
  const [casoConSeguimientoAbierto, setCasoConSeguimientoAbierto] = useState<CasoParaSeguimiento | null>(null);
  const [casoEditando, setCasoEditando] = useState<CasoAdmin | null>(null);
  const [cromaModalAbierto, setCromaModalAbierto] = useState(false);
  const [legalProAbierto, setLegalProAbierto] = useState(false);
  const [casoParaLegalPro, setCasoParaLegalPro] = useState<CasoAdmin | null>(null);
  const [datosInicialesNuevoCaso, setDatosInicialesNuevoCaso] = useState<DatosCasoDesdeCroma | null>(null);
  const [tareasPendientesPorCaso, setTareasPendientesPorCaso] = useState<Record<string, number>>({});

  const cargar = useCallback(() => {
    listarCasos()
      .then(setCasos)
      .catch(() => toast.error("No se pudieron cargar los casos."));
  }, []);

  const cargarConteoTareas = useCallback(() => {
    contarTareasPendientesPorCaso()
      .then(setTareasPendientesPorCaso)
      .catch(() => {
        // Silencioso a propósito: el badge de tareas es un dato secundario, no debe
        // interrumpir con un toast de error la carga principal de la página de Casos.
      });
  }, []);

  useEffect(() => {
    cargar();
    cargarConteoTareas();
  }, [cargar, cargarConteoTareas]);

  // Abre el modal de seguimiento automáticamente la primera vez que los casos ya están
  // cargados, si llegamos con ?radicado=... en la URL (ver radicadoInicial arriba). El ref
  // evita que se vuelva a abrir solo si el usuario lo cierra y los casos se recargan después
  // (ej. tras sincronizar) -- debe abrirse UNA sola vez por visita, no cada vez que cambia el
  // array de casos.
  const yaAbrioSeguimientoInicial = useRef(false);
  useEffect(() => {
    if (!radicadoInicial || yaAbrioSeguimientoInicial.current || !casos) {
      return;
    }
    yaAbrioSeguimientoInicial.current = true;
    const caso = casos.find((c) => c.radicadoId === radicadoInicial);
    if (caso) {
      setCasoConSeguimientoAbierto({ id: caso.id, nombreCliente: caso.nombreCliente, radicadoId: caso.radicadoId });
    } else {
      toast.error(`No se encontró ningún caso con el radicado ${radicadoInicial}.`);
    }
  }, [casos, radicadoInicial]);

  // Bug real encontrado probando en vivo: pasarle a SeguimientoDeCasoModal una función
  // inline (definida directo en el JSX) como onRevisado generaba una identidad NUEVA en
  // cada render, y como el modal la tiene en las dependencias de su propio useCallback de
  // cargar(), eso disparaba una cascada de recargas infinita (confirmado en la pestaña de
  // red: decenas de peticiones repetidas a /linea-tiempo para el mismo caso) que además
  // reintentaba el precargado de boletines una y otra vez contra Publicaciones Procesales --
  // justo el patrón de exceso de peticiones que hay que evitar con ese portal. useCallback
  // con [] mantiene esta función con la misma identidad siempre.
  const onRevisadoSeguimiento = useCallback((casoId: number) => {
    setCasos((prev) =>
      prev ? prev.map((c) => (c.id === casoId ? { ...c, tieneActualizacionSinRevisar: false } : c)) : prev,
    );
  }, []);

  // Buscador 100% local (pedido explícito del usuario: "super rapido") -- filtra el array ya
  // cargado en memoria, sin ningún viaje al servidor, así que responde en cada tecla sin
  // ningún retraso perceptible. Busca por cliente, contacto, radicado y número de caso a la vez.
  const busquedaNormalizada = busqueda.trim().toLowerCase();
  const casosFiltrados =
    casos
      ?.filter((c) => filtroFuente === "TODOS" || c.fuente === filtroFuente)
      .filter((c) => !soloSinRevisar || c.tieneActualizacionSinRevisar)
      .filter((c) => !soloConBoletin || c.diasParaBorrarBoletin !== null)
      .filter((c) => {
        if (!busquedaNormalizada) return true;
        return (
          c.nombreCliente.toLowerCase().includes(busquedaNormalizada) ||
          (c.correoCliente?.toLowerCase().includes(busquedaNormalizada) ?? false) ||
          (c.telefonoCliente?.toLowerCase().includes(busquedaNormalizada) ?? false) ||
          (c.radicadoId?.toLowerCase().includes(busquedaNormalizada) ?? false) ||
          (c.numeroCaso?.toLowerCase().includes(busquedaNormalizada) ?? false)
        );
      })
      .sort((a, b) => compararCasos(a, b, ordenarPor, ordenDireccion)) ?? null;

  async function onSincronizar() {
    setSincronizando(true);
    try {
      const resumen = await sincronizarCasos();
      toast.success(
        `${resumen.casosNuevos} caso(s) nuevo(s), ${resumen.casosActualizados} actualizado(s)` +
          (resumen.casosEliminados > 0 ? `, ${resumen.casosEliminados} eliminado(s) (ya no están en la hoja)` : "") +
          (resumen.filasSinCorreo > 0 ? `, ${resumen.filasSinCorreo} sin correo capturado aún (no se les puede notificar todavía).` : "."),
      );
      if (resumen.fuentesConError.length > 0) {
        toast.error(
          `No se pudo leer: ${resumen.fuentesConError.join(", ")}. Las demás hojas sí se sincronizaron; intenta de nuevo en unos minutos para esa(s).`,
        );
      }
      if (resumen.radicadosDuplicados > 0) {
        toast.warning(
          `${resumen.radicadosDuplicados} radicado(s) no se pudieron asignar porque ya están duplicados en la hoja (el mismo radicado en dos filas distintas). Corrige el duplicado directamente en el Google Sheets y vuelve a actualizar.`,
        );
      }
      if (resumen.radicadosInvalidos > 0) {
        toast.warning(
          `${resumen.radicadosInvalidos} radicado(s) tienen un formato inválido en la hoja (más de 50 caracteres) y se ignoraron -- revisa esa(s) fila(s) directamente en el Google Sheets.`,
        );
      }
      cargar();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo sincronizar con la hoja.");
    } finally {
      setSincronizando(false);
    }
  }

  async function onEnviarPendientes() {
    setEnviandoPendientes(true);
    toast.info(
      "Enviando notificaciones pendientes -- va uno por uno con una pausa entre cada uno para no saturar el correo. Un lote grande puede tardar varios minutos.",
    );
    try {
      const resumen = await enviarCorreosPendientesCasos();
      const totalIntentado = resumen.correosEnviados + resumen.correosFallidos + resumen.whatsappEnviados + resumen.whatsappFallidos;
      if (totalIntentado === 0) {
        toast.info("No hay notificaciones pendientes por enviar.");
      } else {
        toast.success(
          `${resumen.correosEnviados} correo(s) y ${resumen.whatsappEnviados} WhatsApp confirmados como enviados.`,
        );
        if (resumen.correosFallidos > 0 || resumen.whatsappFallidos > 0) {
          toast.error(
            `${resumen.correosFallidos} correo(s) y ${resumen.whatsappFallidos} WhatsApp fallaron (incluso tras reintentar) -- quedaron pendientes para el próximo envío, revisa el Registro del sistema para el detalle.`,
          );
        }
        if (resumen.pendientesPorLimiteDiario > 0) {
          toast.info(
            `${resumen.pendientesPorLimiteDiario} caso(s) más quedaron pendientes por el límite diario de envíos (250/día) -- se enviarán automáticamente mañana.`,
          );
        }
      }
      cargar();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudieron enviar las notificaciones pendientes.");
    } finally {
      setEnviandoPendientes(false);
    }
  }

  async function onEnviarReporteSemanal() {
    if (!window.confirm("¿Enviar el reporte periódico a todos los clientes con caso activo que todavía no lo hayan recibido en este periodo (dos veces al mes: días 1 y 15)? Esto normalmente se envía solo, automáticamente -- úsalo solo para adelantarlo o probarlo.")) {
      return;
    }
    setEnviandoReporteSemanal(true);
    toast.info(
      "Enviando el reporte a todos los clientes con caso activo -- va uno por uno con una pausa entre cada uno. Puede tardar varios minutos con muchos casos.",
    );
    try {
      const resumen = await enviarReporteSemanalCasos();
      if (resumen.casosConReporte === 0) {
        toast.info("No hay casos pendientes del reporte de este periodo -- ya se les envió a todos, o todavía no hay casos con radicado asignado.");
      } else {
        toast.success(
          `${resumen.correosEnviados} correo(s) y ${resumen.whatsappEnviados} WhatsApp enviados de ${resumen.casosConReporte} caso(s) pendientes del reporte de este periodo.`,
        );
        if (resumen.correosFallidos > 0 || resumen.whatsappFallidos > 0) {
          toast.error(
            `${resumen.correosFallidos} correo(s) y ${resumen.whatsappFallidos} WhatsApp fallaron -- revisa el Registro del sistema para el detalle.`,
          );
        }
        if (resumen.pendientesPorLimiteDiario > 0) {
          toast.info(
            `${resumen.pendientesPorLimiteDiario} caso(s) más quedaron pendientes por el límite diario de envíos (250/día) -- se enviarán automáticamente mañana.`,
          );
        }
      }
      cargar();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo enviar el reporte de casos.");
    } finally {
      setEnviandoReporteSemanal(false);
    }
  }

  // Pedido explícito del usuario (2026-11-05): "un boton para cada caso el cual nos permita
  // finalizarlo, y el caso se remite a un nuevo apartado de casos finalizados... una vez
  // finalizado el sistema ya no le hara vigilancia". El caso desaparece de esta lista de
  // inmediato (se mueve a /admin/casos-finalizados) -- nunca se borra nada, el backend solo
  // apaga la bandera que alimenta el ciclo de vigilancia.
  async function onFinalizar(caso: CasoAdmin) {
    if (!window.confirm(`¿Finalizar el caso de ${caso.nombreCliente}? Se mueve a "Casos Finalizados" y el sistema deja de vigilarlo automáticamente. El último seguimiento queda guardado y se puede reabrir después si hace falta.`)) {
      return;
    }
    setFinalizandoId(caso.id);
    try {
      await finalizarCaso(caso.id);
      setCasos((prev) => (prev ? prev.filter((c) => c.id !== caso.id) : prev));
      toast.success(`Caso de ${caso.nombreCliente} finalizado -- movido a Casos Finalizados.`);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo finalizar el caso.");
    } finally {
      setFinalizandoId(null);
    }
  }

  return (
    <div>
      <AdminPageHeader
        title="Casos"
        description="Se sincronizan automáticamente desde el Google Sheets de seguimiento de la firma: número de caso, radicado y datos de contacto del cliente. La notificación del radicado se envía por correo y por WhatsApp (línea de atención de la firma). El estado real se consulta en vivo desde la misma hoja."
      />

      {/* Segundo rediseño pedido explícito del usuario (2026-11-06): "para abogado
          simplemente deja actualizar desde hoja, busqueda legal tech y legal pro, y crear
          caso pero hazlo mas chiquito e intuitivo" / "para administrador si dejalos todos
          pero ordena un poco mejor todo". Antes eran 6 tarjetas grandes sueltas en una
          grilla sin agrupar, iguales para cualquier rol. Ahora: (1) el ABOGADO solo ve las
          4 acciones operativas del día a día (sincronizar, crear, y las dos búsquedas
          judiciales); las 2 de comunicación masiva a clientes quedan solo para
          ADMIN_GENERAL. (2) las tarjetas son más chicas (menos padding, ícono más pequeño,
          sin la frase larga de descripción -- un tooltip la reemplaza). (3) para admin se
          agrupan por intención real (Sincronización | Comunicación a clientes | Búsqueda
          judicial) con un rótulo propio por grupo en vez de una sola grilla plana. */}
      <div className="mt-6 rounded-2xl border border-line bg-surface p-4">
        <p className="text-[11px] font-semibold uppercase tracking-wide text-ink-soft/60">Acciones</p>

        <div className="mt-3 grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
          <button
            type="button"
            onClick={onSincronizar}
            disabled={sincronizando}
            title="Trae los casos nuevos o editados directamente del Google Sheets de la firma."
            className="flex items-center gap-2.5 rounded-xl bg-gold-pale/40 p-2.5 text-left ring-1 ring-gold-deep/20 transition-colors hover:bg-gold-pale/60 disabled:opacity-60"
          >
            <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-gold text-ink-fixed">
              <ArrowsClockwise className={`h-3.5 w-3.5 ${sincronizando ? "admin-loader-anillo" : ""}`} weight="bold" />
            </span>
            <span className="truncate text-[13px] font-semibold text-ink">
              {sincronizando ? "Actualizando..." : "Actualizar desde la hoja"}
            </span>
          </button>

          <button
            type="button"
            onClick={() => {
              setDatosInicialesNuevoCaso(null);
              setModalAbierto(true);
            }}
            title="Solo para un caso puntual que todavía no está en el Google Sheets de la firma."
            className="flex items-center gap-2.5 rounded-xl bg-ink/5 p-2.5 text-left transition-colors hover:bg-ink/10"
          >
            <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-ink/10 text-ink">
              <Plus className="h-3.5 w-3.5" weight="bold" />
            </span>
            <span className="truncate text-[13px] font-semibold text-ink">Nuevo caso manual</span>
          </button>

          <button
            type="button"
            onClick={() => setCromaModalAbierto(true)}
            title="Busca procesos judiciales por Cédula o Nombre en la Rama Judicial cuando el cliente no sabe su radicado."
            className="flex items-center gap-2.5 rounded-xl bg-gold-pale/35 p-2.5 text-left ring-1 ring-gold-deep/20 transition-all hover:bg-gold-pale/60"
          >
            <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-gold text-ink-fixed shadow-sm">
              <Sparkle className="h-3.5 w-3.5" weight="fill" />
            </span>
            <span className="min-w-0 truncate text-[13px] font-semibold text-ink">
              Búsqueda Legal-Tech
              <span className="ml-1.5 rounded-full bg-gold/20 px-1.5 py-0.5 text-[9.5px] font-bold text-gold-deep">Free</span>
            </span>
          </button>

          <button
            type="button"
            onClick={() => {
              setCasoParaLegalPro(null);
              setLegalProAbierto(true);
            }}
            title="Selecciona un caso y extrae todo su informe (actuaciones, notificaciones, boletines y correos del juzgado) si el motor normal no trajo todo."
            className="flex items-center gap-2.5 rounded-xl bg-ink/90 p-2.5 text-left text-paper ring-1 ring-gold/40 transition-all hover:bg-ink"
          >
            <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-gold text-ink-fixed shadow-sm">
              <ShieldCheck className="h-3.5 w-3.5" weight="fill" />
            </span>
            <span className="min-w-0 truncate text-[13px] font-semibold text-paper">
              Legal-Pro
              <span className="ml-1.5 rounded-full bg-gold/25 px-1.5 py-0.5 text-[9.5px] font-bold text-gold">API</span>
            </span>
          </button>
        </div>

        {esAdmin && (
          <div className="mt-3 border-t border-line pt-3">
            <p className="text-[10.5px] font-semibold uppercase tracking-wide text-ink-soft/50">
              Comunicación masiva a clientes (solo administración)
            </p>
            <div className="mt-2 grid gap-2 sm:grid-cols-2">
              <button
                type="button"
                onClick={onEnviarPendientes}
                disabled={enviandoPendientes}
                title="Manda por correo y WhatsApp el radicado a quienes todavía no lo han recibido. Le llega algo real al cliente."
                className="flex items-center gap-2.5 rounded-xl bg-ink/5 p-2.5 text-left transition-colors hover:bg-ink/10 disabled:opacity-60"
              >
                <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-ink/10 text-ink">
                  {enviandoPendientes ? <Spinner className="h-3.5 w-3.5 animate-spin" weight="bold" /> : <EnvelopeSimple className="h-3.5 w-3.5" weight="bold" />}
                </span>
                <span className="truncate text-[13px] font-semibold text-ink">
                  {enviandoPendientes ? "Enviando..." : "Enviar notificaciones pendientes"}
                </span>
              </button>

              <button
                type="button"
                onClick={onEnviarReporteSemanal}
                disabled={enviandoReporteSemanal}
                title="Adelanta a mano el reporte que normalmente se envía solo cada quince días. Le llega algo real al cliente."
                className="flex items-center gap-2.5 rounded-xl bg-ink/5 p-2.5 text-left transition-colors hover:bg-ink/10 disabled:opacity-60"
              >
                <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-ink/10 text-ink">
                  {enviandoReporteSemanal ? <Spinner className="h-3.5 w-3.5 animate-spin" weight="bold" /> : <Bell className="h-3.5 w-3.5" weight="bold" />}
                </span>
                <span className="truncate text-[13px] font-semibold text-ink">
                  {enviandoReporteSemanal ? "Enviando..." : "Enviar reporte periódico"}
                </span>
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Pedido explícito del usuario (2026-11-01): "quita todo boton que haga que dispare
          el ciclo manualmente, el ciclo sera permanentemente automatizado" -- se quitó el
          botón "Buscar boletines de todos los casos" (disparaba
          VigilanciaExternaOrquestador completo). Ya no corre más que por el cron nocturno
          (ver app.vigilancia.ciclo-nocturno-cron); lo único manual que queda es "Volver a
          verificar" DENTRO del modal de Seguimiento de un caso puntual (ver
          SeguimientoDeCasoModal), que nunca toca el ciclo global. */}
      <EnvioLoteProgreso
        activo={sincronizando || enviandoPendientes || enviandoReporteSemanal}
        titulo={
          sincronizando
            ? "Actualizando casos desde el Google Sheets"
            : enviandoPendientes
              ? "Enviando notificaciones de radicado"
              : "Enviando reporte periódico de casos"
        }
        descripcion={
          sincronizando
            ? "Leyendo las hojas de la firma y comparando contra los casos ya registrados..."
            : enviandoPendientes
              ? "Despachando notificaciones de código y radicado por WhatsApp Cloud API y Gmail SMTP con pausas de seguridad..."
              : "Despachando reporte quincenal por WhatsApp y semanal por correo a casos activos..."
        }
      />

      {casos !== null && casos.length > 0 && (
        <div className="relative mt-6">
          <MagnifyingGlass weight="bold" className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-soft" />
          <input
            type="text"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            placeholder="Buscar por cliente, correo, teléfono, radicado o número de caso..."
            className="w-full rounded-full border border-line bg-surface py-2.5 pl-11 pr-4 text-sm text-ink placeholder:text-ink-soft focus:border-gold-deep focus:outline-none"
          />
        </div>
      )}

      {/* Rediseño pedido explícito del usuario ("organiza los separadores y botones de
          casos... mejor organizado, mejorar usabilidad, facilidad, intuitivo"): antes las 3
          secciones (origen, orden, estado del seguimiento) competían por la misma jerarquía
          visual, separadas solo por una línea fina sin ningún título que dijera qué es cada
          bloque. Ahora cada sección tiene su propio ícono + título en mayúsculas, como ya
          se usa en el resto del panel (ver "Acciones" arriba), y el espaciado entre
          secciones es generoso en vez de apretado. */}
      {casos !== null && casos.length > 0 && (
        <div className="mt-6 space-y-5 rounded-2xl border border-line bg-surface p-5">
          {/* Pedido explícito del usuario (2026-11-05): "estado de seguimiento dejalo a mano
              derecha de filtrar por origen" -- antes "Estado del seguimiento" era una 3ra
              sección apilada debajo de "Ordenar"; ahora comparte fila con "Filtrar por
              origen" (lado a lado en pantallas anchas, apilado en móvil). */}
          <div className="grid gap-5 lg:grid-cols-2">
            <div>
              <p className="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide text-ink-soft/60">
                <Funnel className="h-3.5 w-3.5" weight="bold" />
                Filtrar por origen
              </p>
              <div className="mt-2.5 flex flex-wrap gap-2">
                {FUENTES_FILTRO.map((f) => {
                  const cantidad = f.valor === "TODOS" ? casos.length : casos.filter((c) => c.fuente === f.valor).length;
                  if (f.valor !== "TODOS" && cantidad === 0) return null;
                  return (
                    <button
                      key={f.valor}
                      type="button"
                      onClick={() => setFiltroFuente(f.valor)}
                      className={`rounded-full px-4 py-2 text-sm transition-colors ${
                        filtroFuente === f.valor ? "bg-ink text-paper" : "bg-ink/5 text-ink-soft hover:bg-ink/10"
                      }`}
                    >
                      {f.label} <span className="opacity-70">({cantidad})</span>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Pedido explícito del usuario: "explica que es el boton rojo" -- los dos puntos
                de estado del botón "Seguimiento" de cada tarjeta (rojo y, ahora, azul) no
                traían ninguna explicación visible, así que no quedaba claro qué significaban
                sin preguntar. Ambos son independientes entre sí y pueden salir a la vez. Ahora
                además son filtros clicables (pedido explícito: "impementa un filtrador para los
                bonotes, uno que filtre todos los que tiene boletines disponibles (azul) y otro
                para casos sin revisar (rojo)") -- los dos pueden estar activos a la vez (un caso
                puede cumplir ambos). */}
            <div className="border-t border-line pt-5 lg:border-l lg:border-t-0 lg:pl-6 lg:pt-0">
              <p className="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide text-ink-soft/60">
                <ClockCounterClockwise className="h-3.5 w-3.5" weight="bold" />
                Estado del seguimiento
              </p>
              <p className="mt-1 text-xs leading-relaxed text-ink-soft">
                El botón &quot;Seguimiento&quot; de cada tarjeta puede traer un punto rojo, uno azul, los dos o ninguno -- filtra por cualquiera de los dos a la vez.
              </p>
              <div className="mt-2.5 flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  onClick={() => setSoloSinRevisar((v) => !v)}
                  className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs transition-colors ${
                    soloSinRevisar ? "bg-red-500/10 text-red-700 ring-1 ring-red-500/40" : "bg-ink/5 text-ink-soft hover:bg-ink/10"
                  }`}
                >
                  <span className="h-2.5 w-2.5 rounded-full bg-red-500" />
                  Sin revisar (se apaga solo al abrir el caso)
                </button>
                <button
                  type="button"
                  onClick={() => setSoloConBoletin((v) => !v)}
                  className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs transition-colors ${
                    soloConBoletin ? "bg-blue-500/10 text-blue-700 ring-1 ring-blue-500/40" : "bg-ink/5 text-ink-soft hover:bg-ink/10"
                  }`}
                >
                  <span className="h-2.5 w-2.5 rounded-full bg-blue-500" />
                  Con boletín disponible
                </button>
                {(soloSinRevisar || soloConBoletin) && (
                  <button
                    type="button"
                    onClick={() => {
                      setSoloSinRevisar(false);
                      setSoloConBoletin(false);
                    }}
                    className="text-xs text-ink-soft underline-offset-2 hover:underline"
                  >
                    Quitar filtros
                  </button>
                )}
              </div>
            </div>
          </div>

        </div>
      )}

      {casos === null ? (
        <AdminLoader />
      ) : casos.length === 0 ? (
        <EmptyState
          title="Aún no hay casos registrados"
          description='Usa "Actualizar desde la hoja" para traer todos los casos existentes de las hojas de la firma.'
        />
      ) : casosFiltrados?.length === 0 ? (
        <EmptyState title="Sin resultados" description="Ningún caso coincide con la búsqueda o el filtro actual." />
      ) : (
        <>
          {/* Pedido explícito del usuario: "implementar un nuevo apartado solo para ver asi
              como en IUS... para ver todos los casos como en un libro de excel" -- alterna
              entre las tarjetas de siempre y una tabla densa, una fila por caso, sobre la
              MISMA lista ya filtrada/ordenada de arriba (nunca una carga ni un filtro
              aparte). */}
          <div className="mt-6 flex items-center gap-2">
            <button
              type="button"
              onClick={() => setVista("tarjetas")}
              className={`rounded-full px-4 py-2 text-sm font-medium transition-colors ${
                vista === "tarjetas" ? "bg-ink text-paper" : "bg-ink/5 text-ink-soft hover:bg-ink/10"
              }`}
            >
              Tarjetas
            </button>
            <button
              type="button"
              onClick={() => setVista("tabla")}
              className={`rounded-full px-4 py-2 text-sm font-medium transition-colors ${
                vista === "tabla" ? "bg-ink text-paper" : "bg-ink/5 text-ink-soft hover:bg-ink/10"
              }`}
            >
              Tabla
            </button>
          </div>

          {vista === "tabla" ? (
            <TablaCasos
              casos={casosFiltrados ?? []}
              onAbrirSeguimiento={(c) => setCasoConSeguimientoAbierto({ id: c.id, nombreCliente: c.nombreCliente, radicadoId: c.radicadoId })}
            />
          ) : (
        <div className="mt-4 space-y-4">
          {casosFiltrados?.map((c) => (
            // Segundo rediseño pedido explícito del usuario, tras el primero: "necesito que
            // reorganices para que se vea mejor... tiene que llevar la mayor informacion,
            // ser entendible, organizada, facil de leer, agrandar el nombre del caso, el
            // abogado asignado, el informe y botones". Misma grilla de 3 columnas con
            // separadores (identidad | informe del proceso | notificación y acciones), pero
            // dentro de cada columna la jerarquía tipográfica ahora es real: el nombre es lo
            // más grande de la tarjeta, el abogado es una píldora de color (no un texto gris
            // perdido), "Última actualización" sube al principio del informe en vez de
            // quedar enterrada al final, y las acciones son botones de tamaño completo con
            // su propio fondo en vez de píldoras diminutas.
            <AdminCard
              key={c.id}
              accent
              className="grid grid-cols-1 gap-6 p-6 lg:grid-cols-[1.15fr_1.3fr_1fr] lg:items-start lg:divide-x lg:divide-line"
            >
              {/* Columna 1: identidad del caso */}
              <div className="min-w-0">
                <div className="flex items-start justify-between gap-2">
                  <p className="font-display text-xl leading-snug text-ink">{c.nombreCliente}</p>
                  {/* Pedido explícito del usuario (2026-11-04, revierte la decisión original
                      de "solo administrador" de esta misma sesión de trabajo): "permite que
                      todos los gerentes y abogados puedan editar la informacion de un caso".
                      Rediseño pedido explícito (2026-11-06): "los botones de editar y
                      finalizar/archivar hazlos mas intuitivos" -- antes eran dos círculos
                      grises idénticos, sin forma de distinguir a simple vista cuál es la
                      acción reversible (editar) y cuál la consecuente (archivar/finalizar).
                      Ahora cada uno tiene su propio color de intención (azul = editar, ámbar
                      = finalizar, el mismo lenguaje de color de "por vencer" en el resto del
                      panel) y un separador fino entre los dos para que no se lean como un
                      solo bloque de dos clics iguales. */}
                  <div className="flex shrink-0 items-center gap-1.5">
                    <button
                      type="button"
                      onClick={() => setCasoEditando(c)}
                      aria-label="Editar caso"
                      title="Editar información del caso"
                      className="flex h-8 w-8 items-center justify-center rounded-full text-ink-soft ring-1 ring-transparent transition-colors hover:bg-blue-50 hover:text-blue-700 hover:ring-blue-200"
                    >
                      <PencilSimple className="h-4 w-4" weight="bold" />
                    </button>
                    <span className="h-5 w-px bg-line" aria-hidden="true" />
                    {/* Pedido explícito del usuario (2026-11-05): "un boton para cada caso el
                        cual nos permita finalizarlo". */}
                    <button
                      type="button"
                      onClick={() => onFinalizar(c)}
                      disabled={finalizandoId === c.id}
                      aria-label="Finalizar caso"
                      title="Finalizar caso (se mueve a Casos Finalizados y deja de vigilarse)"
                      className="flex h-8 w-8 items-center justify-center rounded-full text-ink-soft ring-1 ring-transparent transition-colors hover:bg-amber-50 hover:text-amber-700 hover:ring-amber-200 disabled:opacity-50"
                    >
                      {finalizandoId === c.id ? (
                        <Spinner className="h-4 w-4 animate-spin" weight="bold" />
                      ) : (
                        <Archive className="h-4 w-4" weight="bold" />
                      )}
                    </button>
                  </div>
                </div>
                <div className="mt-2 flex flex-wrap items-center gap-1.5">
                  <Badge tone="gold">{c.fuenteVisible}</Badge>
                  {/* "h-..." es una huella de contenido interna (ver
                      HojaCalculoService.huellaContenido()), no un número de caso real de la
                      hoja de la firma -- Superintendencia y Procesos Comisaría nunca tuvieron
                      una columna de "NO." confiable, así que no hay nada legible que mostrarle
                      al admin ahí; se omite el badge en vez de mostrar un hash. */}
                  {c.numeroCaso && !c.numeroCaso.startsWith("h-") && (
                    <Badge tone="neutral">Caso Nº {c.numeroCaso}</Badge>
                  )}
                  {/* Pedido explícito del usuario (2026-11-01): "el sistema debe reflejar
                      que fallos hay" -- solo cuando SÍ hay un radicado pero no es un
                      radicado real de la Rama Judicial (23 dígitos). Nunca aparece si el
                      caso simplemente no tiene radicado todavía (eso es un estado normal,
                      no un fallo). */}
                  {c.radicadoId && c.radicadoFormatoInvalido && (
                    <span title="Este radicado no tiene el formato de la Rama Judicial (23 dígitos) -- si es un caso de otra jurisdicción (comisaría, policía), ignora este aviso; si esperabas seguimiento automático, revísalo.">
                      <Badge tone="warning">
                        <WarningCircle className="h-3 w-3" weight="bold" /> Radicado a revisar
                      </Badge>
                    </span>
                  )}
                </div>

                {/* Abogado asignado -- pedido explícito del usuario: "agrandar... el
                    abogado asignado". Antes era un texto gris diminuto que se perdía al
                    lado del nombre del cliente; ahora es una píldora propia, de color
                    distinto según si de verdad hay alguien a cargo o no (ámbar = todavía
                    nadie, para que salte a la vista que falta asignar). Visible para
                    cualquier rol, editable solo desde el botón de arriba. */}
                <p
                  className={`mt-3 inline-flex items-center gap-2 rounded-xl px-3 py-2 text-sm font-medium ${
                    c.abogadoAsignadoNombre
                      ? "bg-emerald-50 text-emerald-800 ring-1 ring-emerald-200"
                      : "bg-amber-50 text-amber-800 ring-1 ring-amber-200"
                  }`}
                >
                  <UserCircle className="h-4 w-4 shrink-0" weight={c.abogadoAsignadoNombre ? "fill" : "bold"} />
                  {c.abogadoAsignadoNombre ? c.abogadoAsignadoNombre : "Sin abogado asignado"}
                </p>

                <div className="mt-3 space-y-1 text-sm text-ink-soft">
                  <p>{c.correoCliente ?? "Sin correo capturado en la hoja"}</p>
                  {c.telefonoCliente && <p>{c.telefonoCliente}</p>}
                </div>
                {c.radicadoId && (
                  <p className="mt-2 inline-block rounded-md bg-gold-pale/40 px-2 py-1 font-mono text-xs tracking-wider text-gold-deep">
                    {c.radicadoId}
                  </p>
                )}
                {c.notasInternas && (
                  <p className="mt-3 rounded-lg bg-ink/[0.03] p-2.5 text-xs leading-relaxed text-ink-soft">
                    Nota: {c.notasInternas}
                  </p>
                )}
              </div>

              {/* Columna 2: informe del proceso -- pedido explícito del usuario: "agrandar...
                  el informe". Reorganizado con un título propio y "Última actualización"
                  arriba de todo (lo que de verdad importa de un vistazo), en vez de
                  enterrada hasta el final como antes. */}
              <div className="min-w-0 lg:px-6">
                <p className="text-[11px] font-semibold uppercase tracking-wide text-ink-soft/60">Informe del proceso</p>

                {/* "Última actualización" (fechaUltimoMovimientoJudicial, la fecha REAL del
                    despacho) -- ahora una caja propia, dorada, al principio del informe. */}
                <div className="mt-2 rounded-xl bg-gold-pale/40 p-3">
                  <span className="flex items-center gap-2 text-sm font-semibold text-gold-deep">
                    <Clock className="h-4 w-4 shrink-0" weight="bold" />
                    {c.fechaUltimoMovimientoJudicial
                      ? formatearFechaHora(c.fechaUltimoMovimientoJudicial)
                      : "Sin movimiento detectado aún"}
                  </span>
                  <span className="mt-1 block text-[11px] text-gold-deep/70">Última actualización judicial</span>
                </div>

                {/* Pedido explícito del usuario: "necesito que el abogado pueda ver
                    absolutamente todos los datos que expone el seguimiento... que se vean
                    las partes que estan en el sistema del estado, demandante, demandado,
                    todas las partes publicas" -- sujetosProcesales del CPNU YA trae todas las
                    partes (demandante, demandado, terceros, defensor, etc. cuando aplican),
                    separadas por " | " y con saltos de línea \r\n crudos -- se parsean aquí
                    en una lista limpia, una parte por línea, en vez de un párrafo plano.
                    null/"" hasta que el seguimiento resuelva el proceso para este caso (ver
                    RamaJudicialCpnuClient.buscarProceso en el backend). */}
                {c.sujetosProcesales && (
                  <div className="mt-3 rounded-xl bg-ink/[0.03] p-3">
                    <p className="text-[10px] font-semibold uppercase tracking-wide text-ink-soft/70">Partes del proceso</p>
                    <ul className="mt-1.5 space-y-1">
                      {formatearPartes(c.sujetosProcesales).map((parte, i) => (
                        <li key={i} className="text-xs leading-relaxed text-ink">{parte}</li>
                      ))}
                    </ul>
                  </div>
                )}
                {/* Pedido explícito del usuario: "pon todos los datos que exponga las APIs
                    del estado en los casos" -- el resto de la ficha del proceso (ver
                    ProcesoResuelto en el backend: tipo/clase/subclase, ponente, ubicación
                    actual del expediente, departamento, fecha de radicación). Cada dato se
                    muestra solo si ya se resolvió para este caso. */}
                {(c.ponente || c.tipoProceso || c.ubicacionProceso || c.departamentoJudicial || c.ultimaActualizacionRama) && (
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {c.tipoProceso && (
                      <span className="rounded-full bg-ink/5 px-2.5 py-1 text-[11px] text-ink-soft">
                        {[c.tipoProceso, c.claseProceso, c.subclaseProceso].filter(Boolean).join(" · ")}
                      </span>
                    )}
                    {c.ponente && (
                      <span className="rounded-full bg-ink/5 px-2.5 py-1 text-[11px] text-ink-soft">Ponente: {c.ponente}</span>
                    )}
                    {c.ubicacionProceso && (
                      <span className="rounded-full bg-gold-pale/50 px-2.5 py-1 text-[11px] text-gold-deep">
                        Ubicación: {c.ubicacionProceso}
                      </span>
                    )}
                    {c.departamentoJudicial && (
                      <span className="rounded-full bg-ink/5 px-2.5 py-1 text-[11px] text-ink-soft">{c.departamentoJudicial}</span>
                    )}
                    {c.fechaRadicacionProceso && (
                      <span className="rounded-full bg-ink/5 px-2.5 py-1 text-[11px] text-ink-soft">
                        Radicado el {formatearFechaSolo(c.fechaRadicacionProceso)}
                      </span>
                    )}
                    {/* Pedido explícito del usuario: "la vigilancia tiene que mostrar toda,
                        absolutamente toda la informacion... todo lo que se detecte" --
                        distinto de "Última actualización" de arriba (una actuación real):
                        esto es cuándo la RAMA JUDICIAL MISMA actualizó el proceso en su
                        propio sistema, dato aparte que el CPNU expone y se descartaba. */}
                    {c.ultimaActualizacionRama && (
                      <span className="rounded-full bg-ink/5 px-2.5 py-1 text-[11px] text-ink-soft">
                        Rama actualizó: {formatearFechaHora(c.ultimaActualizacionRama)}
                      </span>
                    )}
                  </div>
                )}
                <p className="mt-3 inline-flex items-center gap-1 text-[11px] text-ink-soft">
                  <CalendarPlus className="h-3 w-3" weight="bold" /> Creado {formatearFecha(c.fechaCreacion)}
                </p>
              </div>

              {/* Columna 3: estado de notificación + acciones -- pedido explícito del
                  usuario: "agrandar... botones". Las notificaciones pasan a tamaño "md"
                  (antes "sm") y las 3 acciones dejan de ser píldoras diminutas en una fila
                  para ser botones de ancho completo, apilados, con el mismo peso visual que
                  cualquier otro AdminButton del panel. */}
              <div className="min-w-0 lg:pl-6">
                <p className="text-[11px] font-semibold uppercase tracking-wide text-ink-soft/60">Notificación al cliente</p>
                <div className="mt-2 flex flex-col gap-2">
                  {c.radicadoId ? (
                    <>
                      {c.correoCliente ? (
                        <NotificationBadge
                          size="md"
                          tone={c.correoEnviado ? "success" : "warning"}
                          icon={<EnvelopeSimple weight="bold" className="h-3.5 w-3.5" />}
                        >
                          {c.correoEnviado
                            ? c.fechaUltimoReporteSemanal
                              ? `Correo enviado (${formatearFecha(c.fechaUltimoReporteSemanal)})`
                              : "Correo enviado"
                            : "Correo pendiente"}
                        </NotificationBadge>
                      ) : (
                        <NotificationBadge size="md" tone="neutral" icon={<EnvelopeSimple weight="bold" className="h-3.5 w-3.5" />}>
                          Sin correo
                        </NotificationBadge>
                      )}
                      {c.telefonoCliente ? (
                        <NotificationBadge
                          size="md"
                          tone={c.whatsappEnviado ? "success" : "warning"}
                          icon={<WhatsappLogo weight="bold" className="h-3.5 w-3.5" />}
                        >
                          {c.whatsappEnviado
                            ? c.fechaUltimoReporteWhatsapp
                              ? `WhatsApp enviado (${formatearFecha(c.fechaUltimoReporteWhatsapp)})`
                              : "WhatsApp enviado"
                            : "WhatsApp pendiente"}
                        </NotificationBadge>
                      ) : (
                        <NotificationBadge size="md" tone="neutral" icon={<PhoneSlash weight="bold" className="h-3.5 w-3.5" />}>
                          Sin teléfono
                        </NotificationBadge>
                      )}
                    </>
                  ) : (
                    <NotificationBadge size="md" tone="neutral" icon={<HourglassMedium weight="bold" className="h-3.5 w-3.5" />}>
                      Sin radicado aún
                    </NotificationBadge>
                  )}
                </div>

                {/* Acciones -- separadas visualmente del estado de arriba con un filo
                    propio, ahora como botones de ancho completo en vez de píldoras que se
                    envolvían de a dos o tres por fila. */}
                <p className="mt-4 text-[11px] font-semibold uppercase tracking-wide text-ink-soft/60">Acciones</p>
                <div className="mt-2 space-y-2">
                  {/* Registro de tareas por caso (pedido explícito del usuario), con el
                      conteo de pendientes como badge para ver de un vistazo qué caso
                      necesita atención sin tener que abrir cada uno. */}
                  <button
                    type="button"
                    onClick={() => setCasoConTareasAbierto({ tipo: "caso", id: c.id, nombreCliente: c.nombreCliente, radicadoId: c.radicadoId })}
                    className="flex w-full items-center gap-2.5 rounded-xl bg-ink/5 px-3 py-2.5 text-sm font-medium text-ink transition-colors hover:bg-ink/10"
                  >
                    <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-ink/10">
                      <CheckSquare weight="bold" className="h-3.5 w-3.5" />
                    </span>
                    {tareasPendientesPorCaso[c.id] ? `${tareasPendientesPorCaso[c.id]} tarea(s) pendiente(s)` : "Tareas"}
                  </button>

                  {/* Pedido explícito del usuario: ver desde aquí mismo qué documentos del
                      motor de firma electrónica están anexados a este caso y en qué estado
                      va cada uno, sin tener que buscarlo a mano entre todos en
                      /admin/documentos. */}
                  <button
                    type="button"
                    onClick={() => setCasoConDocumentosAbierto({ id: c.id, nombreCliente: c.nombreCliente, radicadoId: c.radicadoId })}
                    className="flex w-full items-center gap-2.5 rounded-xl bg-ink/5 px-3 py-2.5 text-sm font-medium text-ink transition-colors hover:bg-ink/10"
                  >
                    <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-ink/10">
                      <FileText weight="bold" className="h-3.5 w-3.5" />
                    </span>
                    Documentos con firma electrónica
                  </button>

                  {/* Trazabilidad independiente por caso del seguimiento judicial 24/7
                      (pedido explícito del usuario) -- el punto se pone dorado cuando ya hay
                      un proceso identificado en la Rama Judicial, para distinguir de un
                      vistazo qué casos ya están siendo rastreados sin tener que abrir cada
                      uno. El seguimiento en sí corre solo, de forma continua, en el backend
                      (ver SeguimientoJudicialScheduler) -- este botón solo abre la vista. El
                      punto rojo (pedido explícito: "si es nuevo para cada persona... le sale
                      un botoncito rojo") es POR USUARIO, se apaga solo al abrir este modal
                      (ver tieneActualizacionSinRevisar y onRevisado más abajo). El punto
                      azul (pedido explícito: "al lado pon un boton ahora azul simultaneo
                      para quienes tienen boletin disponible") es independiente del rojo --
                      los dos pueden estar prendidos a la vez, por eso van en esquinas
                      opuestas del botón en vez de competir por el mismo lugar (ver la
                      leyenda de arriba, junto al ordenador, que explica ambos). */}
                  <button
                    type="button"
                    onClick={() => setCasoConSeguimientoAbierto({ id: c.id, nombreCliente: c.nombreCliente, radicadoId: c.radicadoId })}
                    className="relative flex w-full items-center gap-2.5 rounded-xl bg-ink/5 px-3 py-2.5 text-sm font-medium text-ink transition-colors hover:bg-ink/10"
                  >
                    <span className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full ${c.tieneProcesoCpnu ? "bg-gold-pale/60 text-gold-deep" : "bg-ink/10"}`}>
                      <ClockCounterClockwise weight="bold" className="h-3.5 w-3.5" />
                    </span>
                    Seguimiento
                    {c.tieneActualizacionSinRevisar && (
                      <span className="absolute right-2 top-2 h-2.5 w-2.5 rounded-full bg-red-500 ring-2 ring-surface" />
                    )}
                    {c.diasParaBorrarBoletin !== null && (
                      <span className="absolute right-2 bottom-2 h-2.5 w-2.5 rounded-full bg-blue-500 ring-2 ring-surface" />
                    )}
                  </button>

                  {/* Pedido explícito del usuario: "pon un contador en cada caso con
                      boletin... que muestre dentro de cuantos dias seran borrados" -- chip
                      aparte, solo visible cuando hay algo que contar. */}
                  {c.diasParaBorrarBoletin !== null && (
                    <span className="flex w-full items-center gap-2.5 rounded-xl bg-blue-50 px-3 py-2.5 text-sm font-medium text-blue-700 ring-1 ring-blue-200">
                      <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-blue-100">
                        <Clock weight="bold" className="h-3.5 w-3.5" />
                      </span>
                      {c.diasParaBorrarBoletin === 0
                        ? "Boletín se borra hoy"
                        : `Boletín se borra en ${c.diasParaBorrarBoletin} día(s)`}
                    </span>
                  )}
                </div>
              </div>
            </AdminCard>
          ))}
        </div>
          )}
        </>
      )}

      <ModalNuevoCaso
        abierto={modalAbierto}
        datosIniciales={datosInicialesNuevoCaso}
        onClose={() => {
          setModalAbierto(false);
          setDatosInicialesNuevoCaso(null);
        }}
        onCreado={(nuevo) => {
          setCasos((prev) => (prev ? [nuevo, ...prev] : [nuevo]));
          setModalAbierto(false);
          setDatosInicialesNuevoCaso(null);
        }}
      />

      <TareasDeCasoModal
        objetivo={casoConTareasAbierto}
        onClose={() => setCasoConTareasAbierto(null)}
        onCambioPendientes={cargarConteoTareas}
      />

      <DocumentosDeCasoModal caso={casoConDocumentosAbierto} onClose={() => setCasoConDocumentosAbierto(null)} />

      <SeguimientoDeCasoModal
        caso={casoConSeguimientoAbierto}
        onClose={() => setCasoConSeguimientoAbierto(null)}
        onRevisado={onRevisadoSeguimiento}
      />

      <EditarCasoModal
        caso={casoEditando}
        onClose={() => setCasoEditando(null)}
        onActualizado={(actualizado) => {
          setCasos((prev) => (prev ? prev.map((c) => (c.id === actualizado.id ? actualizado : c)) : prev));
          setCasoEditando(null);
        }}
      />

      <BusquedaCromaModal
        abierto={cromaModalAbierto}
        onClose={() => setCromaModalAbierto(false)}
        radicadosExistentes={
          new Set((casos ?? []).map((c) => c.radicadoId).filter((r): r is string => Boolean(r)))
        }
        onSeleccionarRadicado={(radicado) => {
          setBusqueda(radicado);
          toast.info(`Filtrando lista por el radicado seleccionado: ${radicado}`);
        }}
        onCrearCaso={(datos) => {
          setDatosInicialesNuevoCaso(datos);
          setModalAbierto(true);
        }}
      />

      <LegalProCromaModal
        abierto={legalProAbierto}
        onClose={() => {
          setLegalProAbierto(false);
          setCasoParaLegalPro(null);
        }}
        casos={casos ?? []}
        casoInicial={casoParaLegalPro}
        onCasoSincronizado={cargar}
      />
    </div>
  );
}

// Pedido explícito del usuario: "implementar un nuevo apartado solo para ver asi como en
// IUS dentro de SIE para ver todos los casos como en un libro de excel" -- una tabla densa,
// una sola fila por caso, con scroll horizontal cuando hace falta, igual que la grilla
// "Todos mis procesos" de IUS Tech (columnas de radicado/demandante/demandado/despacho/
// estado, cabecera fija). Recibe la MISMA lista ya filtrada/ordenada por la página -- nunca
// carga ni filtra nada por su cuenta.
function TablaCasos({
  casos,
  onAbrirSeguimiento,
}: {
  casos: CasoAdmin[];
  onAbrirSeguimiento: (caso: CasoAdmin) => void;
}) {
  return (
    <div className="mt-4 overflow-x-auto rounded-2xl border border-line bg-surface">
      <table className="w-full min-w-[1400px] border-collapse text-sm">
        <thead>
          <tr className="sticky top-0 z-10 bg-ink text-paper">
            <th className="whitespace-nowrap px-4 py-3 text-left font-semibold">Radicado</th>
            <th className="whitespace-nowrap px-4 py-3 text-left font-semibold">Cliente</th>
            <th className="whitespace-nowrap px-4 py-3 text-left font-semibold">Demandante</th>
            <th className="whitespace-nowrap px-4 py-3 text-left font-semibold">Demandado</th>
            <th className="whitespace-nowrap px-4 py-3 text-left font-semibold">Despacho</th>
            <th className="whitespace-nowrap px-4 py-3 text-left font-semibold">Departamento</th>
            <th className="whitespace-nowrap px-4 py-3 text-left font-semibold">Tipo de proceso</th>
            <th className="whitespace-nowrap px-4 py-3 text-left font-semibold">Fuente</th>
            <th className="whitespace-nowrap px-4 py-3 text-left font-semibold">Abogado</th>
            <th className="whitespace-nowrap px-4 py-3 text-left font-semibold">Última actualización</th>
            <th className="whitespace-nowrap px-4 py-3 text-left font-semibold">Seguimiento</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {casos.map((c) => (
            <tr key={c.id} className="transition-colors hover:bg-ink/[0.03]">
              <td
                className={`whitespace-nowrap px-4 py-2.5 font-mono text-xs tracking-wider ${c.radicadoId && c.radicadoFormatoInvalido ? "text-amber-600" : "text-gold-deep"}`}
                title={c.radicadoId && c.radicadoFormatoInvalido ? "No tiene el formato de la Rama Judicial (23 dígitos) -- revísalo si esperabas seguimiento automático." : undefined}
              >
                {c.radicadoId ?? "—"}
              </td>
              <td className="max-w-[220px] truncate px-4 py-2.5 font-medium text-ink" title={c.nombreCliente}>
                {c.nombreCliente}
              </td>
              <td className="max-w-[220px] truncate px-4 py-2.5 text-ink-soft" title={extraerParte(c.sujetosProcesales, RE_DEMANDANTE)}>
                {extraerParte(c.sujetosProcesales, RE_DEMANDANTE) || "—"}
              </td>
              <td className="max-w-[220px] truncate px-4 py-2.5 text-ink-soft" title={extraerParte(c.sujetosProcesales, RE_DEMANDADO)}>
                {extraerParte(c.sujetosProcesales, RE_DEMANDADO) || "—"}
              </td>
              <td className="max-w-[200px] truncate px-4 py-2.5 text-ink-soft" title={c.despachoJudicial ?? ""}>
                {c.despachoJudicial ?? "—"}
              </td>
              <td className="whitespace-nowrap px-4 py-2.5 text-ink-soft">{c.departamentoJudicial ?? "—"}</td>
              <td className="max-w-[180px] truncate px-4 py-2.5 text-ink-soft" title={[c.tipoProceso, c.claseProceso].filter(Boolean).join(" · ")}>
                {[c.tipoProceso, c.claseProceso].filter(Boolean).join(" · ") || "—"}
              </td>
              <td className="whitespace-nowrap px-4 py-2.5">
                <Badge tone="gold">{c.fuenteVisible}</Badge>
              </td>
              <td className="whitespace-nowrap px-4 py-2.5 text-ink-soft">{c.abogadoAsignadoNombre ?? "Sin asignar"}</td>
              <td className="whitespace-nowrap px-4 py-2.5 text-ink-soft">
                {c.fechaUltimoMovimientoJudicial ? formatearFechaHora(c.fechaUltimoMovimientoJudicial) : "—"}
              </td>
              <td className="whitespace-nowrap px-4 py-2.5">
                <button
                  type="button"
                  onClick={() => onAbrirSeguimiento(c)}
                  className="inline-flex items-center gap-1.5 rounded-full bg-ink/5 px-3 py-1 text-xs font-medium text-ink-soft transition-colors hover:bg-ink/10"
                >
                  <ClockCounterClockwise className="h-3 w-3" weight="bold" />
                  Ver
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ModalNuevoCaso({
  abierto,
  datosIniciales,
  onClose,
  onCreado,
}: {
  abierto: boolean;
  datosIniciales?: DatosCasoDesdeCroma | null;
  onClose: () => void;
  onCreado: (caso: CasoAdmin) => void;
}) {
  const [enviando, setEnviando] = useState(false);

  const notasPrefill = [
    datosIniciales?.despacho ? `Despacho: ${datosIniciales.despacho}` : "",
    datosIniciales?.departamento ? `Departamento: ${datosIniciales.departamento}` : "",
    datosIniciales?.partes ? `Partes: ${datosIniciales.partes}` : "",
  ]
    .filter(Boolean)
    .join("\n");

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const data = new FormData(form);
    const radicadoId = String(data.get("radicadoId") ?? "").trim();
    if (!radicadoId) {
      toast.error("Ingresa el número de radicado.");
      return;
    }

    setEnviando(true);
    try {
      const nuevo = await crearCaso({
        nombreCliente: String(data.get("nombreCliente") ?? ""),
        correoCliente: String(data.get("correoCliente") ?? ""),
        telefonoCliente: String(data.get("telefonoCliente") ?? "") || undefined,
        radicadoId,
        notasInternas: String(data.get("notasInternas") ?? "") || undefined,
      });
      toast.success(`Caso creado. Radicado ${nuevo.radicadoId} enviado al correo del cliente.`);
      form.reset();
      onCreado(nuevo);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo crear el caso.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Dialog.Root open={abierto} onOpenChange={(open) => !open && onClose()}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-ink/40 backdrop-blur-sm" />
        <Dialog.Popup className="fixed left-1/2 top-1/2 z-50 w-full max-w-md -translate-x-1/2 -translate-y-1/2 rounded-2xl bg-surface p-6 shadow-2xl ring-1 ring-line">
          <div className="flex items-center justify-between">
            <Dialog.Title className="font-display text-lg text-ink">
              {datosIniciales ? "Crear caso desde hallazgo Croma" : "Nuevo caso manual"}
            </Dialog.Title>
            <Dialog.Close className="flex h-8 w-8 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5">
              <X className="h-4 w-4" />
            </Dialog.Close>
          </div>
          <Dialog.Description className="mt-1 text-sm text-ink-soft">
            {datosIniciales
              ? "Se precargaron el nombre y el radicado de 23 dígitos encontrados en Croma. Solo completa el correo del cliente para registrar el caso e iniciar vigilancia."
              : "Solo para un caso puntual que todavía no está en el Google Sheets de la firma — lo normal es que \"Actualizar desde la hoja\" ya lo traiga automáticamente. El radicado se envía por correo al cliente de inmediato."}
          </Dialog.Description>

          <form key={datosIniciales?.radicadoId ?? "manual"} onSubmit={onSubmit} className="mt-5 space-y-3">
            <input
              name="nombreCliente"
              required
              defaultValue={datosIniciales?.nombreCliente ?? ""}
              placeholder="Nombre del cliente"
              className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
            />
            <input
              name="correoCliente"
              type="email"
              required
              placeholder="Correo del cliente"
              className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
            />
            <input
              name="telefonoCliente"
              placeholder="Teléfono (opcional)"
              className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
            />
            <input
              name="radicadoId"
              required
              defaultValue={datosIniciales?.radicadoId ?? ""}
              placeholder="Número de radicado"
              className="w-full rounded-xl border border-line bg-paper px-4 py-3 font-mono text-sm text-ink focus:border-gold-deep focus:outline-none"
            />
            <textarea
              name="notasInternas"
              rows={3}
              defaultValue={notasPrefill}
              placeholder="Notas internas (opcional, no las ve el cliente)"
              className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
            />

            <AdminButton type="submit" disabled={enviando} className="w-full">
              {enviando ? "Creando..." : "Crear caso"}
            </AdminButton>
          </form>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
