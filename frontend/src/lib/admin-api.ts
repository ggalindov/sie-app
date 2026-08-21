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

export type EtapaCaso = "RADICADO" | "EN_ESTUDIO" | "EN_TRAMITE" | "AUDIENCIA_DILIGENCIA" | "RESUELTO";

export type CasoAdmin = {
  id: number;
  nombreCliente: string;
  correoCliente: string;
  telefonoCliente: string | null;
  categoriaNombre: string;
  codigoUnico: string;
  etapa: EtapaCaso;
  notasInternas: string | null;
  fechaCreacion: string;
  fechaActualizacion: string;
};

export function listarCasos(): Promise<CasoAdmin[]> {
  return pedido<CasoAdmin[]>("/api/admin/casos");
}

export function crearCaso(input: {
  nombreCliente: string;
  correoCliente: string;
  telefonoCliente?: string;
  idCategoria: number;
  notasInternas?: string;
}): Promise<CasoAdmin> {
  return pedido<CasoAdmin>("/api/admin/casos", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function actualizarEtapaCaso(id: number, nuevaEtapa: EtapaCaso): Promise<CasoAdmin> {
  return pedido<CasoAdmin>(`/api/admin/casos/${id}/etapa`, {
    method: "PATCH",
    body: JSON.stringify({ nuevaEtapa }),
  });
}
