const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export type Categoria = {
  id: number;
  nombre: string;
  slug: string;
};

export type ArticuloResumen = {
  id: number;
  titulo: string;
  slug: string;
  resumen: string | null;
  categoria: Categoria;
  autorNombre: string;
  fechaPublicacion: string;
  tiempoLecturaMin: number | null;
};

export type ArticuloDetalle = ArticuloResumen & {
  contenido: string;
  estado: "BORRADOR" | "PUBLICADO";
  fechaCreacion: string;
};

export type CrearSolicitudInput = {
  nombre: string;
  correo: string;
  telefono?: string;
  mensaje: string;
  aceptaTratamientoDatos: boolean;
  aceptaMarketing: boolean;
};

export type TurnoChat = {
  rol: "USUARIO" | "ASISTENTE";
  contenido: string;
};

export type MensajeChatbotInput = {
  conversacionId: number | null;
  mensaje: string;
  historial: TurnoChat[];
};

export type MensajeChatbotOutput = {
  conversacionId: number;
  respuesta: string;
};

export type TestimonioPublico = {
  id: number;
  nombre: string;
  empresa: string | null;
  cargo: string | null;
  cita: string;
  calificacion: number;
  fechaCreacion: string;
};

export type CrearTestimonioInput = {
  nombre: string;
  empresa?: string;
  cargo?: string;
  cita: string;
  calificacion: number;
  correo: string;
};

class ApiError extends Error {
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
    return body.mensaje ?? "Ocurrió un error inesperado";
  } catch {
    return "Ocurrió un error inesperado";
  }
}

export async function getCategorias(): Promise<Categoria[]> {
  const res = await fetch(`${API_URL}/api/categorias`, {
    next: { revalidate: 3600 },
  });
  if (!res.ok) throw new ApiError(await parseErrorMessage(res), res.status);
  return res.json();
}

export async function getArticulos(params?: {
  categoria?: number;
  q?: string;
}): Promise<ArticuloResumen[]> {
  const search = new URLSearchParams();
  if (params?.categoria) search.set("categoria", String(params.categoria));
  if (params?.q) search.set("q", params.q);
  const query = search.toString();

  const res = await fetch(
    `${API_URL}/api/articulos${query ? `?${query}` : ""}`,
    { next: { revalidate: 300 } },
  );
  if (!res.ok) throw new ApiError(await parseErrorMessage(res), res.status);
  return res.json();
}

export async function getArticuloPorSlug(
  slug: string,
): Promise<ArticuloDetalle | null> {
  const res = await fetch(`${API_URL}/api/articulos/${slug}`, {
    next: { revalidate: 300 },
  });
  if (res.status === 404) return null;
  if (!res.ok) throw new ApiError(await parseErrorMessage(res), res.status);
  return res.json();
}

export async function crearSolicitud(input: CrearSolicitudInput) {
  const res = await fetch(`${API_URL}/api/solicitudes`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!res.ok) throw new ApiError(await parseErrorMessage(res), res.status);
  return res.json();
}

export async function enviarMensajeChatbot(
  input: MensajeChatbotInput,
): Promise<MensajeChatbotOutput> {
  const res = await fetch(`${API_URL}/api/chatbot/mensaje`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!res.ok) throw new ApiError(await parseErrorMessage(res), res.status);
  return res.json();
}

export async function getTestimoniosAprobados(): Promise<TestimonioPublico[]> {
  const res = await fetch(`${API_URL}/api/testimonios`, {
    next: { revalidate: 120 },
  });
  if (!res.ok) throw new ApiError(await parseErrorMessage(res), res.status);
  return res.json();
}

export async function crearTestimonio(input: CrearTestimonioInput) {
  const res = await fetch(`${API_URL}/api/testimonios`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!res.ok) throw new ApiError(await parseErrorMessage(res), res.status);
  return res.json();
}

export type SuscribirNewsletterInput = {
  nombre: string;
  correo: string;
};

export async function suscribirNewsletter(input: SuscribirNewsletterInput) {
  const res = await fetch(`${API_URL}/api/marketing/suscriptores`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!res.ok) throw new ApiError(await parseErrorMessage(res), res.status);
}

export { ApiError };
