"use client";

import { useRef, useState, type PointerEvent as ReactPointerEvent } from "react";
import { Eraser } from "@phosphor-icons/react";

export type PuntoTrazo = { x: number; y: number; t: number; p: number };

// Lienzo de firma manuscrita, reutilizado tanto en "Mi firma" (perfil de staff) como en el
// wizard público del firmante. Captura el trazo vectorial (x/y/tiempo/presión) además del
// PNG final -- el trazo es evidencia adicional (ver EvidenceCaptureService en el backend),
// nunca se expone por ningún endpoint público, solo se envía cifrado al firmar.
export function SignatureCanvas({
  onCambio,
  ancho = 640,
  alto = 220,
}: {
  onCambio: (png: Blob | null, trazo: PuntoTrazo[] | null) => void;
  ancho?: number;
  alto?: number;
}) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const dibujando = useRef(false);
  const trazoActual = useRef<PuntoTrazo[]>([]);
  const trazoCompleto = useRef<PuntoTrazo[]>([]);
  const [vacio, setVacio] = useState(true);

  function coordenadas(e: ReactPointerEvent<HTMLCanvasElement>) {
    const canvas = canvasRef.current!;
    const rect = canvas.getBoundingClientRect();
    return {
      x: ((e.clientX - rect.left) / rect.width) * ancho,
      y: ((e.clientY - rect.top) / rect.height) * alto,
      t: Date.now(),
      p: e.pressure || 0.5,
    };
  }

  function onPointerDown(e: ReactPointerEvent<HTMLCanvasElement>) {
    dibujando.current = true;
    const punto = coordenadas(e);
    trazoActual.current = [punto];
    const ctx = canvasRef.current!.getContext("2d")!;
    ctx.beginPath();
    ctx.moveTo(punto.x, punto.y);
    setVacio(false);
  }

  function onPointerMove(e: ReactPointerEvent<HTMLCanvasElement>) {
    if (!dibujando.current) return;
    const punto = coordenadas(e);
    trazoActual.current.push(punto);
    const ctx = canvasRef.current!.getContext("2d")!;
    ctx.lineWidth = 2.4;
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    ctx.strokeStyle = "#1a1a1a";
    ctx.lineTo(punto.x, punto.y);
    ctx.stroke();
  }

  function onPointerUp() {
    if (!dibujando.current) return;
    dibujando.current = false;
    trazoCompleto.current = [...trazoCompleto.current, ...trazoActual.current];
    emitir();
  }

  function emitir() {
    const canvas = canvasRef.current!;
    canvas.toBlob((blob) => onCambio(blob, trazoCompleto.current.length > 0 ? trazoCompleto.current : null), "image/png");
  }

  function limpiar() {
    const canvas = canvasRef.current!;
    const ctx = canvas.getContext("2d")!;
    ctx.clearRect(0, 0, ancho, alto);
    trazoCompleto.current = [];
    setVacio(true);
    onCambio(null, null);
  }

  return (
    <div>
      <div className="relative overflow-hidden rounded-xl border border-dashed border-line bg-white">
        <canvas
          ref={canvasRef}
          width={ancho}
          height={alto}
          className="h-[180px] w-full touch-none sm:h-[220px]"
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerUp}
          onPointerLeave={onPointerUp}
        />
        {vacio && (
          <p className="pointer-events-none absolute inset-0 flex items-center justify-center text-sm text-ink-soft/50">
            Firma aquí con el mouse, el dedo o el lápiz óptico
          </p>
        )}
      </div>
      <button
        type="button"
        onClick={limpiar}
        className="mt-2 inline-flex items-center gap-1.5 text-xs font-medium text-ink-soft hover:text-ink"
      >
        <Eraser className="h-3.5 w-3.5" weight="light" /> Limpiar y empezar de nuevo
      </button>
    </div>
  );
}
