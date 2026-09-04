// Este módulo se usa tanto desde componentes cliente (navegador) como desde Server
// Components (SSR: listado y detalle de blog, categorías). NEXT_PUBLIC_API_URL se
// hornea en el bundle en build time con el dominio público (https://tudominio.com/api,
// ver docker-compose.prod.yml) — correcto para el navegador, pero usarlo también desde
// el servidor hace que el contenedor frontend salga a Internet, pase por Caddy/DNS/TLS
// y vuelva a entrar al contenedor backend para cada render de servidor, en vez de
// hablarle directo por la red interna de Docker. Si el certificado aún no se emitió
// (DNS recién propagado) o Caddy tiene un hipo, el SSR del blog fallaba con backend y
// frontend perfectamente sanos. API_URL_INTERNAL (variable normal, no NEXT_PUBLIC_: se
// lee en tiempo de ejecución en el servidor, nunca llega al bundle del navegador) apunta
// directo a http://backend:8080 dentro de la red sie_red cuando existe.
const API_URL =
  typeof window === "undefined"
    ? (process.env.API_URL_INTERNAL ?? process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080")
    : (process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080");

export type Categoria = {
  id: number;
  nombre: string;
  slug: string;
};

export type TipoContenido = "BLOG" | "NOTICIA";

export type ArticuloResumen = {
  id: number;
  titulo: string;
  slug: string;
  resumen: string | null;
  imagenUrl: string | null;
  tipoContenido: TipoContenido;
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
  // Honeypot (ver CampoTrampa en backend y frontend/src/components/campo-trampa.tsx):
  // siempre vacío en un envío real, nunca se pide al usuario que lo llene.
  sitioWeb?: string;
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
  // Honeypot, ver CrearSolicitudInput.sitioWeb.
  sitioWeb?: string;
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
  tipo?: TipoContenido;
  q?: string;
}): Promise<ArticuloResumen[]> {
  const search = new URLSearchParams();
  if (params?.categoria) search.set("categoria", String(params.categoria));
  if (params?.tipo) search.set("tipo", params.tipo);
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
  // Honeypot, ver CrearSolicitudInput.sitioWeb.
  sitioWeb?: string;
};

export async function suscribirNewsletter(input: SuscribirNewsletterInput) {
  const res = await fetch(`${API_URL}/api/marketing/suscriptores`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!res.ok) throw new ApiError(await parseErrorMessage(res), res.status);
}

export type PreguntaFrecuente = {
  id: number;
  pregunta: string;
  respuesta: string;
};

export async function getFaq(): Promise<PreguntaFrecuente[]> {
  const res = await fetch(`${API_URL}/api/faq`, {
    next: { revalidate: 300 },
  });
  if (!res.ok) throw new ApiError(await parseErrorMessage(res), res.status);
  return res.json();
}

// El estado real del caso se lee en vivo del Google Sheets de la firma (ver
// HojaCalculoService en el backend), no de nuestra base de datos: estadoDisponible=false es
// un estado normal (el radicado está registrado pero la firma aún no cargó esa fila en la
// hoja), no un error.
export type CasoConsulta = {
  radicadoId: string;
  estadoDisponible: boolean;
  despachoJudicial: string | null;
  informacionCaso: string | null;
  tipoCaso: string | null;
  ultimaDecision: string | null;
  estado: string | null;
  fechaActualizacionHoja: string | null;
  fechaRegistro: string;
};

export async function consultarCaso(radicadoId: string): Promise<CasoConsulta> {
  const res = await fetch(
    `${API_URL}/api/casos/consulta?codigo=${encodeURIComponent(radicadoId)}`,
  );
  if (!res.ok) throw new ApiError(await parseErrorMessage(res), res.status);
  return res.json();
}

export async function registrarVisita(visitanteId: string): Promise<void> {
  const res = await fetch(`${API_URL}/api/analitica/visita`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ visitanteId }),
  });
  if (!res.ok) throw new ApiError(await parseErrorMessage(res), res.status);
}

export { ApiError };
