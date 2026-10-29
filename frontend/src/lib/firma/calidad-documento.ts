// Evaluación de calidad del encuadre del documento de identidad, en vivo, cuadro a cuadro.
// Es visión por computador clásica (gradientes/varianza de Laplaciano), no un modelo
// entrenado: una librería de detección de objetos "documento real vs. no documento" con
// garantías fuertes no existe gratis para correr en el navegador, y no vamos a prometer eso
// en los textos legales del flujo (ver EvaluacionRecuadro.aprobado, que es un umbral de
// calidad de imagen -- nítida, con contraste, con un borde plausible donde debería estar el
// del documento -- no una certificación de autenticidad del documento en sí).
//
// Trabaja sobre dos recortes del mismo cuadro de video, tomados por quien llama (ver
// camera-capture.tsx): el "interior" (centro del recuadro guía, ~80%) para nitidez/contraste
// -- "hay algo legible aquí, no un fondo liso" -- y una "banda de borde" (una franja angosta
// centrada justo donde debería caer el borde del recuadro guía) para el indicio de que un
// borde real de documento está alineado ahí -- "el documento llena el recuadro, no quedó más
// chico o corrido".

export interface EvaluacionRecuadro {
  nitidez: number;
  contraste: number;
  densidadBorde: number;
  aprobado: boolean;
}

// Umbrales calibrados a ojo contra fotos de cédula reales tomadas con cámara trasera de
// celular en luz interior normal -- no hay dataset propio para calibrarlos de forma
// estadística, así que son conservadores (prefieren pedir repetir la foto a aceptar una
// borrosa/vacía) y quedan en un solo lugar por si hay que ajustarlos con uso real.
const UMBRAL_NITIDEZ = 18;
const UMBRAL_CONTRASTE = 22;
const UMBRAL_DENSIDAD_BORDE = 10;

// Lado máximo al que se reescala antes de calcular -- el Laplaciano es O(ancho*alto) por
// cuadro y esto corre en un bucle en vivo varias veces por segundo; a resolución nativa de
// cámara trasera (hasta 4K) sería notablemente lento en celulares de gama media.
const LADO_MAXIMO_ANALISIS = 220;

function escalaDeGrisesReescalada(origen: ImageData, ladoMaximo: number): { gris: Float32Array; ancho: number; alto: number } {
  const escala = Math.min(1, ladoMaximo / Math.max(origen.width, origen.height));
  const ancho = Math.max(2, Math.round(origen.width * escala));
  const alto = Math.max(2, Math.round(origen.height * escala));
  const gris = new Float32Array(ancho * alto);
  const { data, width: anchoOrig } = origen;
  for (let y = 0; y < alto; y++) {
    const yOrig = Math.min(origen.height - 1, Math.floor(y / escala));
    for (let x = 0; x < ancho; x++) {
      const xOrig = Math.min(anchoOrig - 1, Math.floor(x / escala));
      const i = (yOrig * anchoOrig + xOrig) * 4;
      gris[y * ancho + x] = 0.299 * data[i] + 0.587 * data[i + 1] + 0.114 * data[i + 2];
    }
  }
  return { gris, ancho, alto };
}

function varianzaLaplaciano(gris: Float32Array, ancho: number, alto: number): number {
  if (ancho < 3 || alto < 3) return 0;
  const valores: number[] = [];
  for (let y = 1; y < alto - 1; y++) {
    for (let x = 1; x < ancho - 1; x++) {
      const i = y * ancho + x;
      const laplaciano = 4 * gris[i] - gris[i - 1] - gris[i + 1] - gris[i - ancho] - gris[i + ancho];
      valores.push(laplaciano);
    }
  }
  if (valores.length === 0) return 0;
  const media = valores.reduce((a, b) => a + b, 0) / valores.length;
  return valores.reduce((a, b) => a + (b - media) ** 2, 0) / valores.length;
}

function desviacionEstandar(gris: Float32Array): number {
  if (gris.length === 0) return 0;
  const media = gris.reduce((a, b) => a + b, 0) / gris.length;
  const varianza = gris.reduce((a, b) => a + (b - media) ** 2, 0) / gris.length;
  return Math.sqrt(varianza);
}

/** Magnitud de gradiente media (Sobel simplificado) -- proxy de "hay un borde marcado aquí". */
function densidadDeBorde(gris: Float32Array, ancho: number, alto: number): number {
  if (ancho < 3 || alto < 3) return 0;
  let suma = 0;
  let cuenta = 0;
  for (let y = 1; y < alto - 1; y++) {
    for (let x = 1; x < ancho - 1; x++) {
      const i = y * ancho + x;
      const gx = gris[i + 1] - gris[i - 1];
      const gy = gris[i + ancho] - gris[i - ancho];
      suma += Math.sqrt(gx * gx + gy * gy);
      cuenta++;
    }
  }
  return cuenta === 0 ? 0 : suma / cuenta;
}

export function evaluarRecuadroDocumento(interior: ImageData, bandaBorde: ImageData): EvaluacionRecuadro {
  const interiorGris = escalaDeGrisesReescalada(interior, LADO_MAXIMO_ANALISIS);
  const bandaGris = escalaDeGrisesReescalada(bandaBorde, LADO_MAXIMO_ANALISIS);

  const nitidez = varianzaLaplaciano(interiorGris.gris, interiorGris.ancho, interiorGris.alto);
  const contraste = desviacionEstandar(interiorGris.gris);
  const densidadBorde = densidadDeBorde(bandaGris.gris, bandaGris.ancho, bandaGris.alto);

  const aprobado = nitidez >= UMBRAL_NITIDEZ && contraste >= UMBRAL_CONTRASTE && densidadBorde >= UMBRAL_DENSIDAD_BORDE;

  return { nitidez, contraste, densidadBorde, aprobado };
}
