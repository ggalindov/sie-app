import { borrarToken, obtenerToken } from "@/lib/auth";
import type { Categoria } from "@/lib/api";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
  ) {
    super(message);
  }
}

async function parseErrorMessage(res: Response): Promise<string> {
  try {
    const body = await res.json();
    return body.mensaje ?? `Error ${res.status}`;
  } catch {
    return `Error ${res.status}`;
  }
}

async function pedido<T>(
  path: string,
  init?: RequestInit,
): Promise<T> {
  const token = obtenerToken();
  const res = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init?.headers,
    },
  });

  // El 401 de /api/auth/login es un intento de login fallido (correo/contraseña
  // incorrectos), no una sesión expirada: no hay ninguna sesión previa que "expirar" en
  // esa ruta. El mensaje genérico de sesión expirada solo aplica a las demás rutas
  // autenticadas, donde un 401 sí significa que el token ya no es válido.
  if (res.status === 401 && path !== "/api/auth/login") {
    // Antes, cada página solo mostraba el mensaje en un toast y se quedaba ahí: el
    // token viejo seguía en localStorage y la página seguía intentando (y fallando)
    // peticiones hasta que el admin saliera manualmente. Centralizado aquí en vez de
    // en cada página, para que no dependa de que cada una lo implemente por su cuenta.
    borrarToken();
    if (typeof window !== "undefined" && !window.location.pathname.startsWith("/admin/login")) {
      window.location.href = "/admin/login";
    }
    throw new ApiError("Tu sesión expiró. Inicia sesión de nuevo.", 401);
  }
  if (!res.ok) {
    throw new ApiError(await parseErrorMessage(res), res.status);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

// Descarga autenticada de un archivo binario (Excel, etc.): un <a href="..."> normal no
// puede llevar el header Authorization, así que hay que pedirlo por fetch, convertir la
// respuesta a blob, y disparar la descarga con un <a> temporal apuntando a un object URL.
async function descargarArchivo(path: string, nombreSugerido: string): Promise<void> {
  const token = obtenerToken();
  const res = await fetch(`${API_URL}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });

  if (res.status === 401) {
    throw new ApiError("Tu sesión expiró. Inicia sesión de nuevo.", 401);
  }
  if (!res.ok) {
    throw new ApiError(await parseErrorMessage(res), res.status);
  }

  const disposicion = res.headers.get("Content-Disposition");
  const coincidencia = disposicion?.match(/filename="?([^";]+)"?/);
  const nombreArchivo = coincidencia?.[1] ?? nombreSugerido;

  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const enlace = document.createElement("a");
  enlace.href = url;
  enlace.download = nombreArchivo;
  document.body.appendChild(enlace);
  enlace.click();
  document.body.removeChild(enlace);
  URL.revokeObjectURL(url);
}

// ---------- Auth ----------

export type LoginResponse = {
  token: string;
  nombre: string;
  correo: string;
  rol: "ADMIN_GENERAL" | "ABOGADO";
  expiraEnMs: number;
};

export function login(correo: string, contrasena: string): Promise<LoginResponse> {
  return pedido<LoginResponse>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ correo, contrasena }),
  });
}

export function cambiarContrasena(contrasenaActual: string, contrasenaNueva: string) {
  return pedido<void>("/api/auth/password", {
    method: "PATCH",
    body: JSON.stringify({ contrasenaActual, contrasenaNueva }),
  });
}

// ---------- Solicitudes ----------

export type EstadoSolicitud = "NUEVO" | "CONTACTADO" | "CERRADO";
export type OrigenSolicitud = "FORMULARIO" | "CHATBOT" | "WHATSAPP" | "PANEL";
export type TipoReunion = "VIRTUAL" | "PRESENCIAL";

export type Solicitud = {
  id: number;
  nombre: string;
  correo: string;
  telefono: string | null;
  mensaje: string;
  origen: OrigenSolicitud;
  estado: EstadoSolicitud;
  notasInternas: string | null;
  fechaCreacion: string;
  fechaActualizacionEstado: string | null;
  fechaCita: string | null;
  tipoReunion: TipoReunion;
  linkReunion: string | null;
  lugarReunion: string | null;
  responsables: Responsable[];
};

export type Responsable = {
  id: number;
  nombre: string;
  rol: "ADMIN_GENERAL" | "ABOGADO";
};

// Crea una solicitud directamente desde el panel (ver SolicitudService.crearDirecta), para
// poder agendarle una reunión a un caso ya existente o a un cliente fuera del sistema --
// nunca dispara los correos/WhatsApp de "recibimos tu solicitud" (el cliente se entera con
// la confirmación de la reunión en sí, en el siguiente paso).
export function crearSolicitudDirecta(datos: {
  nombre: string;
  correo: string;
  telefono?: string;
  mensaje?: string;
}): Promise<Solicitud> {
  return pedido<Solicitud>("/api/admin/solicitudes/directa", {
    method: "POST",
    body: JSON.stringify(datos),
  });
}

export function listarSolicitudes(filtros?: {
  estado?: EstadoSolicitud;
  desde?: string;
  hasta?: string;
}): Promise<Solicitud[]> {
  const params = new URLSearchParams();
  if (filtros?.estado) params.set("estado", filtros.estado);
  if (filtros?.desde) params.set("desde", filtros.desde);
  if (filtros?.hasta) params.set("hasta", filtros.hasta);
  const query = params.toString();
  return pedido<Solicitud[]>(`/api/admin/solicitudes${query ? `?${query}` : ""}`);
}

export function actualizarEstadoSolicitud(id: number, nuevoEstado: EstadoSolicitud) {
  return pedido<Solicitud>(`/api/admin/solicitudes/${id}/estado`, {
    method: "PATCH",
    body: JSON.stringify({ nuevoEstado }),
  });
}

// Buzón de respuesta directa (ver EmailService.enviarRespuestaSolicitud en el backend): el
// correo sale con el formato de marca de la firma y siempre con copia a gerencia, sin que
// quien responde tenga que acordarse de agregarla a mano.
export function responderSolicitud(id: number, asunto: string, mensaje: string) {
  return pedido<void>(`/api/admin/solicitudes/${id}/responder`, {
    method: "POST",
    body: JSON.stringify({ asunto, mensaje }),
  });
}

export function eliminarSolicitud(id: number) {
  return pedido<void>(`/api/admin/solicitudes/${id}`, { method: "DELETE" });
}

export function agendarCita(
  id: number,
  datos: {
    fechaHora: string;
    correo: string;
    telefono?: string;
    tipoReunion: TipoReunion;
    linkReunion?: string;
    lugarReunion?: string;
    responsablesIds?: number[];
  },
) {
  return pedido<Solicitud>(`/api/admin/solicitudes/${id}/cita`, {
    method: "PATCH",
    body: JSON.stringify(datos),
  });
}

// ---------- Calendario de reuniones ----------

export function listarCalendario(filtros: { desde: string; hasta: string; abogadoId?: number }): Promise<Solicitud[]> {
  const params = new URLSearchParams({ desde: filtros.desde, hasta: filtros.hasta });
  if (filtros.abogadoId) params.set("abogadoId", String(filtros.abogadoId));
  return pedido<Solicitud[]>(`/api/admin/solicitudes/calendario?${params.toString()}`);
}

export function listarResponsables(): Promise<Responsable[]> {
  return pedido<Responsable[]>("/api/admin/solicitudes/responsables");
}

export function exportarSolicitudes(filtros?: {
  estado?: EstadoSolicitud;
  desde?: string;
  hasta?: string;
}): Promise<void> {
  const params = new URLSearchParams();
  if (filtros?.estado) params.set("estado", filtros.estado);
  if (filtros?.desde) params.set("desde", filtros.desde);
  if (filtros?.hasta) params.set("hasta", filtros.hasta);
  const query = params.toString();
  return descargarArchivo(
    `/api/admin/solicitudes/exportar${query ? `?${query}` : ""}`,
    "solicitudes.xlsx",
  );
}

// ---------- Artículos ----------

export type EstadoArticulo = "BORRADOR" | "PUBLICADO";
export type TipoContenido = "BLOG" | "NOTICIA";

export type ArticuloAdmin = {
  id: number;
  titulo: string;
  slug: string;
  contenido: string;
  resumen: string | null;
  imagenUrl: string | null;
  tipoContenido: TipoContenido;
  categoria: Categoria;
  autorNombre: string;
  estado: EstadoArticulo;
  fechaCreacion: string;
  fechaPublicacion: string | null;
  tiempoLecturaMin: number | null;
  videoYoutubeUrl: string | null;
};

export function listarArticulosAdmin(): Promise<ArticuloAdmin[]> {
  return pedido<ArticuloAdmin[]>("/api/admin/articulos");
}

export function obtenerArticuloAdmin(id: number): Promise<ArticuloAdmin> {
  return pedido<ArticuloAdmin>(`/api/admin/articulos/${id}`);
}

export type ArticuloInput = {
  titulo: string;
  contenido: string;
  resumen: string;
  imagenUrl: string;
  videoYoutubeUrl?: string | null;
  tipoContenido: TipoContenido;
  idCategoria: number;
  tiempoLecturaMin: number | null;
};

export function crearArticulo(input: ArticuloInput): Promise<ArticuloAdmin> {
  return pedido<ArticuloAdmin>("/api/admin/articulos", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function actualizarArticulo(
  id: number,
  input: ArticuloInput & { estado: EstadoArticulo },
): Promise<ArticuloAdmin> {
  return pedido<ArticuloAdmin>(`/api/admin/articulos/${id}`, {
    method: "PUT",
    body: JSON.stringify(input),
  });
}

export function eliminarArticulo(id: number) {
  return pedido<void>(`/api/admin/articulos/${id}`, { method: "DELETE" });
}

// Subida de la imagen de portada desde el computador (ver ImagenArticuloService en el
// backend). No pasa por pedido(): ese wrapper fija "Content-Type: application/json" en
// todas las peticiones, y eso rompería el multipart/form-data -- el navegador tiene que
// poner su propio Content-Type con el boundary, así que aquí NO se declara ninguno.
export async function subirImagenArticulo(archivo: File): Promise<{ url: string }> {
  const token = obtenerToken();
  const formData = new FormData();
  formData.append("archivo", archivo);

  const res = await fetch(`${API_URL}/api/admin/articulos/imagenes`, {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: formData,
  });

  // Mismo tratamiento que pedido() para un 401 -- bug real encontrado en auditoría: esta
  // función no pasa por pedido() (ver comentario de arriba sobre el Content-Type), así que
  // antes solo lanzaba el error sin limpiar el token viejo ni redirigir. El admin se quedaba
  // en /admin/articulos con una sesión ya inválida, viendo el toast de error pero sin que
  // el panel lo mandara de vuelta al login como sí ocurre con cualquier otra llamada.
  if (res.status === 401) {
    borrarToken();
    if (typeof window !== "undefined" && !window.location.pathname.startsWith("/admin/login")) {
      window.location.href = "/admin/login";
    }
    throw new ApiError("Tu sesión expiró. Inicia sesión de nuevo.", 401);
  }
  if (!res.ok) {
    throw new ApiError(await parseErrorMessage(res), res.status);
  }
  return res.json();
}

// ---------- Usuarios internos ----------

export type UsuarioInterno = {
  id: number;
  nombre: string;
  correo: string;
  rol: "ADMIN_GENERAL" | "ABOGADO";
  activo: boolean;
  fechaCreacion: string;
};

export function listarUsuarios(): Promise<UsuarioInterno[]> {
  return pedido<UsuarioInterno[]>("/api/admin/usuarios");
}

export function crearAbogado(nombre: string, correo: string, contrasena: string) {
  return pedido<UsuarioInterno>("/api/admin/usuarios", {
    method: "POST",
    body: JSON.stringify({ nombre, correo, contrasena }),
  });
}

export function cambiarActivoUsuario(id: number, activo: boolean) {
  return pedido<UsuarioInterno>(`/api/admin/usuarios/${id}/activo`, {
    method: "PATCH",
    body: JSON.stringify({ activo }),
  });
}

// ---------- Marketing ----------

export type SuscriptorMarketing = {
  id: number;
  nombre: string;
  correo: string;
  fechaSuscripcion: string;
};

export function listarSuscriptoresMarketing(): Promise<SuscriptorMarketing[]> {
  return pedido<SuscriptorMarketing[]>("/api/admin/marketing/suscriptores");
}

export function exportarSuscriptoresMarketing(): Promise<void> {
  return descargarArchivo(
    "/api/admin/marketing/suscriptores/exportar",
    "suscriptores-marketing.xlsx",
  );
}

// ---------- Testimonios ----------

export type EstadoTestimonio = "PENDIENTE" | "APROBADO" | "RECHAZADO";

export type TestimonioAdmin = {
  id: number;
  nombre: string;
  empresa: string | null;
  cargo: string | null;
  cita: string;
  calificacion: number;
  correo: string;
  estado: EstadoTestimonio;
  fechaCreacion: string;
  fechaModeracion: string | null;
};

export function listarTestimonios(): Promise<TestimonioAdmin[]> {
  return pedido<TestimonioAdmin[]>("/api/admin/testimonios");
}

export function moderarTestimonio(id: number, nuevoEstado: EstadoTestimonio) {
  return pedido<TestimonioAdmin>(`/api/admin/testimonios/${id}`, {
    method: "PATCH",
    body: JSON.stringify({ nuevoEstado }),
  });
}

// ---------- Estadísticas ----------

export type Estadisticas = {
  solicitudesPorEstado: Record<EstadoSolicitud, number>;
  solicitudesPorOrigen: Record<OrigenSolicitud, number>;
  citasAgendadas: number;
  citasProximas: number;
  solicitudesUltimos7Dias: number;
  testimoniosPorEstado: Record<EstadoTestimonio, number>;
  conversacionesChatbotMesActual: number;
  limiteMensualChatbot: number;
  articulosPublicados: number;
  articulosBorrador: number;
  suscriptoresMarketingActivos: number;
  usuariosInternosActivos: number;
  usuariosPorRol: Record<"ADMIN_GENERAL" | "ABOGADO", number>;
  visitantesMesActual: number;
  historicoVisitantes?: Record<string, number>;
};

export function obtenerEstadisticas(): Promise<Estadisticas> {
  return pedido<Estadisticas>("/api/admin/estadisticas");
}

// ---------- Categorías (público, reusado en el panel) ----------

export function listarCategorias(): Promise<Categoria[]> {
  return pedido<Categoria[]>("/api/categorias");
}

// ---------- Boletín diario automático ----------
// Sin composición manual: se envía solo cuando se publica blog/noticias ese día
// (ver BoletinDiarioScheduler en el backend). Este listado es solo el historial.

export type BoletinEnviado = {
  id: number;
  cantidadPublicaciones: number;
  cantidadDestinatarios: number;
  fechaEnvio: string;
};

export function listarBoletines(): Promise<BoletinEnviado[]> {
  return pedido<BoletinEnviado[]>("/api/admin/boletines");
}

// ---------- FAQ auto-alimentada ----------

export type EstadoPreguntaFrecuente = "CANDIDATA" | "APROBADA" | "RECHAZADA";

export type PreguntaFrecuenteAdmin = {
  id: number;
  preguntaEjemplo: string;
  respuestaSugerida: string | null;
  respuestaFinal: string | null;
  conteo: number;
  estado: EstadoPreguntaFrecuente;
  fechaPrimeraVez: string;
  fechaActualizacion: string;
};

export function listarFaqCandidatas(): Promise<PreguntaFrecuenteAdmin[]> {
  return pedido<PreguntaFrecuenteAdmin[]>("/api/admin/faq");
}

export function moderarFaq(id: number, nuevoEstado: EstadoPreguntaFrecuente, respuesta?: string) {
  return pedido<PreguntaFrecuenteAdmin>(`/api/admin/faq/${id}`, {
    method: "PATCH",
    body: JSON.stringify({ nuevoEstado, respuesta }),
  });
}

// ---------- Casos ----------
// El panel ya no depende de cargar cada caso a mano: sincronizarCasos() trae/actualiza todo
// desde las TRES hojas de seguimiento de la firma (Judiciales, Superintendencia, Procesos
// Comisaría -- cada una con su propia estructura de columnas, ver HojaCalculoService en el
// backend). crearCaso() se mantiene solo como respaldo manual (fuente "MANUAL"). El estado
// real del caso se lee en vivo de la hoja correspondiente, no se gestiona desde aquí.

export type FuenteCaso = "JUDICIALES" | "SUPERINTENDENCIA" | "PROCESOS_COMISARIA" | "MANUAL";

export type CasoAdmin = {
  id: number;
  fuente: FuenteCaso;
  fuenteVisible: string;
  numeroCaso: string | null;
  nombreCliente: string;
  correoCliente: string | null;
  telefonoCliente: string | null;
  radicadoId: string | null;
  correoEnviado: boolean;
  whatsappEnviado: boolean;
  notasInternas: string | null;
  fechaCreacion: string;
  fechaUltimoReporteSemanal: string | null;
  fechaUltimoReporteWhatsapp: string | null;
};

export type ResumenSincronizacionCasos = {
  filasLeidasEnHoja: number;
  casosNuevos: number;
  casosActualizados: number;
  casosEliminados: number;
  filasSinCorreo: number;
  radicadosDuplicados: number;
  fuentesConError: string[];
};

export type ResumenEnvioCorreosCasos = {
  correosEnviados: number;
  correosFallidos: number;
  whatsappEnviados: number;
  whatsappFallidos: number;
  // Cuántos casos ni siquiera se intentaron porque ya se alcanzó el cupo diario compartido de
  // envíos masivos de WhatsApp/correo (ver LimiteEnvioMasivoService en el backend) -- quedan
  // pendientes para la corrida automática de mañana, no cuentan como fallidos.
  pendientesPorLimiteDiario: number;
};

export function listarCasos(): Promise<CasoAdmin[]> {
  return pedido<CasoAdmin[]>("/api/admin/casos");
}

export function crearCaso(input: {
  nombreCliente: string;
  correoCliente: string;
  telefonoCliente?: string;
  radicadoId: string;
  notasInternas?: string;
}): Promise<CasoAdmin> {
  return pedido<CasoAdmin>("/api/admin/casos", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function sincronizarCasos(): Promise<ResumenSincronizacionCasos> {
  return pedido<ResumenSincronizacionCasos>("/api/admin/casos/sincronizar", { method: "POST" });
}

export function enviarCorreosPendientesCasos(): Promise<ResumenEnvioCorreosCasos> {
  return pedido<ResumenEnvioCorreosCasos>("/api/admin/casos/enviar-pendientes", { method: "POST" });
}

export type ResumenReporteSemanalCasos = {
  casosConReporte: number;
  correosEnviados: number;
  correosFallidos: number;
  whatsappEnviados: number;
  whatsappFallidos: number;
  // Igual que en ResumenEnvioCorreosCasos: cuántos casos quedaron sin ni siquiera intentarse
  // por el cupo diario compartido de envíos masivos, pendientes para mañana.
  pendientesPorLimiteDiario: number;
};

// Disparo manual del reporte semanal a todos los clientes con caso activo (además del
// automático de los lunes, ver ReporteSemanalCasosScheduler en el backend).
export function enviarReporteSemanalCasos(): Promise<ResumenReporteSemanalCasos> {
  return pedido<ResumenReporteSemanalCasos>("/api/admin/casos/enviar-reporte-semanal", { method: "POST" });
}

// ---------- Cobros Pendientes ----------
// Igual que Casos: el panel no carga clientes a mano, sincronizarCobros() trae/actualiza todo
// desde las dos pestañas del Google Sheets de cobros (Empresas, Personas Naturales) y elimina
// del sistema los que ya no estén en la hoja (ver CobroService en el backend).

export type TipoClienteCobro = "EMPRESA" | "PERSONA_NATURAL";

export type ClienteCobro = {
  id: number;
  tipo: TipoClienteCobro;
  tipoVisible: string;
  numeroFila: string;
  nombre: string;
  correo: string | null;
  telefono: string | null;
  cedulaNit: string | null;
  honorarios: string | null;
  pagoEsteMes: boolean | null;
  respondioMensaje: string | null;
  fechaUltimoRecordatorio: string | null;
  fechaUltimoRecordatorioCorreo: string | null;
  fechaUltimoRecordatorioWhatsapp: string | null;
  correoEnviado: boolean;
  whatsappEnviado: boolean;
  fechaCreacion: string;
};

export type ResumenSincronizacionCobros = {
  filasLeidasEnHoja: number;
  clientesNuevos: number;
  clientesActualizados: number;
  clientesEliminados: number;
};

export type ResumenEnvioRecordatoriosCobros = {
  correosEnviados: number;
  correosFallidos: number;
  whatsappEnviados: number;
  whatsappFallidos: number;
  clientesSinCosto: number;
  // Igual que en Casos: cuántos clientes quedaron sin ni siquiera intentarse por el cupo
  // diario compartido de envíos masivos, pendientes para mañana.
  pendientesPorLimiteDiario: number;
};

export type ResumenEnvioRecordatorioPrueba = {
  encontrado: boolean;
  nombre: string | null;
  correoEnviado: boolean;
  whatsappEnviado: boolean;
};

export function listarCobros(): Promise<ClienteCobro[]> {
  return pedido<ClienteCobro[]>("/api/admin/cobros");
}

export function sincronizarCobros(): Promise<ResumenSincronizacionCobros> {
  return pedido<ResumenSincronizacionCobros>("/api/admin/cobros/sincronizar", { method: "POST" });
}

export function enviarRecordatoriosCobros(): Promise<ResumenEnvioRecordatoriosCobros> {
  return pedido<ResumenEnvioRecordatoriosCobros>("/api/admin/cobros/enviar-recordatorios", { method: "POST" });
}

// Envío aislado de un único recordatorio al número de prueba fijo de la firma (3126029742),
// sin tocar ni consumir cupo del lote real de clientes -- ver CobroAdminController.
export function enviarRecordatorioPrueba(): Promise<ResumenEnvioRecordatorioPrueba> {
  return pedido<ResumenEnvioRecordatorioPrueba>("/api/admin/cobros/enviar-recordatorio-prueba", { method: "POST" });
}

export function simularRespuestaCobro(
  telefono: string,
  respuesta: string,
): Promise<ClienteCobro[]> {
  return pedido<ClienteCobro[]>("/api/admin/cobros/simular-respuesta", {
    method: "POST",
    body: JSON.stringify({ telefono, respuesta }),
  });
}

// ---------- CRM Jurídico Integral ----------

export type TipoClienteCrm = "PERSONA_NATURAL" | "EMPRESA";
export type EstadoClienteCrm = "PROSPECTO" | "ACTIVO" | "INACTIVO" | "FINALIZADO";
export type TipoActividadCrm = "LLAMADA" | "WHATSAPP" | "CORREO" | "REUNION" | "NOTA_INTERNA" | "CAMBIO_ESTADO" | "PAGO";
export type EtapaPipeline =
  | "NUEVO"
  | "CONTACTADO"
  | "CITA_PROGRAMADA"
  | "VALORACION"
  | "PROPUESTA_ENVIADA"
  | "CONTRATADO"
  | "DESCARTADO";

export type ClienteCrm = {
  id: number;
  tipo: TipoClienteCrm;
  nombre: string;
  cedulaNit: string | null;
  correo: string | null;
  telefono: string | null;
  direccion: string | null;
  ciudad: string | null;
  estado: EstadoClienteCrm;
  etiqueta: string | null;
  notas: string | null;
  fechaCreacion: string;
  fechaActualizacion: string;
  totalCasos: number;
  totalCobros: number;
  pagoAlDia: boolean;
};

// despachoJudicial..fechaActualizacionReporte: estado real del caso leído en vivo del Google
// Sheets de la firma (mismo dato que la consulta pública por radicado) -- todos pueden venir
// null si el caso es MANUAL, no tiene radicado todavía, o la hoja no tiene cargada esa fila.
export type CasoVinculado = {
  id: number;
  radicadoId: string | null;
  fuente: string;
  numeroCaso: string | null;
  nombreCliente: string;
  notasInternas: string | null;
  correoEnviado: boolean;
  whatsappEnviado: boolean;
  despachoJudicial: string | null;
  asuntoCaso: string | null;
  tipoCaso: string | null;
  ultimoReporte: string | null;
  estadoJudicial: string | null;
  fechaActualizacionReporte: string | null;
};

export type CobroVinculado = {
  id: number;
  tipo: string;
  numeroFila: string;
  honorarios: string | null;
  pagoEsteMes: boolean | null;
  respondioMensaje: string | null;
  fechaUltimoRecordatorio: string | null;
};

export type CitaVinculada = {
  id: number;
  fechaHora: string;
  tipoReunion: string;
  linkReunion: string | null;
  lugarReunion: string | null;
};

export type ActividadCrm = {
  id: number;
  clienteCrmId: number;
  solicitudId: number | null;
  casoId: number | null;
  tipo: TipoActividadCrm;
  titulo: string;
  descripcion: string | null;
  usuarioNombre: string | null;
  fechaActividad: string;
  fechaCreacion: string;
};

// El registro de tareas de un cliente ya no viaja en la ficha -- ver listarTareasPorClienteCrm().
export type ClienteCrmDetalle = {
  cliente: ClienteCrm;
  casos: CasoVinculado[];
  cobros: CobroVinculado[];
  citas: CitaVinculada[];
  actividades: ActividadCrm[];
};

export type ItemPipeline = {
  id: number;
  nombre: string;
  correo: string;
  telefono: string | null;
  mensaje: string;
  etapa: EtapaPipeline;
  valorEstimado: number | null;
  areaPractica: string | null;
  fechaCreacion: string;
  clienteCrmId: number | null;
};

export type CrmDashboard = {
  totalClientes: number;
  prospectosActivos: number;
  casosActivos: number;
  valorTotalPipeline: number;
  cobrosAlDia: number;
  cobrosPendientes: number;
  leadsPorEtapa: Record<string, number>;
};

export type ClienteCrmPagina = {
  contenido: ClienteCrm[];
  pagina: number;
  totalPaginas: number;
  totalElementos: number;
};

export function listarClientesCrm(opciones?: {
  busqueda?: string;
  estado?: EstadoClienteCrm;
  tipo?: TipoClienteCrm;
  pagoAlDia?: boolean;
  pagina?: number;
  tamanoPagina?: number;
}): Promise<ClienteCrmPagina> {
  const parametros = new URLSearchParams();
  if (opciones?.busqueda) parametros.set("busqueda", opciones.busqueda);
  if (opciones?.estado) parametros.set("estado", opciones.estado);
  if (opciones?.tipo) parametros.set("tipo", opciones.tipo);
  if (opciones?.pagoAlDia !== undefined) parametros.set("pagoAlDia", String(opciones.pagoAlDia));
  parametros.set("pagina", String(opciones?.pagina ?? 0));
  parametros.set("tamanoPagina", String(opciones?.tamanoPagina ?? 24));
  return pedido<ClienteCrmPagina>(`/api/admin/crm/clientes?${parametros.toString()}`);
}

export function obtenerClienteCrmDetalle(id: number): Promise<ClienteCrmDetalle> {
  return pedido<ClienteCrmDetalle>(`/api/admin/crm/clientes/${id}`);
}

export function crearClienteCrm(datos: Partial<ClienteCrm>): Promise<ClienteCrm> {
  return pedido<ClienteCrm>("/api/admin/crm/clientes", {
    method: "POST",
    body: JSON.stringify(datos),
  });
}

export function actualizarClienteCrm(id: number, datos: Partial<ClienteCrm>): Promise<ClienteCrm> {
  return pedido<ClienteCrm>(`/api/admin/crm/clientes/${id}`, {
    method: "PUT",
    body: JSON.stringify(datos),
  });
}

export function archivarClienteCrm(id: number): Promise<void> {
  return pedido<void>(`/api/admin/crm/clientes/${id}`, { method: "DELETE" });
}

export function obtenerPipelineCrm(): Promise<ItemPipeline[]> {
  return pedido<ItemPipeline[]>("/api/admin/crm/pipeline");
}

export function cambiarEtapaPipeline(
  solicitudId: number,
  nuevaEtapa: EtapaPipeline,
  nota?: string,
  usuarioNombre?: string,
): Promise<ItemPipeline> {
  return pedido<ItemPipeline>(`/api/admin/crm/pipeline/${solicitudId}/etapa`, {
    method: "PATCH",
    body: JSON.stringify({ nuevaEtapa, nota, usuarioNombre }),
  });
}

export function convertirProspectoACrm(
  solicitudId: number,
  datos: {
    tipo?: TipoClienteCrm;
    cedulaNit?: string;
    direccion?: string;
    ciudad?: string;
    etiqueta?: string;
    notas?: string;
  },
): Promise<ClienteCrm> {
  return pedido<ClienteCrm>(`/api/admin/crm/pipeline/${solicitudId}/convertir`, {
    method: "POST",
    body: JSON.stringify(datos),
  });
}

export function registrarActividadCrm(
  clienteId: number,
  actividad: {
    tipo: TipoActividadCrm;
    titulo: string;
    descripcion?: string;
    usuarioNombre?: string;
    solicitudId?: number;
    casoId?: number;
  },
): Promise<ActividadCrm> {
  return pedido<ActividadCrm>(`/api/admin/crm/clientes/${clienteId}/actividades`, {
    method: "POST",
    body: JSON.stringify(actividad),
  });
}

export function obtenerDashboardCrm(): Promise<CrmDashboard> {
  return pedido<CrmDashboard>("/api/admin/crm/dashboard");
}

// ---------- Registro del Sistema ----------
// Bitácora de procesos que el sistema ejecuta por su cuenta o que el admin dispara desde el
// panel (sincronizaciones, envíos masivos, recordatorios programados, boletines) -- solo
// ADMIN_GENERAL, ver SecurityConfig en el backend.

export type TipoRegistroSistema =
  | "SINCRONIZACION_CASOS"
  | "ENVIO_NOTIFICACIONES_CASOS"
  | "REPORTE_SEMANAL_CASOS"
  | "SINCRONIZACION_COBROS"
  | "ENVIO_RECORDATORIOS_COBROS"
  | "RECORDATORIO_CITA"
  | "BOLETIN_ENVIADO"
  | "INICIO_SESION"
  | "USUARIO_CREADO"
  | "USUARIO_ACTIVO_CAMBIADO"
  | "CONSULTA_ESTADO_CASO";

export type RegistroSistemaItem = {
  id: number;
  tipo: TipoRegistroSistema;
  tipoVisible: string;
  descripcion: string;
  detalle: string | null;
  exitoso: boolean;
  fechaHora: string;
};

export type PaginaRegistroSistema = {
  content: RegistroSistemaItem[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

export function listarRegistroSistema(opciones: {
  tipo?: TipoRegistroSistema;
  pagina?: number;
  tamano?: number;
}): Promise<PaginaRegistroSistema> {
  const parametros = new URLSearchParams();
  if (opciones.tipo) parametros.set("tipo", opciones.tipo);
  parametros.set("pagina", String(opciones.pagina ?? 0));
  parametros.set("tamano", String(opciones.tamano ?? 30));
  return pedido<PaginaRegistroSistema>(`/api/admin/registro-sistema?${parametros.toString()}`);
}

// ---------- Tareas (por caso o por persona del CRM, con responsable real asignado) ----------
// Pedido explícito del usuario: "deja un registro de tareas por caso y con responsabilidad
// [...] esta habilidad la podra tener tanto admin como cualquiera de los abogados", y luego
// "arregla desde el directorio de clientes en el CRM ahi es donde se asginara las tareas por
// cada persona" -- el módulo viejo de tareas del CRM (ligado solo a un ClienteCrm, con el
// nombre del responsable en texto libre sin validar) se retiró por completo, reemplazado por
// este.

export type PrioridadTarea = "BAJA" | "MEDIA" | "ALTA";

// casoId/clienteCrmId: una tarea está ligada a un Caso, a una persona del directorio del CRM, o
// a ambos -- nunca a ninguno de los dos (ver migración V46, corrige el bug real de que el 96%
// del directorio no tenía ningún caso judicial y por eso nunca podía tener tareas).
export type Tarea = {
  id: number;
  casoId: number | null;
  clienteCrmId: number | null;
  casoEtiqueta: string;
  clienteNombre: string;
  titulo: string;
  descripcion: string | null;
  fechaVencimiento: string | null;
  prioridad: PrioridadTarea;
  completada: boolean;
  usuarioAsignadoId: number;
  usuarioAsignadoNombre: string;
  usuarioCreadorId: number;
  usuarioCreadorNombre: string;
  fechaCreacion: string;
  fechaCompletada: string | null;
};

export function listarTareasPorCaso(casoId: number): Promise<Tarea[]> {
  return pedido<Tarea[]>(`/api/admin/casos/${casoId}/tareas`);
}

// Registro de tareas de una persona del CRM (pedido explícito del usuario: "arregla desde el
// directorio de clientes en el CRM ahi es donde se asginara las tareas por cada persona"):
// junta las ligadas directamente a su ficha con las de cualquiera de sus casos.
export function listarTareasPorClienteCrm(clienteCrmId: number): Promise<Tarea[]> {
  return pedido<Tarea[]>(`/api/admin/crm/clientes/${clienteCrmId}/tareas`);
}

export function crearTarea(
  casoId: number,
  datos: {
    titulo: string;
    descripcion?: string;
    fechaVencimiento?: string;
    prioridad?: PrioridadTarea;
    usuarioAsignadoId: number;
  },
): Promise<Tarea> {
  return pedido<Tarea>(`/api/admin/casos/${casoId}/tareas`, {
    method: "POST",
    body: JSON.stringify(datos),
  });
}

// Tarea general ligada directamente a una persona del CRM, sin necesitar un caso judicial.
export function crearTareaParaCliente(
  clienteCrmId: number,
  datos: {
    titulo: string;
    descripcion?: string;
    fechaVencimiento?: string;
    prioridad?: PrioridadTarea;
    usuarioAsignadoId: number;
  },
): Promise<Tarea> {
  return pedido<Tarea>(`/api/admin/crm/clientes/${clienteCrmId}/tareas`, {
    method: "POST",
    body: JSON.stringify(datos),
  });
}

// Página "Tareas" del sidebar: tareas pendientes propias, o de toda la firma si el usuario
// autenticado es ADMIN_GENERAL (ver TareaService.listarPendientes en el backend).
export function listarTareasPendientes(): Promise<Tarea[]> {
  return pedido<Tarea[]>("/api/admin/tareas");
}

// Badge "N tareas pendientes" en el listado de Casos -- un solo query bulk, no uno por caso.
export function contarTareasPendientesPorCaso(): Promise<Record<string, number>> {
  return pedido<Record<string, number>>("/api/admin/tareas/conteo-por-caso");
}

export function actualizarTarea(
  id: number,
  datos: {
    titulo: string;
    descripcion?: string;
    fechaVencimiento?: string;
    prioridad?: PrioridadTarea;
    usuarioAsignadoId?: number;
  },
): Promise<Tarea> {
  return pedido<Tarea>(`/api/admin/tareas/${id}`, {
    method: "PATCH",
    body: JSON.stringify(datos),
  });
}

export function completarTarea(id: number, completada: boolean): Promise<Tarea> {
  return pedido<Tarea>(`/api/admin/tareas/${id}/completar?completada=${completada}`, {
    method: "PATCH",
  });
}

export function eliminarTarea(id: number): Promise<void> {
  return pedido<void>(`/api/admin/tareas/${id}`, { method: "DELETE" });
}
