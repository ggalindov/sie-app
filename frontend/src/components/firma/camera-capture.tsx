"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Camera, ArrowClockwise, WarningCircle } from "@phosphor-icons/react";
import { toast } from "sonner";
import { evaluarRecuadroDocumento } from "@/lib/firma/calidad-documento";
import { cargarFaceLandmarker, evaluarCuadroRostro, DetectorParpadeo } from "@/lib/firma/deteccion-rostro";

// Captura una foto desde la cámara del dispositivo (verificación humana / documento de
// identidad) sin depender de <input type="file" capture>, que en algunos navegadores de
// escritorio solo abre el selector de archivos. getUserMedia funciona igual en escritorio
// y móvil, PERO el acceso real a este paso de firma sí está restringido a celular: ver
// MobileOnlySignatureFilter en el backend y esDispositivoMovil() en page.tsx -- este
// componente no decide esa restricción, solo la hereda (si se renderiza, es porque ya se
// pasó ese filtro).
//
// Verificación en vivo, cuadro a cuadro, antes de habilitar el botón de captura: para
// "documento" evalúa nitidez/contraste/borde del recuadro guía (ver lib/firma/calidad-
// documento.ts) y recorta la foto final exactamente a ese recuadro; para "selfie" corre
// un modelo de landmarks faciales en el navegador (ver lib/firma/deteccion-rostro.ts) que
// exige un rostro, centrado, de frente, con los ojos abiertos y un parpadeo deliberado
// dentro de la sesión -- y si ese modelo no carga (red lenta, navegador incompatible), se
// degrada con aviso en vez de bloquear la firma por completo.
export function CameraCapture({
  onCapturar,
  etiqueta,
  // "environment" (cámara trasera) para fotografiar un documento físico; "user" (cámara
  // frontal/selfie) para la verificación humana.
  facingMode = "environment",
}: {
  onCapturar: (foto: Blob) => void;
  etiqueta: string;
  facingMode?: "user" | "environment";
}) {
  const esDocumento = facingMode === "environment";
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const analisisRef = useRef<HTMLCanvasElement | null>(null);
  const rafRef = useRef<number | null>(null);
  const detectorParpadeoRef = useRef(new DetectorParpadeo());
  const aprobacionesSeguidasRef = useRef(0);

  const [activa, setActiva] = useState(false);
  const [vistaPrevia, setVistaPrevia] = useState<string | null>(null);
  const [errorCamara, setErrorCamara] = useState(false);
  const [listoParaCapturar, setListoParaCapturar] = useState(false);
  const [mensajeGuia, setMensajeGuia] = useState("Preparando verificación...");
  const [modeloDisponible, setModeloDisponible] = useState<"cargando" | "listo" | "no_disponible">(
    esDocumento ? "listo" : "cargando",
  );

  useEffect(() => {
    return () => detener();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (activa && streamRef.current && videoRef.current) {
      videoRef.current.srcObject = streamRef.current;
      videoRef.current.play().catch(() => undefined);
    }
  }, [activa]);

  // El modelo de landmarks faciales (~3-4MB) solo hace falta en modo selfie, y solo se
  // intenta cargar una vez por montaje. Si falla (red, navegador sin WebAssembly/GPU), no
  // debe bloquear la firma -- se degrada a captura manual con aviso, ver "no_disponible".
  useEffect(() => {
    if (!esDocumento && modeloDisponible === "cargando") {
      let cancelado = false;
      const limite = setTimeout(() => {
        if (!cancelado) setModeloDisponible((actual) => (actual === "cargando" ? "no_disponible" : actual));
      }, 9000);
      cargarFaceLandmarker()
        .then(() => {
          if (!cancelado) {
            clearTimeout(limite);
            setModeloDisponible("listo");
          }
        })
        .catch(() => {
          if (!cancelado) setModeloDisponible("no_disponible");
        });
      return () => {
        cancelado = true;
        clearTimeout(limite);
      };
    }
  }, [esDocumento, modeloDisponible]);

  const detener = useCallback(() => {
    if (rafRef.current !== null) cancelAnimationFrame(rafRef.current);
    rafRef.current = null;
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    setActiva(false);
    setListoParaCapturar(false);
    aprobacionesSeguidasRef.current = 0;
    detectorParpadeoRef.current.reiniciar();
  }, []);

  async function iniciar() {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode }, audio: false });
      streamRef.current = stream;
      setActiva(true);
      setErrorCamara(false);
    } catch {
      setErrorCamara(true);
      toast.error("No se pudo acceder a la cámara. Revisa los permisos del navegador.");
    }
  }

  // Bucle de análisis en vivo: corre con requestAnimationFrame (no setInterval) para que se
  // pause solo cuando la pestaña no está visible, pero el trabajo pesado (evaluar nitidez o
  // correr el modelo) se limita a ~5 veces por segundo -- correrlo en cada frame de video a
  // 30-60fps no aporta nada aquí y sí gasta batería del celular innecesariamente.
  useEffect(() => {
    if (!activa) return;
    if (!esDocumento && modeloDisponible === "no_disponible") {
      setListoParaCapturar(true);
      setMensajeGuia("No pudimos cargar la verificación automática. Puedes tomar la foto igual.");
      return;
    }
    if (!esDocumento && modeloDisponible === "cargando") {
      setMensajeGuia("Preparando verificación...");
      return;
    }

    let ultimaEjecucion = 0;
    const INTERVALO_MS = 200;
    const UMBRAL_APROBACIONES_SEGUIDAS = 2;

    async function cuadro(timestamp: number) {
      const video = videoRef.current;
      if (!video || !video.videoWidth) {
        rafRef.current = requestAnimationFrame(cuadro);
        return;
      }
      if (timestamp - ultimaEjecucion < INTERVALO_MS) {
        rafRef.current = requestAnimationFrame(cuadro);
        return;
      }
      ultimaEjecucion = timestamp;

      if (esDocumento) {
        evaluarCuadroDocumentoEnVivo(video, analisisRef);
      } else {
        await evaluarCuadroSelfieEnVivo(video, timestamp);
      }
      rafRef.current = requestAnimationFrame(cuadro);
    }

    function evaluarCuadroDocumentoEnVivo(video: HTMLVideoElement, canvasRef: typeof analisisRef) {
      if (!canvasRef.current) canvasRef.current = document.createElement("canvas");
      const canvas = canvasRef.current;
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      const ctx = canvas.getContext("2d", { willReadFrequently: true });
      if (!ctx) return;
      ctx.drawImage(video, 0, 0);

      // Mismo recuadro guía que se dibuja en CSS más abajo: 86% de ancho, proporción de
      // cédula 1.586:1, centrado.
      const anchoRecuadro = video.videoWidth * 0.86;
      const altoRecuadro = anchoRecuadro / 1.586;
      const x = (video.videoWidth - anchoRecuadro) / 2;
      const y = (video.videoHeight - altoRecuadro) / 2;

      const margenInterior = 0.12;
      const interior = ctx.getImageData(
        x + anchoRecuadro * margenInterior,
        y + altoRecuadro * margenInterior,
        anchoRecuadro * (1 - 2 * margenInterior),
        altoRecuadro * (1 - 2 * margenInterior),
      );

      const bandaAncho = anchoRecuadro * 0.12;
      const bandaX = Math.max(0, x - bandaAncho / 2);
      const bandaAlturaDisponible = Math.min(canvas.height - y, altoRecuadro);
      const bandaBorde = ctx.getImageData(
        bandaX,
        y,
        Math.min(bandaAncho, canvas.width - bandaX),
        Math.max(1, bandaAlturaDisponible),
      );

      const evaluacion = evaluarRecuadroDocumento(interior, bandaBorde);
      if (evaluacion.aprobado) {
        aprobacionesSeguidasRef.current += 1;
      } else {
        aprobacionesSeguidasRef.current = 0;
      }
      const listo = aprobacionesSeguidasRef.current >= UMBRAL_APROBACIONES_SEGUIDAS;
      setListoParaCapturar(listo);
      if (listo) {
        setMensajeGuia("Encuadre correcto, puedes tomar la foto.");
      } else if (evaluacion.contraste < 22) {
        setMensajeGuia("Acerca más el documento, que llene el recuadro.");
      } else if (evaluacion.nitidez < 18) {
        setMensajeGuia("Mantén el documento quieto, se ve borroso.");
      } else {
        setMensajeGuia("Alinea los bordes del documento con el recuadro.");
      }
    }

    async function evaluarCuadroSelfieEnVivo(video: HTMLVideoElement, timestamp: number) {
      try {
        const modelo = await cargarFaceLandmarker();
        const resultado = modelo.detectForVideo(video, timestamp);
        const evaluacion = evaluarCuadroRostro(resultado);
        detectorParpadeoRef.current.procesar(evaluacion.ojosAbiertos);
        const parpadeoOk = detectorParpadeoRef.current.parpadeoDetectado;

        if (!evaluacion.unRostro) {
          setMensajeGuia("No detectamos tu rostro, ajusta la posición.");
        } else if (!evaluacion.centrado) {
          setMensajeGuia("Centra tu rostro dentro del óvalo.");
        } else if (!evaluacion.frontal) {
          setMensajeGuia("Mira directo a la cámara.");
        } else if (evaluacion.posibleObstruccion) {
          setMensajeGuia("Quítate gafas oscuras, gorra o cualquier cosa que cubra tu rostro.");
        } else if (!evaluacion.ojosAbiertos) {
          setMensajeGuia("Mantén los ojos abiertos.");
        } else if (!parpadeoOk) {
          setMensajeGuia("Parpadea con naturalidad para continuar.");
        } else {
          setMensajeGuia("Listo, puedes tomar la foto.");
        }

        const listo =
          evaluacion.unRostro &&
          evaluacion.centrado &&
          evaluacion.frontal &&
          evaluacion.ojosAbiertos &&
          !evaluacion.posibleObstruccion &&
          parpadeoOk;
        if (listo) {
          aprobacionesSeguidasRef.current += 1;
        } else {
          aprobacionesSeguidasRef.current = 0;
        }
        setListoParaCapturar(aprobacionesSeguidasRef.current >= 1);
      } catch {
        // El modelo pudo haberse descargado pero fallar en tiempo de inferencia en algún
        // dispositivo raro -- no queremos dejar a alguien atascado sin poder firmar.
        setModeloDisponible("no_disponible");
      }
    }

    rafRef.current = requestAnimationFrame(cuadro);
    return () => {
      if (rafRef.current !== null) cancelAnimationFrame(rafRef.current);
    };
  }, [activa, esDocumento, modeloDisponible]);

  function capturar() {
    const video = videoRef.current;
    if (!video) return;
    const canvas = document.createElement("canvas");

    if (esDocumento) {
      // Recorte exacto al recuadro guía (mismo cálculo que evaluarCuadroDocumentoEnVivo) --
      // ya no se sube el cuadro completo de la cámara, solo el documento.
      const anchoRecuadro = video.videoWidth * 0.86;
      const altoRecuadro = anchoRecuadro / 1.586;
      const x = (video.videoWidth - anchoRecuadro) / 2;
      const y = (video.videoHeight - altoRecuadro) / 2;
      canvas.width = anchoRecuadro;
      canvas.height = altoRecuadro;
      const ctx = canvas.getContext("2d")!;
      ctx.drawImage(video, x, y, anchoRecuadro, altoRecuadro, 0, 0, anchoRecuadro, altoRecuadro);
    } else {
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      const ctx = canvas.getContext("2d")!;
      ctx.drawImage(video, 0, 0);
    }

    canvas.toBlob(
      (blob) => {
        if (!blob) return;
        setVistaPrevia(URL.createObjectURL(blob));
        onCapturar(blob);
        detener();
      },
      "image/jpeg",
      0.88,
    );
  }

  function repetir() {
    setVistaPrevia(null);
    iniciar();
  }

  if (vistaPrevia) {
    return (
      <div>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={vistaPrevia} alt={etiqueta} className="w-full rounded-xl border border-line" />
        <button type="button" onClick={repetir} className="mt-2 inline-flex items-center gap-1.5 text-xs font-medium text-ink-soft hover:text-ink">
          <ArrowClockwise className="h-3.5 w-3.5" weight="light" /> Tomar otra foto
        </button>
      </div>
    );
  }

  if (!activa) {
    return (
      <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-line bg-paper py-10 text-center">
        <Camera className="h-6 w-6 text-ink-soft" weight="light" />
        <p className="max-w-xs text-sm text-ink-soft">{etiqueta}</p>
        <button type="button" onClick={iniciar} className="rounded-lg bg-ink px-4 py-2 text-sm font-medium text-paper">
          Activar cámara
        </button>
        {errorCamara && (
          <p className="max-w-xs text-xs text-red-700">
            Si tu navegador sigue sin pedir permiso, revisa la configuración de cámara del sitio.
          </p>
        )}
      </div>
    );
  }

  return (
    <div>
      <div className="relative overflow-hidden rounded-xl border border-line bg-black">
        <video ref={videoRef} className={`w-full ${facingMode === "user" ? "-scale-x-100" : ""}`} muted playsInline />
        {esDocumento ? (
          <div
            aria-hidden
            className="pointer-events-none absolute left-1/2 top-1/2 w-[86%] -translate-x-1/2 -translate-y-1/2 rounded-lg border-[3px] border-dashed transition-colors"
            style={{
              aspectRatio: "1.586 / 1",
              borderColor: listoParaCapturar ? "#22c55e" : "#d9a925",
              boxShadow: "0 0 0 999px rgba(0,0,0,0.55)",
            }}
          />
        ) : (
          <div
            aria-hidden
            className="pointer-events-none absolute left-1/2 top-1/2 w-[64%] -translate-x-1/2 -translate-y-1/2 rounded-full border-[3px] border-dashed transition-colors"
            style={{
              aspectRatio: "3 / 4",
              borderColor: listoParaCapturar ? "#22c55e" : "#d9a925",
              boxShadow: "0 0 0 999px rgba(0,0,0,0.55)",
            }}
          />
        )}
        <p className="pointer-events-none absolute inset-x-0 bottom-3 px-3 text-center text-xs font-medium text-white drop-shadow">
          {mensajeGuia}
        </p>
        <button
          type="button"
          onClick={capturar}
          disabled={!listoParaCapturar}
          className="absolute bottom-12 left-1/2 flex h-14 w-14 -translate-x-1/2 items-center justify-center rounded-full bg-white shadow-lg transition-opacity disabled:opacity-40"
          aria-label="Tomar foto"
        >
          <span className="h-11 w-11 rounded-full border-2 border-ink" />
        </button>
      </div>
      {!esDocumento && modeloDisponible === "no_disponible" && (
        <p className="mt-2 flex items-start gap-1.5 text-xs text-amber-700">
          <WarningCircle className="mt-0.5 h-3.5 w-3.5 shrink-0" weight="fill" />
          No pudimos cargar la verificación automática en este dispositivo. Puedes continuar: el resto de la
          evidencia del proceso (ubicación, documento, firma) sigue aplicando igual.
        </p>
      )}
    </div>
  );
}
