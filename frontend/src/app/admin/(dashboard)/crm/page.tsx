"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import {
  AddressBook,
  ArrowsClockwise,
  ArrowRight,
  ArrowLeft,
  Briefcase,
  Buildings,
  CalendarCheck,
  CaretRight,
  ChatCircleText,
  Check,
  CurrencyDollar,
  DotsSixVertical,
  EnvelopeSimple,
  FileText,
  Funnel,
  IdentificationCard,
  MagnifyingGlass,
  MapPin,
  PencilSimple,
  Phone,
  Plus,
  Trash,
  User,
  UserCheck,
  Users,
  WhatsappLogo,
  X,
} from "@phosphor-icons/react";
import {
  listarClientesCrm,
  obtenerClienteCrmDetalle,
  crearClienteCrm,
  actualizarClienteCrm,
  archivarClienteCrm,
  obtenerPipelineCrm,
  cambiarEtapaPipeline,
  convertirProspectoACrm,
  registrarActividadCrm,
  crearTareaCrm,
  completarTareaCrm,
  obtenerDashboardCrm,
  ApiError,
  type ClienteCrm,
  type ClienteCrmDetalle,
  type ItemPipeline,
  type CrmDashboard,
  type TipoClienteCrm,
  type EstadoClienteCrm,
  type EtapaPipeline,
  type TipoActividadCrm,
  type PrioridadTareaCrm,
} from "@/lib/admin-api";
import {
  AdminPageHeader,
  AdminCard,
  AdminButton,
  Badge,
  NotificationBadge,
  EmptyState,
  AdminLoader,
} from "@/components/admin/ui";
import { useAuth } from "@/lib/auth-context";

// Etiquetas renombradas (pedido explícito del usuario): el lenguaje original ("Contratado /
// Ganado", "En Valoración") leía como un CRM de ventas genérico, no como algo pensado para
// un bufete. Las keys (EtapaPipeline, lo que viaja al backend) no cambian, solo el texto
// que ve el abogado/admin.
const ETAPAS_ORDENADAS: { key: EtapaPipeline; label: string; tone: "neutral" | "gold" | "warning" | "success" | "danger" }[] = [
  { key: "NUEVO", label: "Nuevo contacto", tone: "neutral" },
  { key: "CONTACTADO", label: "Contactado", tone: "warning" },
  { key: "CITA_PROGRAMADA", label: "Cita agendada", tone: "gold" },
  { key: "VALORACION", label: "Estudio del caso", tone: "warning" },
  { key: "PROPUESTA_ENVIADA", label: "Propuesta enviada", tone: "gold" },
  { key: "CONTRATADO", label: "Cliente activo", tone: "success" },
  { key: "DESCARTADO", label: "Descartado", tone: "danger" },
];

// Color de acento por etapa, reutilizado en la barra superior de cada columna y en el
// avatar de iniciales de cada tarjeta -- mismo lenguaje visual que el acento dorado del
// ítem activo del sidebar, para que el pipeline se sienta parte del mismo sistema.
const TONE_ACCENT: Record<string, string> = {
  neutral: "bg-ink/25",
  gold: "bg-gold",
  warning: "bg-amber-400",
  success: "bg-emerald-500",
  danger: "bg-red-400",
};

function formatearMoneda(val: number | null) {
  if (!val || val === 0) return "$ 0 COP";
  return new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 }).format(val);
}

function formatearFecha(iso: string | null) {
  if (!iso) return "Sin fecha";
  return new Date(iso).toLocaleDateString("es-CO", { day: "numeric", month: "short", year: "numeric" });
}

function tieneCosto(honorarios: string | null | undefined): boolean {
  if (!honorarios) return false;
  return /[1-9]/.test(honorarios);
}

function iniciales(nombre: string): string {
  const partes = nombre.trim().split(/\s+/).filter(Boolean);
  if (partes.length === 0) return "?";
  if (partes.length === 1) return partes[0].slice(0, 2).toUpperCase();
  return (partes[0][0] + partes[1][0]).toUpperCase();
}

export default function CrmAdminPage() {
  const { sesion } = useAuth();

  const [tabActiva, setTabActiva] = useState<"pipeline" | "directorio" | "dashboard">("pipeline");

  // Estado Pipeline
  const [pipelineItems, setPipelineItems] = useState<ItemPipeline[] | null>(null);
  const [cargandoPipeline, setCargandoPipeline] = useState(false);
  // Arrastrar y soltar entre etapas (además de las flechas, que siguen siendo el único
  // camino en touch -- el drag and drop nativo de HTML5 no funciona en móvil/tablet).
  const [arrastrandoId, setArrastrandoId] = useState<number | null>(null);
  const [columnaSobrevolada, setColumnaSobrevolada] = useState<EtapaPipeline | null>(null);

  // Estado Directorio
  const [clientes, setClientes] = useState<ClienteCrm[] | null>(null);
  const [cargandoClientes, setCargandoClientes] = useState(false);
  const [inputBusqueda, setInputBusqueda] = useState("");
  const [busqueda, setBusqueda] = useState("");
  const [filtroTipo, setFiltroTipo] = useState<TipoClienteCrm | "TODOS">("TODOS");
  const [filtroEstado, setFiltroEstado] = useState<EstadoClienteCrm | "TODOS">("TODOS");
  const [filtroCobro, setFiltroCobro] = useState<"TODOS" | "AL_DIA" | "PENDIENTE">("TODOS");
  const [paginaDirectorio, setPaginaDirectorio] = useState(0);
  const [totalPaginasDirectorio, setTotalPaginasDirectorio] = useState(1);
  const [totalClientesDirectorio, setTotalClientesDirectorio] = useState(0);

  // Estado Detalle / Ficha 360
  const [clienteSeleccionadoId, setClienteSeleccionadoId] = useState<number | null>(null);
  const [clienteDetalle, setClienteDetalle] = useState<ClienteCrmDetalle | null>(null);
  const [cargandoDetalle, setCargandoDetalle] = useState(false);

  // Estado Edición de Cliente (Ficha 360°)
  const [modoEdicionCliente, setModoEdicionCliente] = useState(false);
  // Sub-secciones de la Ficha 360°: antes todo (casos, cobros, citas, tareas, bitácora)
  // estaba apilado en un solo scroll largo -- pedido explícito del usuario ("demasiada
  // información suelta sin orden claro"). Ahora cada bloque vive en su propia sección.
  const [fichaTabActiva, setFichaTabActiva] = useState<"resumen" | "casos" | "tareas" | "bitacora">("resumen");
  const [editNombre, setEditNombre] = useState("");
  const [editTipo, setEditTipo] = useState<TipoClienteCrm>("PERSONA_NATURAL");
  const [editCedulaNit, setEditCedulaNit] = useState("");
  const [editCorreo, setEditCorreo] = useState("");
  const [editTelefono, setEditTelefono] = useState("");
  const [editCiudad, setEditCiudad] = useState("");
  const [editEstado, setEditEstado] = useState<EstadoClienteCrm>("ACTIVO");
  const [editNotas, setEditNotas] = useState("");
  const [guardandoEdicion, setGuardandoEdicion] = useState(false);
  const [archivando, setArchivando] = useState(false);

  // Estado Formulario Nueva Actividad / Nota
  const [nuevaActividadTipo, setNuevaActividadTipo] = useState<TipoActividadCrm>("NOTA_INTERNA");
  const [nuevaActividadTitulo, setNuevaActividadTitulo] = useState("");
  const [nuevaActividadDesc, setNuevaActividadDesc] = useState("");
  const [guardandoActividad, setGuardandoActividad] = useState(false);

  // Estado Formulario Nueva Tarea
  const [nuevaTareaTitulo, setNuevaTareaTitulo] = useState("");
  const [nuevaTareaPrioridad, setNuevaTareaPrioridad] = useState<PrioridadTareaCrm>("MEDIA");
  const [nuevaTareaVencimiento, setNuevaTareaVencimiento] = useState("");
  const [guardandoTarea, setGuardandoTarea] = useState(false);

  // Estado Métricas
  const [dashboard, setDashboard] = useState<CrmDashboard | null>(null);

  // Modal Nuevo Cliente
  const [modalNuevoCliente, setModalNuevoCliente] = useState(false);
  const [nuevoNombre, setNuevoNombre] = useState("");
  const [nuevoTipo, setNuevoTipo] = useState<TipoClienteCrm>("PERSONA_NATURAL");
  const [nuevoCedulaNit, setNuevoCedulaNit] = useState("");
  const [nuevoCorreo, setNuevoCorreo] = useState("");
  const [nuevoTelefono, setNuevoTelefono] = useState("");
  const [nuevaCiudad, setNuevaCiudad] = useState("");
  const [nuevasNotas, setNuevasNotas] = useState("");
  const [guardandoCliente, setGuardandoCliente] = useState(false);

  // Cargas
  const cargarPipeline = useCallback(async () => {
    setCargandoPipeline(true);
    try {
      const data = await obtenerPipelineCrm();
      setPipelineItems(data);
    } catch {
      toast.error("No se pudo cargar el pipeline.");
    } finally {
      setCargandoPipeline(false);
    }
  }, []);

  const cargarClientes = useCallback(async () => {
    setCargandoClientes(true);
    try {
      const data = await listarClientesCrm({
        busqueda: busqueda.trim() || undefined,
        tipo: filtroTipo === "TODOS" ? undefined : filtroTipo,
        estado: filtroEstado === "TODOS" ? undefined : filtroEstado,
        pagoAlDia: filtroCobro === "TODOS" ? undefined : filtroCobro === "AL_DIA",
        pagina: paginaDirectorio,
      });
      setClientes(data.contenido);
      setTotalPaginasDirectorio(data.totalPaginas);
      setTotalClientesDirectorio(data.totalElementos);
    } catch {
      toast.error("No se pudieron cargar los clientes.");
    } finally {
      setCargandoClientes(false);
    }
  }, [busqueda, filtroTipo, filtroEstado, filtroCobro, paginaDirectorio]);

  // Debounce de la búsqueda: evita una petición al backend por cada tecla escrita (el
  // directorio ya pasa de 225 clientes, cada tecla disparaba una carga completa).
  useEffect(() => {
    const t = setTimeout(() => setBusqueda(inputBusqueda), 350);
    return () => clearTimeout(t);
  }, [inputBusqueda]);

  // Vuelve a la primera página cada vez que cambia un filtro: quedarse en la página 4 de un
  // resultado que ahora solo tiene 2 páginas confundiría más que reiniciar la vista.
  useEffect(() => {
    setPaginaDirectorio(0);
  }, [busqueda, filtroTipo, filtroEstado, filtroCobro]);

  const cargarDashboard = useCallback(async () => {
    try {
      const data = await obtenerDashboardCrm();
      setDashboard(data);
    } catch {
      // noop
    }
  }, []);

  const cargarFichaCliente = useCallback(async (id: number) => {
    setCargandoDetalle(true);
    try {
      const detalle = await obtenerClienteCrmDetalle(id);
      setClienteDetalle(detalle);
    } catch {
      toast.error("No se pudo cargar la ficha del cliente.");
    } finally {
      setCargandoDetalle(false);
    }
  }, []);

  useEffect(() => {
    cargarPipeline();
    cargarClientes();
    cargarDashboard();
  }, [cargarPipeline, cargarClientes, cargarDashboard]);

  useEffect(() => {
    setModoEdicionCliente(false);
    setFichaTabActiva("resumen");
    if (clienteSeleccionadoId !== null) {
      cargarFichaCliente(clienteSeleccionadoId);
    } else {
      setClienteDetalle(null);
    }
  }, [clienteSeleccionadoId, cargarFichaCliente]);

  // Precarga el formulario de edición con los datos actuales cada vez que llega (o se
  // recarga) la ficha -- así "Editar" siempre arranca mostrando lo que de verdad hay
  // guardado, no un formulario vacío ni datos de la ficha anterior.
  useEffect(() => {
    if (!clienteDetalle) return;
    setEditNombre(clienteDetalle.cliente.nombre);
    setEditTipo(clienteDetalle.cliente.tipo);
    setEditCedulaNit(clienteDetalle.cliente.cedulaNit ?? "");
    setEditCorreo(clienteDetalle.cliente.correo ?? "");
    setEditTelefono(clienteDetalle.cliente.telefono ?? "");
    setEditCiudad(clienteDetalle.cliente.ciudad ?? "");
    setEditEstado(clienteDetalle.cliente.estado);
    setEditNotas(clienteDetalle.cliente.notas ?? "");
  }, [clienteDetalle]);

  // Transiciones de Pipeline
  async function onMoverEtapa(solicitudId: number, nuevaEtapa: EtapaPipeline) {
    try {
      await cambiarEtapaPipeline(solicitudId, nuevaEtapa);
      toast.success("Etapa actualizada");
      cargarPipeline();
      cargarDashboard();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Error al cambiar etapa");
    }
  }

  async function onConvertirACliente(solicitudId: number) {
    try {
      const cliente = await convertirProspectoACrm(solicitudId, {
        tipo: "PERSONA_NATURAL",
      });
      toast.success(`Prospecto convertido en Cliente CRM exitosamente: ${cliente.nombre}`);
      cargarPipeline();
      cargarClientes();
      cargarDashboard();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Error al convertir prospecto");
    }
  }

  // Creación de Cliente Directo
  async function onSubmitNuevoCliente(e: React.FormEvent) {
    e.preventDefault();
    if (!nuevoNombre.trim()) {
      toast.warning("El nombre es obligatorio");
      return;
    }
    setGuardandoCliente(true);
    try {
      await crearClienteCrm({
        nombre: nuevoNombre.trim(),
        tipo: nuevoTipo,
        cedulaNit: nuevoCedulaNit.trim() || undefined,
        correo: nuevoCorreo.trim() || undefined,
        telefono: nuevoTelefono.trim() || undefined,
        ciudad: nuevaCiudad.trim() || undefined,
        estado: "ACTIVO",
        notas: nuevasNotas.trim() || undefined,
      });
      toast.success("Cliente creado en CRM con éxito");
      setModalNuevoCliente(false);
      setNuevoNombre("");
      setNuevoCedulaNit("");
      setNuevoCorreo("");
      setNuevoTelefono("");
      setNuevaCiudad("");
      setNuevasNotas("");
      cargarClientes();
      cargarDashboard();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Error al crear cliente");
    } finally {
      setGuardandoCliente(false);
    }
  }

  // Agregar Actividad en Ficha
  async function onGuardarActividad(e: React.FormEvent) {
    e.preventDefault();
    if (!clienteSeleccionadoId || !nuevaActividadTitulo.trim()) {
      toast.warning("Escribe un título para el registro.");
      return;
    }
    setGuardandoActividad(true);
    try {
      await registrarActividadCrm(clienteSeleccionadoId, {
        tipo: nuevaActividadTipo,
        titulo: nuevaActividadTitulo.trim(),
        descripcion: nuevaActividadDesc.trim() || undefined,
      });
      toast.success("Actividad registrada en la bitácora");
      setNuevaActividadTitulo("");
      setNuevaActividadDesc("");
      cargarFichaCliente(clienteSeleccionadoId);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Error al registrar actividad");
    } finally {
      setGuardandoActividad(false);
    }
  }

  // Agregar Tarea en Ficha
  async function onGuardarTarea(e: React.FormEvent) {
    e.preventDefault();
    if (!clienteSeleccionadoId || !nuevaTareaTitulo.trim()) {
      toast.warning("Escribe el título de la tarea.");
      return;
    }
    setGuardandoTarea(true);
    try {
      await crearTareaCrm(clienteSeleccionadoId, {
        titulo: nuevaTareaTitulo.trim(),
        prioridad: nuevaTareaPrioridad,
        fechaVencimiento: nuevaTareaVencimiento.trim() || undefined,
      });
      toast.success("Tarea asignada con éxito");
      setNuevaTareaTitulo("");
      setNuevaTareaVencimiento("");
      cargarFichaCliente(clienteSeleccionadoId);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Error al crear tarea");
    } finally {
      setGuardandoTarea(false);
    }
  }

  async function onToggleTarea(tareaId: number, estadoActual: boolean) {
    if (!clienteSeleccionadoId) return;
    try {
      await completarTareaCrm(tareaId, !estadoActual);
      cargarFichaCliente(clienteSeleccionadoId);
    } catch {
      toast.error("No se pudo actualizar el estado de la tarea.");
    }
  }

  // Edición de Cliente en Ficha 360°
  async function onGuardarEdicionCliente(e: React.FormEvent) {
    e.preventDefault();
    if (!clienteSeleccionadoId || !editNombre.trim()) {
      toast.warning("El nombre es obligatorio.");
      return;
    }
    setGuardandoEdicion(true);
    try {
      await actualizarClienteCrm(clienteSeleccionadoId, {
        tipo: editTipo,
        nombre: editNombre.trim(),
        cedulaNit: editCedulaNit.trim() || undefined,
        correo: editCorreo.trim() || undefined,
        telefono: editTelefono.trim() || undefined,
        ciudad: editCiudad.trim() || undefined,
        estado: editEstado,
        notas: editNotas.trim() || undefined,
      });
      toast.success("Cliente actualizado.");
      setModoEdicionCliente(false);
      cargarFichaCliente(clienteSeleccionadoId);
      cargarClientes();
      cargarDashboard();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo actualizar el cliente.");
    } finally {
      setGuardandoEdicion(false);
    }
  }

  // Archivar Cliente (solo ADMIN_GENERAL, igual que el backend): deja el cliente en estado
  // INACTIVO, no lo borra -- sigue visible con el filtro "Inactivos" y se puede reactivar
  // editando el estado de nuevo a Activo.
  async function onArchivarCliente() {
    if (!clienteSeleccionadoId || !clienteDetalle) return;
    if (!window.confirm(`¿Archivar a "${clienteDetalle.cliente.nombre}"? Pasará a estado Inactivo.`)) return;
    setArchivando(true);
    try {
      await archivarClienteCrm(clienteSeleccionadoId);
      toast.success("Cliente archivado.");
      cargarFichaCliente(clienteSeleccionadoId);
      cargarClientes();
      cargarDashboard();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo archivar el cliente.");
    } finally {
      setArchivando(false);
    }
  }

  return (
    <div className="space-y-6">
      <AdminPageHeader
        title="CRM Jurídico y Gestión de Clientes"
        description="Gestión integral 360° de prospectos, clientes activos, expediente de casos vinculados, recordatorios de cobro, citas agendadas y bitácora de seguimiento legal."
        action={
          <div className="flex flex-wrap items-center gap-2">
            <AdminButton
              variant="secondary"
              onClick={() => {
                cargarPipeline();
                cargarClientes();
                cargarDashboard();
              }}
            >
              <ArrowsClockwise className="h-4 w-4" weight="bold" />
              Actualizar
            </AdminButton>
            <AdminButton onClick={() => setModalNuevoCliente(true)}>
              <Plus className="h-4 w-4" weight="bold" />
              Nuevo Cliente
            </AdminButton>
          </div>
        }
      />

      {/* Navegación por pestañas */}
      <div className="flex border-b border-line">
        <button
          type="button"
          onClick={() => setTabActiva("pipeline")}
          className={`flex items-center gap-2 border-b-2 px-5 py-3 text-sm font-medium transition-colors ${
            tabActiva === "pipeline"
              ? "border-gold text-ink"
              : "border-transparent text-ink-soft hover:border-line hover:text-ink"
          }`}
        >
          <Funnel className="h-4 w-4" weight="bold" />
          Pipeline de Oportunidades
          {pipelineItems && (
            <span className="rounded-full bg-gold/20 px-2 py-0.5 text-xs text-gold-deep font-semibold">
              {pipelineItems.length}
            </span>
          )}
        </button>

        <button
          type="button"
          onClick={() => setTabActiva("directorio")}
          className={`flex items-center gap-2 border-b-2 px-5 py-3 text-sm font-medium transition-colors ${
            tabActiva === "directorio"
              ? "border-gold text-ink"
              : "border-transparent text-ink-soft hover:border-line hover:text-ink"
          }`}
        >
          <AddressBook className="h-4 w-4" weight="bold" />
          Directorio Clientes 360°
          {clientes && (
            <span className="rounded-full bg-ink/10 px-2 py-0.5 text-xs text-ink font-semibold">
              {totalClientesDirectorio}
            </span>
          )}
        </button>

        <button
          type="button"
          onClick={() => setTabActiva("dashboard")}
          className={`flex items-center gap-2 border-b-2 px-5 py-3 text-sm font-medium transition-colors ${
            tabActiva === "dashboard"
              ? "border-gold text-ink"
              : "border-transparent text-ink-soft hover:border-line hover:text-ink"
          }`}
        >
          <Briefcase className="h-4 w-4" weight="bold" />
          Métricas y Rendimiento
        </button>
      </div>

      {/* ======================= PESTAÑA 1: PIPELINE ======================= */}
      {tabActiva === "pipeline" && (
        <div>
          {cargandoPipeline && !pipelineItems ? (
            <AdminLoader />
          ) : (
            <div>
              {/* Cuadrícula que envuelve en filas en vez de una tira horizontal con scroll
                  lateral -- pedido explícito del usuario ("no así a lo horizontal, que se
                  vea completo"): con 7 etapas, antes siempre sobraba al menos una columna
                  fuera de la pantalla. Ahora las columnas se acomodan solas según el ancho
                  disponible y las 7 quedan visibles de un vistazo, sin scroll lateral. */}
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
                {ETAPAS_ORDENADAS.map((etapa, idx) => {
                  const itemsEnEtapa = pipelineItems?.filter((p) => p.etapa === etapa.key) || [];
                  const valorTotalEtapa = itemsEnEtapa.reduce((acc, curr) => acc + (curr.valorEstimado || 0), 0);
                  const vacia = itemsEnEtapa.length === 0;
                  const sobrevolada = columnaSobrevolada === etapa.key;

                  return (
                    <div
                      key={etapa.key}
                      onDragOver={(e) => {
                        e.preventDefault();
                        if (columnaSobrevolada !== etapa.key) setColumnaSobrevolada(etapa.key);
                      }}
                      onDragLeave={() => setColumnaSobrevolada((c) => (c === etapa.key ? null : c))}
                      onDrop={() => {
                        if (arrastrandoId !== null) onMoverEtapa(arrastrandoId, etapa.key);
                        setArrastrandoId(null);
                        setColumnaSobrevolada(null);
                      }}
                      className={`flex flex-col rounded-xl border bg-paper-soft p-3 transition-colors ${
                        sobrevolada ? "border-gold bg-gold-pale/25" : "border-line"
                      } ${vacia && !sobrevolada ? "opacity-70" : ""}`}
                    >
                      {/* Barra de acento arriba (mismo lenguaje visual que el ítem activo del
                          sidebar) en vez de un Badge suelto -- identifica la etapa de un
                          vistazo sin competir en peso visual con el nombre. */}
                      <div className={`-mx-3 -mt-3 mb-2 h-[3px] shrink-0 rounded-t-xl ${TONE_ACCENT[etapa.tone]}`} />
                      <div className="flex items-center justify-between">
                        <p className="text-xs font-semibold text-ink">{etapa.label}</p>
                        <span className="text-[11px] font-mono text-ink-soft">{itemsEnEtapa.length}</span>
                      </div>
                      {valorTotalEtapa > 0 && (
                        <p className="mt-0.5 text-[11px] font-mono text-gold-deep">
                          {formatearMoneda(valorTotalEtapa)}
                        </p>
                      )}

                      {/* Tarjetas del Kanban: alto fijo moderado (no atado al viewport como
                          antes) -- con varias filas de columnas en pantalla, una sola columna
                          no debe estirarse casi a pantalla completa. Si una etapa tiene más
                          tarjetas de las que caben, esta lista interna scrollea sola. */}
                      <div className="mt-3 flex-1 space-y-2.5 overflow-y-auto max-h-80 pr-1">
                        {vacia ? (
                          <div
                            className={`rounded-lg border border-dashed p-4 text-center text-[11px] transition-colors ${
                              sobrevolada ? "border-gold text-gold-deep" : "border-line/60 text-ink-soft"
                            }`}
                          >
                            {sobrevolada ? "Soltar aquí" : "Sin oportunidades"}
                          </div>
                        ) : (
                          itemsEnEtapa.map((item) => (
                            <div
                              key={item.id}
                              draggable
                              onDragStart={() => setArrastrandoId(item.id)}
                              onDragEnd={() => {
                                setArrastrandoId(null);
                                setColumnaSobrevolada(null);
                              }}
                              className={`group cursor-grab rounded-lg border border-line bg-paper p-3 shadow-xs transition-all hover:shadow-sm active:cursor-grabbing ${
                                arrastrandoId === item.id ? "opacity-40" : ""
                              }`}
                            >
                              <div className="flex items-start gap-2">
                                <span
                                  className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-[10px] font-bold text-ink-fixed ${TONE_ACCENT[etapa.tone]}`}
                                >
                                  {iniciales(item.nombre)}
                                </span>
                                <div className="min-w-0 flex-1">
                                  <p className="truncate text-xs font-semibold text-ink">{item.nombre}</p>
                                  {item.areaPractica && (
                                    <p className="truncate text-[10px] text-ink-soft">{item.areaPractica}</p>
                                  )}
                                </div>
                                <DotsSixVertical
                                  className="h-3.5 w-3.5 shrink-0 text-ink-soft/40 opacity-0 transition-opacity group-hover:opacity-100"
                                  weight="bold"
                                />
                              </div>

                              <div className="mt-2 space-y-1 text-[11px] text-ink-soft">
                                {item.telefono && (
                                  <div className="flex items-center gap-1">
                                    <Phone className="h-3 w-3 shrink-0" />
                                    <span>{item.telefono}</span>
                                  </div>
                                )}
                                {item.correo && (
                                  <div className="flex items-center gap-1 truncate">
                                    <EnvelopeSimple className="h-3 w-3 shrink-0" />
                                    <span className="truncate">{item.correo}</span>
                                  </div>
                                )}
                              </div>

                              {item.mensaje && (
                                <p className="mt-2 text-[11px] text-ink-soft line-clamp-2 italic bg-paper-soft/60 p-1.5 rounded">
                                  "{item.mensaje}"
                                </p>
                              )}

                              <div className="mt-2.5 pt-2 border-t border-line/60 flex items-center justify-between">
                                <span className="text-[11px] font-mono font-medium text-gold-deep">
                                  {item.valorEstimado ? formatearMoneda(item.valorEstimado) : "Sin cotizar"}
                                </span>

                                <div className="flex items-center gap-1">
                                  {item.clienteCrmId ? (
                                    <span className="inline-flex items-center gap-0.5 text-[10px] font-medium text-emerald-700 bg-emerald-50 px-1.5 py-0.5 rounded">
                                      <UserCheck weight="bold" className="h-3 w-3" />
                                      Cliente
                                    </span>
                                  ) : (
                                    <button
                                      type="button"
                                      onClick={() => onConvertirACliente(item.id)}
                                      className="inline-flex items-center gap-1 rounded bg-gold/20 hover:bg-gold/30 px-1.5 py-0.5 text-[10px] font-medium text-gold-deep transition-colors"
                                      title="Convertir a Cliente 360"
                                    >
                                      <User weight="bold" className="h-3 w-3" />
                                      Convertir
                                    </button>
                                  )}
                                  {/* Flechas: único camino en touch, el drag nativo de HTML5
                                      no existe en móvil/tablet */}
                                  {idx > 0 && (
                                    <button
                                      type="button"
                                      onClick={() => onMoverEtapa(item.id, ETAPAS_ORDENADAS[idx - 1].key)}
                                      className="p-1 text-ink-soft hover:text-ink hover:bg-ink/5 rounded"
                                      title={`Retroceder a ${ETAPAS_ORDENADAS[idx - 1].label}`}
                                    >
                                      <ArrowLeft className="h-3 w-3" weight="bold" />
                                    </button>
                                  )}
                                  {idx < ETAPAS_ORDENADAS.length - 1 && (
                                    <button
                                      type="button"
                                      onClick={() => onMoverEtapa(item.id, ETAPAS_ORDENADAS[idx + 1].key)}
                                      className="p-1 text-gold-deep hover:bg-gold/20 rounded font-medium"
                                      title={`Avanzar a ${ETAPAS_ORDENADAS[idx + 1].label}`}
                                    >
                                      <ArrowRight className="h-3 w-3" weight="bold" />
                                    </button>
                                  )}
                                </div>
                              </div>
                            </div>
                          ))
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
              <p className="mt-3 text-[11px] text-ink-soft">
                Arrastra una tarjeta a otra columna para cambiarla de etapa, o usa las flechas.
              </p>
            </div>
          )}
        </div>
      )}

      {/* ======================= PESTAÑA 2: DIRECTORIO 360° ======================= */}
      {tabActiva === "directorio" && (
        <div className="space-y-4">
          {/* Filtros y Buscador */}
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div className="relative flex-1 lg:max-w-sm">
              <MagnifyingGlass className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-ink-soft" />
              <input
                type="text"
                value={inputBusqueda}
                onChange={(e) => setInputBusqueda(e.target.value)}
                placeholder="Buscar por nombre, documento, correo o teléfono..."
                className="w-full rounded-xl border border-line bg-paper pl-9 pr-3 py-2 text-sm text-ink placeholder:text-ink-soft focus:outline-none focus:ring-2 focus:ring-gold"
              />
            </div>

            <div className="flex flex-wrap items-center gap-2">
              {/* Filtro de cobro: pedido explícito del usuario, "ver quién me debe pagos"
                  sin tener que entrar a la ficha de cada cliente uno por uno. */}
              <select
                value={filtroCobro}
                onChange={(e) => setFiltroCobro(e.target.value as any)}
                className="rounded-xl border border-line bg-paper px-3 py-2 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-gold"
              >
                <option value="TODOS">Cualquier cobro</option>
                <option value="PENDIENTE">Deben pago</option>
                <option value="AL_DIA">Al día</option>
              </select>
              <select
                value={filtroTipo}
                onChange={(e) => setFiltroTipo(e.target.value as any)}
                className="rounded-xl border border-line bg-paper px-3 py-2 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-gold"
              >
                <option value="TODOS">Todos los tipos</option>
                <option value="PERSONA_NATURAL">Personas Naturales</option>
                <option value="EMPRESA">Empresas</option>
              </select>

              <select
                value={filtroEstado}
                onChange={(e) => setFiltroEstado(e.target.value as any)}
                className="rounded-xl border border-line bg-paper px-3 py-2 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-gold"
              >
                <option value="TODOS">Todos los estados</option>
                <option value="ACTIVO">Activos</option>
                <option value="PROSPECTO">Prospectos</option>
                <option value="FINALIZADO">Finalizados</option>
                <option value="INACTIVO">Inactivos / Archivados</option>
              </select>
            </div>
          </div>

          {cargandoClientes ? (
            <AdminLoader />
          ) : !clientes || clientes.length === 0 ? (
            <EmptyState
              title="No se encontraron clientes"
              description="No hay clientes que coincidan con los criterios de búsqueda o aún no se han registrado en el CRM."
            />
          ) : (
            // Lista densa tipo tabla en vez de tarjetas: con 225+ clientes reales, un grid
            // de tarjetas grandes se sentía genérico y obligaba a escanear mucho espacio
            // vacío por cada fila de datos -- pedido explícito del usuario. Cada fila sigue
            // abriendo la Ficha 360° completa al hacer clic.
            <div className="overflow-hidden rounded-2xl border border-line bg-surface">
              <div className="hidden grid-cols-[2.1fr_1.6fr_0.7fr_0.9fr_1fr] gap-3 border-b border-line bg-paper px-4 py-2.5 text-[11px] font-semibold uppercase tracking-wide text-ink-soft/70 lg:grid">
                <span>Cliente</span>
                <span>Contacto</span>
                <span className="text-center">Casos</span>
                <span className="text-center">Cobro</span>
                <span className="text-center">Estado</span>
              </div>
              <div className="divide-y divide-line">
                {clientes.map((c) => (
                  <button
                    key={c.id}
                    type="button"
                    onClick={() => setClienteSeleccionadoId(c.id)}
                    className="grid w-full grid-cols-1 items-center gap-2 px-4 py-3 text-left transition-colors hover:bg-ink/[0.03] lg:grid-cols-[2.1fr_1.6fr_0.7fr_0.9fr_1fr] lg:gap-3"
                  >
                    <div className="flex min-w-0 items-center gap-2">
                      <span
                        className={`h-1.5 w-1.5 shrink-0 rounded-full ${c.tipo === "EMPRESA" ? "bg-gold" : "bg-ink/25"}`}
                        aria-hidden="true"
                      />
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium text-ink">{c.nombre}</p>
                        <p className="truncate text-[11px] text-ink-soft">
                          {c.cedulaNit ? `Doc: ${c.cedulaNit}` : "Sin documento"}
                          {c.ciudad ? ` · ${c.ciudad}` : ""}
                        </p>
                      </div>
                    </div>

                    <div className="min-w-0 space-y-0.5 text-xs text-ink-soft">
                      {c.correo && (
                        <p className="flex items-center gap-1 truncate">
                          <EnvelopeSimple className="h-3 w-3 shrink-0" />
                          {c.correo}
                        </p>
                      )}
                      {c.telefono && (
                        <p className="flex items-center gap-1">
                          <Phone className="h-3 w-3 shrink-0" />
                          {c.telefono}
                        </p>
                      )}
                      {!c.correo && !c.telefono && <p className="italic text-ink-soft/60">Sin contacto</p>}
                    </div>

                    <div className="flex items-center gap-1 text-xs text-ink-soft lg:justify-center">
                      <Briefcase className="h-3.5 w-3.5 shrink-0 text-gold-deep" />
                      <span className="font-mono">{c.totalCasos}</span>
                    </div>

                    <div className="lg:flex lg:justify-center">
                      <Badge tone={c.pagoAlDia ? "success" : "warning"}>{c.pagoAlDia ? "Al día" : "Debe"}</Badge>
                    </div>

                    <div className="flex items-center justify-between gap-2 lg:justify-center">
                      <Badge tone={c.estado === "INACTIVO" ? "danger" : c.estado === "ACTIVO" ? "neutral" : "gold"}>
                        {c.estado}
                      </Badge>
                      <CaretRight className="h-4 w-4 shrink-0 text-ink-soft/50 lg:hidden" weight="bold" />
                    </div>
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Paginación: el directorio ya pasa de 225 clientes, antes se renderizaban todos
              de una sola vez en este grid (ver reforma del CRM) */}
          {clientes && clientes.length > 0 && (
            <div className="flex flex-col items-center justify-between gap-3 border-t border-line pt-4 sm:flex-row">
              <p className="text-xs text-ink-soft">
                {totalClientesDirectorio} cliente{totalClientesDirectorio === 1 ? "" : "s"} en total
                {" · "}página {paginaDirectorio + 1} de {totalPaginasDirectorio}
              </p>
              <div className="flex items-center gap-2">
                <AdminButton
                  variant="ghost"
                  onClick={() => setPaginaDirectorio((p) => Math.max(0, p - 1))}
                  disabled={paginaDirectorio === 0 || cargandoClientes}
                >
                  <ArrowLeft className="h-4 w-4" weight="bold" />
                  Anterior
                </AdminButton>
                <AdminButton
                  variant="ghost"
                  onClick={() => setPaginaDirectorio((p) => Math.min(totalPaginasDirectorio - 1, p + 1))}
                  disabled={paginaDirectorio >= totalPaginasDirectorio - 1 || cargandoClientes}
                >
                  Siguiente
                  <ArrowRight className="h-4 w-4" weight="bold" />
                </AdminButton>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ======================= PESTAÑA 3: DASHBOARD ======================= */}
      {tabActiva === "dashboard" && (
        <div className="space-y-6">
          {dashboard ? (
            <>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <AdminCard className="p-5">
                  <p className="text-xs font-semibold uppercase tracking-wider text-ink-soft">Total Clientes CRM</p>
                  <p className="mt-2 text-3xl font-bold text-ink">{dashboard.totalClientes}</p>
                  <p className="mt-1 text-xs text-ink-soft">Base de clientes registrados</p>
                </AdminCard>

                <AdminCard className="p-5">
                  <p className="text-xs font-semibold uppercase tracking-wider text-ink-soft">Prospectos en Pipeline</p>
                  <p className="mt-2 text-3xl font-bold text-gold-deep">{dashboard.prospectosActivos}</p>
                  <p className="mt-1 text-xs text-ink-soft">Oportunidades en proceso de cierre</p>
                </AdminCard>

                <AdminCard className="p-5">
                  <p className="text-xs font-semibold uppercase tracking-wider text-ink-soft">Casos Legales Activos</p>
                  <p className="mt-2 text-3xl font-bold text-ink">{dashboard.casosActivos}</p>
                  <p className="mt-1 text-xs text-ink-soft">Expedientes sincronizados con Google Sheets</p>
                </AdminCard>

                <AdminCard className="p-5">
                  <p className="text-xs font-semibold uppercase tracking-wider text-ink-soft">Valor en Pipeline</p>
                  <p className="mt-2 text-2xl font-mono font-bold text-emerald-600">
                    {formatearMoneda(dashboard.valorTotalPipeline)}
                  </p>
                  <p className="mt-1 text-xs text-ink-soft">Estimación de honorarios en juego</p>
                </AdminCard>
              </div>

              {/* Estado de Cobros y Embudo */}
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                <AdminCard className="p-5">
                  <div className="flex items-center justify-between">
                    <h3 className="text-sm font-semibold text-ink">Estado Mensual de Cobros</h3>
                    <span className="text-[11px] text-ink-soft">Solo honorarios asignados (excluye sin costo)</span>
                  </div>
                  <div className="mt-4 flex items-center gap-6">
                    <div className="flex-1 rounded-xl bg-emerald-50 border border-emerald-200 p-4 text-center">
                      <p className="text-xs font-medium text-emerald-800">Cobros al Día (Aprobados)</p>
                      <p className="mt-2 text-3xl font-bold text-emerald-700">{dashboard.cobrosAlDia}</p>
                    </div>
                    <div className="flex-1 rounded-xl bg-amber-50 border border-amber-200 p-4 text-center">
                      <p className="text-xs font-medium text-amber-800">Cobros Pendientes de Respuesta</p>
                      <p className="mt-2 text-3xl font-bold text-amber-700">{dashboard.cobrosPendientes}</p>
                    </div>
                  </div>
                </AdminCard>

                <AdminCard className="p-5">
                  <h3 className="text-sm font-semibold text-ink">Distribución de Leads en Pipeline</h3>
                  <div className="mt-4 space-y-2">
                    {ETAPAS_ORDENADAS.map((etapa) => {
                      const count = dashboard.leadsPorEtapa?.[etapa.key] || 0;
                      return (
                        <div key={etapa.key} className="flex items-center justify-between text-xs">
                          <span className="text-ink-soft font-medium">{etapa.label}</span>
                          <span className="font-semibold text-ink font-mono">{count}</span>
                        </div>
                      );
                    })}
                  </div>
                </AdminCard>
              </div>
            </>
          ) : (
            <AdminLoader />
          )}
        </div>
      )}

      {/* ======================= DRAWER FICHA 360° ======================= */}
      {clienteSeleccionadoId !== null && (
        <div className="fixed inset-0 z-50 flex justify-end bg-black/40 backdrop-blur-xs">
          <div className="w-full max-w-2xl bg-paper h-full shadow-2xl overflow-y-auto flex flex-col border-l border-line">
            {/* Header Drawer */}
            <div className="p-5 border-b border-line flex items-center justify-between sticky top-0 bg-paper/95 backdrop-blur-sm z-10">
              <div className="flex items-center gap-2">
                <AddressBook weight="bold" className="h-5 w-5 text-gold-deep" />
                <h2 className="text-base font-bold text-ink">Ficha Integral 360° del Cliente</h2>
              </div>
              <div className="flex items-center gap-1">
                {clienteDetalle && !modoEdicionCliente && (
                  <>
                    <button
                      type="button"
                      onClick={() => setModoEdicionCliente(true)}
                      className="inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-semibold text-ink-soft hover:bg-ink/5 hover:text-ink"
                    >
                      <PencilSimple weight="bold" className="h-3.5 w-3.5" />
                      Editar
                    </button>
                    {sesion?.rol === "ADMIN_GENERAL" && clienteDetalle.cliente.estado !== "INACTIVO" && (
                      <button
                        type="button"
                        onClick={onArchivarCliente}
                        disabled={archivando}
                        className="inline-flex items-center gap-1 rounded-lg px-2.5 py-1.5 text-xs font-semibold text-red-600 hover:bg-red-50 disabled:opacity-50"
                      >
                        <Trash weight="bold" className="h-3.5 w-3.5" />
                        {archivando ? "Archivando..." : "Archivar"}
                      </button>
                    )}
                  </>
                )}
                <button
                  type="button"
                  onClick={() => setClienteSeleccionadoId(null)}
                  className="p-1 rounded-lg text-ink-soft hover:bg-ink/5 hover:text-ink"
                >
                  <X className="h-5 w-5" weight="bold" />
                </button>
              </div>
            </div>

            {cargandoDetalle || !clienteDetalle ? (
              <div className="p-10">
                <AdminLoader />
              </div>
            ) : (
              <div className="p-6 space-y-6 flex-1">
                {/* Cabecera Info Cliente */}
                {modoEdicionCliente ? (
                  <form
                    onSubmit={onGuardarEdicionCliente}
                    className="rounded-xl border border-gold-deep/40 bg-paper-soft p-4 space-y-3"
                  >
                    <div className="flex items-center gap-4">
                      <label className="flex items-center gap-1.5 text-xs text-ink cursor-pointer">
                        <input
                          type="radio"
                          checked={editTipo === "PERSONA_NATURAL"}
                          onChange={() => setEditTipo("PERSONA_NATURAL")}
                          className="text-gold focus:ring-gold"
                        />
                        Persona Natural
                      </label>
                      <label className="flex items-center gap-1.5 text-xs text-ink cursor-pointer">
                        <input
                          type="radio"
                          checked={editTipo === "EMPRESA"}
                          onChange={() => setEditTipo("EMPRESA")}
                          className="text-gold focus:ring-gold"
                        />
                        Empresa
                      </label>
                    </div>

                    <input
                      type="text"
                      required
                      value={editNombre}
                      onChange={(e) => setEditNombre(e.target.value)}
                      placeholder="Nombre completo o razón social"
                      className="w-full rounded-lg border border-line bg-paper px-3 py-2 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-gold"
                    />

                    <div className="grid grid-cols-2 gap-2">
                      <input
                        type="text"
                        value={editCedulaNit}
                        onChange={(e) => setEditCedulaNit(e.target.value)}
                        placeholder="Cédula o NIT"
                        className="w-full rounded-lg border border-line bg-paper px-3 py-2 text-xs text-ink focus:outline-none focus:ring-2 focus:ring-gold"
                      />
                      <input
                        type="text"
                        value={editCiudad}
                        onChange={(e) => setEditCiudad(e.target.value)}
                        placeholder="Ciudad"
                        className="w-full rounded-lg border border-line bg-paper px-3 py-2 text-xs text-ink focus:outline-none focus:ring-2 focus:ring-gold"
                      />
                    </div>

                    <div className="grid grid-cols-2 gap-2">
                      <input
                        type="email"
                        value={editCorreo}
                        onChange={(e) => setEditCorreo(e.target.value)}
                        placeholder="Correo electrónico"
                        className="w-full rounded-lg border border-line bg-paper px-3 py-2 text-xs text-ink focus:outline-none focus:ring-2 focus:ring-gold"
                      />
                      <input
                        type="tel"
                        value={editTelefono}
                        onChange={(e) => setEditTelefono(e.target.value)}
                        placeholder="Teléfono celular"
                        className="w-full rounded-lg border border-line bg-paper px-3 py-2 text-xs text-ink focus:outline-none focus:ring-2 focus:ring-gold"
                      />
                    </div>

                    <select
                      value={editEstado}
                      onChange={(e) => setEditEstado(e.target.value as EstadoClienteCrm)}
                      className="w-full rounded-lg border border-line bg-paper px-3 py-2 text-xs text-ink focus:outline-none focus:ring-2 focus:ring-gold"
                    >
                      <option value="ACTIVO">Activo</option>
                      <option value="PROSPECTO">Prospecto</option>
                      <option value="FINALIZADO">Finalizado</option>
                      <option value="INACTIVO">Inactivo</option>
                    </select>

                    <textarea
                      rows={2}
                      value={editNotas}
                      onChange={(e) => setEditNotas(e.target.value)}
                      placeholder="Notas internas o antecedentes"
                      className="w-full rounded-lg border border-line bg-paper p-2 text-xs text-ink focus:outline-none focus:ring-2 focus:ring-gold"
                    />

                    <div className="flex justify-end gap-2 pt-1">
                      <button
                        type="button"
                        onClick={() => setModoEdicionCliente(false)}
                        className="rounded-lg px-3 py-1.5 text-xs font-medium text-ink-soft hover:bg-ink/5"
                      >
                        Cancelar
                      </button>
                      <button
                        type="submit"
                        disabled={guardandoEdicion}
                        className="rounded-lg bg-gold px-4 py-1.5 text-xs font-semibold text-ink-fixed hover:bg-gold-deep hover:text-white transition-colors disabled:opacity-50"
                      >
                        {guardandoEdicion ? "Guardando..." : "Guardar cambios"}
                      </button>
                    </div>
                  </form>
                ) : (
                  <div className="rounded-xl border border-line bg-paper-soft p-4">
                    <div className="flex items-start justify-between">
                      <div>
                        <h3 className="text-lg font-bold text-ink">{clienteDetalle.cliente.nombre}</h3>
                        <p className="text-xs text-ink-soft mt-0.5">
                          {clienteDetalle.cliente.cedulaNit ? `NIT/CC: ${clienteDetalle.cliente.cedulaNit}` : "Sin ID"}
                          {clienteDetalle.cliente.ciudad ? ` · ${clienteDetalle.cliente.ciudad}` : ""}
                        </p>
                      </div>
                      <div className="flex flex-col items-end gap-1">
                        <Badge tone={clienteDetalle.cliente.tipo === "EMPRESA" ? "gold" : "neutral"}>
                          {clienteDetalle.cliente.tipo === "EMPRESA" ? "Empresa" : "Persona Natural"}
                        </Badge>
                        <Badge tone={clienteDetalle.cliente.estado === "ACTIVO" ? "success" : "neutral"}>
                          {clienteDetalle.cliente.estado}
                        </Badge>
                      </div>
                    </div>

                    {/* Vínculos directos de contacto */}
                    <div className="mt-3 flex flex-wrap gap-2 pt-3 border-t border-line">
                      {clienteDetalle.cliente.telefono && (
                        <a
                          href={`https://wa.me/57${clienteDetalle.cliente.telefono.replace(/\D/g, "")}`}
                          target="_blank"
                          rel="noreferrer"
                          className="inline-flex items-center gap-1 rounded-lg bg-emerald-50 border border-emerald-200 px-3 py-1.5 text-xs font-semibold text-emerald-700 hover:bg-emerald-100"
                        >
                          <WhatsappLogo weight="bold" className="h-4 w-4" />
                          Abrir WhatsApp ({clienteDetalle.cliente.telefono})
                        </a>
                      )}
                      {clienteDetalle.cliente.correo && (
                        <a
                          href={`mailto:${clienteDetalle.cliente.correo}`}
                          className="inline-flex items-center gap-1 rounded-lg bg-paper border border-line px-3 py-1.5 text-xs font-medium text-ink hover:bg-ink/5"
                        >
                          <EnvelopeSimple weight="bold" className="h-4 w-4" />
                          Enviar Correo
                        </a>
                      )}
                    </div>
                  </div>
                )}

                {/* Sub-secciones: antes casos, cobros, citas, tareas y bitácora estaban
                    apilados en un solo scroll largo sin jerarquía -- pedido explícito del
                    usuario. Cada bloque vive ahora en su propia pestaña dentro de la ficha. */}
                <div className="flex gap-1 overflow-x-auto border-b border-line">
                  {(
                    [
                      { key: "resumen", label: "Resumen" },
                      {
                        key: "casos",
                        label: `Casos y cobros${
                          clienteDetalle.casos.length + clienteDetalle.cobros.length > 0
                            ? ` (${clienteDetalle.casos.length + clienteDetalle.cobros.length})`
                            : ""
                        }`,
                      },
                      {
                        key: "tareas",
                        label: `Tareas${clienteDetalle.tareas.length > 0 ? ` (${clienteDetalle.tareas.length})` : ""}`,
                      },
                      {
                        key: "bitacora",
                        label: `Bitácora${clienteDetalle.actividades.length > 0 ? ` (${clienteDetalle.actividades.length})` : ""}`,
                      },
                    ] as const
                  ).map((t) => (
                    <button
                      key={t.key}
                      type="button"
                      onClick={() => setFichaTabActiva(t.key)}
                      className={`shrink-0 border-b-2 px-3 py-2 text-xs font-semibold transition-colors ${
                        fichaTabActiva === t.key
                          ? "border-gold text-ink"
                          : "border-transparent text-ink-soft hover:text-ink"
                      }`}
                    >
                      {t.label}
                    </button>
                  ))}
                </div>

                {/* Resumen: panorama en segundos (pedido explícito: "ver quién me debe
                    pagos" y el estado general sin tener que abrir cada sección) */}
                {fichaTabActiva === "resumen" && (
                  <div className="space-y-4">
                    <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                      <div className="rounded-xl border border-line bg-paper-soft p-3">
                        <p className="text-[11px] font-semibold uppercase tracking-wide text-ink-soft">Casos</p>
                        <p className="mt-1 text-xl font-bold text-ink">{clienteDetalle.casos.length}</p>
                      </div>
                      <div className="rounded-xl border border-line bg-paper-soft p-3">
                        <p className="text-[11px] font-semibold uppercase tracking-wide text-ink-soft">Cobro</p>
                        <p className={`mt-1 text-sm font-bold ${clienteDetalle.cliente.pagoAlDia ? "text-emerald-700" : "text-amber-700"}`}>
                          {clienteDetalle.cliente.pagoAlDia ? "Al día" : "Pendiente"}
                        </p>
                      </div>
                      <div className="rounded-xl border border-line bg-paper-soft p-3">
                        <p className="text-[11px] font-semibold uppercase tracking-wide text-ink-soft">Tareas pend.</p>
                        <p className="mt-1 text-xl font-bold text-ink">
                          {clienteDetalle.tareas.filter((t) => !t.completada).length}
                        </p>
                      </div>
                      <div className="rounded-xl border border-line bg-paper-soft p-3">
                        <p className="text-[11px] font-semibold uppercase tracking-wide text-ink-soft">Citas</p>
                        <p className="mt-1 text-xl font-bold text-ink">{clienteDetalle.citas.length}</p>
                      </div>
                    </div>

                    {clienteDetalle.cliente.notas && (
                      <div>
                        <h4 className="mb-1.5 text-sm font-bold text-ink">Notas internas</h4>
                        <p className="rounded-lg border border-line bg-paper-soft p-3 text-xs text-ink-soft whitespace-pre-wrap">
                          {clienteDetalle.cliente.notas}
                        </p>
                      </div>
                    )}

                    {clienteDetalle.citas.length > 0 && (
                      <div>
                        <h4 className="mb-1.5 text-sm font-bold text-ink flex items-center gap-1.5">
                          <CalendarCheck weight="bold" className="h-4 w-4 text-purple-600" />
                          Próximas citas
                        </h4>
                        <div className="space-y-2">
                          {clienteDetalle.citas.map((cita) => (
                            <div key={cita.id} className="rounded-lg border border-line p-3 text-xs bg-paper flex items-center justify-between">
                              <div>
                                <p className="font-semibold text-ink">{cita.tipoReunion}</p>
                                <p className="text-ink-soft">{formatearFecha(cita.fechaHora)}</p>
                              </div>
                              {cita.linkReunion && (
                                <a
                                  href={cita.linkReunion}
                                  target="_blank"
                                  rel="noreferrer"
                                  className="text-xs font-semibold text-gold-deep hover:underline"
                                >
                                  Unirse a reunión
                                </a>
                              )}
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                )}

                {/* Casos y Cobros */}
                {fichaTabActiva === "casos" && (
                  <div className="space-y-6">
                    <div>
                      <h4 className="text-sm font-bold text-ink flex items-center gap-1.5 mb-2">
                        <Briefcase weight="bold" className="h-4 w-4 text-gold-deep" />
                        Expedientes y Casos Vinculados ({clienteDetalle.casos.length})
                      </h4>
                      {clienteDetalle.casos.length === 0 ? (
                        <p className="text-xs text-ink-soft italic bg-paper-soft p-3 rounded-lg border border-line">
                          No hay casos asignados a este cliente aún en la hoja de casos.
                        </p>
                      ) : (
                        <div className="space-y-2">
                          {clienteDetalle.casos.map((caso) => (
                            <div key={caso.id} className="rounded-lg border border-line p-3 text-xs bg-paper flex items-center justify-between">
                              <div>
                                <p className="font-semibold text-ink">Radicado: {caso.radicadoId ?? "Sin radicado"}</p>
                                <p className="text-ink-soft">Fuente: {caso.fuente}</p>
                              </div>
                              <div className="flex items-center gap-1.5">
                                <NotificationBadge size="sm" tone={caso.whatsappEnviado ? "success" : "neutral"} icon={<WhatsappLogo className="h-3 w-3" />}>
                                  {caso.whatsappEnviado ? "WA Enviado" : "WA Pend"}
                                </NotificationBadge>
                                <NotificationBadge size="sm" tone={caso.correoEnviado ? "success" : "neutral"} icon={<EnvelopeSimple className="h-3 w-3" />}>
                                  {caso.correoEnviado ? "Correo Enviado" : "Correo Pend"}
                                </NotificationBadge>
                              </div>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>

                    <div>
                      <h4 className="text-sm font-bold text-ink flex items-center gap-1.5 mb-2">
                        <CurrencyDollar weight="bold" className="h-4 w-4 text-emerald-600" />
                        Cobros y Facturación Mensual ({clienteDetalle.cobros.length})
                      </h4>
                      {clienteDetalle.cobros.length === 0 ? (
                        <p className="text-xs text-ink-soft italic bg-paper-soft p-3 rounded-lg border border-line">
                          Sin cobros registrados en la hoja de cobros pendientes.
                        </p>
                      ) : (
                        <div className="space-y-2">
                          {clienteDetalle.cobros.map((cobro) => (
                            <div key={cobro.id} className="rounded-lg border border-line p-3 text-xs bg-paper flex items-center justify-between">
                              <div>
                                <p className="font-semibold text-ink font-mono text-gold-deep">
                                  {tieneCosto(cobro.honorarios) ? cobro.honorarios : "Caso sin costo"}
                                </p>
                                <p className="text-ink-soft">Fila {cobro.numeroFila} · {cobro.tipo}</p>
                              </div>
                              <div className="flex items-center gap-1.5">
                                {tieneCosto(cobro.honorarios) ? (
                                  <Badge tone={cobro.pagoEsteMes ? "success" : "warning"}>
                                    {cobro.pagoEsteMes ? "Pago Aprobado" : "Respuesta de pago pendiente"}
                                  </Badge>
                                ) : (
                                  <Badge tone="neutral">
                                    Sin cobro asignado
                                  </Badge>
                                )}
                                {cobro.respondioMensaje && (
                                  <Badge tone={cobro.respondioMensaje.toLowerCase().startsWith("s") ? "success" : "danger"}>
                                    Resp: {cobro.respondioMensaje}
                                  </Badge>
                                )}
                              </div>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>
                )}

                {/* Tareas Pendientes */}
                {fichaTabActiva === "tareas" && (
                  <div>
                    {/* Formulario Nueva Tarea */}
                    <form onSubmit={onGuardarTarea} className="flex gap-2 mb-3">
                      <input
                        type="text"
                        value={nuevaTareaTitulo}
                        onChange={(e) => setNuevaTareaTitulo(e.target.value)}
                        placeholder="Nueva tarea jurídica (ej: Radicar memorial, Solicitar poder)..."
                        className="flex-1 rounded-lg border border-line bg-paper px-3 py-1.5 text-xs text-ink placeholder:text-ink-soft focus:outline-none focus:ring-2 focus:ring-gold"
                      />
                      <select
                        value={nuevaTareaPrioridad}
                        onChange={(e) => setNuevaTareaPrioridad(e.target.value as PrioridadTareaCrm)}
                        className="rounded-lg border border-line bg-paper px-2 py-1.5 text-xs text-ink focus:outline-none"
                      >
                        <option value="BAJA">Baja</option>
                        <option value="MEDIA">Media</option>
                        <option value="ALTA">Alta</option>
                        <option value="URGENTE">Urgente</option>
                      </select>
                      <button
                        type="submit"
                        disabled={guardandoTarea}
                        className="rounded-lg bg-gold px-3 py-1.5 text-xs font-semibold text-ink-fixed hover:bg-gold-deep hover:text-white transition-colors"
                      >
                        Asignar
                      </button>
                    </form>

                    {clienteDetalle.tareas.length === 0 ? (
                      <p className="text-xs text-ink-soft italic bg-paper-soft p-3 rounded-lg border border-line">
                        Sin tareas asignadas a este cliente.
                      </p>
                    ) : (
                      <div className="space-y-1.5">
                        {clienteDetalle.tareas.map((tarea) => (
                          <div
                            key={tarea.id}
                            className={`rounded-lg border p-2.5 text-xs flex items-center justify-between transition-colors ${
                              tarea.completada ? "bg-paper-soft/40 border-line/40 text-ink-soft" : "bg-paper border-line"
                            }`}
                          >
                            <div className="flex items-center gap-2">
                              <input
                                type="checkbox"
                                checked={tarea.completada}
                                onChange={() => onToggleTarea(tarea.id, tarea.completada)}
                                className="h-4 w-4 rounded border-line text-gold focus:ring-gold"
                              />
                              <span className={tarea.completada ? "line-through text-ink-soft" : "font-medium text-ink"}>
                                {tarea.titulo}
                              </span>
                            </div>
                            <Badge
                              tone={
                                tarea.prioridad === "ALTA"
                                  ? "danger"
                                  : tarea.prioridad === "MEDIA"
                                    ? "warning"
                                    : "neutral"
                              }
                            >
                              {tarea.prioridad}
                            </Badge>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}

                {/* Bitácora de Actividades y Notas */}
                {fichaTabActiva === "bitacora" && (
                  <div>
                    {/* Formulario Nueva Actividad */}
                    <form onSubmit={onGuardarActividad} className="rounded-xl border border-line bg-paper-soft p-3 mb-4 space-y-2">
                      <div className="flex gap-2">
                        <select
                          value={nuevaActividadTipo}
                          onChange={(e) => setNuevaActividadTipo(e.target.value as TipoActividadCrm)}
                          className="rounded-lg border border-line bg-paper px-2.5 py-1.5 text-xs text-ink focus:outline-none"
                        >
                          <option value="NOTA_INTERNA">Nota interna</option>
                          <option value="LLAMADA">Llamada telefónica</option>
                          <option value="WHATSAPP">WhatsApp</option>
                          <option value="CORREO">Correo electrónico</option>
                          <option value="REUNION">Reunión con cliente</option>
                        </select>
                        <input
                          type="text"
                          value={nuevaActividadTitulo}
                          onChange={(e) => setNuevaActividadTitulo(e.target.value)}
                          placeholder="Título o resumen del contacto..."
                          className="flex-1 rounded-lg border border-line bg-paper px-3 py-1.5 text-xs text-ink placeholder:text-ink-soft focus:outline-none focus:ring-2 focus:ring-gold"
                        />
                      </div>
                      <textarea
                        rows={2}
                        value={nuevaActividadDesc}
                        onChange={(e) => setNuevaActividadDesc(e.target.value)}
                        placeholder="Detalles relevantes de la conversación, acuerdos, peticiones del cliente..."
                        className="w-full rounded-lg border border-line bg-paper p-2 text-xs text-ink placeholder:text-ink-soft focus:outline-none focus:ring-2 focus:ring-gold"
                      />
                      <div className="flex justify-end">
                        <button
                          type="submit"
                          disabled={guardandoActividad}
                          className="rounded-lg bg-ink text-paper px-3 py-1.5 text-xs font-semibold hover:bg-ink/90 transition-colors"
                        >
                          Registrar en Bitácora
                        </button>
                      </div>
                    </form>

                    {/* Timeline de Actividades */}
                    <div className="relative pl-4 border-l-2 border-line space-y-4">
                      {clienteDetalle.actividades.length === 0 ? (
                        <p className="text-xs text-ink-soft italic">No hay actividades registradas en la bitácora.</p>
                      ) : (
                        clienteDetalle.actividades.map((act) => (
                          <div key={act.id} className="relative">
                            <span className="absolute -left-[21px] top-1 h-2.5 w-2.5 rounded-full bg-gold ring-4 ring-paper" />
                            <div className="flex items-center justify-between">
                              <p className="text-xs font-bold text-ink">{act.titulo}</p>
                              <span className="text-[10px] text-ink-soft">{formatearFecha(act.fechaActividad)}</span>
                            </div>
                            {act.descripcion && (
                              <p className="mt-1 text-xs text-ink-soft bg-paper border border-line/60 p-2 rounded-lg">
                                {act.descripcion}
                              </p>
                            )}
                            <p className="mt-0.5 text-[10px] text-ink-soft">
                              Registrado por {act.usuarioNombre ?? "Firma SIE"}
                            </p>
                          </div>
                        ))
                      )}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      )}

      {/* ======================= MODAL NUEVO CLIENTE ======================= */}
      {modalNuevoCliente && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-xs">
          <div className="w-full max-w-lg rounded-2xl border border-line bg-paper p-6 shadow-2xl">
            <div className="flex items-center justify-between pb-4 border-b border-line">
              <h3 className="text-base font-bold text-ink flex items-center gap-2">
                <AddressBook weight="bold" className="h-5 w-5 text-gold-deep" />
                Registrar Nuevo Cliente en CRM
              </h3>
              <button
                type="button"
                onClick={() => setModalNuevoCliente(false)}
                className="p-1 rounded-lg text-ink-soft hover:bg-ink/5"
              >
                <X className="h-5 w-5" weight="bold" />
              </button>
            </div>

            <form onSubmit={onSubmitNuevoCliente} className="mt-4 space-y-3">
              <div>
                <label className="block text-xs font-semibold text-ink">Tipo de Cliente *</label>
                <div className="mt-1 flex gap-4">
                  <label className="flex items-center gap-1.5 text-xs text-ink cursor-pointer">
                    <input
                      type="radio"
                      name="nuevoTipo"
                      value="PERSONA_NATURAL"
                      checked={nuevoTipo === "PERSONA_NATURAL"}
                      onChange={() => setNuevoTipo("PERSONA_NATURAL")}
                      className="text-gold focus:ring-gold"
                    />
                    Persona Natural
                  </label>
                  <label className="flex items-center gap-1.5 text-xs text-ink cursor-pointer">
                    <input
                      type="radio"
                      name="nuevoTipo"
                      value="EMPRESA"
                      checked={nuevoTipo === "EMPRESA"}
                      onChange={() => setNuevoTipo("EMPRESA")}
                      className="text-gold focus:ring-gold"
                    />
                    Empresa / Persona Jurídica
                  </label>
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-ink">Nombre Completo o Razón Social *</label>
                <input
                  type="text"
                  required
                  value={nuevoNombre}
                  onChange={(e) => setNuevoNombre(e.target.value)}
                  placeholder="Ej: Inversiones ABC S.A.S. o Juan Pérez"
                  className="mt-1 w-full rounded-lg border border-line bg-paper px-3 py-2 text-xs text-ink focus:outline-none focus:ring-2 focus:ring-gold"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-ink">Cédula o NIT</label>
                  <input
                    type="text"
                    value={nuevoCedulaNit}
                    onChange={(e) => setNuevoCedulaNit(e.target.value)}
                    placeholder="Ej: 901234567-8"
                    className="mt-1 w-full rounded-lg border border-line bg-paper px-3 py-2 text-xs text-ink focus:outline-none focus:ring-2 focus:ring-gold"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-ink">Ciudad</label>
                  <input
                    type="text"
                    value={nuevaCiudad}
                    onChange={(e) => setNuevaCiudad(e.target.value)}
                    placeholder="Ej: Bucaramanga"
                    className="mt-1 w-full rounded-lg border border-line bg-paper px-3 py-2 text-xs text-ink focus:outline-none focus:ring-2 focus:ring-gold"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-ink">Correo Electrónico</label>
                  <input
                    type="email"
                    value={nuevoCorreo}
                    onChange={(e) => setNuevoCorreo(e.target.value)}
                    placeholder="cliente@ejemplo.com"
                    className="mt-1 w-full rounded-lg border border-line bg-paper px-3 py-2 text-xs text-ink focus:outline-none focus:ring-2 focus:ring-gold"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-ink">Teléfono Celular</label>
                  <input
                    type="tel"
                    value={nuevoTelefono}
                    onChange={(e) => setNuevoTelefono(e.target.value)}
                    placeholder="Ej: 3126029742"
                    className="mt-1 w-full rounded-lg border border-line bg-paper px-3 py-2 text-xs text-ink focus:outline-none focus:ring-2 focus:ring-gold"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-ink">Notas Internas o Antecedentes</label>
                <textarea
                  rows={2}
                  value={nuevasNotas}
                  onChange={(e) => setNuevasNotas(e.target.value)}
                  placeholder="Información clave sobre el cliente..."
                  className="mt-1 w-full rounded-lg border border-line bg-paper px-3 py-2 text-xs text-ink focus:outline-none focus:ring-2 focus:ring-gold"
                />
              </div>

              <div className="mt-5 flex justify-end gap-2 pt-3 border-t border-line">
                <button
                  type="button"
                  onClick={() => setModalNuevoCliente(false)}
                  className="rounded-lg px-4 py-2 text-xs font-medium text-ink-soft hover:bg-ink/5"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={guardandoCliente}
                  className="rounded-lg bg-gold px-4 py-2 text-xs font-semibold text-ink-fixed hover:bg-gold-deep hover:text-white transition-colors"
                >
                  {guardandoCliente ? "Guardando..." : "Crear Cliente"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
