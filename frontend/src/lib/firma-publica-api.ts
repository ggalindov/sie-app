// Cliente del flujo PÚBLICO de firma electrónica (/api/v1/firmas/{token}/**): sin JWT, el
// token opaco del enlace único es la única credencial. Cada llamada va directa al backend
// (nunca por admin-api.ts, que asume sesión de staff) -- ver PublicSignatureController.

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

async function pedido<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
  });
  if (!res.ok) throw new ApiError(await parseErrorMessage(res), res.status);
  if (res.status === 204 || res.status === 202) return undefined as T;
  return res.json();
}

export type FirmaResumen = {
  documentoNombre: string;
  nombreFirmante: string;
  estadoFirmante: string;
  identidadVerificada: boolean;
  otpVerificado: boolean;
  consentimientoRegistrado: boolean;
  geolocalizacionRegistrada: boolean;
  identidadRegistrada: boolean;
  verificacionHumanaRegistrada: boolean;
  firmado: boolean;
};

export function obtenerResumenFirma(token: string): Promise<FirmaResumen> {
  return pedido<FirmaResumen>(`/api/v1/firmas/${token}`);
}

export function verificarIdentidadFirma(token: string, numeroDocumento: string): Promise<FirmaResumen> {
  return pedido<FirmaResumen>(`/api/v1/firmas/${token}/verificar-identidad`, {
    method: "POST",
    body: JSON.stringify({ numeroDocumento }),
  });
}

export function rechazarFirma(token: string, motivo?: string): Promise<FirmaResumen> {
  return pedido<FirmaResumen>(`/api/v1/firmas/${token}/rechazar`, {
    method: "POST",
    body: JSON.stringify({ motivo: motivo ?? null }),
  });
}

export function reenviarOtpFirma(token: string): Promise<void> {
  return pedido<void>(`/api/v1/firmas/${token}/otp/reenviar`, { method: "POST" });
}

export function verificarOtpFirma(token: string, codigo: string): Promise<FirmaResumen> {
  return pedido<FirmaResumen>(`/api/v1/firmas/${token}/otp/verificar`, {
    method: "POST",
    body: JSON.stringify({ codigo }),
  });
}

export function registrarConsentimientoFirma(token: string, textoAceptado: string, aceptar: boolean): Promise<FirmaResumen> {
  return pedido<FirmaResumen>(`/api/v1/firmas/${token}/consentimiento`, {
    method: "POST",
    body: JSON.stringify({ textoAceptado, aceptar }),
  });
}

export function registrarGeolocalizacionFirma(
  token: string,
  datos: { lat: number | null; lon: number | null; precisionM: number | null; rechazada: boolean },
): Promise<FirmaResumen> {
  return pedido<FirmaResumen>(`/api/v1/firmas/${token}/geolocalizacion`, {
    method: "POST",
    body: JSON.stringify(datos),
  });
}

async function subirArchivoFirma(path: string, campo: string, archivo: Blob, nombre: string): Promise<FirmaResumen> {
  const formData = new FormData();
  formData.append(campo, archivo, nombre);
  const res = await fetch(`${API_URL}${path}`, { method: "POST", body: formData });
  if (!res.ok) throw new ApiError(await parseErrorMessage(res), res.status);
  return res.json();
}

export function registrarIdentidadFirma(token: string, foto: Blob): Promise<FirmaResumen> {
  return subirArchivoFirma(`/api/v1/firmas/${token}/identidad`, "archivo", foto, "identidad.jpg");
}

export function registrarVerificacionHumanaFirma(token: string, foto: Blob): Promise<FirmaResumen> {
  return subirArchivoFirma(`/api/v1/firmas/${token}/verificacion-humana`, "archivo", foto, "verificacion.jpg");
}

export async function firmarDocumento(
  token: string,
  firmaPng: Blob,
  opciones: { huellaDispositivo?: string; contextoDispositivoJson?: string; trazoVectorialJson?: string },
): Promise<FirmaResumen> {
  const formData = new FormData();
  formData.append("firma", firmaPng, "firma.png");
  if (opciones.huellaDispositivo) formData.append("huellaDispositivo", opciones.huellaDispositivo);
  if (opciones.contextoDispositivoJson) formData.append("contextoDispositivoJson", opciones.contextoDispositivoJson);
  if (opciones.trazoVectorialJson) formData.append("trazoVectorialJson", opciones.trazoVectorialJson);
  const res = await fetch(`${API_URL}/api/v1/firmas/${token}/firmar`, { method: "POST", body: formData });
  if (!res.ok) throw new ApiError(await parseErrorMessage(res), res.status);
  return res.json();
}

export async function descargarCertificadoEvidenciaFirma(token: string): Promise<void> {
  const res = await fetch(`${API_URL}/api/v1/firmas/${token}/certificado-evidencia`);
  if (!res.ok) throw new ApiError(await parseErrorMessage(res), res.status);
  const blob = await res.blob();
  descargarBlob(blob, "certificado-evidencia.pdf");
}

export async function descargarDocumentoFinalFirma(token: string): Promise<void> {
  const res = await fetch(`${API_URL}/api/v1/firmas/${token}/documento-final`);
  if (!res.ok) throw new ApiError(await parseErrorMessage(res), res.status);
  const disposicion = res.headers.get("Content-Disposition");
  const nombre = disposicion?.match(/filename="?([^";]+)"?/)?.[1] ?? "documento-firmado.pdf";
  const blob = await res.blob();
  descargarBlob(blob, nombre);
}

function descargarBlob(blob: Blob, nombre: string) {
  const url = URL.createObjectURL(blob);
  const enlace = document.createElement("a");
  enlace.href = url;
  enlace.download = nombre;
  document.body.appendChild(enlace);
  enlace.click();
  document.body.removeChild(enlace);
  URL.revokeObjectURL(url);
}

export async function contarPaginasDocumento(token: string): Promise<number> {
  const res = await fetch(`${API_URL}/api/v1/firmas/${token}/documento/paginas`);
  if (!res.ok) throw new ApiError(await parseErrorMessage(res), res.status);
  const data = await res.json();
  return data.paginas;
}

// Devuelve la URL de la imagen de la página (marca de agua "SOLO LECTURA", nunca el PDF
// real) para usar directo en un <img src="...">.
export function urlPaginaDocumento(token: string, numero: number): string {
  return `${API_URL}/api/v1/firmas/${token}/documento/paginas/${numero}`;
}
