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

// recaptchaToken: pedido explícito del usuario ("captcha al login") -- token de un solo
// uso que entrega el widget de Google reCAPTCHA v2 (ver RecaptchaWidget), verificado en el
// backend contra la API real de Google antes de tocar la base de datos.
export function login(correo: string, contrasena: string, recaptchaToken: string): Promise<LoginResponse> {
  return pedido<LoginResponse>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ correo, contrasena, recaptchaToken }),
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

// ---------- Configuración de envíos ----------
// Pedido explícito del usuario: "un boton para apagar los envios de correo, apagar los
// envios de whatsapp (boton independiente notificacones, boletin, marketing entre otros)"
// -- ADMIN_GENERAL únicamente. Distinto de BLOQUEO_TOTAL_CLIENTES (esa es una variable de
// entorno estática del backend, el interruptor maestro) -- esto es por categoría y canal,
// se prende/apaga en caliente desde el panel.
export type ConfiguracionEnvio = {
  clave: string;
  nombreVisible: string;
  activo: boolean;
};

export function listarConfiguracionEnvios(): Promise<ConfiguracionEnvio[]> {
  return pedido<ConfiguracionEnvio[]>("/api/admin/configuracion-envios");
}

export function actualizarConfiguracionEnvio(clave: string, activo: boolean): Promise<void> {
  return pedido<void>(`/api/admin/configuracion-envios/${clave}`, {
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

// ---------- PQRS (Peticiones, Quejas, Reclamos y Sugerencias) ----------
// Mismo nivel de acceso que Solicitudes (ADMIN_GENERAL o ABOGADO) -- ver PqrsAdminController
// en el backend. A diferencia de Testimonios, responder una PQRS es atención al cliente con
// plazo legal de referencia, no moderación de contenido de marketing.

export type TipoPqrs = "PETICION" | "QUEJA" | "RECLAMO" | "SUGERENCIA";
export type EstadoPqrs = "RECIBIDO" | "EN_TRAMITE" | "RESUELTO" | "CERRADO";

export type PqrsAdmin = {
  id: number;
  radicado: string;
  tipo: TipoPqrs;
  nombre: string;
  correo: string;
  telefono: string | null;
  asunto: string;
  descripcion: string;
  estado: EstadoPqrs;
  respuesta: string | null;
  respondidoPor: string | null;
  fechaCreacion: string;
  fechaLimiteRespuesta: string;
  fechaRespuesta: string | null;
};

export function listarPqrs(): Promise<PqrsAdmin[]> {
  return pedido<PqrsAdmin[]>("/api/admin/pqrs");
}

export function cambiarEstadoPqrs(id: number, nuevoEstado: EstadoPqrs): Promise<PqrsAdmin> {
  return pedido<PqrsAdmin>(`/api/admin/pqrs/${id}/estado`, {
    method: "PATCH",
    body: JSON.stringify({ nuevoEstado }),
  });
}

export function responderPqrs(id: number, respuesta: string): Promise<PqrsAdmin> {
  return pedido<PqrsAdmin>(`/api/admin/pqrs/${id}/responder`, {
    method: "PATCH",
    body: JSON.stringify({ respuesta }),
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
// desde las OCHO hojas de seguimiento de la firma (Judiciales, Superintendencia, Procesos
// Comisaría, Tutelas, Procesos Salen, Procesos ICBF, Inspecciones de Policía, Insolvencia --
// cada una con su propia estructura de columnas, ver HojaCalculoService en el backend).
// crearCaso() se mantiene solo como respaldo manual (fuente "MANUAL"). El estado real del
// caso se lee en vivo de la hoja correspondiente, no se gestiona desde aquí.

export type FuenteCaso =
  | "JUDICIALES"
  | "SUPERINTENDENCIA"
  | "PROCESOS_COMISARIA"
  | "TUTELAS"
  | "PROCESOS_SALEN"
  | "PROCESOS_ICBF"
  | "INSPECCIONES_POLICIA"
  | "INSOLVENCIA"
  | "MANUAL";

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
  // Trazabilidad del seguimiento judicial 24/7 (ver sie.siejuridicos.vigilancia en el
  // backend) -- línea de tiempo completa por caso en GET /api/admin/vigilancia/casos/{id}/linea-tiempo.
  tieneProcesoCpnu: boolean;
  fechaUltimoSeguimiento: string | null;
  ultimoSeguimientoExitoso: boolean | null;
  // Pedido explícito del usuario: "si es nuevo para cada persona, si no la ha abierto le
  // sale un botoncito rojo" -- true cuando este caso tiene una actuación detectada después
  // de la última vez que EL USUARIO ACTUAL (no global) abrió su línea de tiempo. Abrir el
  // modal de Seguimiento (ver obtenerLineaTiempoJudicial) la apaga del lado del backend.
  tieneActualizacionSinRevisar: boolean;
  // Pedido explícito del usuario: "el filtro no es de ultima revision, sino en orden de
  // fecha en la que se encontro el ultimo reporte o notificacion, para ver arriba los que
  // han tenido movimiento" -- cuándo se DETECTÓ realmente la actuación más reciente (no
  // cuándo corrió el último chequeo, que pasa igual para todos los casos sin importar si
  // encontró algo). Null si el caso nunca ha tenido ninguna actuación detectada.
  fechaUltimaActuacionDetectada: string | null;
  // Corrección real pedida por el usuario: "un caso que tuvo movimiento hoy tiene que salir
  // arriba" -- fechaUltimaActuacionDetectada (arriba) es cuándo NUESTRO sistema vio la
  // actuación por primera vez, y el historial completo de los 362 casos reales se cargó de
  // una sola vez en un backfill esta misma sesión: eso deja casi empatados, en ese campo, a
  // un caso con movimiento real de hoy y a uno con movimiento real de hace meses. Este es el
  // dato real -- cuándo el despacho registró el movimiento -- y es el que debe usarse para
  // ordenar "por última actualización" (ver compararCasos). Null si el caso nunca ha tenido
  // ninguna actuación con fecha. Trae hora exacta desde la migración V65 del backend (pedido
  // explícito del usuario: "el reporte completo por fecha y hora exacta").
  fechaUltimoMovimientoJudicial: string | null;
  // Pedido explícito del usuario: "asignacion de caso por abogado" -- null cuando el caso
  // todavía no tiene abogado asignado. Visible para cualquier rol, editable solo por
  // ADMIN_GENERAL (ver actualizarCaso).
  abogadoAsignadoId: number | null;
  abogadoAsignadoNombre: string | null;
  // Pedido explícito del usuario: punto azul junto al de "Seguimiento" + "un contador en
  // cada caso con boletin... que muestre dentro de cuantos dias seran borrados" -- null
  // cuando el caso no tiene ningún boletín cacheado (no se muestra el punto azul). Cuando
  // tiene varios con distinta fecha de expiración, es el de la expiración MÁS PRÓXIMA (ver
  // backend BoletinJudicialCacheRepository.proximaExpiracionPorCaso()). 0 = se borra hoy.
  diasParaBorrarBoletin: number | null;
  // Gap real detectado comparando contra el correo diario de Lupa Jurídica (pedido explícito
  // del usuario: "lupa nos da un seguimiento mas robusto... necesito mejorar esto") -- el
  // propio CPNU ya da demandante/demandado ("Demandante: NOMBRE | Demandado: NOMBRE ") y el
  // despacho completo, tal cual para mostrar. null hasta que el próximo ciclo de seguimiento
  // lo resuelva para este caso.
  sujetosProcesales: string | null;
  despachoJudicial: string | null;
  // Pedido explícito del usuario: "tienes que traer absolutamente toda la informacion...
  // entre mas datos mucho mejor" -- el resto de lo que da el CPNU al resolver el proceso,
  // incluido un endpoint aparte (Proceso/Detalle) que trae el ponente (juez), tipo/clase/
  // subclase de proceso, el recurso y dónde está físicamente el expediente ahora mismo.
  departamentoJudicial: string | null;
  fechaRadicacionProceso: string | null;
  procesoPrivado: boolean | null;
  ponente: string | null;
  tipoProceso: string | null;
  claseProceso: string | null;
  subclaseProceso: string | null;
  recursoProceso: string | null;
  ubicacionProceso: string | null;
  // Pedido explícito del usuario: "la vigilancia tiene que mostrar toda, absolutamente toda
  // la informacion... todo lo que se detecte" -- cuándo la Rama Judicial misma actualizó
  // este proceso en SU sistema (nunca confundir con fechaUltimoSeguimiento, que es cuándo
  // NOSOTROS revisamos el caso).
  ultimaActualizacionRama: string | null;
  // Pedido explícito del usuario (2026-11-01): "el sistema debe reflejar que fallos hay" --
  // true cuando el radicado, aunque tenga un valor, no es un radicado real de la Rama
  // Judicial (23 dígitos). NUNCA significa que el caso esté mal -- hay jurisdicciones reales
  // sin radicado CPNU (comisaría de familia, inspección de policía) -- solo que este caso
  // puntual no tendrá seguimiento automático 24/7 hasta que se corrija o se confirme que es
  // de otro tipo.
  radicadoFormatoInvalido: boolean;
};

// Pedido explícito del usuario: "una variable para editar los casos, la idea es
// desligarnos del excel, un boton para editar caso para administrador unicamente" --
// PATCH /api/admin/casos/{id}, ADMIN_GENERAL únicamente (el backend devuelve 403 para
// ABOGADO). numeroCaso no es editable a propósito (ver ActualizarCasoRequest en el
// backend): es la llave que usa la sincronización automática para emparejar cada fila de
// la hoja con su Caso local.
export function actualizarCaso(
  id: number,
  datos: {
    nombreEnHoja: string | null;
    correoCliente: string | null;
    telefonoCliente: string | null;
    radicadoId: string | null;
    notasInternas: string | null;
    abogadoAsignadoId: number | null;
  },
): Promise<CasoAdmin> {
  return pedido<CasoAdmin>(`/api/admin/casos/${id}`, {
    method: "PATCH",
    body: JSON.stringify(datos),
  });
}

export type ResumenSincronizacionCasos = {
  filasLeidasEnHoja: number;
  casosNuevos: number;
  casosActualizados: number;
  casosEliminados: number;
  filasSinCorreo: number;
  radicadosDuplicados: number;
  // Fila con un radicado que excede los 50 caracteres permitidos (dato corrupto de la
  // hoja, ej. una fila mal pegada) -- se ignora ese radicado puntual en vez de tumbar toda
  // la sincronización (bug real encontrado el 2026-10-31).
  radicadosInvalidos: number;
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

// =========================================================================
// TODOS LOS BOLETINES (pedido explícito del usuario: "el sistema debe de traer y darle la
// opcion de todos los casos de ver/descargar todos los boletines que esten disponibles en
// las ramas o donde esten") -- una sola lista con TODOS los boletines ya cacheados de TODOS
// los casos, en vez de tener que abrir el Seguimiento de cada caso uno por uno.
// =========================================================================

export type BoletinJudicial = {
  id: number;
  casoId: number;
  nombreCliente: string;
  radicadoId: string | null;
  nombreArchivo: string;
  urlOrigen?: string | null;
  coincideNombreActuacion: boolean;
  fechaDescarga: string;
  fechaExpiracion: string;
  fechaEmision?: string | null;
  tipoActuacion?: string | null;
};

// Progreso en vivo SOLO del paso de boletines (no del ciclo completo de vigilancia).
export type ProgresoBoletines = {
  enCurso: boolean;
  // EN_ESPERA | PASADA (recorrido principal) | REINTENTOS (segunda etapa solo sobre lo que fallo)
  fase: "EN_ESPERA" | "PASADA" | "REINTENTOS" | "ESPERANDO_PORTAL";
  total: number;
  revisadas: number;
  nuevos: number;
  porcentaje: number;
  inicio: string | null;
  fin: string | null;
  reintentosTotal: number;
  reintentosHechos: number;
  recuperados: number;
  // Boletines/busquedas que siguen en cola esperando reintento (nunca se pierden).
  pendientes: number;
};

export function obtenerProgresoBoletines(): Promise<ProgresoBoletines> {
  return pedido<ProgresoBoletines>("/api/admin/vigilancia/boletines/progreso");
}

export function listarBoletinesJudiciales(): Promise<BoletinJudicial[]> {
  return pedido<BoletinJudicial[]>("/api/admin/vigilancia/boletines");
}

// Ver el PDF en una pestaña nueva -- mismo patrón de fetch+blob+window.open que
// abrirPdfDocumentoJudicial() más abajo, pero GET (sin cuerpo, el id ya va en la URL).
// Sirve el PDF que YA está cacheado, nunca vuelve a consultar Publicaciones Procesales en
// vivo -- instantáneo, no depende de que el portal del Estado esté respondiendo.
export async function abrirPdfBoletin(id: number): Promise<void> {
  const ventana = window.open("", "_blank");
  const token = obtenerToken();
  const res = await fetch(`${API_URL}/api/admin/vigilancia/boletines/${id}/pdf`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });

  if (res.status === 401) {
    ventana?.close();
    throw new ApiError("Tu sesión expiró. Inicia sesión de nuevo.", 401);
  }
  if (!res.ok) {
    ventana?.close();
    throw new ApiError(await parseErrorMessage(res), res.status);
  }

  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  if (ventana) {
    ventana.location.href = url;
  } else {
    window.open(url, "_blank");
  }
}

export function descargarBoletin(id: number, nombreArchivo: string): Promise<void> {
  return descargarArchivo(`/api/admin/vigilancia/boletines/${id}/pdf`, nombreArchivo);
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

// ---------- Vigilancia judicial (seguimiento 24/7 por caso, ver sie.siejuridicos.vigilancia
// en el backend) ----------
// Ya no depende de ningún botón para actualizarse (SeguimientoJudicialScheduler corre solo,
// de forma continua) -- esta sección es la VISTA de lo que ese ciclo ya fue guardando, no un
// disparador. Solo lectura hacia la Rama Judicial. El único disparador real que SÍ puede
// terminar mandándole un correo a un cliente es enviarDocumentoJudicial() más abajo, y
// siempre a pedido manual y explícito de un abogado sobre UN documento puntual.

// Los 4 campos de término (descripcionTermino...estadoTermino) se calculan al vuelo en el
// backend (ver InferenciaTerminoProcesal) a partir del nombre y la fecha de esta misma
// actuación -- nunca se guardan aparte. null cuando la actuación no abre ningún término
// reconocido (la mayoría de actuaciones de trámite, ej. "Recepción memorial").
export type EstadoTerminoProcesal = "CORRIENDO" | "POR_VENCER" | "VENCIDO";

export type EventoLineaTiempoJudicial = {
  // Pedido explícito del usuario (2026-10-31): "implementa algo mas rapido... que el sistema
  // lo guarde en el servidor" -- id real de la actuación, antes no expuesto, necesario para
  // cruzar cada evento con lo que ya trajo obtenerBoletinesCacheDeCaso() sin tener que
  // volver a consultar Publicaciones Procesales en vivo.
  id: number;
  // Fecha Y HORA exacta desde la migración V65 del backend -- pedido explícito del usuario:
  // "el reporte completo por fecha y hora exacta". El CPNU sí la trae, antes se descartaba.
  fechaActuacion: string | null;
  nombreActuacion: string;
  anotacion: string | null;
  fechaRegistro: string | null;
  conDocumentos: boolean;
  esFundamental: boolean;
  fechaDetectada: string;
  // Consecutivo real que el propio despacho le asignó a esta actuación dentro de su
  // proceso (verificado en vivo el 2026-10-31 contra el CPNU) -- null para actuaciones
  // guardadas antes de esta captura, nunca inventado.
  consecutivo: number | null;
  descripcionTermino: string | null;
  diasHabilesTermino: number | null;
  fechaVencimientoTermino: string | null;
  fechaInicialTermino?: string | null;
  fechaFinalTermino?: string | null;
  estadoTermino: EstadoTerminoProcesal | null;
  // true: fechaVencimientoTermino viene directo de fechaFinal informado por la Rama
  // Judicial para esta actuación (un hecho). false: es una estimación por palabras clave
  // (ver InferenciaTerminoProcesal.inferir en el backend). null: no hay ningún término.
  terminoEsOficial: boolean | null;
  // Pedido explícito del usuario: "implementa todas las mejoras identificadas" (comparando
  // contra Lupa Jurídica) -- búsqueda de palabras clave sobre el nombre/anotación de la
  // actuación, NUNCA un hecho verificado (ver ActuacionRegistradaResponse en el backend).
  posibleLiquidacionCostas: boolean;
  posibleAcreedorSubrogatorio: boolean;
  // Gap real detectado revisando los correos reales de Lupa Jurídica de la propia firma
  // (pedido explícito del usuario: "mejores la vigilancia... saca informaciones de portales
  // abiertos"). Mismo criterio: palabras clave, nunca un hecho verificado.
  posibleMedidaCautelar: boolean;
  // Pedido explicito del usuario (2026-11-04, auditoria en vivo de LUPA/IUS): una sola
  // categoria por actuacion (ver ClasificadorMovimientoJudicial.TipoMovimiento en el
  // backend) -- ADMISION_DEMANDA | INADMISION_DEMANDA | AUDIENCIA_PROGRAMADA |
  // SENTENCIA_FALLO | MANDAMIENTO_PAGO | TERMINACION_PROCESO | null.
  tipoMovimiento: string | null;
  // ASISTIO | NO_ASISTIO | null (solo cuando el texto menciona una audiencia).
  asistenciaAudiencia: string | null;
};

export type LineaTiempoJudicial = {
  tieneProcesoCpnu: boolean;
  fechaUltimoSeguimiento: string | null;
  ultimoSeguimientoExitoso: boolean | null;
  // Pedido explícito del usuario: "lupa nos da un seguimiento mas robusto" -- demandante/
  // demandado y despacho completo, tal cual los da el CPNU. null hasta que el próximo ciclo
  // de seguimiento los resuelva para este caso.
  sujetosProcesales: string | null;
  despachoJudicial: string | null;
  // Pedido explícito del usuario: "trae absolutamente toda la informacion... entre mas datos
  // mucho mejor" -- ver CasoAdmin, mismo origen (ProcesoResuelto en el backend).
  departamentoJudicial: string | null;
  fechaRadicacionProceso: string | null;
  procesoPrivado: boolean | null;
  ponente: string | null;
  tipoProceso: string | null;
  claseProceso: string | null;
  subclaseProceso: string | null;
  recursoProceso: string | null;
  ubicacionProceso: string | null;
  ultimaActualizacionRama: string | null;
  eventos: EventoLineaTiempoJudicial[];
};

export function obtenerLineaTiempoJudicial(casoId: number): Promise<LineaTiempoJudicial> {
  return pedido<LineaTiempoJudicial>(`/api/admin/vigilancia/casos/${casoId}/linea-tiempo`);
}

// Pedido explícito del usuario (2026-10-31): "las cargas se estan demorando demasiado...
// implementa algo mas rapido, que no se demore tanto sin que nos lleguen a bloquear". Lectura
// PURA de lo que BoletinJudicialCacheService ya dejó guardado en el ciclo nocturno -- nunca
// llama a Publicaciones Procesales, responde en milisegundos sin importar cuántas actuaciones
// tenga el caso. La clave del mapa es EventoLineaTiempoJudicial.id.
export function obtenerBoletinesCacheDeCaso(casoId: number): Promise<Record<number, DocumentoCandidatoJudicial[]>> {
  return pedido<Record<number, DocumentoCandidatoJudicial[]>>(`/api/admin/vigilancia/casos/${casoId}/boletines-cache`);
}

// Gap real detectado en la auditoría del plan de desarrollo: antes el término procesal solo
// se veía como una insignia dentro del modal de Seguimiento -- si el abogado no entraba a
// revisarlo, no había ninguna alerta activa en otro lado del panel. Esto alimenta el
// semáforo de /admin/calendario (ver TerminoCalendarioResponse en el backend).
export type TerminoCalendario = {
  id: number;
  casoId: number;
  nombreCliente: string;
  radicadoId: string | null;
  descripcion: string;
  fechaVencimiento: string;
  diasHabiles: number;
  estado: EstadoTerminoProcesal;
  esOficial: boolean;
  abogadoAsignadoId: number | null;
  abogadoAsignadoNombre: string | null;
};

export function listarTerminosCalendario(filtros: { desde: string; hasta: string; abogadoId?: number }): Promise<TerminoCalendario[]> {
  const params = new URLSearchParams({ desde: filtros.desde, hasta: filtros.hasta });
  if (filtros.abogadoId) params.set("abogadoId", String(filtros.abogadoId));
  return pedido<TerminoCalendario[]>(`/api/admin/vigilancia/terminos/calendario?${params.toString()}`);
}

// Pedido explicito del usuario (2026-11-04, auditoria en vivo de LUPA/IUS: "deja la
// vigilancia lo mas profesional y unica posible") -- replica del "Control diario procesal"
// de LUPA (ver ControlDiarioProcesalResponse en el backend). conteosPorTipo siempre trae las
// 6 categorias de ClasificadorMovimientoJudicial.TipoMovimiento, con 0 cuando no hubo
// ninguna en el rango.
export type AudienciaAlerta = {
  casoId: number;
  nombreCliente: string;
  radicadoId: string | null;
  nombreActuacion: string;
  anotacion: string | null;
  fechaRegistro: string | null;
  abogadoAsignadoNombre: string | null;
};

export type ControlDiarioProcesal = {
  desde: string;
  hasta: string;
  conteosPorTipo: Record<string, number>;
  vencimientosTermino: number;
  audienciasConPosibleInasistencia: AudienciaAlerta[];
};

export function obtenerControlDiario(filtros: { desde: string; hasta: string; abogadoId?: number }): Promise<ControlDiarioProcesal> {
  const params = new URLSearchParams({ desde: filtros.desde, hasta: filtros.hasta });
  if (filtros.abogadoId) params.set("abogadoId", String(filtros.abogadoId));
  return pedido<ControlDiarioProcesal>(`/api/admin/vigilancia/control-diario?${params.toString()}`);
}

// Documento candidato de la última actuación de fondo -- "candidato" porque puede haber más
// de uno por boletín. Corrección real (2026-10-31, "el sistema esta trayendo boletines que
// no son para cada caso"): ya NO se devuelve todo lo que había en el boletín del despacho
// marcando apenas una pista de similitud de palabras -- el backend (ver
// PublicacionesProcesalesClient.documentosDelBoletin) ahora VERIFICA cada documento contra el
// año+consecutivo real del radicado antes de devolverlo, así que todo lo que llega aquí ya
// está confirmado como del proceso correcto. coincideConNombreActuacion (el nombre quedó del
// mecanismo viejo, la semántica cambió) ahora significa específicamente "es un documento
// INDIVIDUAL de este proceso, seguro para mandarlo a este cliente puntual" -- false cuando es
// un LISTADO verificado (el proceso sí está ahí, pero junto con los de otros clientes/
// terceros: útil para gerencia/abogados, nunca para mandárselo a un cliente en concreto).
export type DocumentoCandidatoJudicial = {
  nombreArchivo: string;
  urlDescarga: string;
  coincideConNombreActuacion: boolean;
};

export type DocumentoRecienteJudicial = {
  disponible: boolean;
  mensaje: string;
  nombreActuacion: string | null;
  anotacion: string | null;
  fechaActuacion: string | null;
  documentosCandidatos: DocumentoCandidatoJudicial[];
};

export function obtenerDocumentoRecienteJudicial(casoId: number): Promise<DocumentoRecienteJudicial> {
  return pedido<DocumentoRecienteJudicial>(`/api/admin/casos/${casoId}/documento-reciente`);
}

// Pedido explícito del usuario: "un botón por cada suceso directo" -- busca el boletín del
// Estado de CUALQUIER actuación de la línea de tiempo (no solo la más reciente de fondo),
// usando la fecha y el nombre que ya trae ese mismo evento (ver EventoLineaTiempoJudicial).
export function buscarDocumentosBoletin(
  casoId: number,
  evento: { fechaRegistro: string; nombreActuacion: string },
): Promise<DocumentoCandidatoJudicial[]> {
  return pedido<DocumentoCandidatoJudicial[]>(`/api/admin/casos/${casoId}/documentos-boletin`, {
    method: "POST",
    body: JSON.stringify(evento),
  });
}

export type EstadoEnvioDocumentoJudicial = "ENVIADO" | "BLOQUEADO_SEGURIDAD" | "FALLIDO";

export type ResultadoEnvioDocumentoJudicial = {
  estado: EstadoEnvioDocumentoJudicial;
  mensaje: string;
};

// Pedido manual y explícito sobre UN documento puntual que el abogado ya vio en la lista de
// candidatos -- nunca se auto-selecciona nada. El backend respeta
// app.bloqueo-total-clientes (ver EnvioDocumentoJudicialService) y distingue "bloqueado por
// seguridad" de "enviado de verdad", nunca un mensaje de éxito ambiguo.
export function enviarDocumentoJudicial(
  casoId: number,
  documento: { urlDocumento: string; nombreArchivo: string; nombreActuacion: string },
): Promise<ResultadoEnvioDocumentoJudicial> {
  return pedido<ResultadoEnvioDocumentoJudicial>(`/api/admin/casos/${casoId}/documento-reciente/enviar`, {
    method: "POST",
    body: JSON.stringify(documento),
  });
}

export type TextoDocumentoJudicial = {
  disponible: boolean;
  texto: string | null;
  mensaje: string | null;
};

// El "anotacion" corto que ya trae EventoLineaTiempoJudicial (ej. "DESCORRE") NO es el texto
// real del Estado Electrónico -- pedido explícito del usuario, esto extrae el texto VERBATIM
// del PDF oficial. Bajo demanda sobre UN documento puntual, nunca automático.
export function obtenerTextoDocumentoJudicial(
  casoId: number,
  urlDocumento: string,
): Promise<TextoDocumentoJudicial> {
  return pedido<TextoDocumentoJudicial>(`/api/admin/casos/${casoId}/documento-reciente/texto`, {
    method: "POST",
    body: JSON.stringify({ urlDocumento }),
  });
}

// Abre el PDF real en una pestaña nueva del navegador (su propio visor nativo), no lo
// descarga a disco -- mismo patrón de fetch+blob que descargarArchivo() (un <a href> normal
// no puede llevar el header Authorization), pero con window.open() en vez de forzar
// "download". El popup se abre ANTES del fetch (con about:blank) porque los navegadores
// bloquean window.open() si no ocurre sincrónicamente dentro del gesto de clic del usuario.
export async function abrirPdfDocumentoJudicial(casoId: number, urlDocumento: string): Promise<void> {
  const ventana = window.open("", "_blank");
  const token = obtenerToken();
  const res = await fetch(`${API_URL}/api/admin/casos/${casoId}/documento-reciente/pdf`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ urlDocumento }),
  });

  if (res.status === 401) {
    ventana?.close();
    throw new ApiError("Tu sesión expiró. Inicia sesión de nuevo.", 401);
  }
  if (!res.ok) {
    ventana?.close();
    throw new ApiError(await parseErrorMessage(res), res.status);
  }

  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  if (ventana) {
    ventana.location.href = url;
  } else {
    // El navegador ya bloqueó el popup vacío -- último intento, puede volver a bloquearse
    // por no ser ya parte del gesto de clic original.
    window.open(url, "_blank");
  }
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

export function actualizarEstadoPagoCobro(
  id: number,
  pagoEsteMes: boolean | null,
  respuesta: string | null,
): Promise<ClienteCobro> {
  return pedido<ClienteCobro>(`/api/admin/cobros/${id}/estado-pago`, {
    method: "PUT",
    body: JSON.stringify({ pagoEsteMes, respuesta }),
  });
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

// Corregido 2026-10-31 (auditoría de SIE-Vigilancia): a este tipo le faltaban 5 valores que
// el backend ya tenía desde antes (EDICION_CASO, CONFIGURACION_ENVIOS, SEGUIMIENTO_JUDICIAL,
// ENVIO_DOCUMENTO_JUDICIAL, CASO_CREADO_DESDE_DETECCION) -- un registro de esos tipos ya
// llegaba al panel, solo que sin el narrowing de TypeScript correcto. Se completa aquí y se
// agregan los 2 nuevos pasos del ciclo nocturno de vigilancia (ver TipoRegistroSistema.java).
export type TipoRegistroSistema =
  | "SINCRONIZACION_CASOS"
  | "ENVIO_NOTIFICACIONES_CASOS"
  | "REPORTE_SEMANAL_CASOS"
  | "SINCRONIZACION_COBROS"
  | "ENVIO_RECORDATORIOS_COBROS"
  | "REINICIO_MENSUAL_COBROS"
  | "RECORDATORIO_CITA"
  | "BOLETIN_ENVIADO"
  | "INICIO_SESION"
  | "USUARIO_CREADO"
  | "USUARIO_ACTIVO_CAMBIADO"
  | "CONSULTA_ESTADO_CASO"
  | "GESTION_CRM"
  | "GESTION_TAREAS"
  | "EDICION_CASO"
  | "CONFIGURACION_ENVIOS"
  | "SEGUIMIENTO_JUDICIAL"
  | "ENVIO_DOCUMENTO_JUDICIAL"
  | "CASO_CREADO_DESDE_DETECCION"
  | "VIGILANCIA_BOLETINES"
  | "VIGILANCIA_DETECCION_PROCESOS"
  | "RADICADO_FORMATO_INVALIDO";

// Agrupador de alto nivel de TipoRegistroSistema (ver CategoriaRegistroSistema en el
// backend) -- pedido explícito del usuario: "pon categorias para que sea mas intuitivo de
// entender".
export type CategoriaRegistroSistema = "COMUNICACIONES" | "SINCRONIZACION" | "SEGURIDAD" | "GESTION_INTERNA";

export type RegistroSistemaItem = {
  id: number;
  tipo: TipoRegistroSistema;
  tipoVisible: string;
  categoria: CategoriaRegistroSistema;
  categoriaVisible: string;
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

// ---------- Registro de envíos (correos y WhatsApp salientes, con destinatario) ----------
// Pedido explícito del usuario: "pon tambien un registro de correos y whatsapp que salen del
// sistema y para quien salen" -- a diferencia del registro del sistema de arriba, cada fila
// acá sí trae el destinatario real (nombre + correo/teléfono), por eso solo ADMIN_GENERAL
// puede verlo (ver RegistroEnvioAdminController en el backend).

export type CanalEnvio = "EMAIL" | "WHATSAPP";

// Corregido 2026-10-31 (auditoría de SIE-Vigilancia): a este tipo también le faltaban 10
// valores que el backend ya tenía desde el motor de firma electrónica y PQRS (ver
// TipoEnvio.java) -- mismo gap que TipoRegistroSistema arriba, corregido igual.
export type TipoEnvio =
  | "CODIGO_CASO"
  | "REPORTE_CASO"
  | "RECORDATORIO_COBRO"
  | "CONFIRMACION_CITA"
  | "RECORDATORIO_CITA"
  | "SOLICITUD_CONFIRMACION"
  | "SOLICITUD_RESPUESTA"
  | "NOTIFICACION_INTERNA"
  | "PUBLICACION_BLOG"
  | "AVISO_REDES_SOCIALES"
  | "BOLETIN"
  | "BIENVENIDA_BOLETIN"
  | "ENLACE_FIRMA_DOCUMENTO"
  | "OTP_FIRMA_DOCUMENTO"
  | "DOCUMENTO_FIRMADO_FINAL"
  | "RECORDATORIO_FIRMA"
  | "ALERTA_INTEGRIDAD_DOCUMENTO"
  | "ALERTA_FIRMA_RECHAZADA"
  | "RADICADO_PQRS"
  | "RESPUESTA_PQRS"
  | "PROVIDENCIA_JUDICIAL"
  | "BOLETIN_JUDICIAL"
  | "ALERTA_VIGILANCIA_CAIDA";

export type RegistroEnvioItem = {
  id: number;
  canal: CanalEnvio;
  canalVisible: string;
  tipo: TipoEnvio;
  tipoVisible: string;
  destinatarioNombre: string | null;
  destinatarioContacto: string;
  resumen: string;
  exitoso: boolean;
  fechaHora: string;
};

export type PaginaRegistroEnvios = {
  content: RegistroEnvioItem[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

export function listarRegistroEnvios(opciones: {
  canal?: CanalEnvio;
  tipo?: TipoEnvio;
  pagina?: number;
  tamano?: number;
}): Promise<PaginaRegistroEnvios> {
  const parametros = new URLSearchParams();
  if (opciones.canal) parametros.set("canal", opciones.canal);
  if (opciones.tipo) parametros.set("tipo", opciones.tipo);
  parametros.set("pagina", String(opciones.pagina ?? 0));
  parametros.set("tamano", String(opciones.tamano ?? 30));
  return pedido<PaginaRegistroEnvios>(`/api/admin/registro-envios?${parametros.toString()}`);
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

// ---------- Mi firma (perfil) ----------
// Autoservicio: cada usuario interno sube su propia firma manuscrita una sola vez (o la
// reemplaza). Ver AuthController#actualizarFirma/consultarFirma en el backend.

export function consultarMiFirma(): Promise<{ tieneFirma: boolean }> {
  return pedido<{ tieneFirma: boolean }>("/api/auth/firma");
}

export async function subirMiFirma(archivo: File): Promise<void> {
  const token = obtenerToken();
  const formData = new FormData();
  formData.append("archivo", archivo);
  const res = await fetch(`${API_URL}/api/auth/firma`, {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: formData,
  });
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
}

// ---------- Motor de firma electrónica de documentos ----------
// Gerencia (ADMIN_GENERAL) sube el documento, asigna abogados y partes de la biblioteca de
// contactos (CRM); el motor inserta las firmas de staff automáticamente (en cuanto cada uno
// tiene "Mi firma" cargada) y notifica a las partes para que firmen en línea. Ver
// sie.siejuridicos.documento.DocumentoAdminController en el backend.

export type EstadoDocumentoFirma =
  | "DILIGENCIADO"
  | "EMITIDO"
  | "FIRMADO_STAFF"
  | "NOTIFICADO"
  | "EN_FIRMA_PARTES"
  | "SELLADO"
  | "REMITIDO"
  | "ENCRIPTADO"
  | "ANULADO"
  | "CANCELADO";

export type EstadoIntegridadDocumento = "VIGENTE" | "ALTERADO";

export type TipoFirmante = "STAFF_GERENTE" | "STAFF_ABOGADO" | "PARTE";

export type EstadoFirmante =
  | "PENDIENTE"
  | "NOTIFICADO"
  | "OTP_VERIFICADO"
  | "FIRMADO"
  | "RECHAZADO"
  | "EXPIRADO"
  | "REVOCADO";

export type CanalNotificacionFirma = "EMAIL" | "WHATSAPP";

export type DocumentoFirmaListado = {
  id: string;
  nombre: string;
  casoId: number | null;
  creadoPorNombre: string | null;
  numeroSecuencia: number | null;
  estadoDocumento: EstadoDocumentoFirma;
  estadoIntegridad: EstadoIntegridadDocumento;
  fechaCreacion: string;
};

export type DocumentoFirmanteSeguimiento = {
  id: string;
  tipoFirmante: TipoFirmante;
  nombreCapturado: string;
  estado: EstadoFirmante;
  canalNotificacion: CanalNotificacionFirma | null;
  identidadVerificadaEn: string | null;
  ipCaptura: string | null;
  geolocalizacionLat: string | null;
  geolocalizacionLon: string | null;
  geolocalizacionRechazada: boolean;
  documentoIdentidadCapturado: boolean;
  firmadoEn: string | null;
};

export type DocumentoEventoSeguimiento = {
  secuencia: number;
  tipoEvento: string;
  actorTipo: "STAFF" | "FIRMANTE" | "SISTEMA";
  actorNombre: string | null;
  descripcion: string;
  ip: string | null;
  hashDocumentoEnEvento: string | null;
  ocurridoEn: string;
};

export type DocumentoSeguimiento = {
  documentoId: string;
  nombre: string;
  estadoDocumento: EstadoDocumentoFirma;
  estadoIntegridad: EstadoIntegridadDocumento;
  firmantes: DocumentoFirmanteSeguimiento[];
  eventos: DocumentoEventoSeguimiento[];
  cadenaEventosIntegra: boolean;
  cadenaEventosDetalle: string | null;
};

export function listarDocumentosFirma(estado?: EstadoDocumentoFirma): Promise<DocumentoFirmaListado[]> {
  const parametros = estado ? `?estado=${estado}` : "";
  return pedido<DocumentoFirmaListado[]>(`/api/admin/documentos${parametros}`);
}

export function obtenerSeguimientoDocumento(id: string): Promise<DocumentoSeguimiento> {
  return pedido<DocumentoSeguimiento>(`/api/admin/documentos/${id}`);
}

// Cancela el proceso de firma en marcha (nadie rechazó firmar, gerencia decide no seguir
// adelante) -- queda en estado CANCELADO, distinto de ANULADO (ver DocumentoFirmaService).
export function cancelarDocumentoFirma(id: string, motivo: string): Promise<DocumentoFirmaListado> {
  return pedido<DocumentoFirmaListado>(`/api/admin/documentos/${id}/cancelar`, {
    method: "POST",
    body: JSON.stringify({ motivo }),
  });
}

// Solo documentos CANCELADO o ANULADO -- el backend rechaza cualquier otro estado (ver
// DocumentoAdminService#eliminar). Un documento ya ENCRIPTADO nunca se puede eliminar.
export function eliminarDocumentoFirma(id: string): Promise<void> {
  return pedido<void>(`/api/admin/documentos/${id}`, { method: "DELETE" });
}

// Una parte a asignar como firmante: clienteCrmId de un contacto ya existente en el CRM
// (créalo primero con crearClienteCrm() si no existe) y el canal de notificación -- el
// backend fuerza EMAIL si el contacto es EMPRESA, sin importar lo que se envíe aquí.
export type ParteAFirmarInput = { clienteCrmId: number; canal: CanalNotificacionFirma };

export async function crearDocumentoFirma(datos: {
  archivo: File;
  nombre: string;
  casoId?: number;
  abogadoIds: number[];
  // Pedido explícito del usuario: "en abogados pon tambien opcional las firmas de los
  // gerentes" -- OTROS ADMIN_GENERAL activos, aparte de quien emite (ese sigue firmando
  // siempre, el backend lo agrega solo). Opcional, igual que abogadoIds.
  gerenteIds?: number[];
  partes: ParteAFirmarInput[];
}): Promise<DocumentoFirmaListado> {
  const token = obtenerToken();
  const formData = new FormData();
  formData.append("archivo", datos.archivo);
  formData.append("nombre", datos.nombre);
  if (datos.casoId) formData.append("casoId", String(datos.casoId));
  datos.abogadoIds.forEach((id) => formData.append("abogadoIds", String(id)));
  (datos.gerenteIds ?? []).forEach((id) => formData.append("gerenteIds", String(id)));
  formData.append("partesJson", JSON.stringify(datos.partes));

  const res = await fetch(`${API_URL}/api/admin/documentos`, {
    method: "POST",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: formData,
  });
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

export function descargarDocumentoFirma(id: string, nombreDocumento: string, conHojaCierre = false): Promise<void> {
  return descargarArchivo(
    `/api/admin/documentos/${id}/descargar?hojaCierre=${conHojaCierre}`,
    `${nombreDocumento}.pdf`,
  );
}

export function descargarCertificadoEvidencia(id: string, nombreDocumento: string): Promise<void> {
  return descargarArchivo(
    `/api/admin/documentos/${id}/certificado-evidencia`,
    `certificado-evidencia-${nombreDocumento}.pdf`,
  );
}

// ---------- Solicitudes de cambio sobre un documento (doble confirmación) ----------
// Un cambio sensible sobre un documento ya sellado (invalidarlo, re-emitirlo, corregir un
// metadato, anular la firma de una parte, revertir un estado ALTERADO) necesita que un
// ADMIN_GENERAL y un ABOGADO distintos lo confirmen antes de poder ejecutarse.

export type TipoAccionCambioDocumento =
  | "INVALIDAR"
  | "RE_EMITIR"
  | "CORREGIR_METADATO"
  | "ANULAR_FIRMA_PARTE"
  | "REVERTIR_ALTERADO";

export type EstadoCambioSolicitud = "PENDIENTE" | "CONFIRMADA_PARCIAL" | "APROBADA" | "EJECUTADA" | "RECHAZADA";

export type DocumentoCambioSolicitud = {
  id: string;
  documentoId: string;
  tipoAccion: TipoAccionCambioDocumento;
  justificacion: string;
  estado: EstadoCambioSolicitud;
  solicitadoPorNombre: string;
  solicitadoEn: string;
  confirmacionGerenteNombre: string | null;
  confirmacionAbogadoNombre: string | null;
  ejecutadoEn: string | null;
  motivoRechazo: string | null;
};

export function crearCambioSolicitudDocumento(datos: {
  documentoId: string;
  tipoAccion: TipoAccionCambioDocumento;
  justificacion: string;
  payloadJson?: string;
}): Promise<DocumentoCambioSolicitud> {
  return pedido<DocumentoCambioSolicitud>("/api/admin/documentos/cambios", {
    method: "POST",
    body: JSON.stringify(datos),
  });
}

export function listarCambiosSolicitudDocumento(estado?: EstadoCambioSolicitud): Promise<DocumentoCambioSolicitud[]> {
  const parametros = estado ? `?estado=${estado}` : "";
  return pedido<DocumentoCambioSolicitud[]>(`/api/admin/documentos/cambios${parametros}`);
}

export function confirmarCambioSolicitudDocumento(id: string): Promise<DocumentoCambioSolicitud> {
  return pedido<DocumentoCambioSolicitud>(`/api/admin/documentos/cambios/${id}/confirmar`, { method: "POST" });
}

export function rechazarCambioSolicitudDocumento(id: string, motivo: string): Promise<DocumentoCambioSolicitud> {
  return pedido<DocumentoCambioSolicitud>(`/api/admin/documentos/cambios/${id}/rechazar`, {
    method: "POST",
    body: JSON.stringify({ motivo }),
  });
}

export function ejecutarCambioSolicitudDocumento(id: string): Promise<DocumentoCambioSolicitud> {
  return pedido<DocumentoCambioSolicitud>(`/api/admin/documentos/cambios/${id}/ejecutar`, { method: "POST" });
}

// =========================================================================
// BUSCADOR TOTAL DEL PANEL (visible para ADMIN_GENERAL y ABOGADO)
// =========================================================================

export type TipoResultadoBusqueda = "CASO" | "CLIENTE" | "SOLICITUD" | "DOCUMENTO";

export type ResultadoBusqueda = {
  tipo: TipoResultadoBusqueda;
  id: string;
  titulo: string;
  subtitulo: string;
  ruta: string;
};

export function buscarGlobal(q: string): Promise<ResultadoBusqueda[]> {
  const parametros = new URLSearchParams({ q });
  return pedido<ResultadoBusqueda[]>(`/api/admin/busqueda?${parametros.toString()}`);
}

// =========================================================================
// PROCESOS NUEVOS DETECTADOS (pedido explícito del usuario: "implementa todas las mejoras
// identificadas", comparando contra Lupa Jurídica) -- el CPNU buscó por el NOMBRE de cada
// cliente (no solo por los radicados que ya conocíamos) y encontró procesos que todavía no
// están en nuestra tabla de casos. Cada hallazgo queda aquí como "candidato" -- nunca se crea
// un caso solo, porque la búsqueda por nombre puede ser un homónimo real, no necesariamente
// el mismo cliente. Un abogado/admin decide con uno de dos botones (pedido explícito del
// usuario tras ver la primera versión: "deja dos botones, CREAR Y ADJUNTAR AUTOMATICAMENTE
// EN SIE, NO (ES OTRA PERSONA)"):
//   1. crearCasoDesdeProcesoDetectado -- confirma que SÍ es el cliente: el backend crea el
//      Caso de una vez con los datos que el CPNU ya trajo (radicado, partes, despacho), sin
//      que haya que volver a escribir nada a mano.
//   2. actualizarProcesoDetectado(id, "DESCARTADO") -- confirma que es un homónimo, lo quita
//      de la lista sin crear nada.
// =========================================================================

export type EstadoProcesoCandidato = "PENDIENTE" | "DESCARTADO" | "YA_ES_CASO";

export type ProcesoCandidato = {
  id: number;
  clienteId: number;
  clienteNombre: string;
  llaveProceso: string;
  despacho: string | null;
  departamento: string | null;
  sujetosProcesales: string | null;
  fechaProceso: string | null;
  fechaUltimaActuacion: string | null;
  estado: EstadoProcesoCandidato;
  fechaDeteccion: string;
};

export function listarProcesosDetectados(): Promise<ProcesoCandidato[]> {
  return pedido<ProcesoCandidato[]>("/api/admin/vigilancia/procesos-detectados");
}

// Botón "No (es otra persona)" -- el único destino real por este camino hoy es DESCARTADO.
export function actualizarProcesoDetectado(
  id: number,
  estado: Exclude<EstadoProcesoCandidato, "PENDIENTE" | "YA_ES_CASO">,
): Promise<ProcesoCandidato> {
  return pedido<ProcesoCandidato>(`/api/admin/vigilancia/procesos-detectados/${id}`, {
    method: "PATCH",
    body: JSON.stringify({ estado }),
  });
}

// Botón "Crear y adjuntar automáticamente en SIE" -- crea el Caso real (ver
// CasoService.crearDesdeProcesoCandidato en el backend) y marca el candidato como YA_ES_CASO,
// los dos en un solo paso. Devuelve el caso recién creado para poder mostrar su radicado.
export function crearCasoDesdeProcesoDetectado(id: number): Promise<CasoAdmin> {
  return pedido<CasoAdmin>(`/api/admin/vigilancia/procesos-detectados/${id}/crear-caso`, {
    method: "POST",
  });
}

// ============================================================================
// Integración Legal-Tech Croma API (usecroma.com) - Plan Free ($0 USD / mes)
// Búsqueda de procesos judiciales por Cédula / Nombre de persona o empresa.
// Tope de 1,500 créditos mensuales (150 consultas en vivo de 10 créditos c/u).
// ============================================================================

export type CromaEstadoCuota = {
  mesAno: string;
  creditosConsumidos: number;
  limiteCreditos: number;
  creditosRestantes: number;
  consultasRealizadas: number;
  consultasRestantes: number;
  creditosPorConsulta: number;
  tieneApiKeyConfigurada: boolean;
  cuotaDisponible: boolean;
  keyActivaIndice?: number;
  totalKeysConfiguradas?: number;
  creditosConsumidosKeyActiva?: number;
  creditosPorKey?: number;
};

export type CromaCaso = {
  radicado: string;
  despacho: string;
  departamento: string;
  fechaInicio: string | null;
  fechaUltimaActuacion: string | null;
  partes: string;
  esPrivado: boolean;
  rama?: string;
  fuente?: string;
  yaExisteEnSie?: boolean;
  casoId?: number | null;
  claseProceso?: string | null;
};

export type CromaBusquedaResultado = {
  exito: boolean;
  mensaje: string;
  totalCasos: number;
  casos: CromaCaso[];
  creditosConsumidosMes: number;
  limiteCreditosMes: number;
  consultasRestantesMes: number;
  documentoIdentificado?: string | null;
  nombreResuelto?: string | null;
  ramasConsultadas?: string[];
  totalPorRama?: Record<string, number>;
};

export function obtenerCuotaCroma(): Promise<CromaEstadoCuota> {
  return pedido<CromaEstadoCuota>("/api/admin/croma/cuota");
}

export function buscarProcesosCroma(params: {
  nombre: string;
  tipoEntidad?: "natural" | "juridical";
  soloActivos?: boolean;
  corporacionSamai?: string;
  ramaFiltro?: string;
  documento?: string;
}): Promise<CromaBusquedaResultado> {
  return pedido<CromaBusquedaResultado>("/api/admin/croma/buscar", {
    method: "POST",
    body: JSON.stringify(params),
  });
}

export type CromaBuzonDespacho = {
  email: string;
  nombre: string;
  tipoCuenta: string;
  corporacion: string;
  especialidad: string;
  departamento: string;
  ciudad: string;
  seccional: string;
  codigoDespacho: string;
};

export type CromaLegalProResultado = {
  exito: boolean;
  mensaje: string;
  casoId: number | null;
  nombreCliente: string;
  radicado: string;
  fuenteVisible: string | null;
  encontradoEnCroma: boolean;
  idProcesoCpnu: number | null;
  connectionIdCroma?: number | null;
  idsProcesosRelacionados?: number[];
  soloPrivadoEnRama?: boolean;
  codigoDespacho12Digitos?: string | null;
  despacho: string | null;
  departamento: string | null;
  sujetosProcesales: string | null;
  fechaInicio: string | null;
  fechaUltimaActuacion: string | null;
  esPrivado: boolean;
  ponente: string | null;
  tipoProceso: string | null;
  claseProceso: string | null;
  subclaseProceso?: string | null;
  recursoProceso?: string | null;
  ubicacion: string | null;
  contenidoRadicacion?: string | null;
  ultimaActualizacionRama?: string | null;
  correoCliente?: string | null;
  telefonoCliente?: string | null;
  abogadoAsignadoNombre?: string | null;
  totalActuaciones: number;
  actuacionesNuevasInyectadasEnSie: number;
  actuacionesFundamentalesTotal: number;
  terminosActivosTotal: number;
  boletinesDisponiblesTotal: number;
  boletinesNuevosDescargados: number;
  buzonesDespacho: CromaBuzonDespacho[];
  boletinesCacheados: BoletinJudicial[];
  documentosEnVivo: DocumentoCandidatoJudicial[];
  actuaciones: EventoLineaTiempoJudicial[];
  creditosConsumidosMes: number;
  limiteCreditosMes: number;
  consultasRestantesMes: number;
  cargadoDesdeMemoria?: boolean;
  fechaExtraccionInforme?: string | null;
  fechaExpiracionInforme?: string | null;
  diasParaBorrarInforme?: number | null;
  guardadoHaceTexto?: string | null;
};

export type CromaMemoriaResumen = {
  casoId: number | null;
  radicado: string;
  fechaExtraccion: string;
  fechaExpiracion: string;
  fechaEmision?: string | null;
  tipoActuacion?: string | null;
  diasParaBorrar: number;
  guardadoHaceTexto: string;
};

export function generarInformeLegalProCroma(params: {
  casoId?: number;
  radicado?: string;
  buscarBoletinesEnVivo?: boolean;
}): Promise<CromaLegalProResultado> {
  return pedido<CromaLegalProResultado>("/api/admin/croma/legal-pro", {
    method: "POST",
    body: JSON.stringify(params),
  });
}

export function listarMemoriasLegalProCroma(): Promise<CromaMemoriaResumen[]> {
  return pedido<CromaMemoriaResumen[]>("/api/admin/croma/legal-pro/memorias");
}

export async function obtenerMemoriaLegalProCroma(params: {
  casoId?: number;
  radicado?: string;
}): Promise<CromaLegalProResultado | null> {
  const qs = new URLSearchParams();
  if (params.casoId !== undefined) qs.set("casoId", String(params.casoId));
  if (params.radicado) qs.set("radicado", params.radicado);
  return pedido<CromaLegalProResultado | null>(`/api/admin/croma/legal-pro/memoria?${qs.toString()}`);
}
