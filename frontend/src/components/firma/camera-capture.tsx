"use client";

import { useEffect, useRef, useState } from "react";
import { Camera, ArrowClockwise } from "@phosphor-icons/react";
import { toast } from "sonner";

// Captura una foto desde la cámara del dispositivo (verificación humana / documento de
// identidad) sin depender de <input type="file" capture>, que en algunos navegadores de
// escritorio solo abre el selector de archivos. getUserMedia funciona igual en escritorio
// y móvil -- coherente con la decisión de NO bloquear la firma a solo celulares (ver
// CLAUDE.md, decisión 8 del motor de firma).
export function CameraCapture({
  onCapturar,
  etiqueta,
  // "environment" (cámara trasera) para fotografiar un documento físico; "user" (cámara
  // frontal/selfie) para la verificación humana. Antes quedaba fijo en "user" para los dos
  // casos -- funcional, pero forzaba a sostener el documento mirando hacia la pantalla en
  // vez de hacia la cámara trasera, normalmente de mejor calidad para este uso.
  facingMode = "environment",
}: {
  onCapturar: (foto: Blob) => void;
  etiqueta: string;
  facingMode?: "user" | "environment";
}) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [activa, setActiva] = useState(false);
  const [vistaPrevia, setVistaPrevia] = useState<string | null>(null);
  const [errorCamara, setErrorCamara] = useState(false);

  useEffect(() => {
    return () => detener();
  }, []);

  // Bug real encontrado: el <video> solo existe en el DOM cuando `activa` ya es true (ver
  // el return de más abajo), pero antes se intentaba asignarle srcObject ANTES de llamar
  // setActiva(true) -- en ese momento videoRef.current todavía era null (el elemento ni
  // siquiera se había montado), así que el `if (videoRef.current)` lo saltaba en silencio
  // y la cámara quedaba en negro para siempre, aunque el permiso sí se hubiera concedido.
  // Ahora el stream se conecta en un efecto que corre DESPUÉS de que `activa` cambia y el
  // <video> ya existe.
  useEffect(() => {
    if (activa && streamRef.current && videoRef.current) {
      videoRef.current.srcObject = streamRef.current;
      videoRef.current.play().catch(() => undefined);
    }
  }, [activa]);

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

  function detener() {
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    setActiva(false);
  }

  function capturar() {
    const video = videoRef.current;
    if (!video) return;
    const canvas = document.createElement("canvas");
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const ctx = canvas.getContext("2d")!;
    ctx.drawImage(video, 0, 0);
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
    <div className="relative overflow-hidden rounded-xl border border-line bg-black">
      {/* El espejo (-scale-x-100) solo tiene sentido para la cámara frontal (como un
          espejo real); con la trasera, un documento con texto se vería al revés mientras
          se encuadra -- la foto capturada nunca queda espejada de todas formas, porque
          canvas.drawImage lee los píxeles reales del video, no el CSS aplicado. */}
      <video ref={videoRef} className={`w-full ${facingMode === "user" ? "-scale-x-100" : ""}`} muted playsInline />
      <button
        type="button"
        onClick={capturar}
        className="absolute bottom-4 left-1/2 flex h-14 w-14 -translate-x-1/2 items-center justify-center rounded-full bg-white shadow-lg"
        aria-label="Tomar foto"
      >
        <span className="h-11 w-11 rounded-full border-2 border-ink" />
      </button>
    </div>
  );
}
