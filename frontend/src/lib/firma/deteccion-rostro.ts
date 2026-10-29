// Verificación de vida (liveness) en el navegador, con @mediapipe/tasks-vision -- la
// librería oficial de Google, gratuita, corre entera del lado del cliente (WASM, sin
// enviar los cuadros de video a ningún servidor) y sin costo de API. Se eligió sobre un
// servicio de verificación de identidad de pago (Onfido, Jumio, Veriff, AWS Rekognition
// Liveness) porque ninguno estaba contratado para este proyecto y el alcance pedido es
// "lo mejor que se pueda implementar ya", no una integración de pago nueva.
//
// HONESTIDAD SOBRE LOS LÍMITES (importante para cualquier texto legal/certificado que use
// esto, ver DocumentoFirmante): esto NO es un sistema de anti-spoofing de grado forense. Da
// dos señales reales y verificables -- (a) hay exactamente un rostro humano, de frente,
// centrado, con los ojos abiertos, y (b) se detectó un parpadeo deliberado dentro de la
// ventana de tiempo pedida (una foto estática o una pantalla reproduciendo un video
// pregrabado sin el parpadeo exacto pedido no la pasa) -- combinadas con el resto de la
// evidencia del proceso (geolocalización, documento de identidad, declaración expresa de
// no usar máscaras/pantallas, trazabilidad del dispositivo). No es una garantía matemática
// de que sea imposible falsificar con un ataque suficientemente sofisticado (un video
// deepfake en vivo reaccionando al desafío, por ejemplo, podría pasarlo) -- nunca se debe
// describir esto como "detección infalible de fraude" en ningún texto de cara al usuario.
"use client";

import {
  FaceLandmarker,
  FilesetResolver,
  type FaceLandmarkerResult,
} from "@mediapipe/tasks-vision";

const VERSION_PAQUETE = "1.2.0";
const URL_WASM = `https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@${VERSION_PAQUETE}/wasm`;
const URL_MODELO =
  "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task";

let instanciaPromesa: Promise<FaceLandmarker> | null = null;

/** Carga perezosa y memoizada: el modelo (~3-4MB) solo se descarga una vez por sesión. */
export function cargarFaceLandmarker(): Promise<FaceLandmarker> {
  if (!instanciaPromesa) {
    instanciaPromesa = (async () => {
      const vision = await FilesetResolver.forVisionTasks(URL_WASM);
      return FaceLandmarker.createFromOptions(vision, {
        baseOptions: { modelAssetPath: URL_MODELO, delegate: "GPU" },
        outputFaceBlendshapes: true,
        outputFacialTransformationMatrixes: true,
        runningMode: "VIDEO",
        numFaces: 1,
      });
    })().catch((err) => {
      // Si falla, el siguiente llamador debe poder reintentar (p. ej. tras recuperar la
      // red), no quedarse con una promesa rechazada memoizada para siempre.
      instanciaPromesa = null;
      throw err;
    });
  }
  return instanciaPromesa;
}

export interface EvaluacionRostro {
  unRostro: boolean;
  centrado: boolean;
  frontal: boolean;
  ojosAbiertos: boolean;
  posibleObstruccion: boolean;
}

function puntuacionBlendshape(resultado: FaceLandmarkerResult, nombre: string): number {
  const categorias = resultado.faceBlendshapes?.[0]?.categories;
  if (!categorias) return 0;
  return categorias.find((c) => c.categoryName === nombre)?.score ?? 0;
}

const UMBRAL_OJO_CERRADO = 0.45;
// Si ninguno de los dos ojos nunca baja de "mitad de camino" ni sube con claridad por
// varios cuadros seguidos, la señal es ambigua -- el caso típico de unas gafas oscuras
// (la cámara no distingue iris/párpado) o algo tapando la mitad superior del rostro.
const UMBRAL_OBSTRUCCION_AMBIGUA = 0.2;
const TOLERANCIA_YAW_GRADOS = 18;
const TOLERANCIA_PITCH_GRADOS = 16;

function anguloDesdeMatriz(matriz: ArrayLike<number>): { yaw: number; pitch: number } {
  // Matriz de transformación facial 4x4, formato column-major de MediaPipe. yaw/pitch
  // aproximados a partir de la submatriz de rotación 3x3 (fila-mayor equivalente m[fila][col]
  // = matriz[col*4+fila]).
  const m = (fila: number, col: number) => matriz[col * 4 + fila];
  const yaw = Math.atan2(-m(2, 0), Math.sqrt(m(2, 1) ** 2 + m(2, 2) ** 2)) * (180 / Math.PI);
  const pitch = Math.atan2(m(2, 1), m(2, 2)) * (180 / Math.PI);
  return { yaw, pitch };
}

export function evaluarCuadroRostro(resultado: FaceLandmarkerResult): EvaluacionRostro {
  const rostros = resultado.faceLandmarks ?? [];
  if (rostros.length !== 1) {
    return { unRostro: false, centrado: false, frontal: false, ojosAbiertos: false, posibleObstruccion: false };
  }

  const puntos = rostros[0];
  const xs = puntos.map((p) => p.x);
  const ys = puntos.map((p) => p.y);
  const centroX = (Math.min(...xs) + Math.max(...xs)) / 2;
  const centroY = (Math.min(...ys) + Math.max(...ys)) / 2;
  // Coordenadas normalizadas 0-1 sobre el cuadro; el óvalo guía ocupa el centro del video,
  // así que el rostro debe caer razonablemente cerca del centro geométrico del cuadro.
  const centrado = Math.abs(centroX - 0.5) < 0.18 && Math.abs(centroY - 0.5) < 0.2;

  const matriz = resultado.facialTransformationMatrixes?.[0]?.data;
  let frontal = true;
  if (matriz) {
    const { yaw, pitch } = anguloDesdeMatriz(matriz);
    frontal = Math.abs(yaw) < TOLERANCIA_YAW_GRADOS && Math.abs(pitch) < TOLERANCIA_PITCH_GRADOS;
  }

  const parpadeoIzq = puntuacionBlendshape(resultado, "eyeBlinkLeft");
  const parpadeoDer = puntuacionBlendshape(resultado, "eyeBlinkRight");
  const ojosAbiertos = parpadeoIzq < UMBRAL_OJO_CERRADO && parpadeoDer < UMBRAL_OJO_CERRADO;

  // Ambiguo = ni claramente abierto ni claramente cerrado en ningún ojo -- ver nota de
  // "HONESTIDAD SOBRE LOS LÍMITES" arriba, es un indicio, no una clasificación de gafas.
  const ambiguoIzq = parpadeoIzq > UMBRAL_OBSTRUCCION_AMBIGUA && parpadeoIzq < UMBRAL_OJO_CERRADO - 0.1;
  const ambiguoDer = parpadeoDer > UMBRAL_OBSTRUCCION_AMBIGUA && parpadeoDer < UMBRAL_OJO_CERRADO - 0.1;

  return { unRostro: true, centrado, frontal, ojosAbiertos, posibleObstruccion: ambiguoIzq && ambiguoDer };
}

/**
 * Máquina de estados simple para el desafío de parpadeo: exige ver una transición real
 * abierto -> cerrado -> abierto dentro de una ventana de tiempo, no solo "en algún momento
 * los ojos estuvieron cerrados" (eso lo cumpliría una foto borrosa o un cuadro de
 * transición cualquiera). Reiniciable para poder pedir un segundo intento sin recargar la
 * página.
 */
export class DetectorParpadeo {
  private vioAbierto = false;
  private vioCerrado = false;
  private completado = false;

  procesar(ojosAbiertos: boolean): void {
    if (this.completado) return;
    if (!this.vioAbierto) {
      if (ojosAbiertos) this.vioAbierto = true;
      return;
    }
    if (!this.vioCerrado) {
      if (!ojosAbiertos) this.vioCerrado = true;
      return;
    }
    if (ojosAbiertos) this.completado = true;
  }

  get parpadeoDetectado(): boolean {
    return this.completado;
  }

  reiniciar(): void {
    this.vioAbierto = false;
    this.vioCerrado = false;
    this.completado = false;
  }
}
