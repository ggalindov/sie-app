"use client";

import { useEffect, useRef, useState } from "react";
import { Camera, ArrowClockwise } from "@phosphor-icons/react";
import { toast } from "sonner";

// Captura una foto desde la cámara del dispositivo (verificación humana / documento de
// identidad) sin depender de <input type="file" capture>, que en algunos navegadores de
// escritorio solo abre el selector de archivos. getUserMedia funciona igual en escritorio
// y móvil -- coherente con la decisión de NO bloquear la firma a solo celulares (ver
// CLAUDE.md, decisión 8 del motor de firma).
export function CameraCapture({ onCapturar, etiqueta }: { onCapturar: (foto: Blob) => void; etiqueta: string }) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [activa, setActiva] = useState(false);
  const [vistaPrevia, setVistaPrevia] = useState<string | null>(null);
  const [errorCamara, setErrorCamara] = useState(false);

  useEffect(() => {
    return () => detener();
  }, []);

  async function iniciar() {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "user" }, audio: false });
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play();
      }
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
      <video ref={videoRef} className="w-full -scale-x-100" muted playsInline />
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
