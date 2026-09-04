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
export type OrigenSolicitud = "FORMULARIO" | "CHATBOT" | "WHATSAPP";

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
};

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

export function agendarCita(id: number, fechaHora: string) {
  return pedido<Solicitud>(`/api/admin/solicitudes/${id}/cita`, {
    method: "PATCH",
    body: JSON.stringify({ fechaHora }),
  });
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

// ---------- Registro del Sistema ----------
// Bitácora de procesos que el sistema ejecuta por su cuenta o que el admin dispara desde el
// panel (sincronizaciones, envíos masivos, recordatorios programados, boletines) -- solo
// ADMIN_GENERAL, ver SecurityConfig en el backend.

export type TipoRegistroSistema =
  | "SINCRONIZACION_CASOS"
  | "ENVIO_NOTIFICACIONES_CASOS"
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
