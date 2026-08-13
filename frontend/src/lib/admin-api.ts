import { obtenerToken } from "@/lib/auth";
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

  if (res.status === 401) {
    throw new ApiError("Tu sesión expiró. Inicia sesión de nuevo.", 401);
  }
  if (!res.ok) {
    throw new ApiError(await parseErrorMessage(res), res.status);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
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

// ---------- Artículos ----------

export type EstadoArticulo = "BORRADOR" | "PUBLICADO";

export type ArticuloAdmin = {
  id: number;
  titulo: string;
  slug: string;
  contenido: string;
  resumen: string | null;
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

// ---------- Categorías (público, reusado en el panel) ----------

export function listarCategorias(): Promise<Categoria[]> {
  return pedido<Categoria[]>("/api/categorias");
}
